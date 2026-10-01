package contratos;

import contratos.security.JwtService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M6-70 (achado do JSON malformado): erros "de framework" (JSON ilegível, rota inexistente, método ou content-type
 * não suportado) precisam sair com o status certo (400/404/405/415), não como 403 vazio.
 *
 * Por que NÃO dá para testar isso com MockMvc (como a {@link RbacMatrixIntegrationTest}): o MockMvc não faz o
 * "error dispatch" (o redirecionamento interno para /error que o servlet container faz em sendError). É nesse segundo
 * passo que o Spring Security reavaliava a requisição como anônima e devolvia 403. Aqui sobe um servidor de verdade
 * (porta aleatória, H2 próprio "errortest") e usa um cliente HTTP real.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:errortest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ErrorResponsesIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwt;

    private final HttpClient client = HttpClient.newHttpClient();

    /** Admin de bootstrap do perfil test (admin@test.local), criado pelo DataInitializer ao subir o contexto. */
    private String adminToken() {
        return jwt.generate("admin@test.local", "Administrador de Teste");
    }

    private int status(String method, String path, String body, String contentType, boolean autenticado) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        if (contentType != null) b.header("Content-Type", contentType);
        if (autenticado) b.header("Authorization", "Bearer " + adminToken());
        return client.send(b.build(), BodyHandlers.discarding()).statusCode();
    }

    @Test
    void jsonMalformadoDevolve400() throws Exception {
        assertThat(status("PUT", "/api/users/1", "{\"name\":", "application/json", true)).isEqualTo(400);
        assertThat(status("POST", "/api/sectors", "{\"name\":", "application/json", true)).isEqualTo(400);
    }

    @Test
    void rotaInexistenteDevolve404() throws Exception {
        assertThat(status("GET", "/api/naoexiste", null, null, true)).isEqualTo(404);
    }

    @Test
    void metodoNaoSuportadoDevolve405() throws Exception {
        assertThat(status("PATCH", "/api/contracts", null, null, true)).isEqualTo(405);
    }

    @Test
    void contentTypeNaoSuportadoDevolve415() throws Exception {
        assertThat(status("POST", "/api/sectors", "x", "text/plain", true)).isEqualTo(415);
    }

    @Test
    void anonimoContinuaRecebendo403EmRotaProtegidaEmRotaInexistente() throws Exception {
        // a correção do /error NÃO pode abrir nada para quem não tem token
        assertThat(status("GET", "/api/contracts", null, null, false)).isEqualTo(403);
        assertThat(status("GET", "/api/naoexiste", null, null, false)).isEqualTo(403);
        assertThat(status("PUT", "/api/users/1", "{\"name\":", "application/json", false)).isEqualTo(403);
    }
}
