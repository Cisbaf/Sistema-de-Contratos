package contratos;

import com.jayway.jsonpath.JsonPath;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.ContractStatusHistory;
import contratos.domain.GeneratedDocument;
import contratos.domain.Sector;
import contratos.domain.enums.ContractStatus;
import contratos.domain.enums.ContractStatusTrigger;
import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Linha do tempo unificada do contrato ({@code GET /api/contracts/{id}/timeline}): junta status, documentos gerados e
 * anexos (envio e remoção), do mais recente para o mais antigo. JWT real, H2 isolado ("timelineapitest"), sem
 * transação no teste.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:timelineapitest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ContractTimelineIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4 conteudo-secreto-do-anexo".getBytes();

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired SectorRepository sectors;
    @Autowired ContractRepository contracts;
    @Autowired ContractAttachmentRepository attachments;
    @Autowired ContractStatusHistoryRepository history;
    @Autowired GeneratedDocumentRepository documents;
    @Autowired InterestEmailConfirmationRepository interests;
    @Autowired TechnicalOpinionRepository opinions;
    @Autowired LancamentoFinanceiroRepository lancamentos;
    @Autowired LancamentoFinanceiroHistoricoRepository lancamentoHistorico;
    @Autowired AuditLogRepository audit;

    MockMvc mvc;
    AppUser admin;
    AppUser fiscalVinculado;
    AppUser fiscalSemVinculo;
    Contract contract;
    Contract outroContrato;

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

        Sector setor = sectors.save(new Sector("Setor Timeline"));
        admin = users.save(new AppUser("admin.tl@test.local", "{noop}x", "Admin Timeline",
                "admin.tl@test.local", null, setor, PerfilUsuario.ADMIN));
        fiscalVinculado = users.save(new AppUser("fiscal.tl@test.local", "{noop}x", "Fiscal Vinculado",
                "fiscal.tl@test.local", null, setor, PerfilUsuario.FISCAL));
        fiscalSemVinculo = users.save(new AppUser("fiscal.sem@test.local", "{noop}x", "Fiscal Sem Vinculo",
                "fiscal.sem@test.local", null, setor, PerfilUsuario.FISCAL));

        contract = novoContrato("TL-001/2026", "SEI-TL-001", fiscalVinculado);
        outroContrato = novoContrato("TL-002/2026", "SEI-TL-002", fiscalSemVinculo);
    }

    // ------------------------------------------------------------------ conteúdo e ordem

    @Test
    void juntaAsQuatroFontesDoMaisRecenteParaOMaisAntigo() throws Exception {
        history.save(new ContractStatusHistory(LocalDateTime.of(2026, 3, 1, 10, 0), null, contract,
                ContractStatus.EM_VIGENCIA, ContractStatus.AGUARDANDO_EMAIL_INTERESSE, ContractStatusTrigger.DEADLINE));
        documents.save(new GeneratedDocument("interesse.pdf", 1, "%PDF-1.4 conteudo-secreto-do-documento".getBytes(),
                LocalDateTime.of(2026, 3, 5, 8, 59), admin, DocumentFormat.PDF, DocumentTemplateType.INTEREST_EMAIL, contract));
        history.save(new ContractStatusHistory(LocalDateTime.of(2026, 3, 5, 9, 0), admin, contract,
                ContractStatus.AGUARDANDO_EMAIL_INTERESSE, ContractStatus.EMAIL_ENVIADO, ContractStatusTrigger.INTEREST_EMAIL_GENERATED));
        Long attId = ((Number) JsonPath.read(upload(contract, "a.pdf").getContentAsString(), "$[0].id")).longValue();
        assertThat(remover(attId).getStatus()).isEqualTo(204);

        MockHttpServletResponse res = timeline(contract, admin);

        assertThat(res.getStatus()).isEqualTo(200);
        String json = res.getContentAsString();
        assertThat((List<String>) JsonPath.read(json, "$[*].type")).containsExactly(
                "ATTACHMENT_REMOVED", "ATTACHMENT_UPLOADED", "STATUS_CHANGED", "DOCUMENT_GENERATED", "STATUS_CHANGED");

        // status por prazo: sem usuário (sistema)
        assertThat((String) JsonPath.read(json, "$[4].trigger")).isEqualTo("DEADLINE");
        assertThat((Object) JsonPath.read(json, "$[4].actorName")).isNull();
        assertThat((String) JsonPath.read(json, "$[4].fromStatus")).isEqualTo("EM_VIGENCIA");
        assertThat((String) JsonPath.read(json, "$[4].toStatus")).isEqualTo("AGUARDANDO_EMAIL_INTERESSE");
        // status provocado por um usuário
        assertThat((String) JsonPath.read(json, "$[2].actorName")).isEqualTo("Admin Timeline");
        assertThat((String) JsonPath.read(json, "$[2].trigger")).isEqualTo("INTEREST_EMAIL_GENERATED");
        // documento gerado
        assertThat((String) JsonPath.read(json, "$[3].documentType")).isEqualTo("INTEREST_EMAIL");
        assertThat((Integer) JsonPath.read(json, "$[3].version")).isEqualTo(1);
        assertThat((String) JsonPath.read(json, "$[3].fileName")).isEqualTo("interesse.pdf");
        // anexo enviado e removido
        assertThat((String) JsonPath.read(json, "$[1].fileName")).isEqualTo("a.pdf");
        assertThat((String) JsonPath.read(json, "$[1].actorName")).isEqualTo("Admin Timeline");
        assertThat((String) JsonPath.read(json, "$[1].attType")).isEqualTo("GERAL");
        assertThat((String) JsonPath.read(json, "$[0].fileName")).isEqualTo("a.pdf");
        assertThat((String) JsonPath.read(json, "$[0].actorName")).isEqualTo("Admin Timeline");
    }

    @Test
    void envioERemocaoNoMesmoInstanteMostramARemocaoAcima() throws Exception {
        Long attId = ((Number) JsonPath.read(upload(contract, "a.pdf").getContentAsString(), "$[0].id")).longValue();
        assertThat(remover(attId).getStatus()).isEqualTo(204);
        // força o empate de horário (o relógio real raramente empata): a remoção é o evento mais recente, então vem primeiro
        var anexo = attachments.findById(attId).orElseThrow();
        LocalDateTime instante = LocalDateTime.of(2026, 4, 1, 12, 0);
        ReflectionTestUtils.setField(anexo, "uploadedAt", instante);
        ReflectionTestUtils.setField(anexo, "removedAt", instante);
        attachments.save(anexo);

        String json = timeline(contract, admin).getContentAsString();

        assertThat((List<String>) JsonPath.read(json, "$[*].type")).containsExactly("ATTACHMENT_REMOVED", "ATTACHMENT_UPLOADED");
    }

    @Test
    void naoVazaConteudoDeArquivoNemCaminhoEmDisco() throws Exception {
        documents.save(new GeneratedDocument("interesse.pdf", 1, "%PDF-1.4 conteudo-secreto-do-documento".getBytes(),
                LocalDateTime.of(2026, 3, 5, 8, 59), admin, DocumentFormat.PDF, DocumentTemplateType.INTEREST_EMAIL, contract));
        upload(contract, "a.pdf");

        String json = timeline(contract, admin).getContentAsString();

        assertThat(json).contains("a.pdf").contains("interesse.pdf")
                .doesNotContain("conteudo-secreto").doesNotContain("storagePath").doesNotContain(".gz")
                .doesNotContain("content");
    }

    @Test
    void naoMisturaEventosDeOutroContrato() throws Exception {
        upload(outroContrato, "do-outro.pdf");
        documents.save(new GeneratedDocument("outro.pdf", 1, "x".getBytes(), LocalDateTime.of(2026, 3, 5, 8, 59), admin,
                DocumentFormat.PDF, DocumentTemplateType.INTEREST_EMAIL, outroContrato));
        history.save(new ContractStatusHistory(LocalDateTime.of(2026, 3, 1, 10, 0), null, outroContrato,
                ContractStatus.EM_VIGENCIA, ContractStatus.AGUARDANDO_EMAIL_INTERESSE, ContractStatusTrigger.DEADLINE));
        upload(contract, "meu.pdf");

        String json = timeline(contract, admin).getContentAsString();

        assertThat((List<String>) JsonPath.read(json, "$[*].fileName")).containsExactly("meu.pdf");
        assertThat((List<String>) JsonPath.read(json, "$[*].type")).containsExactly("ATTACHMENT_UPLOADED");
    }

    @Test
    void contratoSemEventosDevolveListaVazia() throws Exception {
        MockHttpServletResponse res = timeline(contract, admin);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString()).isEqualTo("[]");
    }

    // ------------------------------------------------------------------ permissão

    @Test
    void fiscalVinculadoVeALinhaDoTempoDoSeuContrato() throws Exception {
        upload(contract, "a.pdf");

        MockHttpServletResponse res = timeline(contract, fiscalVinculado);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat((List<String>) JsonPath.read(res.getContentAsString(), "$[*].fileName")).containsExactly("a.pdf");
    }

    @Test
    void fiscalSemVinculoEAnonimoRecebem403() throws Exception {
        upload(contract, "a.pdf");

        assertThat(timeline(contract, fiscalSemVinculo).getStatus()).as("fiscal sem vínculo").isEqualTo(403);
        assertThat(timeline(outroContrato, fiscalVinculado).getStatus()).as("fiscal em contrato que não é dele").isEqualTo(403);
        assertThat(mvc.perform(get("/api/contracts/" + contract.getId() + "/timeline")).andReturn().getResponse().getStatus())
                .as("anônimo").isEqualTo(403);
    }

    @Test
    void contratoInexistenteDa404ParaAdmin() throws Exception {
        MockHttpServletResponse res = mvc.perform(get("/api/contracts/999999/timeline")
                .header("Authorization", bearer(admin))).andReturn().getResponse();

        assertThat(res.getStatus()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ apoio

    private Contract novoContrato(String numero, String sei, AppUser fiscal) {
        Contract c = new Contract();
        c.update(numero, "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 1),
                null, "2", Set.of(fiscal), sei, 12);
        return contracts.save(c);
    }

    private MockHttpServletResponse timeline(Contract alvo, AppUser as) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/contracts/" + alvo.getId() + "/timeline");
        req.header("Authorization", bearer(as));
        return mvc.perform(req).andReturn().getResponse();
    }

    private MockHttpServletResponse upload(Contract alvo, String nome) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/attachment/" + alvo.getId());
        req.file(new MockMultipartFile("files", nome, "application/pdf", PDF));
        req.header("Authorization", bearer(admin));
        MockHttpServletResponse res = mvc.perform(req).andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return res;
    }

    private MockHttpServletResponse remover(Long id) throws Exception {
        return mvc.perform(delete("/api/attachment/" + id).header("Authorization", bearer(admin))).andReturn().getResponse();
    }

    private String bearer(AppUser as) {
        return "Bearer " + jwt.generate(as.getUsername(), as.getName());
    }
}
