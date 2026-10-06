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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Competência do lançamento dentro da vigência do contrato (mês de início ao mês de término, inclusive) e parcela de
 * 1 até o número de meses da vigência. Vale na criação e na edição. JWT real, H2 isolado ("lancamentovigenciaapitest"),
 * sem transação no teste (cada requisição grava e confirma de verdade).
 *
 * Contrato padrão: 06/10/2026 a 16/02/2027 = meses 10/2026, 11, 12, 01/2027 e 02/2027 = 5 parcelas.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:lancamentovigenciaapitest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class LancamentoCompetenciaParcelaIntegrationTest {

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

        Sector setor = sectors.save(new Sector("Setor Vigencia"));
        admin = users.save(new AppUser("admin.vig@test.local", "{noop}senha-irrelevante", "Admin Vigencia",
                "admin.vig@test.local", null, setor, PerfilUsuario.ADMIN));
        contract = novoContrato("VIG-001/2026", LocalDate.of(2026, 10, 6), LocalDate.of(2027, 2, 16));
    }

    // ------------------------------------------------------------------ competência na criação

    @Test
    void competenciaNoMesDeInicioEhAceitaMesmoComODiaDepoisDoInicio() throws Exception {
        assertThat(criar(contract, "2026-10-01", "1").getStatus()).isEqualTo(201);
    }

    @Test
    void competenciaNoMesDeTerminoEhAceitaMesmoComODiaDepoisDoTermino() throws Exception {
        assertThat(criar(contract, "2027-02-28", "5").getStatus()).isEqualTo(201);
    }

    @Test
    void competenciaNoMeioDaVigenciaEhAceita() throws Exception {
        assertThat(criar(contract, "2026-12-01", "3").getStatus()).isEqualTo(201);
    }

    @Test
    void competenciaUmMesAntesDoInicioEhRecusada() throws Exception {
        MockHttpServletResponse res = criar(contract, "2026-09-30", "1");

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(corpo(res)).contains("10/2026").contains("02/2027");
        assertThat(lancamentos.count()).isZero();
    }

    @Test
    void competenciaUmMesDepoisDoTerminoEhRecusada() throws Exception {
        assertThat(criar(contract, "2027-03-01", "1").getStatus()).isEqualTo(400);
        assertThat(lancamentos.count()).isZero();
    }

    @Test
    void competenciaDeAnosAtrasEhRecusada() throws Exception {
        assertThat(criar(contract, "2007-06-01", "1").getStatus()).isEqualTo(400);
        assertThat(lancamentos.count()).isZero();
    }

    // ------------------------------------------------------------------ parcela na criação

    @Test
    void parcelaPrimeiraEUltimaSaoAceitas() throws Exception {
        assertThat(criar(contract, "2026-10-01", "1").getStatus()).isEqualTo(201);
        assertThat(criar(contract, "2027-02-01", "5").getStatus()).isEqualTo(201);
    }

    @Test
    void parcelaUmAcimaDoTotalDeMesesEhRecusada() throws Exception {
        MockHttpServletResponse res = criar(contract, "2026-10-01", "6");

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(corpo(res)).contains("1 e 5");
        assertThat(lancamentos.count()).isZero();
    }

    @Test
    void parcelaZeroZeroAEsquerdaEMuitoGrandeSaoRecusadasPeloFormato() throws Exception {
        assertThat(criar(contract, "2026-10-01", "0").getStatus()).isEqualTo(400);
        assertThat(criar(contract, "2026-10-01", "01").getStatus()).isEqualTo(400);
        assertThat(criar(contract, "2026-10-01", "1000").getStatus()).isEqualTo(400);
        assertThat(criar(contract, "2026-10-01", "99999999999999999999").getStatus()).isEqualTo(400);
        assertThat(criar(contract, "2026-10-01", "abc").getStatus()).isEqualTo(400);
        assertThat(lancamentos.count()).isZero();
    }

    @Test
    void parcelaEhOpcional() throws Exception {
        assertThat(criar(contract, "2026-10-01", null).getStatus()).isEqualTo(201);
        assertThat(criar(contract, "2026-11-01", "").getStatus()).isEqualTo(201);
    }

    @Test
    void parcelaRepetidaEmOutraNotaContinuaPermitida() throws Exception {
        assertThat(criar(contract, "2026-10-01", "2").getStatus()).isEqualTo(201);
        assertThat(criar(contract, "2026-10-01", "2").getStatus()).isEqualTo(201);
    }

    @Test
    void totalDeParcelasDependeDaVigenciaDoContrato() throws Exception {
        Contract maior = novoContrato("VIG-002/2026", LocalDate.of(2026, 10, 6), LocalDate.of(2027, 4, 30));
        Contract umMes = novoContrato("VIG-003/2026", LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 30));

        assertThat(criar(contract, "2026-10-01", "6").getStatus()).as("5 meses: parcela 6 não existe").isEqualTo(400);
        assertThat(criar(maior, "2026-10-01", "7").getStatus()).as("7 meses: parcela 7 existe").isEqualTo(201);
        assertThat(criar(maior, "2026-10-01", "8").getStatus()).isEqualTo(400);
        assertThat(criar(umMes, "2026-10-01", "1").getStatus()).as("contrato de um mês só").isEqualTo(201);
        assertThat(criar(umMes, "2026-10-01", "2").getStatus()).isEqualTo(400);
        assertThat(criar(umMes, "2026-11-01", "1").getStatus()).isEqualTo(400);
    }

    @Test
    void viradaDeAnoContaOsMesesCorretamente() throws Exception {
        Contract virada = novoContrato("VIG-004/2026", LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1));

        assertThat(criar(virada, "2026-12-01", "1").getStatus()).isEqualTo(201);
        assertThat(criar(virada, "2027-01-01", "2").getStatus()).isEqualTo(201);
        assertThat(criar(virada, "2027-01-01", "3").getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ edição

    @Test
    void editarParaCompetenciaForaDaVigenciaEhRecusadoENaoMudaNada() throws Exception {
        Long id = criarValido("2026-11-01", "2");

        MockHttpServletResponse res = editar(id, "2007-06-01", "2");

        assertThat(res.getStatus()).isEqualTo(400);
        LancamentoFinanceiro depois = lancamentos.findById(id).orElseThrow();
        assertThat(depois.getCompetencia()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(lancamentoHistorico.count()).as("edição recusada não grava histórico").isZero();
    }

    @Test
    void editarParaParcelaAcimaDoTotalEhRecusadoENaoMudaNada() throws Exception {
        Long id = criarValido("2026-11-01", "2");

        MockHttpServletResponse res = editar(id, "2026-11-01", "6");

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(lancamentos.findById(id).orElseThrow().getParcela()).isEqualTo("2");
        assertThat(lancamentoHistorico.count()).isZero();
    }

    @Test
    void editarParaValoresDentroDaVigenciaFunciona() throws Exception {
        Long id = criarValido("2026-11-01", "2");

        MockHttpServletResponse res = editar(id, "2027-02-01", "5");

        assertThat(res.getStatus()).isEqualTo(200);
        LancamentoFinanceiro depois = lancamentos.findById(id).orElseThrow();
        assertThat(depois.getCompetencia()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(depois.getParcela()).isEqualTo("5");
    }

    // ------------------------------------------------------------------ apoio

    private Contract novoContrato(String numero, LocalDate inicio, LocalDate fim) {
        Contract c = new Contract();
        c.update(numero, "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("100000.00"), new BigDecimal("1000.00"), inicio, fim,
                null, null, Set.of(), "SEI-" + numero, 12);
        return contracts.save(c);
    }

    private Long criarValido(String competencia, String parcela) throws Exception {
        MockHttpServletResponse res = criar(contract, competencia, parcela);
        assertThat(res.getStatus()).as(corpo(res)).isEqualTo(201);
        return ((Number) JsonPath.read(corpo(res), "$.id")).longValue();
    }

    private MockHttpServletResponse criar(Contract alvo, String competencia, String parcela) throws Exception {
        String nota = "NF-" + notas.incrementAndGet();
        return mvc.perform(post("/api/contracts/" + alvo.getId() + "/lancamentos")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(nota, competencia, parcela))).andReturn().getResponse();
    }

    private MockHttpServletResponse editar(Long id, String competencia, String parcela) throws Exception {
        String nota = lancamentos.findById(id).orElseThrow().getNotaFiscal();
        return mvc.perform(put("/api/lancamentos/" + id).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(json(nota, competencia, parcela)))
                .andReturn().getResponse();
    }

    private static String json(String nota, String competencia, String parcela) {
        String p = parcela == null ? "null" : "\"" + parcela + "\"";
        return "{\"numeroProcesso\":\"PROC-1\",\"notaFiscal\":\"" + nota + "\",\"parcela\":" + p + ","
                + "\"competencia\":\"" + competencia + "\",\"valorNota\":100.00,\"observacoes\":null}";
    }

    private static String corpo(MockHttpServletResponse res) throws Exception {
        return res.getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer(AppUser as) {
        return "Bearer " + jwt.generate(as.getUsername(), as.getName());
    }
}
