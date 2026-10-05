package contratos;

import com.jayway.jsonpath.JsonPath;
import contratos.config.AttachmentStorageProperties;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.ContractAttachment;
import contratos.domain.Sector;
import contratos.domain.enums.AttachmentType;
import contratos.domain.enums.PerfilUsuario;
import contratos.repository.AuditLogRepository;
import contratos.repository.ContractAttachmentRepository;
import contratos.repository.ContractRepository;
import contratos.repository.ContractStatusHistoryRepository;
import contratos.repository.InterestEmailConfirmationRepository;
import contratos.repository.SectorRepository;
import contratos.repository.TechnicalOpinionRepository;
import contratos.repository.UserRepository;
import contratos.security.JwtService;
import contratos.service.AttachmentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * ANX-10.2 — o conteúdo dos anexos vai para o disco (comprimido), não para o banco: upload, download, remoção,
 * arquivo sumido do disco e exclusão de contrato. JWT real, H2 isolado
 * ("attachdiskapitest"), sem transação no teste (cada requisição grava e confirma de verdade).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:attachdiskapitest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class AttachmentDiskIntegrationTest {

    // repetido de propósito: comprime bem, então dá para provar que o arquivo em disco é menor que o original
    private static final byte[] PDF_A = "%PDF-1.4 anexo A ".repeat(60).getBytes();
    private static final byte[] PDF_B = "%PDF-1.4 anexo B".getBytes();

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired SectorRepository sectors;
    @Autowired ContractRepository contracts;
    @Autowired ContractAttachmentRepository attachments;
    @Autowired ContractStatusHistoryRepository history;
    @Autowired InterestEmailConfirmationRepository interests;
    @Autowired TechnicalOpinionRepository opinions;
    @Autowired AuditLogRepository audit;
    @Autowired AttachmentStorage storage;
    @Autowired AttachmentStorageProperties storageProps;

    MockMvc mvc;
    AppUser admin, ci, fiscal, fiscalDeOutro;
    Long contractId, otherContractId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();

        audit.deleteAll();
        history.deleteAll();
        interests.deleteAll();
        opinions.deleteAll();
        attachments.deleteAll();
        contracts.deleteAll();
        users.deleteAll();
        sectors.deleteAll();

        Sector setor = sectors.save(new Sector("Setor Disco"));
        admin = users.save(user("admin.disco@test.local", "Admin Disco", setor, PerfilUsuario.ADMIN));
        ci = users.save(user("ci.disco@test.local", "CI Disco", setor, PerfilUsuario.CONTROLE_INTERNO));
        fiscal = users.save(user("fiscal.disco@test.local", "Fiscal Disco", setor, PerfilUsuario.FISCAL));
        fiscalDeOutro = users.save(user("outro.disco@test.local", "Outro Fiscal", setor, PerfilUsuario.FISCAL));

        contractId = contracts.save(contract("DISCO-001/2026", fiscal)).getId();
        otherContractId = contracts.save(contract("DISCO-002/2026", fiscalDeOutro)).getId();
    }

    // ------------------------------------------------------------------ upload e download

    @Test
    void uploadGravaEmDiscoComprimidoNaoNoBancoEODownloadDevolveOMesmoArquivo() throws Exception {
        MockHttpServletResponse up = upload(contractId, admin, file("a.pdf", PDF_A));

        assertThat(up.getStatus()).isEqualTo(201);
        Long id = idDaPosicao(up, 0);

        ContractAttachment salvo = attachments.findById(id).orElseThrow();
        assertThat(salvo.getStoragePath()).matches(contractId + "/[0-9a-f-]{36}\\.gz");
        assertThat(salvo.getSizeBytes()).as("guarda o tamanho original").isEqualTo(PDF_A.length);
        assertThat(salvo.getAttType()).isEqualTo(AttachmentType.GERAL);
        assertThat(storage.exists(salvo.getStoragePath())).isTrue();
        assertThat(Files.size(arquivosDoContrato(contractId).getFirst())).as("comprimido em disco").isLessThan(PDF_A.length);

        MockHttpServletResponse down = download(id, admin);
        assertThat(down.getStatus()).isEqualTo(200);
        assertThat(down.getContentAsByteArray()).isEqualTo(PDF_A);
        assertThat(down.getContentType()).isEqualTo("application/pdf");
        assertThat(down.getHeader("Content-Disposition")).contains("a.pdf");
    }

    @Test
    void uploadDeDoisArquivosGravaOsDoisEmCaminhosDiferentes() throws Exception {
        MockHttpServletResponse up = upload(contractId, ci, file("a.pdf", PDF_A), file("b.pdf", PDF_B));

        assertThat(up.getStatus()).isEqualTo(201);
        ContractAttachment a = attachments.findById(idDaPosicao(up, 0)).orElseThrow();
        ContractAttachment b = attachments.findById(idDaPosicao(up, 1)).orElseThrow();
        assertThat(a.getStoragePath()).isNotEqualTo(b.getStoragePath());
        assertThat(arquivosDoContrato(contractId)).hasSize(2);
        assertThat(download(a.getId(), ci).getContentAsByteArray()).isEqualTo(PDF_A);
        assertThat(download(b.getId(), ci).getContentAsByteArray()).isEqualTo(PDF_B);
    }

    @Test
    void loteComArquivoInvalidoNoMeioNaoGravaNadaNemNoBancoNemNoDisco() throws Exception {
        MockHttpServletResponse up = upload(contractId, admin,
                file("a.pdf", PDF_A), new MockMultipartFile("files", "nota.txt", "text/plain", "oi".getBytes()));

        assertThat(up.getStatus()).isEqualTo(400);
        assertThat(attachments.countByContract_IdAndAtivoTrue(contractId)).isZero();
        assertThat(arquivosDoContrato(contractId)).isEmpty();
    }

    @Test
    void fiscalVinculadoBaixaOArquivoDoDiscoEOutroFiscalNao() throws Exception {
        Long id = idDaPosicao(upload(contractId, admin, file("a.pdf", PDF_A)), 0);

        MockHttpServletResponse vinculado = download(id, fiscal);
        MockHttpServletResponse semVinculo = download(id, fiscalDeOutro);

        assertThat(vinculado.getStatus()).isEqualTo(200);
        assertThat(vinculado.getContentAsByteArray()).isEqualTo(PDF_A);
        assertThat(semVinculo.getStatus()).isIn(403, 404);
        assertThat(semVinculo.getContentAsByteArray()).isNotEqualTo(PDF_A);
    }

    // ------------------------------------------------------------------ remoção

    @Test
    void removerApagaOArquivoDoDiscoEEsqueceOCaminho() throws Exception {
        Long id = idDaPosicao(upload(contractId, admin, file("a.pdf", PDF_A)), 0);
        String caminho = attachments.findById(id).orElseThrow().getStoragePath();

        MockHttpServletResponse response = mvc.perform(delete("/api/attachment/" + id)
                .header("Authorization", bearer(admin))).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(204);
        ContractAttachment depois = attachments.findById(id).orElseThrow();
        assertThat(depois.isAtivo()).isFalse();
        assertThat(depois.getStoragePath()).isNull();
        assertThat(depois.getFileName()).as("o histórico mantém o nome").isEqualTo("a.pdf");
        assertThat(storage.exists(caminho)).as("o arquivo some do disco depois do commit").isFalse();
        assertThat(download(id, admin).getStatus()).isEqualTo(404);
    }

    @Test
    void removerNaoMexeNosArquivosDosOutrosAnexos() throws Exception {
        MockHttpServletResponse up = upload(contractId, admin, file("a.pdf", PDF_A), file("b.pdf", PDF_B));
        Long a = idDaPosicao(up, 0);
        Long b = idDaPosicao(up, 1);
        String caminhoB = attachments.findById(b).orElseThrow().getStoragePath();

        mvc.perform(delete("/api/attachment/" + a).header("Authorization", bearer(admin)));

        assertThat(storage.exists(caminhoB)).isTrue();
        assertThat(download(b, admin).getContentAsByteArray()).isEqualTo(PDF_B);
        assertThat(arquivosDoContrato(contractId)).hasSize(1);
    }

    // ------------------------------------------------------------------ arquivo sumido do disco

    @Test
    void arquivoSumidoDoDiscoDevolve500ComMensagemGenericaSemVazarCaminho() throws Exception {
        Long id = idDaPosicao(upload(contractId, admin, file("a.pdf", PDF_A)), 0);
        String caminho = attachments.findById(id).orElseThrow().getStoragePath();
        assertThat(storage.delete(caminho)).isTrue();

        MockHttpServletResponse down = download(id, admin);

        assertThat(down.getStatus()).isEqualTo(500);
        String corpo = down.getContentAsString();
        assertThat(corpo).contains("Não foi possível ler o arquivo do anexo");
        assertThat(corpo).doesNotContain(caminho).doesNotContain(".gz").doesNotContain(storageProps.storageDir());
    }

    // ------------------------------------------------------------------ exclusão de contrato

    @Test
    void excluirContratoApagaOsArquivosDeTodosOsAnexosENaoMexeEmOutroContrato() throws Exception {
        MockHttpServletResponse up = upload(contractId, admin, file("a.pdf", PDF_A), file("b.pdf", PDF_B));
        String a = attachments.findById(idDaPosicao(up, 0)).orElseThrow().getStoragePath();
        String b = attachments.findById(idDaPosicao(up, 1)).orElseThrow().getStoragePath();
        // um anexo já removido antes (sem arquivo) não atrapalham a exclusão
        Long removido = idDaPosicao(upload(contractId, admin, file("c.pdf", PDF_B)), 0);
        mvc.perform(delete("/api/attachment/" + removido).header("Authorization", bearer(admin)));

        Long idOutro = idDaPosicao(upload(otherContractId, admin, file("o.pdf", PDF_B)), 0);
        String caminhoOutro = attachments.findById(idOutro).orElseThrow().getStoragePath();

        MockHttpServletResponse response = mvc.perform(delete("/api/contracts/" + contractId)
                .header("Authorization", bearer(admin))).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(contracts.existsById(contractId)).isFalse();
        assertThat(attachments.countByContract_IdAndAtivoTrue(contractId)).isZero();
        assertThat(storage.exists(a)).isFalse();
        assertThat(storage.exists(b)).isFalse();
        assertThat(arquivosDoContrato(contractId)).isEmpty();
        assertThat(storage.exists(caminhoOutro)).as("o outro contrato não é tocado").isTrue();
        assertThat(download(idOutro, admin).getContentAsByteArray()).isEqualTo(PDF_B);
    }

    // ------------------------------------------------------------------ apoio

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, "application/pdf", content);
    }

    private MockHttpServletResponse upload(Long contractId, AppUser as, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/attachment/" + contractId);
        for (MockMultipartFile f : files) req.file(f);
        if (as != null) req.header("Authorization", bearer(as));
        return mvc.perform(req).andReturn().getResponse();
    }

    private MockHttpServletResponse download(Long attachmentId, AppUser as) throws Exception {
        return mvc.perform(get("/api/attachment/baixar/" + attachmentId).header("Authorization", bearer(as)))
                .andReturn().getResponse();
    }

    private static Long idDaPosicao(MockHttpServletResponse upload, int posicao) throws Exception {
        return ((Number) JsonPath.read(upload.getContentAsString(), "$[" + posicao + "].id")).longValue();
    }

    private List<Path> arquivosDoContrato(Long id) throws IOException {
        Path dir = Path.of(storageProps.storageDir()).toAbsolutePath().resolve(id.toString());
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).toList();
        }
    }

    private String bearer(AppUser as) {
        return "Bearer " + jwt.generate(as.getUsername(), as.getName());
    }

    private static Contract contract(String number, AppUser fiscal) {
        Contract c = new Contract();
        c.update(number, "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 1),
                null, "2", Set.of(fiscal), "SEI-" + number, 12);
        return c;
    }

    private static AppUser user(String email, String name, Sector sector, PerfilUsuario perfil) {
        return new AppUser(email, "{noop}senha-irrelevante", name, email, null, sector, perfil);
    }
}
