package contratos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS por variável de ambiente (COR-10): só as origens de {@code cors.allowed-origins} (lista separada por vírgula)
 * passam; qualquer outra é barrada, inclusive com credenciais. Antes o back aceitava {@code *}.
 *
 * <p>A lista do teste tem duas origens de propósito: se o valor não for separado por vírgula, a segunda origem falha.
 * Pré-voo (OPTIONS) não precisa de login, então não usa JWT. H2 isolado "corstest".
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:corstest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "cors.allowed-origins=http://localhost:3000,http://192.168.1.10:3014"
})
@ActiveProfiles("test")
class CorsIntegrationTest {

    private static final String PRIMEIRA = "http://localhost:3000";
    private static final String SEGUNDA = "http://192.168.1.10:3014";

    @Autowired WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    private ResultActions preVoo(String origem, String metodo) throws Exception {
        return mvc.perform(options("/api/contracts")
                .header(HttpHeaders.ORIGIN, origem)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, metodo));
    }

    @ParameterizedTest
    @ValueSource(strings = {PRIMEIRA, SEGUNDA})
    void origemDaListaPassaComCredenciais(String origem) throws Exception {
        preVoo(origem, "GET").andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origem))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://evil.example", "http://localhost:3014", "http://192.168.1.10:3000", "https://localhost:3000"})
    void origemForaDaListaEBarrada(String origem) throws Exception {
        // mesmo host com outra porta ou outro esquema também é outra origem
        preVoo(origem, "GET").andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void asteriscoNaoValeMais() throws Exception {
        preVoo("*", "GET").andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void metodoNaoPermitidoNoPreVooEBarrado() throws Exception {
        preVoo(PRIMEIRA, "PATCH").andExpect(status().isForbidden());
    }

    @Test
    void requisicaoDeOrigemForaDaListaNaoRecebeCabecalhoCors() throws Exception {
        // sem login a resposta é 401/403 do Security; o que importa é não vazar Allow-Origin para a origem estranha
        mvc.perform(get("/api/contracts").header(HttpHeaders.ORIGIN, "http://evil.example"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void semOrigemNaoMudaNada() throws Exception {
        // chamada do proxy do front (servidor para servidor) não manda Origin
        mvc.perform(get("/api/contracts")).andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
