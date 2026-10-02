package contratos;

import com.jayway.jsonpath.JsonPath;
import contratos.domain.AppUser;
import contratos.domain.AuditLog;
import contratos.domain.Contract;
import contratos.domain.ContractAttachment;
import contratos.domain.ContractStatusHistory;
import contratos.domain.InterestEmailConfirmation;
import contratos.domain.Sector;
import contratos.domain.TechnicalOpinionEntry;
import contratos.domain.enums.AttachmentType;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import contratos.domain.enums.ContractStatus;
import contratos.domain.enums.ContractStatusTrigger;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * TA-10.3 — {@code POST /api/contracts/{id}/amendments}: JWT real, banco H2 em memória isolado ("amendapitest"),
 * sem transação no teste (cada requisição grava e confirma de verdade, para provar atomicidade e histórico).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:amendapitest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ContractAmendmentIntegrationTest {

    private static final LocalDate END = LocalDate.of(2026, 12, 1);
    private static final byte[] PDF = "%PDF-1.4 termo aditivo".getBytes();

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
    AppUser admin, ci, fiscal, outroFiscal;

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

        Sector setor = sectors.save(new Sector("Setor Aditivo"));
        admin = users.save(user("admin.ta@test.local", "Admin TA", setor, PerfilUsuario.ADMIN));
        ci = users.save(user("ci.ta@test.local", "CI TA", setor, PerfilUsuario.CONTROLE_INTERNO));
        fiscal = users.save(user("fiscal.ta@test.local", "Fiscal TA", setor, PerfilUsuario.FISCAL));
        outroFiscal = users.save(user("outro.ta@test.local", "Outro Fiscal", setor, PerfilUsuario.FISCAL));
    }

    // ------------------------------------------------------------------ caminho feliz

    @Test
    void controleInternoRegistraEOContratoVoltaAVigenciaComTudoGravado() throws Exception {
        Long id = contractInRenewal("03", 12);
        interests.save(new InterestEmailConfirmation(contracts.findById(id).orElseThrow(), fiscal, LocalDateTime.now()));
        opinions.save(new TechnicalOpinionEntry(contracts.findById(id).orElseThrow(), fiscal, "parecer do ciclo anterior"));

        MockHttpServletResponse response = send(id, ci, pdf("aditivo.pdf"), "2027-06-01");

        assertThat(response.getStatus()).isEqualTo(200);
        String json = response.getContentAsString();
        assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("EM_VIGENCIA");
        assertThat((String) JsonPath.read(json, "$.endDate")).isEqualTo("2027-06-01");
        assertThat((String) JsonPath.read(json, "$.ta")).isEqualTo("4");

        Contract c = contracts.findById(id).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(ContractStatus.EM_VIGENCIA);
        assertThat(c.getEndDate()).isEqualTo(LocalDate.of(2027, 6, 1));
        assertThat(c.getTa()).isEqualTo("4");

        List<ContractAttachment> anexos = attachments.findByContract_IdOrderByUploadedAtAsc(id);
        assertThat(anexos).hasSize(1);
        assertThat(anexos.get(0).getAttType()).isEqualTo(AttachmentType.TERMO_ADITIVO);
        assertThat(anexos.get(0).isAtivo()).isTrue();
        assertThat(anexos.get(0).getFileName()).isEqualTo("aditivo.pdf");
        assertThat(anexos.get(0).getContent()).isEqualTo(PDF);

        tx.executeWithoutResult(s -> {
            List<ContractStatusHistory> rows = history.findAll().stream()
                    .filter(h -> h.getContract().getId().equals(id)).toList();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getStatusTrigger()).isEqualTo(ContractStatusTrigger.ADITIVO_REGISTRADO);
            assertThat(rows.get(0).getPreviousStatus()).isEqualTo(ContractStatus.RENOVACAO_ABERTA_SEI);
            assertThat(rows.get(0).getNewStatus()).isEqualTo(ContractStatus.EM_VIGENCIA);
            assertThat(rows.get(0).getChangedBy().getId()).isEqualTo(ci.getId());
        });

        assertThat(interests.findByContract_Id(id)).isEmpty();
        assertThat(opinions.findByContract_Id(id)).isEmpty();

        List<AuditLog> logs = audit.findAll().stream().filter(a -> id.equals(a.getContractId())).toList();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(logs.get(0).getEntityType()).isEqualTo(AuditEntityType.CONTRACT);
        assertThat(logs.get(0).getActorId()).isEqualTo(ci.getId());
        assertThat(logs.get(0).getDetails())
                .contains("Término da vigência: 2026-12-01 -> 2027-06-01")
                .contains("Status: RENOVACAO_ABERTA_SEI -> EM_VIGENCIA")
                .contains("TA: 03 -> 4")
                .contains("aditivo.pdf");
    }

    @Test
    void adminTambemRegistra() throws Exception {
        Long id = contractInRenewal(null, 12);

        MockHttpServletResponse response = send(id, admin, pdf("aditivo.pdf"), "2027-03-01");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(contracts.findById(id).orElseThrow().getTa()).isEqualTo("1");
    }

    @Test
    void dataNoLimiteExatoDaProrrogacaoEAceita() throws Exception {
        Long id = contractInRenewal("1", 12); // limite = 01/12/2027

        assertThat(send(id, ci, pdf("a.pdf"), "2027-12-01").getStatus()).isEqualTo(200);
    }

    @Test
    void semLimiteDeProrrogacaoQualquerDataFuturaMaiorEAceita() throws Exception {
        Long id = contractInRenewal("1", null);

        assertThat(send(id, ci, pdf("a.pdf"), "2035-01-01").getStatus()).isEqualTo(200);
        assertThat(contracts.findById(id).orElseThrow().getEndDate()).isEqualTo(LocalDate.of(2035, 1, 1));
    }

    @Test
    void depoisDoAditivoOFiscalConfirmaInteresseDeNovoENoSegundoAditivoOTaSegue() throws Exception {
        Long id = contractInRenewal("03", null);
        interests.save(new InterestEmailConfirmation(contracts.findById(id).orElseThrow(), fiscal, LocalDateTime.now()));

        assertThat(send(id, ci, pdf("um.pdf"), "2027-06-01").getStatus()).isEqualTo(200);

        // Sem o reset, esta confirmação bateria na chave única (contrato + fiscal) e quebraria.
        interests.saveAndFlush(new InterestEmailConfirmation(contracts.findById(id).orElseThrow(), fiscal, LocalDateTime.now()));
        assertThat(interests.findByContract_Id(id)).hasSize(1);

        // O novo ciclo chega à renovação de novo (pelos métodos de transição do domínio).
        tx.executeWithoutResult(s -> {
            Contract c = contracts.findById(id).orElseThrow();
            c.updateStatusByDeadline(c.getEndDate().minusMonths(1));
            c.markInterestEmailSent();
            c.markTechnicalOpinionGenerated();
        });

        MockHttpServletResponse second = send(id, ci, pdf("dois.pdf"), "2028-06-01");

        assertThat(second.getStatus()).isEqualTo(200);
        assertThat((String) JsonPath.read(second.getContentAsString(), "$.ta")).isEqualTo("5");
        assertThat(attachments.countByContract_IdAndAttType(id, AttachmentType.TERMO_ADITIVO)).isEqualTo(2);
    }

    // ------------------------------------------------------------------ perfis

    @Test
    void fiscalVinculadoELeituraSemTokenNaoRegistram() throws Exception {
        Long id = contractInRenewal("1", 12);

        assertThat(send(id, fiscal, pdf("a.pdf"), "2027-03-01").getStatus()).isEqualTo(403);
        assertThat(send(id, outroFiscal, pdf("a.pdf"), "2027-03-01").getStatus()).isEqualTo(403);
        assertThat(send(id, null, pdf("a.pdf"), "2027-03-01").getStatus()).isEqualTo(403);

        assertThat(contracts.findById(id).orElseThrow().getStatus()).isEqualTo(ContractStatus.RENOVACAO_ABERTA_SEI);
        assertThat(attachments.findByContract_IdOrderByUploadedAtAsc(id)).isEmpty();
    }

    // ------------------------------------------------------------------ recusas de regra

    @Test
    void foraDeRenovacaoResponde409ENadaMuda() throws Exception {
        Long id = contractInRenewal("1", 12);
        tx.executeWithoutResult(s -> contracts.findById(id).orElseThrow()
                .registerAmendment(END, "1")); // volta a EM_VIGENCIA sem passar pelo serviço

        MockHttpServletResponse response = send(id, ci, pdf("a.pdf"), "2027-03-01");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("renovação aberta no SEI");
        assertThat(attachments.findByContract_IdOrderByUploadedAtAsc(id)).isEmpty();
        assertThat(history.findAll()).isEmpty();
    }

    @Test
    void contratoInexistenteResponde404() throws Exception {
        assertThat(send(999_999L, ci, pdf("a.pdf"), "2027-03-01").getStatus()).isEqualTo(404);
    }

    @Test
    void dataIgualOuAnteriorAAtualResponde400() throws Exception {
        Long id = contractInRenewal("1", 12);

        MockHttpServletResponse igual = send(id, ci, pdf("a.pdf"), "2026-12-01");
        MockHttpServletResponse anterior = send(id, ci, pdf("a.pdf"), "2026-11-30");

        assertThat(igual.getStatus()).isEqualTo(400);
        assertThat(igual.getContentAsString()).contains("posterior à atual").contains("01/12/2026");
        assertThat(anterior.getStatus()).isEqualTo(400);
        assertUntouched(id, "1");
    }

    @Test
    void dataAcimaDoLimiteResponde400ComALimiteNaMensagem() throws Exception {
        Long id = contractInRenewal("1", 12); // limite = 01/12/2027

        MockHttpServletResponse response = send(id, ci, pdf("a.pdf"), "2027-12-02");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("01/12/2027").contains("12 meses");
        assertUntouched(id, "1");
    }

    @Test
    void dataAusenteOuMalFormadaResponde400() throws Exception {
        Long id = contractInRenewal("1", 12);

        assertThat(send(id, ci, pdf("a.pdf"), null).getStatus()).isEqualTo(400);
        assertThat(send(id, ci, pdf("a.pdf"), "01/03/2027").getStatus()).isEqualTo(400);
        assertUntouched(id, "1");
    }

    @Test
    void arquivoAusenteVazioOuDeTipoErradoResponde400ENadaMuda() throws Exception {
        Long id = contractInRenewal("1", 12);
        interests.save(new InterestEmailConfirmation(contracts.findById(id).orElseThrow(), fiscal, LocalDateTime.now()));

        assertThat(send(id, ci, null, "2027-03-01").getStatus()).isEqualTo(400);
        assertThat(send(id, ci, new MockMultipartFile("file", "vazio.pdf", "application/pdf", new byte[0]),
                "2027-03-01").getStatus()).isEqualTo(400);
        assertThat(send(id, ci, new MockMultipartFile("file", "nota.txt", "text/plain", "oi".getBytes()),
                "2027-03-01").getStatus()).isEqualTo(400);

        assertUntouched(id, "1");
        assertThat(interests.findByContract_Id(id)).hasSize(1); // o reset só acontece se tudo deu certo
    }

    @Test
    void contratoCom10AnexosAtivosRecusaComMensagemClara() throws Exception {
        Long id = contractInRenewal("1", 12);
        Contract c = contracts.findById(id).orElseThrow();
        for (int i = 0; i < 10; i++) {
            attachments.save(new ContractAttachment(c, "comum" + i + ".pdf", "application/pdf", 8, PDF,
                    AttachmentType.GERAL, admin));
        }

        MockHttpServletResponse response = send(id, ci, pdf("a.pdf"), "2027-03-01");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("Limite de 10 anexos");
        assertThat(attachments.countByContract_IdAndAttType(id, AttachmentType.TERMO_ADITIVO)).isZero();
        assertThat(contracts.findById(id).orElseThrow().getStatus()).isEqualTo(ContractStatus.RENOVACAO_ABERTA_SEI);
    }

    // ------------------------------------------------------------------ apoio

    private void assertUntouched(Long id, String ta) {
        Contract c = contracts.findById(id).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(ContractStatus.RENOVACAO_ABERTA_SEI);
        assertThat(c.getEndDate()).isEqualTo(END);
        assertThat(c.getTa()).isEqualTo(ta);
        assertThat(attachments.findByContract_IdOrderByUploadedAtAsc(id)).isEmpty();
        assertThat(history.findAll()).isEmpty();
        assertThat(audit.findAll().stream().filter(a -> id.equals(a.getContractId()))).isEmpty();
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", PDF);
    }

    private MockHttpServletResponse send(Long contractId, AppUser as, MockMultipartFile file, String newEndDate) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/contracts/" + contractId + "/amendments");
        if (file != null) req.file(file);
        if (newEndDate != null) req.param("newEndDate", newEndDate);
        if (as != null) req.header("Authorization", "Bearer " + jwt.generate(as.getUsername(), as.getName()));
        return mvc.perform(req).andReturn().getResponse();
    }

    /** Contrato (vigência até {@link #END}) levado a RENOVACAO_ABERTA_SEI só pelos métodos de transição do domínio. */
    private Long contractInRenewal(String ta, Integer maxExtensionMonths) {
        Contract c = new Contract();
        c.update("TA-001/2026", "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"), LocalDate.of(2026, 1, 1), END,
                null, ta, Set.of(fiscal), "SEI-TA-001", maxExtensionMonths);
        c.updateStatusByDeadline(END.minusMonths(1));
        c.markInterestEmailSent();
        c.markTechnicalOpinionGenerated();
        assertThat(c.getStatus()).isEqualTo(ContractStatus.RENOVACAO_ABERTA_SEI);
        return contracts.save(c).getId();
    }

    private static AppUser user(String email, String name, Sector sector, PerfilUsuario perfil) {
        return new AppUser(email, "{noop}senha-irrelevante", name, email, null, sector, perfil);
    }
}
