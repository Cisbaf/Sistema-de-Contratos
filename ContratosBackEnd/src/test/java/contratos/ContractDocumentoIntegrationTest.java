package contratos;

import com.jayway.jsonpath.JsonPath;
import contratos.domain.AppUser;
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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * O prestador do contrato pode ser pessoa física (CPF) ou jurídica (CNPJ, numérico ou alfanumérico). O campo
 * continua se chamando {@code cnpj} na API e no banco (coluna de 14 posições); o valor é guardado sem máscara e em
 * maiúsculas. JWT real, H2 isolado ("contratodocumentoapitest"), sem transação no teste.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:contratodocumentoapitest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ContractDocumentoIntegrationTest {

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
    AppUser admin, fiscal;
    final AtomicInteger numeros = new AtomicInteger();

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

        Sector setor = sectors.save(new Sector("Setor Documento"));
        admin = users.save(new AppUser("admin.doc@test.local", "{noop}senha-irrelevante", "Admin Doc",
                "admin.doc@test.local", null, setor, PerfilUsuario.ADMIN));
        fiscal = users.save(new AppUser("fiscal.doc@test.local", "{noop}senha-irrelevante", "Fiscal Doc",
                "fiscal.doc@test.local", null, setor, PerfilUsuario.FISCAL));
    }

    // ------------------------------------------------------------------ criação

    @Test
    void cpfComMascaraEhAceitoEGuardadoSoComOsDigitos() throws Exception {
        MockHttpServletResponse res = criar("529.982.247-25");

        assertThat(res.getStatus()).as(corpo(res)).isEqualTo(200);
        assertThat((String) JsonPath.read(corpo(res), "$.cnpj")).isEqualTo("52998224725");
        assertThat(contracts.findAll().get(0).getCnpj()).isEqualTo("52998224725");
    }

    @Test
    void cpfSemMascaraEhAceito() throws Exception {
        assertThat(criar("52998224725").getStatus()).isEqualTo(200);
    }

    @Test
    void cnpjNumericoContinuaSendoAceito() throws Exception {
        MockHttpServletResponse res = criar("11.222.333/0001-81");

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat((String) JsonPath.read(corpo(res), "$.cnpj")).isEqualTo("11222333000181");
    }

    @Test
    void cnpjAlfanumericoEhAceitoEGuardadoEmMaiusculas() throws Exception {
        MockHttpServletResponse res = criar("12.abc.345/01de-35");

        assertThat(res.getStatus()).as(corpo(res)).isEqualTo(200);
        assertThat((String) JsonPath.read(corpo(res), "$.cnpj")).isEqualTo("12ABC34501DE35");
    }

    @Test
    void cpfComDigitoVerificadorErradoEhRecusadoComMensagemClara() throws Exception {
        MockHttpServletResponse res = criar("529.982.247-24");

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(corpo(res)).contains("CPF ou CNPJ inválido");
        assertThat(contracts.count()).isZero();
    }

    @Test
    void cnpjComDigitoVerificadorErradoEhRecusado() throws Exception {
        assertThat(criar("11.222.333/0001-82").getStatus()).isEqualTo(400);
        assertThat(criar("12.ABC.345/01DE-36").getStatus()).isEqualTo(400);
        assertThat(contracts.count()).isZero();
    }

    @Test
    void cpfComTodosOsDigitosIguaisEhRecusado() throws Exception {
        assertThat(criar("111.111.111-11").getStatus()).isEqualTo(400);
    }

    @Test
    void tamanhoQueNaoEOdeCpfNemODeCnpjEhRecusado() throws Exception {
        assertThat(criar("123456789012").getStatus()).isEqualTo(400);   // 12
        assertThat(criar("1122233300018").getStatus()).isEqualTo(400);  // 13
        assertThat(criar("1234567890").getStatus()).isEqualTo(400);     // 10
    }

    @Test
    void documentoVazioContinuaRecusado() throws Exception {
        assertThat(criar("").getStatus()).isEqualTo(400);
        assertThat(criar("   ").getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ edição

    @Test
    void editarParaTrocarCnpjPorCpfFunciona() throws Exception {
        MockHttpServletResponse criado = criar("11.222.333/0001-81");
        long id = ((Number) JsonPath.read(corpo(criado), "$.id")).longValue();
        String numero = JsonPath.read(corpo(criado), "$.numberContract");

        MockHttpServletResponse res = mvc.perform(put("/api/contracts/" + id)
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(numero, "529.982.247-25"))).andReturn().getResponse();

        assertThat(res.getStatus()).as(corpo(res)).isEqualTo(200);
        assertThat(contracts.findById(id).orElseThrow().getCnpj()).isEqualTo("52998224725");
    }

    @Test
    void editarComDocumentoInvalidoNaoAlteraOContrato() throws Exception {
        MockHttpServletResponse criado = criar("11.222.333/0001-81");
        long id = ((Number) JsonPath.read(corpo(criado), "$.id")).longValue();
        String numero = JsonPath.read(corpo(criado), "$.numberContract");

        MockHttpServletResponse res = mvc.perform(put("/api/contracts/" + id)
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(numero, "529.982.247-00"))).andReturn().getResponse();

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(contracts.findById(id).orElseThrow().getCnpj()).isEqualTo("11222333000181");
    }

    // ------------------------------------------------------------------ apoio

    private MockHttpServletResponse criar(String documento) throws Exception {
        String numero = "DOC-" + numeros.incrementAndGet() + "/2026";
        return mvc.perform(post("/api/contracts").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(numero, documento))).andReturn().getResponse();
    }

    private String json(String numero, String documento) {
        return """
                {"numberContract":"%s","numberProcess":"PROC","object":"Objeto","company":"Prestador",
                 "cnpj":"%s","valueGlobal":1000.00,"valueMensal":100.00,
                 "startDate":"2026-01-01","endDate":"2027-12-31","fiscalIds":[%d],"seiProcessNumber":"SEI"}
                """.formatted(numero, documento, fiscal.getId());
    }

    private String bearer() {
        return "Bearer " + jwt.generate(admin.getUsername(), admin.getName());
    }

    private static String corpo(MockHttpServletResponse res) throws Exception {
        return res.getContentAsString(StandardCharsets.UTF_8);
    }
}
