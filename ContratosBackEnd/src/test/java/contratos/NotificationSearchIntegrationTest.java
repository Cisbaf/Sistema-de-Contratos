package contratos;

import com.jayway.jsonpath.JsonPath;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.NotificationLog;
import contratos.domain.Sector;
import contratos.domain.enums.NotificationAlertType;
import contratos.domain.enums.NotificationChannel;
import contratos.domain.enums.NotificationStatus;
import contratos.domain.enums.PerfilUsuario;
import contratos.domain.enums.RecipientRole;
import contratos.repository.ContractRepository;
import contratos.repository.NotificationLogRepository;
import contratos.repository.SectorRepository;
import contratos.repository.UserRepository;
import contratos.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * PG-10.2 — {@code GET /api/notificacoes} paginado e filtrado no servidor, e {@code GET /api/notificacoes/resumo}.
 * JWT real, H2 isolado ("notifsearchapitest"), sem transação no teste.
 *
 * <p>Base de 20 linhas de {@code notification_log}: 12 "de paginação" (horários distintos), 3 empatadas no mesmo
 * instante (provam o desempate por id), 2 do contrato BETA (que tem dois fiscais: provam que a busca por fiscal não
 * duplica linhas), uma com {@code %}, {@code _} e {@code !} no texto, e linhas nas bordas de 01/07 e 02/07.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notifsearchapitest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class NotificationSearchIntegrationTest {

    private static final int TOTAL = 20;

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired SectorRepository sectors;
    @Autowired ContractRepository contracts;
    @Autowired NotificationLogRepository logs;

    MockMvc mvc;
    AppUser admin;
    AppUser controleInterno;
    AppUser fiscal;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();

        logs.deleteAll();
        contracts.deleteAll();
        users.deleteAll();
        sectors.deleteAll();

        Sector setor = sectors.save(new Sector("Setor Busca"));
        admin = pessoa("admin.busca@test.local", "Admin Busca", setor, PerfilUsuario.ADMIN);
        controleInterno = pessoa("ci.busca@test.local", "CI Busca", setor, PerfilUsuario.CONTROLE_INTERNO);
        fiscal = pessoa("fiscal.busca@test.local", "Fiscal Comum", setor, PerfilUsuario.FISCAL);
        AppUser ana = pessoa("ana@test.local", "Ana Souza", setor, PerfilUsuario.FISCAL);
        AppUser bruno = pessoa("bruno@test.local", "Bruno Fiscal", setor, PerfilUsuario.FISCAL);
        AppUser carla = pessoa("carla@test.local", "Carla Fiscal", setor, PerfilUsuario.FISCAL);

        Contract alfa = contrato("ALFA-001/2026", "SEI-111", ana);
        Contract beta = contrato("BETA-002/2026", "SEI-222", bruno, carla);
        Contract gama = contrato("GAMA-003/2026", "SEI-333", ana);

        // 12 linhas de paginação: horários distintos, de 10/06 10:00 a 10/06 21:00
        for (int i = 0; i < 12; i++) {
            log(alfa, "Dest " + i, "pag" + i + "@t.local", NotificationStatus.SENT,
                    LocalDateTime.of(2026, 6, 10, 10, 0).plusHours(i));
        }
        // 3 empatadas no mesmo instante
        LocalDateTime empate = LocalDateTime.of(2026, 6, 20, 9, 0);
        log(alfa, "Empate Um", "empate1@t.local", NotificationStatus.SENT, empate);
        log(alfa, "Empate Dois", "empate2@t.local", NotificationStatus.SENT, empate);
        log(alfa, "Empate Tres", "empate3@t.local", NotificationStatus.SENT, empate);
        // bordas de data
        log(beta, "Roberto Almeida", "roberto@cisbaf.org.br", NotificationStatus.FAILED, LocalDateTime.of(2026, 7, 1, 0, 0, 0));
        log(gama, "Zulmira", "zulmira@cisbaf.org.br", NotificationStatus.SENT, LocalDateTime.of(2026, 7, 2, 23, 59, 59));
        // %, _ e ! no texto, e um "gêmeo" sem esses caracteres (um curinga o acharia)
        log(alfa, "Desc_100%!", "esc_100%!@t.local", NotificationStatus.SENT, LocalDateTime.of(2026, 5, 1, 8, 0));
        log(gama, "Plain100", "plain100@t.local", NotificationStatus.SENT, LocalDateTime.of(2026, 6, 15, 8, 0));
        // segunda linha do contrato com dois fiscais
        log(beta, "Segundo Dest", "segundo@t.local", NotificationStatus.SENT, LocalDateTime.of(2026, 6, 12, 8, 0));
    }

    // ------------------------------------------------------------------ paginação e ordem

    @Test
    void paginaDividePeloTamanhoEInformaTotais() throws Exception {
        String p0 = listar("size", "4");
        assertThat(ids(p0)).hasSize(4);
        assertThat(total(p0)).isEqualTo(TOTAL);
        assertThat((Integer) JsonPath.read(p0, "$.totalPages")).isEqualTo(5);

        assertThat(ids(listar("size", "4", "page", "4"))).hasSize(4);   // última página cheia
        String alemDoFim = listar("size", "4", "page", "5");
        assertThat(ids(alemDoFim)).isEmpty();
        assertThat(total(alemDoFim)).isEqualTo(TOTAL);                  // o total não some
    }

    @Test
    void semParametrosUsaPaginaZeroComVinteLinhas() throws Exception {
        String json = listar();
        assertThat(ids(json)).hasSize(TOTAL);       // 20 cabem na página padrão (size=20)
        assertThat((Integer) JsonPath.read(json, "$.size")).isEqualTo(20);
        assertThat((Integer) JsonPath.read(json, "$.number")).isEqualTo(0);
    }

    @Test
    void ordemDoMaisRecenteParaOMaisAntigoSemRepetirNemPularLinhasEntrePaginas() throws Exception {
        List<Long> esperado = logs.findAll().stream()
                .sorted(Comparator.comparing(NotificationLog::getAttemptedAt).reversed()
                        .thenComparing(Comparator.comparing(NotificationLog::getId).reversed()))
                .map(NotificationLog::getId).toList();
        assertThat(esperado).hasSize(TOTAL);

        // size=4: o grupo empatado (3 linhas) é cortado entre a página 0 e a 1
        List<Long> lidas = new ArrayList<>();
        for (int page = 0; page < 5; page++) {
            lidas.addAll(ids(listar("size", "4", "page", String.valueOf(page))));
        }

        assertThat(lidas).doesNotHaveDuplicates();
        assertThat(lidas).containsExactlyElementsOf(esperado);
    }

    @Test
    void linhaDaListaTrazOsDadosDoContratoEOsFiscais() throws Exception {
        String json = listar("busca", "roberto");

        assertThat((String) JsonPath.read(json, "$.content[0].contractNumber")).isEqualTo("BETA-002/2026");
        assertThat((String) JsonPath.read(json, "$.content[0].seiProcessNumber")).isEqualTo("SEI-222");
        assertThat((String) JsonPath.read(json, "$.content[0].status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(json, "$.content[0].recipientName")).isEqualTo("Roberto Almeida");
        List<String> fiscais = JsonPath.read(json, "$.content[0].fiscais");
        assertThat(fiscais).containsExactlyInAnyOrder("Bruno Fiscal", "Carla Fiscal");
    }

    // ------------------------------------------------------------------ busca

    @Test
    void buscaPorNumeroDoContratoIgnoraMaiusculas() throws Exception {
        assertThat(total(listar("busca", "beta-002"))).isEqualTo(2);
        assertThat(total(listar("busca", "BETA-002"))).isEqualTo(2);
        assertThat(total(listar("busca", "gama"))).isEqualTo(2);
    }

    @Test
    void buscaPorProcessoSei() throws Exception {
        assertThat(total(listar("busca", "SEI-333"))).isEqualTo(2);
        assertThat(total(listar("busca", "sei-222"))).isEqualTo(2);
    }

    @Test
    void buscaPorNomeEPorEmailDoDestinatario() throws Exception {
        assertThat(total(listar("busca", "roberto"))).isEqualTo(1);
        assertThat(total(listar("busca", "ZULMIRA@"))).isEqualTo(1);
        assertThat(total(listar("busca", "empate"))).isEqualTo(3);
    }

    @Test
    void buscaPorFiscalAchaLinhaQueNaoCasaComMaisNadaENaoDuplicaComDoisFiscais() throws Exception {
        // "carla" só existe como fiscal do contrato BETA; os destinatários das linhas têm outros nomes
        String carla = listar("busca", "carla");
        assertThat(total(carla)).isEqualTo(2);
        assertThat(ids(carla)).hasSize(2);

        // "fiscal" casa com os DOIS fiscais do BETA: cada linha ainda aparece uma vez só (um join duplicaria)
        String ambos = listar("busca", "fiscal");
        assertThat(total(ambos)).isEqualTo(2);
        assertThat(ids(ambos)).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void buscaIgnoraEspacosNasPontasEBuscaEmBrancoNaoFiltra() throws Exception {
        assertThat(total(listar("busca", "  ROBERTO  "))).isEqualTo(1);
        assertThat(total(listar("busca", "   "))).isEqualTo(TOTAL);
        assertThat(total(listar("busca", ""))).isEqualTo(TOTAL);
    }

    @Test
    void termoInexistenteDevolveVazioComTotalZero() throws Exception {
        String json = listar("busca", "zzzzinexistente");
        assertThat(ids(json)).isEmpty();
        assertThat(total(json)).isZero();
    }

    @Test
    void porcentoUnderscoreEExclamacaoValemComoTextoNaoComoCuringa() throws Exception {
        // "Desc_100%!" é a única linha com esses caracteres; "Plain100" não pode aparecer
        assertThat(total(listar("busca", "%"))).isEqualTo(1);       // curinga devolveria as 20
        assertThat(total(listar("busca", "_"))).isEqualTo(1);
        assertThat(total(listar("busca", "!"))).isEqualTo(1);
        assertThat(total(listar("busca", "_100"))).isEqualTo(1);    // "_" curinga acharia "Plain100" também
        assertThat(total(listar("busca", "100%"))).isEqualTo(1);    // "%" curinga acharia "Plain100" também
        assertThat(total(listar("busca", "_100%!"))).isEqualTo(1);
        assertThat(total(listar("busca", "pl_in"))).isZero();       // "_" curinga acharia "Plain"
        assertThat(total(listar("busca", "%%"))).isZero();
        assertThat(total(listar("busca", "!!"))).isZero();          // o próprio caractere de escape também é literal
        assertThat(ids(listar("busca", "_100"))).hasSize(1);
        assertThat((String) JsonPath.read(listar("busca", "_100"), "$.content[0].recipientName")).isEqualTo("Desc_100%!");
    }

    // ------------------------------------------------------------------ período

    @Test
    void dataInicioIncluiOPrimeiroInstanteDoDia() throws Exception {
        // 01/07 00:00:00 (Roberto) e 02/07 23:59:59 (Zulmira)
        assertThat(total(listar("dataInicio", "2026-07-01"))).isEqualTo(2);
        assertThat(total(listar("dataInicio", "2026-07-02"))).isEqualTo(1);
        assertThat(total(listar("dataInicio", "2026-07-03"))).isZero();
    }

    @Test
    void dataFimIncluiOUltimoInstanteDoDia() throws Exception {
        assertThat(total(listar("dataFim", "2026-07-02"))).isEqualTo(TOTAL);   // 23:59:59 ainda entra
        assertThat(total(listar("dataFim", "2026-07-01"))).isEqualTo(TOTAL - 1);
        assertThat(total(listar("dataFim", "2026-06-30"))).isEqualTo(TOTAL - 2);
        assertThat(total(listar("dataFim", "2026-04-30"))).isZero();
    }

    @Test
    void periodoDeUmDiaSoETambemUmIntervalo() throws Exception {
        assertThat(total(listar("dataInicio", "2026-07-02", "dataFim", "2026-07-02"))).isEqualTo(1);
        assertThat(total(listar("dataInicio", "2026-06-20", "dataFim", "2026-06-20"))).isEqualTo(3);
        assertThat(total(listar("dataInicio", "2026-06-10", "dataFim", "2026-06-10"))).isEqualTo(12);
    }

    @Test
    void dataFimAnteriorADataInicioDevolveVazioSem400() throws Exception {
        String json = listar("dataInicio", "2026-07-02", "dataFim", "2026-06-01");
        assertThat(ids(json)).isEmpty();
        assertThat(total(json)).isZero();
    }

    @Test
    void buscaECombinadaComOPeriodo() throws Exception {
        assertThat(total(listar("busca", "SEI-333", "dataInicio", "2026-07-01"))).isEqualTo(1);   // só a Zulmira
        assertThat(total(listar("busca", "roberto", "dataFim", "2026-06-30"))).isZero();
        assertThat(total(listar("busca", "beta", "dataFim", "2026-06-30"))).isEqualTo(1);         // só "Segundo Dest"
    }

    @Test
    void totalComFiltroEPaginaSeguemOMesmoConjunto() throws Exception {
        // 3 linhas do ALFA/GAMA... "ana" é fiscal de ALFA e GAMA: 16 + 2 = 18 linhas; páginas de 5
        String p0 = listar("busca", "ana souza", "size", "5");
        assertThat(total(p0)).isEqualTo(18);
        assertThat((Integer) JsonPath.read(p0, "$.totalPages")).isEqualTo(4);
        assertThat(ids(listar("busca", "ana souza", "size", "5", "page", "3"))).hasSize(3);
    }

    // ------------------------------------------------------------------ limites e erros

    @Test
    void tamanhoMaximoDaPaginaEhCem() throws Exception {
        assertThat(chamar(admin, "size", "100").getStatus()).isEqualTo(200);
        MockHttpServletResponse acima = chamar(admin, "size", "101");
        assertThat(acima.getStatus()).isEqualTo(400);
        assertThat(acima.getContentAsString()).contains("100");
    }

    @Test
    void parametrosInvalidosDevolvem400() throws Exception {
        assertThat(chamar(admin, "size", "0").getStatus()).isEqualTo(400);
        assertThat(chamar(admin, "page", "-1").getStatus()).isEqualTo(400);
        assertThat(chamar(admin, "dataInicio", "01/07/2026").getStatus()).isEqualTo(400);
        assertThat(chamar(admin, "dataFim", "abc").getStatus()).isEqualTo(400);
        assertThat(chamar(admin, "size", "abc").getStatus()).isEqualTo(400);
    }

    @Test
    void controleInternoTambemListaEFiscalNao() throws Exception {
        assertThat(chamar(controleInterno).getStatus()).isEqualTo(200);
        assertThat(chamar(fiscal).getStatus()).isEqualTo(403);
        assertThat(chamar(null).getStatus()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ resumo

    @Test
    void resumoContaTudoEPorSituacaoSemDependerDoFiltroNemDaPagina() throws Exception {
        String json = resumo(admin);

        assertThat(((Number) JsonPath.read(json, "$.total")).intValue()).isEqualTo(TOTAL);
        assertThat(((Number) JsonPath.read(json, "$.enviados")).intValue()).isEqualTo(TOTAL - 1);
        assertThat(((Number) JsonPath.read(json, "$.falhas")).intValue()).isEqualTo(1);
    }

    @Test
    void resumoSemNenhumaNotificacaoEhZero() throws Exception {
        logs.deleteAll();

        String json = resumo(controleInterno);

        assertThat(((Number) JsonPath.read(json, "$.total")).intValue()).isZero();
        assertThat(((Number) JsonPath.read(json, "$.enviados")).intValue()).isZero();
        assertThat(((Number) JsonPath.read(json, "$.falhas")).intValue()).isZero();
    }

    @Test
    void resumoSoParaAdminEControleInterno() throws Exception {
        assertThat(mvc.perform(get("/api/notificacoes/resumo").header("Authorization", bearer(fiscal)))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/notificacoes/resumo")).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ apoio

    private AppUser pessoa(String email, String nome, Sector setor, PerfilUsuario perfil) {
        return users.save(new AppUser(email, "{noop}x", nome, email, null, setor, perfil));
    }

    private Contract contrato(String numero, String sei, AppUser... fiscais) {
        Contract c = new Contract();
        c.update(numero, "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 1),
                null, "2", Set.of(fiscais), sei, 12);
        return contracts.save(c);
    }

    private void log(Contract contract, String nome, String email, NotificationStatus status, LocalDateTime quando) {
        NotificationLog l = new NotificationLog(contract, NotificationAlertType.SIX_MONTHS, contract.getEndDate(),
                NotificationChannel.EMAIL, RecipientRole.FISCAL, nome, email, status,
                status == NotificationStatus.FAILED ? "erro" : null);
        ReflectionTestUtils.setField(l, "attemptedAt", quando);
        logs.save(l);
    }

    /** GET /api/notificacoes como admin com pares chave,valor de parâmetros; exige 200 e devolve o JSON. */
    private String listar(String... pares) throws Exception {
        MockHttpServletResponse res = chamar(admin, pares);
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return res.getContentAsString();
    }

    private MockHttpServletResponse chamar(AppUser como, String... pares) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/notificacoes");
        for (int i = 0; i < pares.length; i += 2) {
            req.param(pares[i], pares[i + 1]);
        }
        if (como != null) {
            req.header("Authorization", bearer(como));
        }
        return mvc.perform(req).andReturn().getResponse();
    }

    private String resumo(AppUser como) throws Exception {
        MockHttpServletResponse res = mvc.perform(get("/api/notificacoes/resumo").header("Authorization", bearer(como)))
                .andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return res.getContentAsString();
    }

    private static int total(String json) {
        return ((Number) JsonPath.read(json, "$.totalElements")).intValue();
    }

    private static List<Long> ids(String json) {
        List<Number> brutos = JsonPath.read(json, "$.content[*].id");
        return brutos.stream().map(Number::longValue).toList();
    }

    private String bearer(AppUser as) {
        return "Bearer " + jwt.generate(as.getUsername(), as.getName());
    }
}
