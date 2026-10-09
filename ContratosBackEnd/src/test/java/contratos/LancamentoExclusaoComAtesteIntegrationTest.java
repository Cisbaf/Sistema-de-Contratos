package contratos;

import com.jayway.jsonpath.JsonPath;
import contratos.domain.AppUser;
import contratos.domain.AuditLog;
import contratos.domain.Contract;
import contratos.domain.DocumentTemplate;
import contratos.domain.GeneratedDocument;
import contratos.domain.Sector;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import contratos.domain.enums.DocumentTemplateType;
import contratos.domain.enums.PerfilUsuario;
import contratos.repository.AuditLogRepository;
import contratos.repository.ContractAttachmentRepository;
import contratos.repository.ContractRepository;
import contratos.repository.ContractStatusHistoryRepository;
import contratos.repository.DocumentTemplateRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Exclusão de lançamento que já tem ateste gerado (achado no servidor em 08/10/2026: FK
 * {@code generated_document.lancamento_id} -> erro 1451 / 409 "conflito de dados"). Desde o M4 a regra é "exclusão
 * híbrida": lançamento nunca editado some de verdade e a nota fiscal fica livre; já editado só é desativado. Quando o
 * ateste ganhou o vínculo com o lançamento (30/09), a exclusão física passou a esbarrar nele.
 *
 * <p>Comportamento (decisão de 08/10, EXC-10): a exclusão funciona, o ateste do lançamento é excluído junto, a nota
 * fiscal fica livre e a Auditoria registra o lançamento e cada documento apagado. Lançamento já editado continua só
 * desativado, com o ateste mantido. JWT real do admin, H2 isolado "lancamentoexclusaoatesteapitest", sem transação no teste.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:lancamentoexclusaoatesteapitest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class LancamentoExclusaoComAtesteIntegrationTest {

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
    @Autowired DocumentTemplateRepository templates;
    @Autowired AuditLogRepository audit;

    MockMvc mvc;
    AppUser admin;
    Contract contract;

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
        templates.deleteAll();
        users.deleteAll();
        sectors.deleteAll();

        Sector setor = sectors.save(new Sector("Setor Exclusao"));
        admin = users.save(new AppUser("admin.exc@test.local", "{noop}senha-irrelevante", "Admin Exclusao",
                "admin.exc@test.local", null, setor, PerfilUsuario.ADMIN));
        templates.save(new DocumentTemplate(DocumentTemplateType.PAYMENT_CHECKLIST, "Ateste dos fiscais",
                LocalDateTime.now(), admin));

        Contract c = new Contract();
        c.update("EXC-001/2026", "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("100000.00"), new BigDecimal("1000.00"), LocalDate.of(2026, 10, 6),
                LocalDate.of(2027, 2, 16), null, null, Set.of(), "SEI-EXC", 12);
        contract = contracts.save(c);
    }

    @Test
    void excluirLancamentoNuncaEditadoComAtesteExcluiOAtesteJuntoEFicaRegistradoNaAuditoria() throws Exception {
        Long id = criar("NF-1");
        gerarAteste(id);
        String arquivo = atestesDoContrato().get(0).getFileName();

        MockHttpServletResponse res = excluir(id);

        assertThat(res.getStatus()).as(corpo(res)).isEqualTo(204);
        assertThat(lancamentos.findById(id)).as("exclusão física").isEmpty();
        assertThat(atestesDoContrato()).as("o ateste do lançamento é excluído junto").isEmpty();

        List<AuditLog> docs = audit.findAll().stream()
                .filter(a -> a.getEntityType() == AuditEntityType.DOCUMENT && a.getAction() == AuditAction.DELETE).toList();
        assertThat(docs).as("uma linha de auditoria por documento apagado").hasSize(1);
        assertThat(docs.get(0).getSummary()).contains("NF-1").contains("EXC-001/2026");
        assertThat(docs.get(0).getDetails()).contains(arquivo);

        AuditLog doLancamento = audit.findAll().stream()
                .filter(a -> a.getEntityType() == AuditEntityType.LANCAMENTO && a.getAction() == AuditAction.DELETE)
                .findFirst().orElseThrow();
        assertThat(doLancamento.getDetails()).contains("Exclusão definitiva").contains("Ateste excluído junto").contains(arquivo);
    }

    @Test
    void exclusaoAparecePeloHistoricoDoContratoComQuemEQuando() throws Exception {
        Long id = criar("NF-1");
        gerarAteste(id);
        assertThat(excluir(id).getStatus()).isEqualTo(204);

        String corpo = corpo(mvc.perform(get("/api/contracts/" + contract.getId() + "/timeline")
                .header("Authorization", bearer())).andReturn().getResponse());

        List<String> lancamento = JsonPath.read(corpo, "$[?(@.type=='LANCAMENTO_DELETED')].description");
        List<String> documento = JsonPath.read(corpo, "$[?(@.type=='DOCUMENT_DELETED')].description");
        List<String> quem = JsonPath.read(corpo, "$[?(@.type=='LANCAMENTO_DELETED')].actorName");
        assertThat(lancamento).hasSize(1);
        assertThat(lancamento.get(0)).contains("NF-1");
        assertThat(documento).hasSize(1);
        assertThat(documento.get(0)).contains("NF-1");
        assertThat(quem).containsExactly("Admin Exclusao");
        assertThat(corpo).as("o histórico não expõe valores financeiros").doesNotContain("100.00");
        // o ateste gerado antes não aparece mais como documento gerado (foi excluído), só como exclusão
        assertThat((List<?>) JsonPath.read(corpo, "$[?(@.type=='DOCUMENT_GENERATED')]")).isEmpty();
    }

    @Test
    void notaFiscalFicaLivreEGeraNovoAtesteParaOLancamentoNovo() throws Exception {
        Long primeiro = criar("NF-1");
        gerarAteste(primeiro);
        assertThat(excluir(primeiro).getStatus()).isEqualTo(204);

        MockHttpServletResponse recriado = post("NF-1");
        assertThat(recriado.getStatus()).as(corpo(recriado)).isEqualTo(201);
        Long segundo = ((Number) JsonPath.read(corpo(recriado), "$.id")).longValue();
        gerarAteste(segundo);

        assertThat(atestesDoContrato()).as("só o ateste novo; o antigo foi excluído com o lançamento").hasSize(1);
        assertThat(documents.findByDocumentTypeAndLancamento_Id(DocumentTemplateType.PAYMENT_CHECKLIST, segundo))
                .as("o ateste novo é do lançamento novo").isPresent();
    }

    @Test
    void excluirLancamentoJaEditadoComAtesteSoDesativaEMantemOAtesteEOVinculo() throws Exception {
        Long id = criar("NF-1");
        gerarAteste(id);
        assertThat(editar(id, "NF-1", "150.00").getStatus()).isEqualTo(200);

        MockHttpServletResponse res = excluir(id);

        assertThat(res.getStatus()).as(corpo(res)).isEqualTo(204);
        assertThat(lancamentos.findById(id)).isPresent().get().extracting("ativo").isEqualTo(false);
        List<GeneratedDocument> atestes = atestesDoContrato();
        assertThat(atestes).hasSize(1);
        assertThat(atestes.get(0).getLancamento()).isNotNull();
        assertThat(audit.findAll()).as("nenhum documento é apagado nesse caminho")
                .noneMatch(a -> a.getEntityType() == AuditEntityType.DOCUMENT && a.getAction() == AuditAction.DELETE);
    }

    @Test
    void excluirLancamentoSemAtesteContinuaFuncionando() throws Exception {
        Long id = criar("NF-1");

        assertThat(excluir(id).getStatus()).isEqualTo(204);
        assertThat(lancamentos.findById(id)).isEmpty();
    }

    // ------------------------------------------------------------------ apoio

    private List<GeneratedDocument> atestesDoContrato() {
        return documents.findAll().stream()
                .filter(d -> d.getDocumentType() == DocumentTemplateType.PAYMENT_CHECKLIST)
                .toList();
    }

    private Long criar(String nota) throws Exception {
        MockHttpServletResponse res = post(nota);
        assertThat(res.getStatus()).as(corpo(res)).isEqualTo(201);
        return ((Number) JsonPath.read(corpo(res), "$.id")).longValue();
    }

    private MockHttpServletResponse post(String nota) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/contracts/" + contract.getId() + "/lancamentos")
                        .header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(nota, "100.00"))).andReturn().getResponse();
    }

    private MockHttpServletResponse editar(Long id, String nota, String valor) throws Exception {
        return mvc.perform(put("/api/lancamentos/" + id).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(nota, valor))).andReturn().getResponse();
    }

    private void gerarAteste(Long id) throws Exception {
        MockHttpServletResponse res = mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/lancamentos/" + id + "/checklist").header("Authorization", bearer()))
                .andReturn().getResponse();
        assertThat(res.getStatus()).as(corpo(res)).isEqualTo(201);
    }

    private MockHttpServletResponse excluir(Long id) throws Exception {
        return mvc.perform(delete("/api/lancamentos/" + id).header("Authorization", bearer())).andReturn().getResponse();
    }

    private static String json(String nota, String valor) {
        return "{\"numeroProcesso\":\"PROC-1\",\"notaFiscal\":\"" + nota + "\",\"parcela\":\"1\","
                + "\"competencia\":\"2026-10-01\",\"valorNota\":" + valor + ",\"observacoes\":null}";
    }

    private static String corpo(MockHttpServletResponse res) throws Exception {
        return res.getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer() {
        return "Bearer " + jwt.generate(admin.getUsername(), admin.getName());
    }
}
