package contratos;

import contratos.domain.AppUser;
import contratos.domain.AuditLog;
import contratos.domain.Contract;
import contratos.domain.ContractAttachment;
import contratos.domain.Sector;
import contratos.domain.enums.AttachmentType;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import contratos.domain.enums.ContractStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * TA-10.3b — {@code PUT /api/contracts/{id}/amendments/{attachmentId}/file} (substituir o documento do Termo Aditivo)
 * e o bloqueio de exclusão desse documento em {@code DELETE /api/attachment/{id}}. JWT real, H2 isolado ("amenddocapitest"),
 * sem transação no teste (cada requisição grava e confirma de verdade).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:amenddocapitest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ContractAmendmentDocumentIntegrationTest {

    private static final LocalDate END = LocalDate.of(2027, 6, 1);
    private static final byte[] OLD = "%PDF-1.4 documento errado".getBytes();
    private static final byte[] NEW = "%PDF-1.4 documento certo".getBytes();

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
    @Autowired PlatformTransactionManager tm;

    MockMvc mvc;
    TransactionTemplate tx;
    AppUser admin, ci, fiscal;
    Long contractId, otherContractId, aditivoId, geralId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        tx = new TransactionTemplate(tm);

        audit.deleteAll();
        history.deleteAll();
        interests.deleteAll();
        opinions.deleteAll();
        attachments.deleteAll();
        contracts.deleteAll();
        users.deleteAll();
        sectors.deleteAll();

        Sector setor = sectors.save(new Sector("Setor Doc Aditivo"));
        admin = users.save(user("admin.doc@test.local", "Admin Doc", setor, PerfilUsuario.ADMIN));
        ci = users.save(user("ci.doc@test.local", "CI Doc", setor, PerfilUsuario.CONTROLE_INTERNO));
        fiscal = users.save(user("fiscal.doc@test.local", "Fiscal Doc", setor, PerfilUsuario.FISCAL));

        // Contrato já aditivado: em vigência, TA "2", com o documento do aditivo enviado pelo Admin.
        Contract c = contract("DOC-001/2026");
        contractId = contracts.save(c).getId();
        otherContractId = contracts.save(contract("DOC-002/2026")).getId();
        aditivoId = attachments.save(new ContractAttachment(c, "errado.pdf", "application/pdf", OLD.length, OLD,
                AttachmentType.TERMO_ADITIVO, admin)).getId();
        geralId = attachments.save(new ContractAttachment(c, "comum.pdf", "application/pdf", OLD.length, OLD,
                AttachmentType.GERAL, admin)).getId();
    }

    // ------------------------------------------------------------------ substituição

    @Test
    void controleInternoSubstituiOArquivoENadaDoFluxoMuda() throws Exception {
        Contract antes = contracts.findById(contractId).orElseThrow();
        ContractStatus statusAntes = antes.getStatus();
        LocalDate fimAntes = antes.getEndDate();
        String taAntes = antes.getTa();
        long historicoAntes = history.count();
        LocalDateTime uploadAntes = attachments.findById(aditivoId).orElseThrow().getUploadedAt();

        Thread.sleep(20); // garante que "agora" seja estritamente depois do envio original
        MockHttpServletResponse response = replace(contractId, aditivoId, ci, pdf("certo.pdf", NEW));

        assertThat(response.getStatus()).isEqualTo(204);

        // mesma linha, conteúdo novo, dono da troca = quem substituiu
        assertThat(attachments.count()).isEqualTo(2);
        ContractAttachment depois = attachments.findById(aditivoId).orElseThrow();
        assertThat(depois.getFileName()).isEqualTo("certo.pdf");
        assertThat(depois.getContent()).isEqualTo(NEW);
        assertThat(depois.getSizeBytes()).isEqualTo(NEW.length);
        assertThat(depois.getAttType()).isEqualTo(AttachmentType.TERMO_ADITIVO);
        assertThat(depois.isAtivo()).isTrue();
        assertThat(depois.getUploadedAt()).isAfter(uploadAntes);
        tx.executeWithoutResult(s -> assertThat(attachments.findById(aditivoId).orElseThrow().getUploadedBy().getId())
                .isEqualTo(ci.getId()));

        // o fluxo do contrato não é tocado
        Contract c = contracts.findById(contractId).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(statusAntes);
        assertThat(c.getEndDate()).isEqualTo(fimAntes);
        assertThat(c.getTa()).isEqualTo(taAntes);
        assertThat(history.count()).isEqualTo(historicoAntes);

        // o anexo comum do mesmo contrato também não é tocado
        ContractAttachment geral = attachments.findById(geralId).orElseThrow();
        assertThat(geral.getFileName()).isEqualTo("comum.pdf");
        assertThat(geral.getContent()).isEqualTo(OLD);
    }

    @Test
    void substituicaoGeraUmaLinhaDeAuditoriaComONomeAnteriorENovo() throws Exception {
        assertThat(replace(contractId, aditivoId, admin, pdf("certo.pdf", NEW)).getStatus()).isEqualTo(204);

        List<AuditLog> logs = audit.findAll().stream().filter(a -> contractId.equals(a.getContractId())).toList();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(logs.get(0).getEntityType()).isEqualTo(AuditEntityType.CONTRACT);
        assertThat(logs.get(0).getActorId()).isEqualTo(admin.getId());
        assertThat(logs.get(0).getSummary()).contains("Documento do Termo Aditivo substituído").contains("DOC-001/2026");
        assertThat(logs.get(0).getDetails()).contains("errado.pdf").contains("certo.pdf");
    }

    @Test
    void podeSubstituirMaisDeUmaVezEMantemOLimiteDeAnexos() throws Exception {
        assertThat(replace(contractId, aditivoId, ci, pdf("segundo.pdf", NEW)).getStatus()).isEqualTo(204);
        assertThat(replace(contractId, aditivoId, admin, pdf("terceiro.pdf", OLD)).getStatus()).isEqualTo(204);

        ContractAttachment a = attachments.findById(aditivoId).orElseThrow();
        assertThat(a.getFileName()).isEqualTo("terceiro.pdf");
        assertThat(attachments.count()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ perfis

    @Test
    void fiscalESemTokenNaoSubstituem() throws Exception {
        assertThat(replace(contractId, aditivoId, fiscal, pdf("certo.pdf", NEW)).getStatus()).isEqualTo(403);
        assertThat(replace(contractId, aditivoId, null, pdf("certo.pdf", NEW)).getStatus()).isEqualTo(403);

        assertThat(attachments.findById(aditivoId).orElseThrow().getFileName()).isEqualTo("errado.pdf");
    }

    // ------------------------------------------------------------------ recusas

    @Test
    void anexoQueNaoEDoAditivoResponde409() throws Exception {
        MockHttpServletResponse response = replace(contractId, geralId, ci, pdf("certo.pdf", NEW));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("Termo Aditivo ativo");
        assertThat(attachments.findById(geralId).orElseThrow().getContent()).isEqualTo(OLD);
    }

    @Test
    void aditivoJaRemovidoResponde409() throws Exception {
        tx.executeWithoutResult(s -> attachments.findById(aditivoId).orElseThrow().removeAttachment(admin));

        MockHttpServletResponse response = replace(contractId, aditivoId, ci, pdf("certo.pdf", NEW));

        assertThat(response.getStatus()).isEqualTo(409);
        ContractAttachment a = attachments.findById(aditivoId).orElseThrow();
        assertThat(a.isAtivo()).isFalse();
        assertThat(a.getFileName()).isEqualTo("errado.pdf"); // a substituição recusada não ressuscita nem renomeia
    }

    @Test
    void anexoDeOutroContratoOuInexistenteResponde404() throws Exception {
        assertThat(replace(otherContractId, aditivoId, ci, pdf("certo.pdf", NEW)).getStatus()).isEqualTo(404);
        assertThat(replace(contractId, 999_999L, ci, pdf("certo.pdf", NEW)).getStatus()).isEqualTo(404);

        assertThat(attachments.findById(aditivoId).orElseThrow().getContent()).isEqualTo(OLD);
    }

    @Test
    void arquivoVazioOuDeTipoErradoResponde400ENadaMuda() throws Exception {
        assertThat(replace(contractId, aditivoId, ci,
                new MockMultipartFile("file", "vazio.pdf", "application/pdf", new byte[0])).getStatus()).isEqualTo(400);
        assertThat(replace(contractId, aditivoId, ci,
                new MockMultipartFile("file", "nota.txt", "text/plain", "oi".getBytes())).getStatus()).isEqualTo(400);

        ContractAttachment a = attachments.findById(aditivoId).orElseThrow();
        assertThat(a.getFileName()).isEqualTo("errado.pdf");
        assertThat(a.getContent()).isEqualTo(OLD);
        assertThat(audit.findAll().stream().filter(l -> contractId.equals(l.getContractId()))).isEmpty();
    }

    // ------------------------------------------------------------------ exclusão bloqueada

    @Test
    void documentoDoAditivoNaoPodeSerExcluidoMasOComumPode() throws Exception {
        MockHttpServletResponse bloqueado = mvc.perform(delete("/api/attachment/" + aditivoId)
                .header("Authorization", bearer(admin))).andReturn().getResponse();

        assertThat(bloqueado.getStatus()).isEqualTo(409);
        assertThat(bloqueado.getContentAsString()).contains("Substituir documento");
        assertThat(attachments.findById(aditivoId).orElseThrow().isAtivo()).isTrue();

        MockHttpServletResponse comum = mvc.perform(delete("/api/attachment/" + geralId)
                .header("Authorization", bearer(admin))).andReturn().getResponse();

        assertThat(comum.getStatus()).isEqualTo(204);
        assertThat(attachments.findById(geralId).orElseThrow().isAtivo()).isFalse();
    }

    // ------------------------------------------------------------------ apoio

    private String bearer(AppUser as) {
        return "Bearer " + jwt.generate(as.getUsername(), as.getName());
    }

    private static MockMultipartFile pdf(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/pdf", content);
    }

    private MockHttpServletResponse replace(Long contractId, Long attachmentId, AppUser as, MockMultipartFile file) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart(HttpMethod.PUT,
                "/api/contracts/" + contractId + "/amendments/" + attachmentId + "/file");
        if (file != null) req.file(file);
        if (as != null) req.header("Authorization", bearer(as));
        return mvc.perform(req).andReturn().getResponse();
    }

    private Contract contract(String number) {
        Contract c = new Contract();
        c.update(number, "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"), LocalDate.of(2026, 1, 1), END,
                null, "2", Set.of(fiscal), "SEI-" + number, 12);
        assertThat(c.getStatus()).isEqualTo(ContractStatus.EM_VIGENCIA);
        return c;
    }

    private static AppUser user(String email, String name, Sector sector, PerfilUsuario perfil) {
        return new AppUser(email, "{noop}senha-irrelevante", name, email, null, sector, perfil);
    }
}
