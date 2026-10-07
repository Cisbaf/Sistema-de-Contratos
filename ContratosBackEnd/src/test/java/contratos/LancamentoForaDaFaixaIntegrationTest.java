package contratos;

import com.jayway.jsonpath.JsonPath;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.LancamentoFinanceiro;
import contratos.domain.Sector;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * LC-10 — {@code GET /api/contracts/{id}/lancamentos/fora-da-faixa?startDate=&endDate=}: quais lançamentos ativos
 * ficariam fora da vigência proposta. Só consulta. JWT real, H2 isolado ("lancamentoforafaixaapitest"), sem
 * transação no teste.
 *
 * Contrato padrão: 06/10/2026 a 16/02/2027 = 10/2026, 11, 12, 01/2027 e 02/2027 (5 parcelas).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:lancamentoforafaixaapitest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class LancamentoForaDaFaixaIntegrationTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 6);
    private static final LocalDate FIM = LocalDate.of(2027, 2, 16);

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
    @Autowired AuditLogRepository audit;

    MockMvc mvc;
    AppUser admin;
    Contract contract;
    final AtomicInteger notas = new AtomicInteger();

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

        Sector setor = sectors.save(new Sector("Setor Fora da Faixa"));
        admin = users.save(new AppUser("admin.faixa@test.local", "{noop}senha-irrelevante", "Admin Faixa",
                "admin.faixa@test.local", null, setor, PerfilUsuario.ADMIN));
        contract = novoContrato("FAIXA-001/2026");
    }

    // ------------------------------------------------------------------ o que entra e o que não entra

    @Test
    void comAsMesmasDatasDoContratoNenhumLancamentoFicaFora() throws Exception {
        lanc(contract, "2026-10-01", "1");
        lanc(contract, "2027-02-01", "5");

        MockHttpServletResponse res = consultar(contract.getId(), "2026-10-06", "2027-02-16");

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(corpo(res)).isEqualTo("[]");
    }

    @Test
    void encurtarOFimMarcaOsLancamentosDosMesesDepoisDoNovoFimComCompetenciaEParcelaFora() throws Exception {
        lanc(contract, "2026-12-01", "3");   // dentro (novo fim 15/12/2026, 3 parcelas)
        Long janeiro = lanc(contract, "2027-01-01", "4");
        Long fevereiro = lanc(contract, "2027-02-01", "5");

        MockHttpServletResponse res = consultar(contract.getId(), "2026-10-06", "2026-12-15");
        String json = corpo(res);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat((List<?>) JsonPath.read(json, "$")).hasSize(2);
        assertThat(ids(json)).containsExactly(janeiro, fevereiro);
        assertThat((Boolean) JsonPath.read(json, "$[0].competenciaFora")).isTrue();
        assertThat((Boolean) JsonPath.read(json, "$[0].parcelaFora")).isTrue();
        assertThat((String) JsonPath.read(json, "$[0].competencia")).isEqualTo("2027-01-01");
        assertThat((String) JsonPath.read(json, "$[0].parcela")).isEqualTo("4");
        assertThat((String) JsonPath.read(json, "$[0].notaFiscal")).isNotBlank();
    }

    @Test
    void adiarOInicioMarcaOsLancamentosDosMesesAntesDoNovoInicio() throws Exception {
        Long outubro = lanc(contract, "2026-10-01", null);
        Long novembro = lanc(contract, "2026-11-01", null);
        lanc(contract, "2026-12-01", null);

        MockHttpServletResponse res = consultar(contract.getId(), "2026-12-01", "2027-02-16");
        String json = corpo(res);

        assertThat(ids(json)).containsExactly(outubro, novembro);
        assertThat((Boolean) JsonPath.read(json, "$[0].competenciaFora")).isTrue();
        assertThat((Boolean) JsonPath.read(json, "$[0].parcelaFora")).isFalse();
    }

    @Test
    void soAParcelaForaDoNovoTotalMarcaApenasAParcela() throws Exception {
        Long id = lanc(contract, "2026-10-01", "5");

        // novo contrato de 2 meses (10/2026 e 11/2026): a competência continua dentro, a parcela 5 não
        MockHttpServletResponse res = consultar(contract.getId(), "2026-10-06", "2026-11-30");
        String json = corpo(res);

        assertThat(ids(json)).containsExactly(id);
        assertThat((Boolean) JsonPath.read(json, "$[0].competenciaFora")).isFalse();
        assertThat((Boolean) JsonPath.read(json, "$[0].parcelaFora")).isTrue();
    }

    @Test
    void parcelaAntigaComTextoApareceComoForaSemQuebrarAConsulta() throws Exception {
        Long id = lanc(contract, "2026-11-01", "1/12");
        lanc(contract, "2026-11-01", "única");

        MockHttpServletResponse res = consultar(contract.getId(), "2026-10-06", "2027-02-16");
        String json = corpo(res);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat((List<?>) JsonPath.read(json, "$")).hasSize(2);
        assertThat((Long) ((Number) JsonPath.read(json, "$[0].id")).longValue()).isEqualTo(id);
        assertThat((Boolean) JsonPath.read(json, "$[0].parcelaFora")).isTrue();
        assertThat((Boolean) JsonPath.read(json, "$[0].competenciaFora")).isFalse();
    }

    @Test
    void lancamentoSemParcelaEDentroDaCompetenciaNaoApareceMesmoComFaixaCurta() throws Exception {
        lanc(contract, "2026-10-01", null);

        assertThat(corpo(consultar(contract.getId(), "2026-10-06", "2026-10-31"))).isEqualTo("[]");
    }

    @Test
    void lancamentoDesativadoNaoApareceNoAviso() throws Exception {
        Long ativo = lanc(contract, "2027-02-01", "5");
        Long desativado = lanc(contract, "2027-01-01", "4");
        tx(() -> {
            LancamentoFinanceiro l = lancamentos.findById(desativado).orElseThrow();
            l.desativaLancamento(admin);
            lancamentos.save(l);
        });

        String json = corpo(consultar(contract.getId(), "2026-10-06", "2026-12-15"));

        assertThat(ids(json)).containsExactly(ativo);
    }

    @Test
    void lancamentoDeOutroContratoNaoApareceNoAviso() throws Exception {
        Contract outro = novoContrato("FAIXA-002/2026");
        lanc(outro, "2027-02-01", "5");
        Long meu = lanc(contract, "2027-02-01", "5");

        String json = corpo(consultar(contract.getId(), "2026-10-06", "2026-12-15"));

        assertThat(ids(json)).containsExactly(meu);
    }

    @Test
    void oResultadoVemOrdenadoPorCompetenciaDepoisPorId() throws Exception {
        Long fev = lanc(contract, "2027-02-01", "5");
        Long jan = lanc(contract, "2027-01-01", "4");
        Long jan2 = lanc(contract, "2027-01-15", "4");

        String json = corpo(consultar(contract.getId(), "2026-10-06", "2026-12-15"));

        assertThat(ids(json)).containsExactly(jan, jan2, fev);
    }

    @Test
    void osLimitesDoMesDeInicioEDeTerminoSaoInclusivos() throws Exception {
        lanc(contract, "2026-12-01", null);
        lanc(contract, "2026-12-31", "1");

        // faixa 12/2026 a 12/2026: competência em qualquer dia de dezembro está dentro
        String json = corpo(consultar(contract.getId(), "2026-12-31", "2026-12-31"));

        assertThat(ids(json)).isEmpty();
    }

    // ------------------------------------------------------------------ só consulta, nada é gravado

    @Test
    void aConsultaNaoAlteraContratoLancamentosNemAuditoria() throws Exception {
        lanc(contract, "2027-02-01", "5");
        long antes = lancamentos.count();

        assertThat(consultar(contract.getId(), "2026-10-06", "2026-11-30").getStatus()).isEqualTo(200);

        Contract depois = contracts.findById(contract.getId()).orElseThrow();
        assertThat(depois.getStartDate()).isEqualTo(INICIO);
        assertThat(depois.getEndDate()).isEqualTo(FIM);
        assertThat(lancamentos.count()).isEqualTo(antes);
        assertThat(lancamentos.findAll().get(0).isAtivo()).isTrue();
        assertThat(audit.count()).isZero();
        assertThat(lancamentoHistorico.count()).isZero();
    }

    // ------------------------------------------------------------------ entradas inválidas

    @Test
    void fimAnteriorAoInicioDa400() throws Exception {
        MockHttpServletResponse res = consultar(contract.getId(), "2026-12-31", "2026-12-01");

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(corpo(res)).contains("data final");
    }

    @Test
    void parametroAusenteOuMalFormadoDa400() throws Exception {
        String base = "/api/contracts/" + contract.getId() + "/lancamentos/fora-da-faixa";

        assertThat(chamar(base + "?startDate=2026-10-06").getStatus()).isEqualTo(400);
        assertThat(chamar(base + "?endDate=2027-02-16").getStatus()).isEqualTo(400);
        assertThat(chamar(base + "?startDate=amanha&endDate=2027-02-16").getStatus()).isEqualTo(400);
    }

    @Test
    void contratoInexistenteDa404() throws Exception {
        assertThat(consultar(999_999L, "2026-10-06", "2027-02-16").getStatus()).isEqualTo(404);
    }

    @Test
    void semTokenEhRecusadoComOMesmoStatusDosDemaisEndpoints() throws Exception {
        MockHttpServletResponse res = mvc.perform(get("/api/contracts/" + contract.getId()
                + "/lancamentos/fora-da-faixa").param("startDate", "2026-10-06").param("endDate", "2027-02-16"))
                .andReturn().getResponse();

        assertThat(res.getStatus()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ apoio

    private Contract novoContrato(String numero) {
        Contract c = new Contract();
        c.update(numero, "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("100000.00"), new BigDecimal("1000.00"), INICIO, FIM,
                null, null, Set.of(), "SEI-" + numero, 12);
        return contracts.save(c);
    }

    /** Grava direto no repositório, para poder criar também lançamentos "antigos" que a API de hoje recusaria. */
    private Long lanc(Contract alvo, String competencia, String parcela) {
        return lancamentos.save(new LancamentoFinanceiro("PROC-1", "NF-" + notas.incrementAndGet(),
                LocalDate.parse(competencia), parcela, new BigDecimal("10.00"), null, alvo, admin)).getId();
    }

    private MockHttpServletResponse consultar(Long id, String inicio, String fim) throws Exception {
        return chamar("/api/contracts/" + id + "/lancamentos/fora-da-faixa?startDate=" + inicio + "&endDate=" + fim);
    }

    private MockHttpServletResponse chamar(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + jwt.generate(admin.getUsername(), admin.getName())))
                .andReturn().getResponse();
    }

    private static List<Long> ids(String json) {
        List<Number> raw = JsonPath.read(json, "$[*].id");
        return raw.stream().map(Number::longValue).toList();
    }

    private static String corpo(MockHttpServletResponse res) throws Exception {
        return res.getContentAsString(StandardCharsets.UTF_8);
    }

    private void tx(Runnable action) {
        new org.springframework.transaction.support.TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                .executeWithoutResult(s -> action.run());
    }
}
