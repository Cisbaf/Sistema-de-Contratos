package contratos;

import contratos.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mensagens 400 em português para parâmetros de requisição inválidos (página/tamanho, data, número, enum e
 * parâmetro obrigatório ausente). Antes, {@code size=0}/{@code page=-1} devolviam a mensagem do Spring Data em inglês
 * e data/número inválidos caíam no corpo padrão do Boot ({@code timestamp/status/error/path}, sem {@code message}).
 *
 * <p>MockMvc basta: os handlers de {@code ApiExceptionHandler} rodam no mesmo passo. JWT real do admin de bootstrap do
 * perfil test ({@code admin@test.local}); H2 isolado "paraminvalidotest".
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:paraminvalidotest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ParametrosInvalidosIntegrationTest {

    private static final String[] PAGINADOS = {"/api/notificacoes", "/api/auditoria", "/api/generate-document"};

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String bearer;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        bearer = "Bearer " + jwt.generate("admin@test.local", "Administrador de Teste");
    }

    private ResultActions chamar(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", bearer));
    }

    private void espera400(String url, String mensagem) throws Exception {
        chamar(url).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(mensagem))
                // formato do ApiErrorResponse, não o corpo padrão do Boot
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    // ---------- página e tamanho (os três endpoints paginados) ----------

    @ParameterizedTest
    @ValueSource(strings = {"/api/notificacoes", "/api/auditoria", "/api/generate-document"})
    void tamanhoZeroOuNegativo(String base) throws Exception {
        espera400(base + "?size=0", "O tamanho da página deve ser pelo menos 1");
        espera400(base + "?size=-5", "O tamanho da página deve ser pelo menos 1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/notificacoes", "/api/auditoria", "/api/generate-document"})
    void paginaNegativa(String base) throws Exception {
        espera400(base + "?page=-1", "O número da página não pode ser negativo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/notificacoes", "/api/auditoria", "/api/generate-document"})
    void tamanhoAcimaDoTeto(String base) throws Exception {
        espera400(base + "?size=101", "O tamanho máximo da página é 100");
    }

    @Test
    void paginaNegativaVemAntesDeTamanhoInvalido() throws Exception {
        espera400("/api/auditoria?page=-1&size=0", "O número da página não pode ser negativo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/notificacoes", "/api/auditoria", "/api/generate-document"})
    void limitesValidosContinuamPassando(String base) throws Exception {
        chamar(base + "?page=0&size=1").andExpect(status().isOk());
        chamar(base + "?page=0&size=100").andExpect(status().isOk());
        chamar(base).andExpect(status().isOk());
    }

    // ---------- data, número e enum inválidos ----------

    @Test
    void dataInvalida() throws Exception {
        String msg = "Parâmetro '%s' inválido: use uma data no formato AAAA-MM-DD";
        espera400("/api/notificacoes?dataInicio=abc", msg.formatted("dataInicio"));
        espera400("/api/notificacoes?dataFim=2026-13-45", msg.formatted("dataFim"));
        espera400("/api/auditoria?dataInicio=08/10/2026", msg.formatted("dataInicio"));
        espera400("/api/generate-document?dataFim=ontem", msg.formatted("dataFim"));
    }

    @Test
    void numeroInvalido() throws Exception {
        String msg = "Parâmetro '%s' inválido: use um número inteiro";
        espera400("/api/auditoria?contractId=xyz", msg.formatted("contractId"));
        espera400("/api/auditoria?actorId=1.5", msg.formatted("actorId"));
        espera400("/api/generate-document?authorId=abc", msg.formatted("authorId"));
    }

    @Test
    void enumNaoReconhecido() throws Exception {
        String msg = "Parâmetro '%s' inválido: valor não reconhecido";
        espera400("/api/auditoria?entityType=XYZ", msg.formatted("entityType"));
        espera400("/api/auditoria?action=XYZ", msg.formatted("action"));
        espera400("/api/generate-document?documentType=XYZ", msg.formatted("documentType"));
    }

    @Test
    void variavelDeCaminhoNaoNumerica() throws Exception {
        chamar("/api/contracts/abc").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", allOf(startsWith("Parâmetro '"), endsWith("inválido: use um número inteiro"))))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    void mensagemNaoRepeteOValorDigitado() throws Exception {
        chamar("/api/notificacoes?dataInicio=SEGREDO123").andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SEGREDO123"))));
    }

    // ---------- parâmetro obrigatório ausente ----------

    @Test
    void parametroObrigatorioAusenteUsaONomeDoParametro() throws Exception {
        String base = "/api/contracts/1/lancamentos/fora-da-faixa";
        espera400(base + "?endDate=2026-12-31", "Parâmetro obrigatório ausente: 'startDate'");
        espera400(base + "?startDate=2026-01-01", "Parâmetro obrigatório ausente: 'endDate'");
    }

    // ---------- segurança não muda ----------

    @Test
    void anonimoContinuaRecebendo403ComParametroInvalido() throws Exception {
        mvc.perform(get("/api/notificacoes?size=0")).andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria?dataInicio=abc")).andExpect(status().isForbidden());
    }
}
