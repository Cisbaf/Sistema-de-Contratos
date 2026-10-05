package contratos;

import com.jayway.jsonpath.JsonPath;
import contratos.api.dto.GeneratedDocument.GeneratedDocumentRequest;
import contratos.config.AttachmentStorageProperties;
import contratos.domain.AppUser;
import contratos.domain.AuditLog;
import contratos.domain.Contract;
import contratos.domain.Sector;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;
import contratos.domain.enums.PerfilUsuario;
import contratos.repository.AuditLogRepository;
import contratos.repository.ContractAttachmentRepository;
import contratos.repository.ContractRepository;
import contratos.repository.ContractStatusHistoryRepository;
import contratos.repository.GeneratedDocumentRepository;
import contratos.repository.InterestEmailConfirmationRepository;
import contratos.repository.LancamentoFinanceiroHistoricoRepository;
import contratos.repository.LancamentoFinanceiroRepository;
import contratos.repository.SectorRepository;
import contratos.repository.TechnicalOpinionRepository;
import contratos.repository.UserRepository;
import contratos.security.JwtService;
import contratos.service.GeneratedDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * AUD-10 — a auditoria passa a registrar anexos (envio/remoção), documentos gerados e lançamentos financeiros
 * (criação, edição, exclusão nos dois caminhos). JWT real, H2 isolado ("audittrailapitest"), sem transação no teste
 * (cada requisição grava e confirma de verdade).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:audittrailapitest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class AuditTrailIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4 conteudo-secreto-do-anexo".getBytes();

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired SectorRepository sectors;
    @Autowired ContractRepository contracts;
    @Autowired ContractAttachmentRepository attachments;
    @Autowired ContractStatusHistoryRepository history;
    @Autowired InterestEmailConfirmationRepository interests;
    @Autowired TechnicalOpinionRepository opinions;
    @Autowired LancamentoFinanceiroRepository lancamentos;
    @Autowired LancamentoFinanceiroHistoricoRepository lancamentoHistorico;
    @Autowired GeneratedDocumentRepository documents;
    @Autowired GeneratedDocumentService documentService;
    @Autowired AuditLogRepository audit;
    @Autowired AttachmentStorageProperties storageProps;

    MockMvc mvc;
    AppUser admin;
    Contract contract;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();

        audit.deleteAll();
        documents.deleteAll();
        lancamentoHistorico.deleteAll();
        lancamentos.deleteAll();
        history.deleteAll();
        interests.deleteAll();
        opinions.deleteAll();
        attachments.deleteAll();
        contracts.deleteAll();
        users.deleteAll();
        sectors.deleteAll();

        Sector setor = sectors.save(new Sector("Setor Auditoria"));
        admin = users.save(new AppUser("admin.aud@test.local", "{noop}senha-irrelevante", "Admin Auditoria",
                "admin.aud@test.local", null, setor, PerfilUsuario.ADMIN));
        AppUser fiscal = users.save(new AppUser("fiscal.aud@test.local", "{noop}senha-irrelevante", "Fiscal Auditoria",
                "fiscal.aud@test.local", null, setor, PerfilUsuario.FISCAL));

        Contract c = new Contract();
        c.update("AUD-001/2026", "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 1),
                null, "2", Set.of(fiscal), "SEI-AUD-001", 12);
        contract = contracts.save(c);
    }

    // ------------------------------------------------------------------ anexos

    @Test
    void uploadGravaUmaLinhaPorArquivoSemConteudoNemCaminho() throws Exception {
        MockHttpServletResponse up = upload(file("a.pdf"), file("b.pdf"));

        assertThat(up.getStatus()).isEqualTo(201);
        List<AuditLog> linhas = linhas(AuditAction.UPLOAD_ATTACHMENT);
        assertThat(linhas).hasSize(2);

        Long idA = ((Number) JsonPath.read(up.getContentAsString(), "$[0].id")).longValue();
        AuditLog a = linhas.stream().filter(l -> l.getEntityId().equals(idA)).findFirst().orElseThrow();
        assertThat(a.getEntityType()).isEqualTo(AuditEntityType.ATTACHMENT);
        assertThat(a.getContractId()).isEqualTo(contract.getId());
        assertThat(a.getActorEmail()).isEqualTo("admin.aud@test.local");
        assertThat(a.getSummary()).contains("AUD-001/2026").contains("a.pdf");
        assertThat(a.getDetails()).isEqualTo("Tamanho: " + PDF.length + " bytes");
        assertThat(todoOTexto(linhas)).doesNotContain(".gz").doesNotContain(storageProps.storageDir())
                .doesNotContain("conteudo-secreto");
    }

    @Test
    void loteComArquivoInvalidoNaoDeixaLinhaDeAuditoria() throws Exception {
        MockHttpServletResponse up = upload(file("a.pdf"), new MockMultipartFile("files", "nota.txt", "text/plain", "oi".getBytes()));

        assertThat(up.getStatus()).isEqualTo(400);
        assertThat(audit.findAll()).isEmpty();
    }

    @Test
    void removerGravaUmaLinhaESegundaRemocaoNaoGravaOutra() throws Exception {
        Long id = ((Number) JsonPath.read(upload(file("a.pdf")).getContentAsString(), "$[0].id")).longValue();

        assertThat(remover(id).getStatus()).isEqualTo(204);
        assertThat(remover(id).getStatus()).as("anexo já inativo: nada muda").isEqualTo(204);

        List<AuditLog> linhas = linhas(AuditAction.REMOVE_ATTACHMENT);
        assertThat(linhas).hasSize(1);
        AuditLog l = linhas.getFirst();
        assertThat(l.getEntityType()).isEqualTo(AuditEntityType.ATTACHMENT);
        assertThat(l.getEntityId()).isEqualTo(id);
        assertThat(l.getContractId()).isEqualTo(contract.getId());
        assertThat(l.getSummary()).contains("AUD-001/2026").contains("a.pdf");
    }

    // ------------------------------------------------------------------ documentos gerados

    @Test
    void gerarDocumentoGravaLinhaSemOConteudoDoPdf() {
        var response = documentService.store(new GeneratedDocumentRequest(contract.getId(),
                DocumentTemplateType.INTEREST_EMAIL, DocumentFormat.PDF, "interesse.pdf",
                "%PDF-1.4 conteudo-secreto-do-documento".getBytes()), admin.getUsername());

        List<AuditLog> linhas = linhas(AuditAction.GENERATE_DOCUMENT);
        assertThat(linhas).hasSize(1);
        AuditLog l = linhas.getFirst();
        assertThat(l.getEntityType()).isEqualTo(AuditEntityType.DOCUMENT);
        assertThat(l.getEntityId()).isEqualTo(response.id());
        assertThat(l.getContractId()).isEqualTo(contract.getId());
        assertThat(l.getSummary()).contains("INTEREST_EMAIL").contains("v1").contains("AUD-001/2026");
        assertThat(l.getDetails()).contains("Arquivo: interesse.pdf").contains("Formato: PDF");
        assertThat(todoOTexto(linhas)).doesNotContain("conteudo-secreto");
    }

    @Test
    void segundaGeracaoDoMesmoTipoGravaOutraLinhaComVersaoSeguinte() {
        var request = new GeneratedDocumentRequest(contract.getId(), DocumentTemplateType.INTEREST_EMAIL,
                DocumentFormat.PDF, "interesse.pdf", "%PDF".getBytes());
        documentService.store(request, admin.getUsername());
        documentService.store(request, admin.getUsername());

        assertThat(linhas(AuditAction.GENERATE_DOCUMENT)).extracting(AuditLog::getSummary)
                .anyMatch(s -> s.contains("v1")).anyMatch(s -> s.contains("v2"));
    }

    // ------------------------------------------------------------------ lançamentos

    @Test
    void criarLancamentoGravaLinhaComOsDados() throws Exception {
        Long id = criarLancamento("NF-1", "100.50", "Observação confidencial");

        List<AuditLog> linhas = linhas(AuditAction.CREATE);
        assertThat(linhas).hasSize(1);
        AuditLog l = linhas.getFirst();
        assertThat(l.getEntityType()).isEqualTo(AuditEntityType.LANCAMENTO);
        assertThat(l.getEntityId()).isEqualTo(id);
        assertThat(l.getContractId()).isEqualTo(contract.getId());
        assertThat(l.getSummary()).contains("AUD-001/2026").contains("NF-1");
        assertThat(l.getDetails()).contains("Nota fiscal: NF-1").contains("Processo: PROC-1")
                .contains("Competência: 03/2026").contains("Parcela: 1").contains("Valor: 100.50");
        assertThat(l.getDetails()).as("texto livre não vai para o log").doesNotContain("confidencial");
    }

    @Test
    void editarGravaSoOQueMudouENaoCopiaAsObservacoes() throws Exception {
        Long id = criarLancamento("NF-1", "100.50", "Original");

        MockHttpServletResponse res = editarLancamento(id, "NF-1", "120.00", "Mudou o texto livre");

        assertThat(res.getStatus()).isEqualTo(200);
        List<AuditLog> linhas = linhas(AuditAction.UPDATE);
        assertThat(linhas).hasSize(1);
        AuditLog l = linhas.getFirst();
        assertThat(l.getEntityType()).isEqualTo(AuditEntityType.LANCAMENTO);
        assertThat(l.getEntityId()).isEqualTo(id);
        assertThat(l.getDetails()).contains("Valor: 100.50 -> 120.00").contains("Observações alteradas")
                .doesNotContain("Nota fiscal:").doesNotContain("Mudou o texto livre").doesNotContain("Original");
    }

    @Test
    void editarSemMudancaNaoGravaLinha() throws Exception {
        Long id = criarLancamento("NF-1", "100.50", "Original");

        assertThat(editarLancamento(id, "NF-1", "100.50", "Original").getStatus()).isEqualTo(200);

        assertThat(linhas(AuditAction.UPDATE)).isEmpty();
    }

    @Test
    void excluirLancamentoNuncaEditadoGravaLinhaDeExclusaoDefinitiva() throws Exception {
        Long id = criarLancamento("NF-1", "100.50", null);

        assertThat(excluirLancamento(id).getStatus()).isEqualTo(204);

        assertThat(lancamentos.findById(id)).as("exclusão física").isEmpty();
        List<AuditLog> linhas = linhas(AuditAction.DELETE);
        assertThat(linhas).hasSize(1);
        AuditLog l = linhas.getFirst();
        assertThat(l.getEntityType()).isEqualTo(AuditEntityType.LANCAMENTO);
        assertThat(l.getEntityId()).isEqualTo(id);
        assertThat(l.getContractId()).isEqualTo(contract.getId());
        assertThat(l.getSummary()).contains("AUD-001/2026").contains("NF-1");
        assertThat(l.getDetails()).contains("Nota fiscal: NF-1").contains("Valor: 100.50")
                .contains("Competência: 03/2026").contains("Exclusão definitiva");
    }

    @Test
    void excluirLancamentoJaEditadoGravaLinhaDeDesativacao() throws Exception {
        Long id = criarLancamento("NF-1", "100.50", null);
        editarLancamento(id, "NF-1", "120.00", null);

        assertThat(excluirLancamento(id).getStatus()).isEqualTo(204);

        assertThat(lancamentos.findById(id).orElseThrow().isAtivo()).isFalse();
        List<AuditLog> linhas = linhas(AuditAction.DELETE);
        assertThat(linhas).hasSize(1);
        assertThat(linhas.getFirst().getDetails()).contains("Valor: 120.00").contains("desativado")
                .doesNotContain("Exclusão definitiva");
    }

    // ------------------------------------------------------------------ apoio

    private List<AuditLog> linhas(AuditAction action) {
        return audit.findAll().stream().filter(l -> l.getAction() == action).toList();
    }

    private static String todoOTexto(List<AuditLog> linhas) {
        StringBuilder texto = new StringBuilder();
        linhas.forEach(l -> texto.append(l.getSummary()).append(' ').append(l.getDetails()).append('\n'));
        return texto.toString();
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("files", name, "application/pdf", PDF);
    }

    private MockHttpServletResponse upload(MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/attachment/" + contract.getId());
        for (MockMultipartFile f : files) req.file(f);
        req.header("Authorization", bearer(admin));
        return mvc.perform(req).andReturn().getResponse();
    }

    private MockHttpServletResponse remover(Long id) throws Exception {
        return mvc.perform(delete("/api/attachment/" + id).header("Authorization", bearer(admin))).andReturn().getResponse();
    }

    private Long criarLancamento(String nota, String valor, String observacoes) throws Exception {
        MockHttpServletResponse res = mvc.perform(post("/api/contracts/" + contract.getId() + "/lancamentos")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(lancamentoJson(nota, valor, observacoes))).andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return ((Number) JsonPath.read(res.getContentAsString(), "$.id")).longValue();
    }

    private MockHttpServletResponse editarLancamento(Long id, String nota, String valor, String observacoes) throws Exception {
        return mvc.perform(put("/api/lancamentos/" + id).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(lancamentoJson(nota, valor, observacoes)))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse excluirLancamento(Long id) throws Exception {
        return mvc.perform(delete("/api/lancamentos/" + id).header("Authorization", bearer(admin))).andReturn().getResponse();
    }

    private static String lancamentoJson(String nota, String valor, String observacoes) {
        String obs = observacoes == null ? "null" : "\"" + observacoes + "\"";
        return "{\"numeroProcesso\":\"PROC-1\",\"notaFiscal\":\"" + nota + "\",\"parcela\":\"1\","
                + "\"competencia\":\"2026-03-15\",\"valorNota\":" + valor + ",\"observacoes\":" + obs + "}";
    }

    private String bearer(AppUser as) {
        return "Bearer " + jwt.generate(as.getUsername(), as.getName());
    }
}
