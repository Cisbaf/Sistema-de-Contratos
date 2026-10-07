package contratos;

import contratos.domain.AppUser;
import contratos.domain.AuditLog;
import contratos.domain.Contract;
import contratos.domain.InterestEmailConfirmation;
import contratos.domain.Sector;
import contratos.domain.TechnicalOpinionEntry;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * ST-10 — o status é recalculado quando o contrato é editado (e quando o aditivo é registrado).
 * JWT real, H2 isolado ("st10editapitest"), sem transação no teste (cada requisição grava e confirma de verdade).
 * Todas as datas são relativas a hoje, para o teste não envelhecer.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:st10editapitest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ContractEditStatusIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate START = TODAY.minusYears(1);

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
    int seq;

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

        Sector setor = sectors.save(new Sector("Setor ST10"));
        admin = users.save(user("admin.st10@test.local", "Admin ST10", setor, PerfilUsuario.ADMIN));
        ci = users.save(user("ci.st10@test.local", "CI ST10", setor, PerfilUsuario.CONTROLE_INTERNO));
        fiscal = users.save(user("fiscal.st10@test.local", "Fiscal ST10", setor, PerfilUsuario.FISCAL));
    }

    // ------------------------------------------------------------------ EM_VIGENCIA e AGUARDANDO_EMAIL_INTERESSE

    @Test
    void emVigenciaComTerminoPuxadoParaDentroDosSeisMesesVaiParaAguardando() throws Exception {
        Long id = contract(ContractStatus.EM_VIGENCIA, TODAY.plusMonths(12));

        assertThat(edit(id, admin, START, TODAY.plusMonths(3)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.AGUARDANDO_EMAIL_INTERESSE);
        assertThat(rows(id)).containsExactly(
                row(ContractStatus.EM_VIGENCIA, ContractStatus.AGUARDANDO_EMAIL_INTERESSE, ContractStatusTrigger.CONTRACT_EDITED, admin));
        assertThat(auditDetails(id)).contains("Status: EM_VIGENCIA -> AGUARDANDO_EMAIL_INTERESSE");
    }

    @Test
    void emVigenciaComTerminoAindaLongeContinuaEmVigenciaSemHistorico() throws Exception {
        Long id = contract(ContractStatus.EM_VIGENCIA, TODAY.plusMonths(12));

        assertThat(edit(id, admin, START, TODAY.plusMonths(8)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.EM_VIGENCIA);
        assertThat(rows(id)).isEmpty();
        assertThat(auditDetails(id)).contains("Término da vigência").doesNotContain("Status:");
    }

    @Test
    void aguardandoComTerminoEmpurradoParaMaisDeSeisMesesVoltaParaEmVigencia() throws Exception {
        Long id = contract(ContractStatus.AGUARDANDO_EMAIL_INTERESSE, TODAY.plusMonths(3));

        assertThat(edit(id, admin, START, TODAY.plusMonths(12)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.EM_VIGENCIA);
        assertThat(rows(id)).containsExactly(
                row(ContractStatus.AGUARDANDO_EMAIL_INTERESSE, ContractStatus.EM_VIGENCIA, ContractStatusTrigger.CONTRACT_EDITED, admin));
        assertThat(auditDetails(id)).contains("Status: AGUARDANDO_EMAIL_INTERESSE -> EM_VIGENCIA");
    }

    @Test
    void fronteiraDeSeisMesesExatosContaComoDentroDaJanela() throws Exception {
        Long id = contract(ContractStatus.EM_VIGENCIA, TODAY.plusMonths(12));

        assertThat(edit(id, admin, START, TODAY.plusMonths(6)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.AGUARDANDO_EMAIL_INTERESSE);
    }

    @Test
    void aguardandoComNovoTerminoEmSeisMesesExatosContinuaAguardando() throws Exception {
        Long id = contract(ContractStatus.AGUARDANDO_EMAIL_INTERESSE, TODAY.plusMonths(3));

        assertThat(edit(id, admin, START, TODAY.plusMonths(6)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.AGUARDANDO_EMAIL_INTERESSE);
        assertThat(rows(id)).isEmpty();
    }

    @Test
    void umDiaAlemDosSeisMesesAindaEstaForaDaJanela() throws Exception {
        Long id = contract(ContractStatus.AGUARDANDO_EMAIL_INTERESSE, TODAY.plusMonths(3));

        assertThat(edit(id, admin, START, TODAY.plusMonths(6).plusDays(1)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.EM_VIGENCIA);
    }

    @Test
    void aguardandoComNovoTerminoAindaDentroDaJanelaContinuaAguardando() throws Exception {
        Long id = contract(ContractStatus.AGUARDANDO_EMAIL_INTERESSE, TODAY.plusMonths(3));

        assertThat(edit(id, admin, START, TODAY.plusMonths(5)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.AGUARDANDO_EMAIL_INTERESSE);
        assertThat(rows(id)).isEmpty();
    }

    @Test
    void aguardandoEditadoSemMexerNoTerminoNaoMudaDeStatus() throws Exception {
        Long id = contract(ContractStatus.AGUARDANDO_EMAIL_INTERESSE, TODAY.plusMonths(3));

        assertThat(edit(id, admin, START, TODAY.plusMonths(3), "250.00").getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.AGUARDANDO_EMAIL_INTERESSE);
        assertThat(rows(id)).isEmpty();
    }

    @Test
    void aguardandoComTerminoJaVencidoNaoVoltaParaEmVigencia() throws Exception {
        Long id = contract(ContractStatus.AGUARDANDO_EMAIL_INTERESSE, TODAY.minusDays(10));

        assertThat(edit(id, admin, START, TODAY.minusDays(3)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.AGUARDANDO_EMAIL_INTERESSE);
        assertThat(rows(id)).isEmpty();
    }

    @Test
    void controleInternoPodeMudarOTerminoForaDaRenovacaoEORecalculoVemComOSeuNome() throws Exception {
        Long id = contract(ContractStatus.EM_VIGENCIA, TODAY.plusMonths(12));

        assertThat(edit(id, ci, START, TODAY.plusMonths(2)).getStatus()).isEqualTo(200);

        assertThat(rows(id)).containsExactly(
                row(ContractStatus.EM_VIGENCIA, ContractStatus.AGUARDANDO_EMAIL_INTERESSE, ContractStatusTrigger.CONTRACT_EDITED, ci));
    }

    // ------------------------------------------------------------------ renovação em andamento (Administrador)

    @Test
    void adminMudaOTerminoComEmailEnviadoARenovacaoCaiEOCicloEApagado() throws Exception {
        Long id = contract(ContractStatus.EMAIL_ENVIADO, TODAY.plusMonths(3));
        interests.save(new InterestEmailConfirmation(contracts.findById(id).orElseThrow(), fiscal, LocalDateTime.now()));

        assertThat(edit(id, admin, START, TODAY.plusMonths(12)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.EM_VIGENCIA);
        assertThat(interests.findByContract_Id(id)).isEmpty();
        assertThat(rows(id)).containsExactly(
                row(ContractStatus.EMAIL_ENVIADO, ContractStatus.EM_VIGENCIA, ContractStatusTrigger.CONTRACT_EDITED, admin));
        assertThat(auditDetails(id)).contains("Status: EMAIL_ENVIADO -> EM_VIGENCIA");
    }

    @Test
    void adminMudaOTerminoComRenovacaoAbertaENovoPrazoAindaNaJanelaVaiParaAguardando() throws Exception {
        Long id = contract(ContractStatus.RENOVACAO_ABERTA_SEI, TODAY.plusMonths(2));
        interests.save(new InterestEmailConfirmation(contracts.findById(id).orElseThrow(), fiscal, LocalDateTime.now()));
        opinions.save(new TechnicalOpinionEntry(contracts.findById(id).orElseThrow(), fiscal, "parecer do ciclo"));

        assertThat(edit(id, admin, START, TODAY.plusMonths(4)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.AGUARDANDO_EMAIL_INTERESSE);
        assertThat(interests.findByContract_Id(id)).isEmpty();
        assertThat(opinions.findAll().stream().filter(o -> id.equals(o.getContract().getId())).count()).isZero();
        assertThat(rows(id)).containsExactly(
                row(ContractStatus.RENOVACAO_ABERTA_SEI, ContractStatus.AGUARDANDO_EMAIL_INTERESSE, ContractStatusTrigger.CONTRACT_EDITED, admin));
    }

    @Test
    void adminMudaOTerminoComRenovacaoAbertaENovoPrazoLongeVoltaParaEmVigencia() throws Exception {
        Long id = contract(ContractStatus.RENOVACAO_ABERTA_SEI, TODAY.plusMonths(2));

        assertThat(edit(id, admin, START, TODAY.plusMonths(10)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.EM_VIGENCIA);
        assertThat(rows(id)).containsExactly(
                row(ContractStatus.RENOVACAO_ABERTA_SEI, ContractStatus.EM_VIGENCIA, ContractStatusTrigger.CONTRACT_EDITED, admin));
    }

    @Test
    void adminMudaSoOInicioNaRenovacaoNadaMudaECicloFica() throws Exception {
        Long id = contract(ContractStatus.RENOVACAO_ABERTA_SEI, TODAY.plusMonths(2));
        interests.save(new InterestEmailConfirmation(contracts.findById(id).orElseThrow(), fiscal, LocalDateTime.now()));

        assertThat(edit(id, admin, START.minusMonths(1), TODAY.plusMonths(2)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.RENOVACAO_ABERTA_SEI);
        assertThat(contracts.findById(id).orElseThrow().getStartDate()).isEqualTo(START.minusMonths(1));
        assertThat(interests.findByContract_Id(id)).hasSize(1);
        assertThat(rows(id)).isEmpty();
    }

    @Test
    void adminEditaOutrosCamposNaRenovacaoSemMexerNasDatasNadaMuda() throws Exception {
        Long id = contract(ContractStatus.RENOVACAO_ABERTA_SEI, TODAY.plusMonths(2));
        interests.save(new InterestEmailConfirmation(contracts.findById(id).orElseThrow(), fiscal, LocalDateTime.now()));

        assertThat(edit(id, admin, START, TODAY.plusMonths(2), "999.00").getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.RENOVACAO_ABERTA_SEI);
        assertThat(interests.findByContract_Id(id)).hasSize(1);
        assertThat(rows(id)).isEmpty();
    }

    // ------------------------------------------------------------------ renovação em andamento (Controle Interno)

    @Test
    void controleInternoNaoMudaOTerminoComEmailEnviado409ENadaMuda() throws Exception {
        Long id = contract(ContractStatus.EMAIL_ENVIADO, TODAY.plusMonths(3));
        interests.save(new InterestEmailConfirmation(contracts.findById(id).orElseThrow(), fiscal, LocalDateTime.now()));

        MockHttpServletResponse response = edit(id, ci, START, TODAY.plusMonths(12));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("Administrador");
        assertUntouched(id, ContractStatus.EMAIL_ENVIADO, TODAY.plusMonths(3), START);
        assertThat(interests.findByContract_Id(id)).hasSize(1);
    }

    @Test
    void controleInternoNaoMudaOInicioComRenovacaoAberta409ENadaMuda() throws Exception {
        Long id = contract(ContractStatus.RENOVACAO_ABERTA_SEI, TODAY.plusMonths(2));

        MockHttpServletResponse response = edit(id, ci, START.minusDays(5), TODAY.plusMonths(2));

        assertThat(response.getStatus()).isEqualTo(409);
        assertUntouched(id, ContractStatus.RENOVACAO_ABERTA_SEI, TODAY.plusMonths(2), START);
    }

    @Test
    void controleInternoEditaOutrosCamposNaRenovacaoComAsMesmasDatas() throws Exception {
        Long id = contract(ContractStatus.RENOVACAO_ABERTA_SEI, TODAY.plusMonths(2));

        assertThat(edit(id, ci, START, TODAY.plusMonths(2), "777.00").getStatus()).isEqualTo(200);

        Contract c = contracts.findById(id).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(ContractStatus.RENOVACAO_ABERTA_SEI);
        assertThat(c.getValueMensal()).isEqualByComparingTo("777.00");
        assertThat(rows(id)).isEmpty();
    }

    // ------------------------------------------------------------------ aditivo

    @Test
    void aditivoComNovoTerminoDentroDaJanelaDeixaOContratoAguardandoComDoisRegistrosNoHistorico() throws Exception {
        Long id = contract(ContractStatus.RENOVACAO_ABERTA_SEI, TODAY.plusMonths(2));

        assertThat(amend(id, ci, TODAY.plusMonths(5)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.AGUARDANDO_EMAIL_INTERESSE);
        assertThat(contracts.findById(id).orElseThrow().getEndDate()).isEqualTo(TODAY.plusMonths(5));
        assertThat(rows(id)).containsExactly(
                row(ContractStatus.RENOVACAO_ABERTA_SEI, ContractStatus.EM_VIGENCIA, ContractStatusTrigger.ADITIVO_REGISTRADO, ci),
                row(ContractStatus.EM_VIGENCIA, ContractStatus.AGUARDANDO_EMAIL_INTERESSE, ContractStatusTrigger.DEADLINE, null));
        assertThat(auditDetails(id)).contains("Status: RENOVACAO_ABERTA_SEI -> AGUARDANDO_EMAIL_INTERESSE");
    }

    @Test
    void aditivoComNovoTerminoLongeDeixaOContratoEmVigenciaComUmRegistro() throws Exception {
        Long id = contract(ContractStatus.RENOVACAO_ABERTA_SEI, TODAY.plusMonths(2));

        assertThat(amend(id, ci, TODAY.plusMonths(14)).getStatus()).isEqualTo(200);

        assertThat(statusOf(id)).isEqualTo(ContractStatus.EM_VIGENCIA);
        assertThat(rows(id)).containsExactly(
                row(ContractStatus.RENOVACAO_ABERTA_SEI, ContractStatus.EM_VIGENCIA, ContractStatusTrigger.ADITIVO_REGISTRADO, ci));
    }

    // ------------------------------------------------------------------ apoio

    /** Linha de histórico reduzida ao que importa (previous, new, trigger, id de quem mudou ou null). */
    private record Row(ContractStatus previous, ContractStatus next, ContractStatusTrigger trigger, Long by) { }

    private static Row row(ContractStatus previous, ContractStatus next, ContractStatusTrigger trigger, AppUser by) {
        return new Row(previous, next, trigger, by == null ? null : by.getId());
    }

    private List<Row> rows(Long contractId) {
        List<Row> result = new ArrayList<>();
        tx.executeWithoutResult(s -> history.findAll().stream()
                .filter(h -> h.getContract().getId().equals(contractId))
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .forEach(h -> result.add(new Row(h.getPreviousStatus(), h.getNewStatus(), h.getStatusTrigger(),
                        h.getChangedBy() == null ? null : h.getChangedBy().getId()))));
        return result;
    }

    private ContractStatus statusOf(Long id) {
        return contracts.findById(id).orElseThrow().getStatus();
    }

    private String auditDetails(Long contractId) {
        List<AuditLog> logs = audit.findAll().stream().filter(a -> contractId.equals(a.getContractId())).toList();
        assertThat(logs).hasSize(1);
        return logs.get(0).getDetails();
    }

    private void assertUntouched(Long id, ContractStatus status, LocalDate end, LocalDate start) {
        Contract c = contracts.findById(id).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(status);
        assertThat(c.getEndDate()).isEqualTo(end);
        assertThat(c.getStartDate()).isEqualTo(start);
        assertThat(rows(id)).isEmpty();
        assertThat(audit.findAll().stream().filter(a -> id.equals(a.getContractId()))).isEmpty();
    }

    private MockHttpServletResponse edit(Long id, AppUser as, LocalDate start, LocalDate end) throws Exception {
        return edit(id, as, start, end, "100.00");
    }

    private MockHttpServletResponse edit(Long id, AppUser as, LocalDate start, LocalDate end, String valueMensal) throws Exception {
        String body = """
                {"numberContract":"%s","numberProcess":"PROC","object":"Objeto","company":"Empresa Ltda",
                 "cnpj":"11222333000181","valueGlobal":5000.00,"valueMensal":%s,
                 "startDate":"%s","endDate":"%s","fiscalIds":[%d],"seiProcessNumber":"SEI-ST10"}
                """.formatted(contracts.findById(id).orElseThrow().getNumberContract(), valueMensal, start, end, fiscal.getId());
        return mvc.perform(put("/api/contracts/" + id)
                        .header("Authorization", "Bearer " + jwt.generate(as.getUsername(), as.getName()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse amend(Long id, AppUser as, LocalDate newEnd) throws Exception {
        return mvc.perform(multipart("/api/contracts/" + id + "/amendments")
                        .file(new MockMultipartFile("file", "aditivo.pdf", "application/pdf", "%PDF-1.4 aditivo".getBytes()))
                        .param("newEndDate", newEnd.toString())
                        .header("Authorization", "Bearer " + jwt.generate(as.getUsername(), as.getName())))
                .andReturn().getResponse();
    }

    /** Contrato levado ao status pedido só pelos métodos de transição do domínio (a data de referência é o término menos 1 mês). */
    private Long contract(ContractStatus target, LocalDate end) {
        Contract c = new Contract();
        c.update("ST10-" + (++seq) + "/2026", "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("5000.00"), new BigDecimal("100.00"), START, end,
                null, null, Set.of(fiscal), "SEI-ST10", null);
        if (target != ContractStatus.EM_VIGENCIA) {
            c.updateStatusByDeadline(end.minusMonths(1));
            if (target == ContractStatus.EMAIL_ENVIADO || target == ContractStatus.RENOVACAO_ABERTA_SEI) c.markInterestEmailSent();
            if (target == ContractStatus.RENOVACAO_ABERTA_SEI) c.markTechnicalOpinionGenerated();
        }
        assertThat(c.getStatus()).isEqualTo(target);
        return contracts.save(c).getId();
    }

    private static AppUser user(String email, String name, Sector sector, PerfilUsuario perfil) {
        return new AppUser(email, "{noop}senha-irrelevante", name, email, null, sector, perfil);
    }
}
