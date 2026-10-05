package contratos;

import com.jayway.jsonpath.JsonPath;
import contratos.domain.*;
import contratos.domain.enums.AttachmentType;
import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;
import contratos.domain.enums.PerfilUsuario;
import contratos.repository.*;
import contratos.security.JwtService;
import contratos.service.AttachmentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * M6-70 — Matriz de permissões (RBAC): cada endpoint protegido x cada perfil, com JWT real
 * (token gerado pelo {@link JwtService} e enviado em Authorization: Bearer, passando pelo
 * JwtAuthenticationFilter de verdade) e banco H2 em memória isolado ("rbactest", só de teste).
 *
 * Regra de leitura da matriz:
 *  - perfil NÃO permitido  -> exige 403 exato (anônimo também: Http403ForbiddenEntryPoint);
 *  - perfil permitido      -> exige "passou da autorização": status diferente de 401/403.
 *    O que acontece depois da autorização (200, 404, 409...) é regra de negócio, testada em outros lugares.
 *
 * Limite conhecido do MockMvc: uma exceção sem handler vira ServletException (em execução real viraria
 * 403 vazio pelo /error). Aqui isso conta como "passou da autorização", a menos que a causa seja AccessDenied.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:rbactest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class RbacMatrixIntegrationTest {

    enum Persona { ADMIN, CI, FV, FS, ANON }

    /** Passou da autorização mas o handler estourou sem tratamento (ver javadoc da classe). */
    private static final int PASSED_BUT_UNHANDLED = -1;

    @FunctionalInterface
    interface Req {
        AbstractMockHttpServletRequestBuilder<?> build(RbacMatrixIntegrationTest t) throws Exception;
    }

    record Case(String name, Req req, Set<Persona> allowed) {
        @Override public String toString() { return name; }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private SectorRepository sectors;
    @Autowired private ContractRepository contracts;
    @Autowired private LancamentoFinanceiroRepository lancamentos;
    @Autowired private ContractAttachmentRepository attachments;
    @Autowired private AttachmentStorage storage;
    @Autowired private GeneratedDocumentRepository documents;
    @Autowired private DocumentTemplateRepository templates;

    private MockMvc mvc;

    // Atores
    private AppUser admin, ci, fv, fs;
    // Alvos de gestão de usuários
    private AppUser targetFiscal, targetAdmin;
    // Dados
    private Long sectorId, contractId, otherContractId, lancamentoId, attachmentId, amendmentDocId, documentId, templateId;
    private static final long MISSING = 999_999L;

    @BeforeEach
    void seed() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Sector sector = sectors.save(new Sector("Setor RBAC"));
        sectorId = sector.getId();
        admin = users.save(user("admin.rbac@test.local", "Admin RBAC", sector, PerfilUsuario.ADMIN));
        ci = users.save(user("ci.rbac@test.local", "CI RBAC", sector, PerfilUsuario.CONTROLE_INTERNO));
        fv = users.save(user("fv.rbac@test.local", "Fiscal Vinculado", sector, PerfilUsuario.FISCAL));
        fs = users.save(user("fs.rbac@test.local", "Fiscal Sem Vinculo", sector, PerfilUsuario.FISCAL));
        targetFiscal = users.save(user("alvo.rbac@test.local", "Alvo Fiscal", sector, PerfilUsuario.FISCAL));
        targetAdmin = users.save(user("alvoadmin.rbac@test.local", "Alvo Admin", sector, PerfilUsuario.ADMIN));

        // contrato do FV; "outro" pertence ao targetFiscal (FV não pode enxergar)
        Contract contract = contracts.save(contract("RBAC-001", fv));
        Contract other = contracts.save(contract("RBAC-002", targetFiscal));
        contractId = contract.getId();
        otherContractId = other.getId();

        lancamentoId = lancamentos.save(new LancamentoFinanceiro("PROC-1", "NF-1", LocalDate.of(2026, 2, 1), "1",
                new BigDecimal("10.00"), "obs", contract, admin)).getId();
        attachmentId = attachments.save(new ContractAttachment(contract, "doc.pdf", "application/pdf", 8,
                storedFile(contract.getId()), AttachmentType.GERAL, admin)).getId();
        amendmentDocId = attachments.save(new ContractAttachment(contract, "aditivo.pdf", "application/pdf", 8,
                storedFile(contract.getId()), AttachmentType.TERMO_ADITIVO, admin)).getId();
        documentId = documents.save(new GeneratedDocument("gerado.pdf", 1, "%PDF-1.4".getBytes(),
                LocalDateTime.now(), admin, DocumentFormat.PDF, DocumentTemplateType.TECHNICAL_OPINION, contract)).getId();
        templateId = templates.findAll().stream().findFirst()
                .orElseGet(() -> templates.save(new DocumentTemplate(DocumentTemplateType.INTEREST_EMAIL, "Texto",
                        LocalDateTime.now(), admin))).getId();
    }

    private static AppUser user(String email, String name, Sector sector, PerfilUsuario perfil) {
        // username == e-mail em minúsculas (convenção do sistema; o token usa o username como subject)
        return new AppUser(email, "{noop}senha-irrelevante", name, email, null, sector, perfil);
    }

    private Contract contract(String number, AppUser fiscal) {
        Contract c = new Contract();
        c.update(number, "PROC", "Objeto", "Empresa Ltda", "11222333000181", new BigDecimal("1000.00"),
                new BigDecimal("100.00"), LocalDate.of(2026, 1, 1), LocalDate.now().plusYears(1), null, null,
                Set.of(fiscal), "SEI", null);
        return c;
    }

    // ------------------------------------------------------------------ infraestrutura

    private String token(Persona p) {
        AppUser u = switch (p) {
            case ADMIN -> admin;
            case CI -> ci;
            case FV -> fv;
            case FS -> fs;
            case ANON -> null;
        };
        return u == null ? null : jwt.generate(u.getUsername(), u.getName());
    }

    private AbstractMockHttpServletRequestBuilder<?> as(Persona p, AbstractMockHttpServletRequestBuilder<?> req) {
        String token = token(p);
        return token == null ? req : req.header("Authorization", "Bearer " + token);
    }

    /** Executa e devolve só o status (ver javadoc da classe sobre ServletException). */
    private int call(Persona p, AbstractMockHttpServletRequestBuilder<?> req) throws Exception {
        try {
            return mvc.perform(as(p, req)).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            if (causedByAccessDenied(e)) return 403;
            return PASSED_BUT_UNHANDLED;
        }
    }

    private String body(Persona p, AbstractMockHttpServletRequestBuilder<?> req) throws Exception {
        return mvc.perform(as(p, req)).andReturn().getResponse().getContentAsString();
    }

    private static boolean causedByAccessDenied(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof AccessDeniedException) return true;
            if (c.getCause() == c) break;
        }
        return false;
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String json) {
        return b.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    // ------------------------------------------------------------------ corpos válidos (para não cair em 400 antes do @PreAuthorize)

    private String contractJson(String number) {
        return """
                {"numberContract":"%s","numberProcess":"PROC","object":"Objeto","company":"Empresa Ltda",
                 "cnpj":"11222333000181","valueGlobal":1000.00,"valueMensal":100.00,
                 "startDate":"2026-01-01","endDate":"2027-12-31","fiscalIds":[%d],"seiProcessNumber":"SEI"}
                """.formatted(number, fv.getId());
    }

    private String userJson(String email, String perfil) {
        return """
                {"name":"Pessoa RBAC","email":"%s","cellPhone":"31999999999","sectorId":%d,
                 "password":"senha123","perfil":"%s"}
                """.formatted(email, sectorId, perfil);
    }

    private static final String LANCAMENTO_JSON = """
            {"numeroProcesso":"P1","notaFiscal":"NF2","parcela":"2","competencia":"2026-03-01",
             "valorNota":15.00,"observacoes":"x"}
            """;
    private static final String TEXT_JSON = "{\"text\":\"texto\"}";

    // ------------------------------------------------------------------ a matriz

    private static final Set<Persona> ADM = EnumSet.of(Persona.ADMIN);
    private static final Set<Persona> AC = EnumSet.of(Persona.ADMIN, Persona.CI);
    private static final Set<Persona> ACF = EnumSet.of(Persona.ADMIN, Persona.CI, Persona.FV);
    private static final Set<Persona> FVO = EnumSet.of(Persona.FV);
    private static final Set<Persona> FISCAIS = EnumSet.of(Persona.FV, Persona.FS);
    private static final Set<Persona> ALL = EnumSet.of(Persona.ADMIN, Persona.CI, Persona.FV, Persona.FS);

    private static Case c(String name, Req req, Set<Persona> allowed) { return new Case(name, req, allowed); }

    static List<Case> cases() {
        return List.of(
            // ---- contratos
            c("GET /contracts", t -> get("/api/contracts"), AC),
            c("GET /contracts/mine", t -> get("/api/contracts/mine"), FISCAIS),
            c("GET /contracts/{id}", t -> get("/api/contracts/" + t.contractId), ACF),
            c("POST /contracts", t -> json(post("/api/contracts"), t.contractJson("RBAC-NOVO")), AC),
            c("PUT /contracts/{id}", t -> json(put("/api/contracts/" + t.contractId), t.contractJson("RBAC-001")), AC),
            c("DELETE /contracts/{id}", t -> delete("/api/contracts/" + t.contractId), ADM),
            c("POST /contracts/{id}/interest-email/confirm", t -> post("/api/contracts/" + t.contractId + "/interest-email/confirm"), FVO),
            c("GET /contracts/{id}/interest-email/preview", t -> get("/api/contracts/" + t.contractId + "/interest-email/preview"), ACF),
            c("POST /contracts/{id}/technical-opinion/preview", t -> json(post("/api/contracts/" + t.contractId + "/technical-opinion/preview"), TEXT_JSON), ACF),
            c("GET /contracts/{id}/technical-opinion/mine", t -> get("/api/contracts/" + t.contractId + "/technical-opinion/mine"), FVO),
            c("GET /contracts/{id}/technical-opinion/collective", t -> get("/api/contracts/" + t.contractId + "/technical-opinion/collective"), AC),
            c("GET /contracts/{id}/technical-opinion/progress", t -> get("/api/contracts/" + t.contractId + "/technical-opinion/progress"), ACF),
            c("POST /contracts/{id}/technical-opinion/submit", t -> json(post("/api/contracts/" + t.contractId + "/technical-opinion/submit"), TEXT_JSON), FVO),
            c("GET /contracts/{id}/supplier-mask/preview", t -> get("/api/contracts/" + t.contractId + "/supplier-mask/preview"), ACF),
            // ---- modelos de documento
            c("GET /document-templates", t -> get("/api/document-templates"), AC),
            c("POST /document-templates", t -> json(post("/api/document-templates"), "{\"templateType\":\"SUPPLIER_RENEWAL_EMAIL\",\"content\":\"x\"}"), AC),
            c("PUT /document-templates/{id}", t -> json(put("/api/document-templates/" + t.templateId), "{\"content\":\"novo\"}"), AC),
            // ---- anexos
            c("GET /attachment/ativos/{contractId}", t -> get("/api/attachment/ativos/" + t.contractId), ACF),
            c("GET /attachment/time_line/{contractId}", t -> get("/api/attachment/time_line/" + t.contractId), ACF),
            c("POST /attachment/{contractId}", t -> multipart("/api/attachment/" + t.contractId)
                    .file(new MockMultipartFile("files", "a.pdf", "application/pdf", "%PDF-1.4 x".getBytes())), AC),
            c("POST /contracts/{id}/amendments", t -> multipart("/api/contracts/" + t.contractId + "/amendments")
                    .file(new MockMultipartFile("file", "aditivo.pdf", "application/pdf", "%PDF-1.4 x".getBytes()))
                    .param("newEndDate", "2027-03-01"), AC),
            c("PUT /contracts/{id}/amendments/{attId}/file", t -> multipart(HttpMethod.PUT, "/api/contracts/" + t.contractId + "/amendments/" + t.amendmentDocId + "/file")
                    .file(new MockMultipartFile("file", "novo.pdf", "application/pdf", "%PDF-1.4 y".getBytes())), AC),
            c("GET /attachment/baixar/{attId}", t -> get("/api/attachment/baixar/" + t.attachmentId), ACF),
            c("DELETE /attachment/{attId}", t -> delete("/api/attachment/" + t.attachmentId), AC),
            // ---- documentos gerados
            c("GET /generate-document/history", t -> get("/api/generate-document/history").param("contractId", String.valueOf(t.contractId)), ACF),
            c("GET /generate-document/download", t -> get("/api/generate-document/download").param("documentId", String.valueOf(t.documentId)), ACF),
            c("GET /generate-document (busca)", t -> get("/api/generate-document"), AC),
            // ---- lançamentos financeiros
            c("POST /contracts/{id}/lancamentos", t -> json(post("/api/contracts/" + t.contractId + "/lancamentos"), LANCAMENTO_JSON), ACF),
            c("GET /contracts/{id}/lancamentos", t -> get("/api/contracts/" + t.contractId + "/lancamentos"), ACF),
            c("GET /contracts/{id}/lancamentos/saldo", t -> get("/api/contracts/" + t.contractId + "/lancamentos/saldo"), ACF),
            c("GET /contracts/{id}/lancamentos/historico", t -> get("/api/contracts/" + t.contractId + "/lancamentos/historico"), ACF),
            c("PUT /lancamentos/{id}", t -> json(put("/api/lancamentos/" + t.lancamentoId), LANCAMENTO_JSON), ACF),
            c("DELETE /lancamentos/{id}", t -> delete("/api/lancamentos/" + t.lancamentoId), ACF),
            c("GET /lancamentos/{id}/checklist/preview", t -> get("/api/lancamentos/" + t.lancamentoId + "/checklist/preview"), ACF),
            c("POST /lancamentos/{id}/checklist", t -> post("/api/lancamentos/" + t.lancamentoId + "/checklist"), ACF),
            // ---- notificações e auditoria
            c("GET /contracts/{id}/notificacoes", t -> get("/api/contracts/" + t.contractId + "/notificacoes"), ACF),
            c("GET /notificacoes", t -> get("/api/notificacoes"), AC),
            c("GET /notificacoes/parametros", t -> get("/api/notificacoes/parametros"), AC),
            c("PUT /notificacoes/parametros", t -> json(put("/api/notificacoes/parametros"), "{\"firstAlertMonths\":6,\"secondAlertMonths\":3}"), ADM),
            c("GET /auditoria", t -> get("/api/auditoria"), AC),
            // ---- setores
            c("GET /sectors", t -> get("/api/sectors"), ALL),
            c("POST /sectors", t -> json(post("/api/sectors"), "{\"name\":\"Setor Novo\"}"), AC),
            c("PUT /sectors/{id}", t -> json(put("/api/sectors/" + t.sectorId), "{\"name\":\"Setor Editado\"}"), AC),
            c("DELETE /sectors/{id}", t -> delete("/api/sectors/" + t.sectorId), ADM),
            // ---- usuários
            c("GET /users", t -> get("/api/users"), AC),
            c("GET /users/me", t -> get("/api/users/me"), ALL),
            c("POST /users", t -> json(post("/api/users"), t.userJson("novo.rbac@test.local", "FISCAL")), AC),
            c("PUT /users/{id}", t -> json(put("/api/users/" + t.targetFiscal.getId()), t.userJson("alvo.rbac@test.local", "FISCAL")), AC),
            c("DELETE /users/{id}", t -> delete("/api/users/" + t.targetFiscal.getId()), ADM)
        );
    }

    static Stream<Arguments> matrix() {
        return cases().stream().flatMap(cs -> Stream.of(Persona.values()).map(p -> Arguments.of(cs, p)));
    }

    @ParameterizedTest(name = "{0} como {1}")
    @MethodSource("matrix")
    void matrizRbac(Case cs, Persona persona) throws Exception {
        int status = call(persona, cs.req().build(this));

        if (cs.allowed().contains(persona)) {
            assertThat(status)
                    .as("%s deveria PASSAR da autorização em %s", persona, cs)
                    .isNotIn(401, 403);
        } else {
            assertThat(status)
                    .as("%s deveria receber 403 em %s", persona, cs)
                    .isEqualTo(403);
        }
    }

    @Test
    void matrizCobreTodosOsEndpointsEsperados() {
        // trava contra "esquecer" uma linha ao editar a matriz: 48 endpoints protegidos x 5 perfis
        assertThat(cases()).hasSize(49);
        assertThat(cases().stream().map(Case::name)).doesNotHaveDuplicates();
    }

    // ------------------------------------------------------------------ vínculo do fiscal ao contrato

    @Test
    void fiscalVeSomenteOsProprioContratosEmMine() throws Exception {
        List<Integer> idsFv = JsonPath.read(body(Persona.FV, get("/api/contracts/mine")), "$[*].id");
        assertThat(idsFv).containsExactly(contractId.intValue());

        List<Integer> idsFs = JsonPath.read(body(Persona.FS, get("/api/contracts/mine")), "$[*].id");
        assertThat(idsFs).isEmpty();
    }

    @Test
    void fiscalNaoLeContratoDeOutroFiscalMasLeOProprio() throws Exception {
        assertThat(call(Persona.FV, get("/api/contracts/" + contractId))).isEqualTo(200);
        assertThat(call(Persona.FV, get("/api/contracts/" + otherContractId))).isEqualTo(403);
        assertThat(call(Persona.FV, get("/api/attachment/ativos/" + otherContractId))).isEqualTo(403);
        assertThat(call(Persona.FV, json(post("/api/contracts/" + otherContractId + "/lancamentos"), LANCAMENTO_JSON))).isEqualTo(403);
        assertThat(call(Persona.FV, get("/api/contracts/" + otherContractId + "/lancamentos"))).isEqualTo(403);
    }

    @Test
    void autorizacaoPorFilhoVerificaOContratoDonoDoRecurso() throws Exception {
        // recursos do contrato do FV; FS (fiscal sem vínculo) não pode tocar em nenhum deles
        assertThat(call(Persona.FS, get("/api/attachment/baixar/" + attachmentId))).isEqualTo(403);
        assertThat(call(Persona.FS, get("/api/generate-document/download").param("documentId", String.valueOf(documentId)))).isEqualTo(403);
        assertThat(call(Persona.FS, json(put("/api/lancamentos/" + lancamentoId), LANCAMENTO_JSON))).isEqualTo(403);
        assertThat(call(Persona.FS, delete("/api/lancamentos/" + lancamentoId))).isEqualTo(403);
        assertThat(call(Persona.FS, get("/api/lancamentos/" + lancamentoId + "/checklist/preview"))).isEqualTo(403);
        assertThat(call(Persona.FS, post("/api/lancamentos/" + lancamentoId + "/checklist"))).isEqualTo(403);
        // e o dono (FV) passa
        assertThat(call(Persona.FV, get("/api/attachment/baixar/" + attachmentId))).isEqualTo(200);
        assertThat(call(Persona.FV, get("/api/generate-document/download").param("documentId", String.valueOf(documentId)))).isEqualTo(200);
    }

    /**
     * Regra (decidida em 01/10/2026, correção do achado "id de filho vaza existência"): quem NÃO é Admin/CI recebe 403
     * tanto para "id não existe" quanto para "id existe mas não é meu" — em qualquer endpoint, de contrato ou de filho
     * (lançamento, anexo, documento gerado). Só Admin/CI enxergam o 404 de verdade.
     */
    @Test
    void idInexistenteNaoVazaExistencia_fiscalSempre403_adminECI404() throws Exception {
        // contrato (o @PreAuthorize já cobria)
        assertThat(call(Persona.FS, get("/api/contracts/" + MISSING))).isEqualTo(403);
        assertThat(call(Persona.FS, get("/api/contracts/" + contractId))).isEqualTo(403);
        assertThat(call(Persona.ADMIN, get("/api/contracts/" + MISSING))).isEqualTo(404);

        for (Persona p : List.of(Persona.FS, Persona.FV)) {
            for (Req r : childRequests(MISSING)) {
                assertThat(call(p, r.build(this))).as("%s em id-filho inexistente", p).isEqualTo(403);
            }
        }
        for (Persona p : List.of(Persona.ADMIN, Persona.CI)) {
            for (Req r : childRequests(MISSING)) {
                assertThat(call(p, r.build(this))).as("%s em id-filho inexistente", p).isEqualTo(404);
            }
        }
        // e "existe mas não é meu" continua 403 para o fiscal sem vínculo
        for (Req r : childRequests(null)) {
            assertThat(call(Persona.FS, r.build(this))).as("FS em id-filho existente de outro contrato").isEqualTo(403);
        }
    }

    /** Os 6 endpoints por id de filho; {@code missing == null} usa os ids reais semeados. */
    private List<Req> childRequests(Long missing) {
        return List.of(
            t -> json(put("/api/lancamentos/" + (missing != null ? missing : t.lancamentoId)), LANCAMENTO_JSON),
            t -> delete("/api/lancamentos/" + (missing != null ? missing : t.lancamentoId)),
            t -> get("/api/lancamentos/" + (missing != null ? missing : t.lancamentoId) + "/checklist/preview"),
            t -> post("/api/lancamentos/" + (missing != null ? missing : t.lancamentoId) + "/checklist"),
            t -> get("/api/attachment/baixar/" + (missing != null ? missing : t.attachmentId)),
            t -> get("/api/generate-document/download").param("documentId", String.valueOf(missing != null ? missing : t.documentId))
        );
    }

    // ------------------------------------------------------------------ Controle Interno: gestão de usuários e setores (regra de contenção)

    @Test
    void controleInternoNaoCriaAdministrador() throws Exception {
        assertThat(call(Persona.CI, json(post("/api/users"), userJson("novo.admin@test.local", "ADMIN")))).isEqualTo(403);
        assertThat(users.findByUsername("novo.admin@test.local")).isEmpty();
    }

    @Test
    void controleInternoCriaFiscalEControleInterno() throws Exception {
        assertThat(call(Persona.CI, json(post("/api/users"), userJson("novo.fiscal@test.local", "FISCAL")))).isIn(200, 201);
        assertThat(call(Persona.CI, json(post("/api/users"), userJson("novo.ci@test.local", "CONTROLE_INTERNO")))).isIn(200, 201);
        assertThat(users.findByUsername("novo.fiscal@test.local")).isPresent();
        assertThat(users.findByUsername("novo.ci@test.local")).isPresent();
    }

    @Test
    void controleInternoNaoPromoveNinguemParaAdminNemSiMesmo() throws Exception {
        assertThat(call(Persona.CI, json(put("/api/users/" + targetFiscal.getId()), userJson("alvo.rbac@test.local", "ADMIN")))).isEqualTo(403);
        assertThat(call(Persona.CI, json(put("/api/users/" + ci.getId()), userJson("ci.rbac@test.local", "ADMIN")))).isEqualTo(403);
        assertThat(users.findById(targetFiscal.getId()).orElseThrow().getPerfil()).isEqualTo(PerfilUsuario.FISCAL);
        assertThat(users.findById(ci.getId()).orElseThrow().getPerfil()).isEqualTo(PerfilUsuario.CONTROLE_INTERNO);
    }

    @Test
    void controleInternoNaoEditaNemRebaixaAdministrador() throws Exception {
        // mesmo só trocando senha/nome (perfil ADMIN mantido) ou rebaixando para outro perfil: 403
        assertThat(call(Persona.CI, json(put("/api/users/" + targetAdmin.getId()), userJson("alvoadmin.rbac@test.local", "ADMIN")))).isEqualTo(403);
        assertThat(call(Persona.CI, json(put("/api/users/" + targetAdmin.getId()), userJson("alvoadmin.rbac@test.local", "CONTROLE_INTERNO")))).isEqualTo(403);
        assertThat(call(Persona.CI, json(put("/api/users/" + targetAdmin.getId()), userJson("alvoadmin.rbac@test.local", "FISCAL")))).isEqualTo(403);
        assertThat(users.findById(targetAdmin.getId()).orElseThrow().getPerfil()).isEqualTo(PerfilUsuario.ADMIN);
    }

    @Test
    void controleInternoEditaFiscalEControleInterno() throws Exception {
        // fs não tem contrato ativo (targetFiscal tem, e a regra de negócio "fiscal com contrato ativo não muda de perfil" daria 409)
        assertThat(call(Persona.CI, json(put("/api/users/" + fs.getId()), userJson("fs.rbac@test.local", "FISCAL")))).isEqualTo(200);
        assertThat(call(Persona.CI, json(put("/api/users/" + fs.getId()), userJson("fs.rbac@test.local", "CONTROLE_INTERNO")))).isEqualTo(200);
        assertThat(users.findById(fs.getId()).orElseThrow().getPerfil()).isEqualTo(PerfilUsuario.CONTROLE_INTERNO);
    }

    @Test
    void administradorPodeCriarEEditarAdministrador() throws Exception {
        assertThat(call(Persona.ADMIN, json(post("/api/users"), userJson("novo.admin@test.local", "ADMIN")))).isIn(200, 201);
        assertThat(call(Persona.ADMIN, json(put("/api/users/" + targetAdmin.getId()), userJson("alvoadmin.rbac@test.local", "ADMIN")))).isEqualTo(200);
    }

    @Test
    void ninguemAlemDoAdminExcluiUsuarioOuSetor() throws Exception {
        for (Persona p : List.of(Persona.CI, Persona.FV, Persona.FS, Persona.ANON)) {
            assertThat(call(p, delete("/api/users/" + targetFiscal.getId()))).as("delete user como %s", p).isEqualTo(403);
            assertThat(call(p, delete("/api/sectors/" + sectorId))).as("delete setor como %s", p).isEqualTo(403);
        }
        assertThat(users.findById(targetFiscal.getId())).isPresent();
        assertThat(sectors.findById(sectorId)).isPresent();
    }

    @Test
    void controleInternoCriaEEditaSetor() throws Exception {
        assertThat(call(Persona.CI, json(post("/api/sectors"), "{\"name\":\"Setor do CI\"}"))).isIn(200, 201);
        assertThat(call(Persona.CI, json(put("/api/sectors/" + sectorId), "{\"name\":\"Setor Renomeado\"}"))).isEqualTo(200);
    }

    @Test
    void fiscalNaoGerenciaUsuariosNemSetores() throws Exception {
        for (Persona p : List.of(Persona.FV, Persona.FS)) {
            assertThat(call(p, get("/api/users"))).isEqualTo(403);
            assertThat(call(p, json(post("/api/users"), userJson("x.rbac@test.local", "FISCAL")))).isEqualTo(403);
            assertThat(call(p, json(post("/api/sectors"), "{\"name\":\"Setor X\"}"))).isEqualTo(403);
        }
        assertThat(users.findByUsername("x.rbac@test.local")).isEmpty();
    }

    // ------------------------------------------------------------------ caminhos felizes (o 403 não pode ter "quebrado" quem tem permissão)

    @Test
    void perfisPermitidosRealmenteExecutamAOperacao() throws Exception {
        assertThat(call(Persona.ADMIN, json(post("/api/contracts"), contractJson("RBAC-A")))).isBetween(200, 201);
        assertThat(call(Persona.CI, json(post("/api/contracts"), contractJson("RBAC-CI")))).isBetween(200, 201);
        assertThat(call(Persona.CI, json(put("/api/contracts/" + contractId), contractJson("RBAC-001")))).isEqualTo(200);
        assertThat(call(Persona.CI, get("/api/contracts"))).isEqualTo(200);
        assertThat(call(Persona.ADMIN, get("/api/auditoria"))).isEqualTo(200);
        assertThat(call(Persona.CI, get("/api/notificacoes/parametros"))).isEqualTo(200);
        assertThat(call(Persona.ADMIN, json(put("/api/notificacoes/parametros"),
                "{\"firstAlertMonths\":6,\"secondAlertMonths\":3}"))).isEqualTo(200);
        assertThat(call(Persona.FV, json(post("/api/contracts/" + contractId + "/lancamentos"), LANCAMENTO_JSON))).isBetween(200, 201);
        assertThat(call(Persona.FV, get("/api/contracts/" + contractId + "/lancamentos"))).isEqualTo(200);
        assertThat(call(Persona.FV, get("/api/contracts/mine"))).isEqualTo(200);
    }

    @Test
    void somenteAdminExcluiContrato() throws Exception {
        Contract livre = contracts.save(contract("RBAC-LIVRE", fv));
        assertThat(call(Persona.CI, delete("/api/contracts/" + livre.getId()))).isEqualTo(403);
        assertThat(call(Persona.FV, delete("/api/contracts/" + livre.getId()))).isEqualTo(403);
        assertThat(contracts.findById(livre.getId())).isPresent();
        assertThat(call(Persona.ADMIN, delete("/api/contracts/" + livre.getId()))).isBetween(200, 204);
    }

    @Test
    void usersMeDevolveOProprioUsuarioParaTodosOsPerfis() throws Exception {
        for (Persona p : List.of(Persona.ADMIN, Persona.CI, Persona.FV, Persona.FS)) {
            String email = JsonPath.read(body(p, get("/api/users/me")), "$.email");
            assertThat(email).as("/users/me como %s", p).isEqualTo(
                    switch (p) {
                        case ADMIN -> admin.getEmail();
                        case CI -> ci.getEmail();
                        case FV -> fv.getEmail();
                        default -> fs.getEmail();
                    });
        }
    }

    @Test
    void listaDeUsuariosNaoExpoeSenha() throws Exception {
        String json = body(Persona.CI, get("/api/users"));
        assertThat(json).doesNotContainIgnoringCase("password").doesNotContain("senha-irrelevante");
    }

    // ------------------------------------------------------------------ autenticação (endpoints públicos) e token

    @Test
    void tokenInvalidoOuAdulteradoEhTratadoComoAnonimo() throws Exception {
        String bom = token(Persona.ADMIN);
        String adulterado = bom.substring(0, bom.length() - 2) + (bom.endsWith("AA") ? "BB" : "AA");
        assertThat(call(Persona.ANON, get("/api/contracts").header("Authorization", "Bearer " + adulterado))).isEqualTo(403);
        assertThat(call(Persona.ANON, get("/api/contracts").header("Authorization", "Bearer lixo"))).isEqualTo(403);
    }

    /**
     * Achado do M6-80: JWT ainda válido (assinatura e prazo ok) cujo dono não existe mais — usuário excluído, ou
     * e-mail trocado (o username é o e-mail) — estourava UsernameNotFoundException dentro do JwtAuthenticationFilter
     * e virava 500 em TODA requisição, inclusive /auth/validate. O correto é tratar como anônimo: 403 em rota
     * protegida e {"valid": false} no /auth/validate (que é público).
     */
    @Test
    void tokenDeUsuarioExcluidoOuComEmailTrocadoViraAnonimo_nunca500() throws Exception {
        Sector sector = sectors.findById(sectorId).orElseThrow();

        // caso 1: usuário excluído
        AppUser excluido = users.save(user("excluido.rbac@test.local", "Excluido", sector, PerfilUsuario.FISCAL));
        String tokenExcluido = jwt.generate(excluido.getUsername(), excluido.getName());
        // antes de excluir, o token funciona (sanidade do teste)
        assertThat(call(Persona.ANON, get("/api/contracts/mine").header("Authorization", "Bearer " + tokenExcluido))).isEqualTo(200);
        users.delete(excluido);
        users.flush();
        assertThat(call(Persona.ANON, get("/api/contracts/mine").header("Authorization", "Bearer " + tokenExcluido))).isEqualTo(403);
        String json = mvc.perform(get("/api/auth/validate").header("Authorization", "Bearer " + tokenExcluido))
                .andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(json, "$.valid")).isFalse();

        // caso 2: e-mail trocado pelo Admin (o token antigo aponta para o username antigo)
        AppUser renomeado = users.save(user("antigo.rbac@test.local", "Renomeado", sector, PerfilUsuario.FISCAL));
        String tokenAntigo = jwt.generate(renomeado.getUsername(), renomeado.getName());
        assertThat(call(Persona.ADMIN, json(put("/api/users/" + renomeado.getId()), userJson("novo.email.rbac@test.local", "FISCAL")))).isEqualTo(200);
        assertThat(call(Persona.ANON, get("/api/users/me").header("Authorization", "Bearer " + tokenAntigo))).isEqualTo(403);
        String json2 = mvc.perform(get("/api/auth/validate").header("Authorization", "Bearer " + tokenAntigo))
                .andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(json2, "$.valid")).isFalse();
    }

    /** O token NOVO (com o e-mail novo) continua funcionando: só o antigo morre. */
    @Test
    void tokenComEmailNovoFuncionaDepoisDaTroca() throws Exception {
        Sector sector = sectors.findById(sectorId).orElseThrow();
        AppUser u = users.save(user("troca.rbac@test.local", "Troca", sector, PerfilUsuario.FISCAL));
        assertThat(call(Persona.ADMIN, json(put("/api/users/" + u.getId()), userJson("troca2.rbac@test.local", "FISCAL")))).isEqualTo(200);
        String tokenNovo = jwt.generate("troca2.rbac@test.local", "Troca");
        assertThat(call(Persona.ANON, get("/api/users/me").header("Authorization", "Bearer " + tokenNovo))).isEqualTo(200);
    }

    @Test
    void cookieAuthTokenAutenticaIgualAoHeader() throws Exception {
        var cookie = new jakarta.servlet.http.Cookie("auth_token", token(Persona.CI));
        assertThat(mvc.perform(get("/api/contracts").cookie(cookie)).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void loginComCredenciaisErradasDevolve401() throws Exception {
        int status = mvc.perform(json(post("/api/auth/login"), "{\"username\":\"admin@test.local\",\"password\":\"errada\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(401);
    }

    @Test
    void loginDoAdminBootstrapDevolve200ECookieHttpOnly() throws Exception {
        var res = mvc.perform(json(post("/api/auth/login"), "{\"username\":\"admin@test.local\",\"password\":\"admin123\"}"))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader("Set-Cookie")).contains("auth_token=").contains("HttpOnly");
    }

    @Test
    void validateEhPublicoERefleteOEstadoDoToken() throws Exception {
        assertThat((Boolean) JsonPath.read(body(Persona.ANON, get("/api/auth/validate")), "$.valid")).isFalse();
        String json = body(Persona.CI, get("/api/auth/validate"));
        assertThat((Boolean) JsonPath.read(json, "$.valid")).isTrue();
        assertThat((String) JsonPath.read(json, "$.perfil")).isEqualTo("CONTROLE_INTERNO");
        assertThat((Boolean) JsonPath.read(json, "$.admin")).isFalse();
    }

    @Test
    void logoutEhPublico() throws Exception {
        assertThat(call(Persona.ANON, post("/api/auth/logout"))).isEqualTo(200);
    }

    /** Cadastro de usuário pelo CRUD de usuários: só Admin e Controle Interno; o perfil criado é FISCAL. */
    @Test
    void cadastroDeUsuarioSoPorAdminOuControleInterno() throws Exception {
        String emailTarget = "qualquer@test.local";
        Req reqPost = t -> json(post("/api/users"), t.userJson(emailTarget, "FISCAL"));

        // 1. Anônimo recebe 403 e não cria usuário
        assertThat(call(Persona.ANON, reqPost.build(this))).isEqualTo(403);
        assertThat(users.findByUsername(emailTarget)).isEmpty();

        // 2. Fiscal recebe 403 e não cria usuário
        assertThat(call(Persona.FV, reqPost.build(this))).isEqualTo(403);
        assertThat(users.findByUsername(emailTarget)).isEmpty();

        // 3. Admin recebe 200/201 e cria um FISCAL
        assertThat(call(Persona.ADMIN, reqPost.build(this))).isIn(200, 201);
        assertThat(users.findByUsername(emailTarget))
                .isPresent()
                .hasValueSatisfying(u -> assertThat(u.getPerfil()).isEqualTo(PerfilUsuario.FISCAL));

        // Limpa o usuário criado para isolar o próximo teste
        users.findByUsername(emailTarget).ifPresent(users::delete);

        // 4. Controle Interno recebe 200/201 e cria um FISCAL
        assertThat(call(Persona.CI, reqPost.build(this))).isIn(200, 201);
        assertThat(users.findByUsername(emailTarget))
                .isPresent()
                .hasValueSatisfying(u -> assertThat(u.getPerfil()).isEqualTo(PerfilUsuario.FISCAL));
    }

    /**
     * M6-90: o cadastro por /api/auth/register deixou de ser público. Só Admin e Controle Interno cadastram,
     * e a conta criada é sempre FISCAL (nunca ADMIN/CI), então não há escalada de privilégio por esse caminho.
     */
    @Test
    void registerSoPorAdminOuControleInterno() throws Exception {
        String email = "registro@test.local";
        String corpo = "{\"name\":\"Qualquer Um\",\"email\":\"" + email + "\",\"password\":\"senha123\"}";
        Req reqRegister = t -> json(post("/api/auth/register"), corpo);

        // 1. Anônimo recebe 403 e não cria usuário
        assertThat(call(Persona.ANON, reqRegister.build(this))).isEqualTo(403);
        assertThat(users.findByUsername(email)).isEmpty();

        // 2. Fiscal recebe 403 e não cria usuário
        assertThat(call(Persona.FV, reqRegister.build(this))).isEqualTo(403);
        assertThat(users.findByUsername(email)).isEmpty();

        // 3. Admin recebe 200 e cria um FISCAL
        assertThat(call(Persona.ADMIN, reqRegister.build(this))).isEqualTo(200);
        assertThat(users.findByUsername(email))
                .isPresent()
                .hasValueSatisfying(u -> assertThat(u.getPerfil()).isEqualTo(PerfilUsuario.FISCAL));
        users.findByUsername(email).ifPresent(users::delete);

        // 4. Controle Interno recebe 200 e cria um FISCAL
        assertThat(call(Persona.CI, reqRegister.build(this))).isEqualTo(200);
        assertThat(users.findByUsername(email))
                .isPresent()
                .hasValueSatisfying(u -> assertThat(u.getPerfil()).isEqualTo(PerfilUsuario.FISCAL));
        users.findByUsername(email).ifPresent(users::delete);
    }

    // ------------------------------------------------------------------ endurecimento do M6-80

    @Test
    void adminNaoExcluiASiMesmoMasExcluiOutroAdmin() throws Exception {
        assertThat(call(Persona.ADMIN, delete("/api/users/" + admin.getId()))).isEqualTo(409);
        assertThat(users.findById(admin.getId())).isPresent();
        assertThat(call(Persona.ADMIN, delete("/api/users/" + targetAdmin.getId()))).isIn(200, 204);
        assertThat(users.findById(targetAdmin.getId())).isEmpty();
    }

    @Test
    void tamanhoDePaginaTemTetoDe100NaAuditoriaENaBuscaDeDocumentos() throws Exception {
        for (String path : List.of("/api/auditoria", "/api/generate-document")) {
            assertThat(call(Persona.ADMIN, get(path).param("size", "100"))).as("%s size=100", path).isEqualTo(200);
            assertThat(call(Persona.CI, get(path).param("size", "101"))).as("%s size=101", path).isEqualTo(400);
            String json = body(Persona.ADMIN, get(path).param("size", "101"));
            assertThat((String) JsonPath.read(json, "$.message")).contains("100");
        }
    }

    @Test
    void trocaDeSenhaFicaNaAuditoriaSemExporASenha() throws Exception {
        // userJson() manda password "senha123"; o perfil e os demais campos não mudam
        assertThat(call(Persona.ADMIN, json(put("/api/users/" + targetFiscal.getId()), userJson("alvo.rbac@test.local", "FISCAL")))).isEqualTo(200);
        String json = body(Persona.ADMIN, get("/api/auditoria").param("entityType", "USER").param("action", "UPDATE").param("size", "50"));
        assertThat(json).contains("Senha redefinida").doesNotContain("senha123");
    }

    // ------------------------------------------------------------------ e-mail duplicado (achado da tela, M6-80)

    /**
     * Achado do roteiro de tela: ao criar usuário com e-mail já existente a tela mostrou "Operação não pôde ser
     * concluída por conflito de dados" (mensagem genérica do handler de DataIntegrityViolationException) em vez de
     * "E-mail já cadastrado". Causa: ensureUnique só procura por username; um usuário cujo username é diferente do
     * e-mail (legado, ou criado antes da convenção username = e-mail) não é achado e quem barra é a constraint do banco.
     */
    private void seedUsuarioLegado() {
        Sector sector = sectors.findById(sectorId).orElseThrow();
        users.save(new AppUser("legado", "{noop}x", "Legado", "dup.rbac@test.local", null, sector, PerfilUsuario.FISCAL));
        users.flush();
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "criar com e-mail {0} (username == e-mail)")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"fv.rbac@test.local", "FV.RBAC@test.local"})
    void emailDuplicadoDevolve409ComMensagemClara_caso_normal(String email) throws Exception {
        String json = body(Persona.ADMIN, json(post("/api/users"), userJson(email, "FISCAL")));
        assertThat((String) JsonPath.read(json, "$.message")).isEqualTo("E-mail já cadastrado");
    }

    // Um teste por e-mail porque, no caso ainda quebrado, a violação de constraint estraga a sessão do teste.
    @org.junit.jupiter.params.ParameterizedTest(name = "criar com e-mail {0} (usuário legado: username != e-mail)")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"dup.rbac@test.local", "DUP.rbac@test.local"})
    void emailDuplicadoDevolve409ComMensagemClara_usernameDiferenteDoEmail(String email) throws Exception {
        seedUsuarioLegado();
        MockHttpServletRequestBuilder req = json(post("/api/users"), userJson(email, "FISCAL"));
        var res = mvc.perform(as(Persona.ADMIN, req)).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(409);
        assertThat((String) JsonPath.read(res.getContentAsString(), "$.message")).isEqualTo("E-mail já cadastrado");
    }

    @Test
    void editarParaEmailDeOutroUsuarioDevolve409ComMensagemClara() throws Exception {
        seedUsuarioLegado();
        var res = mvc.perform(as(Persona.ADMIN, json(put("/api/users/" + targetFiscal.getId()),
                userJson("dup.rbac@test.local", "FISCAL")))).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(409);
        assertThat((String) JsonPath.read(res.getContentAsString(), "$.message")).isEqualTo("E-mail já cadastrado");
    }

    /** O username (usado no login e como subject do token) tem de nascer normalizado: minúsculo e sem espaços. */
    @Test
    void usernameNasceNormalizadoNaCriacao() throws Exception {
        assertThat(call(Persona.ADMIN, json(post("/api/users"), userJson("Maiusc.Rbac@Test.Local", "FISCAL")))).isIn(200, 201);
        assertThat(users.findByUsername("maiusc.rbac@test.local")).isPresent();
    }

    /** Grava um PDF mínimo na pasta de anexos de teste e devolve o caminho que o anexo guarda no banco. */
    private String storedFile(Long contractId) {
        try {
            return storage.save(contractId, "%PDF-1.4".getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
