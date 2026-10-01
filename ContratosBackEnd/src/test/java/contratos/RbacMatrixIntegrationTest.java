package contratos;

import com.jayway.jsonpath.JsonPath;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.ContractAttachment;
import contratos.domain.DocumentTemplate;
import contratos.domain.GeneratedDocument;
import contratos.domain.LancamentoFinanceiro;
import contratos.domain.Sector;
import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;
import contratos.domain.enums.PerfilUsuario;
import contratos.repository.ContractAttachmentRepository;
import contratos.repository.ContractRepository;
import contratos.repository.DocumentTemplateRepository;
import contratos.repository.GeneratedDocumentRepository;
import contratos.repository.LancamentoFinanceiroRepository;
import contratos.repository.SectorRepository;
import contratos.repository.UserRepository;
import contratos.security.JwtService;
import jakarta.servlet.ServletException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
    @Autowired private GeneratedDocumentRepository documents;
    @Autowired private DocumentTemplateRepository templates;

    private MockMvc mvc;

    // Atores
    private AppUser admin, ci, fv, fs;
    // Alvos de gestão de usuários
    private AppUser targetFiscal, targetAdmin;
    // Dados
    private Long sectorId, contractId, otherContractId, lancamentoId, attachmentId, documentId, templateId;
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
                "%PDF-1.4".getBytes(), admin)).getId();
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
        // trava contra "esquecer" uma linha ao editar a matriz: 47 endpoints protegidos x 5 perfis
        assertThat(cases()).hasSize(47);
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
     * ACHADO (baixa severidade), fixado aqui para não passar despercebido: nos endpoints por id de FILHO
     * (lançamento, anexo, documento gerado) o service faz findById -> 404 ANTES de checar o contrato -> 403.
     * Um fiscal sem vínculo consegue distinguir "id não existe" (404) de "id existe mas não é meu" (403),
     * ou seja, enumerar ids. Nos endpoints por id de CONTRATO o @PreAuthorize roda antes e responde 403 nos dois casos.
     * Se um dia o service passar a checar permissão antes de buscar, este teste deve ser atualizado para 403.
     */
    @Test
    void ordemDe404e403_idDeContratoNaoVazaExistencia_idDeFilhoVaza() throws Exception {
        // contrato: inexistente e "de outro" são indistinguíveis para o fiscal sem vínculo
        assertThat(call(Persona.FS, get("/api/contracts/" + MISSING))).isEqualTo(403);
        assertThat(call(Persona.FS, get("/api/contracts/" + contractId))).isEqualTo(403);
        // admin enxerga o 404 normalmente
        assertThat(call(Persona.ADMIN, get("/api/contracts/" + MISSING))).isEqualTo(404);

        // filho: comportamento ATUAL (vazamento de existência) fixado
        assertThat(call(Persona.FS, delete("/api/lancamentos/" + MISSING))).isEqualTo(404);
        assertThat(call(Persona.FS, delete("/api/lancamentos/" + lancamentoId))).isEqualTo(403);
        assertThat(call(Persona.FS, get("/api/attachment/baixar/" + MISSING))).isEqualTo(404);
        assertThat(call(Persona.FS, get("/api/attachment/baixar/" + attachmentId))).isEqualTo(403);
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

    /**
     * ACHADO PENDENTE (cartão M6-90, bloco de segurança): o cadastro público está aberto — qualquer pessoa sem login
     * cria uma conta com perfil FISCAL. O teste fixa o comportamento ATUAL (200) para que a decisão de fechar/restringir
     * o endpoint seja consciente: ao fechar, trocar a expectativa para 403/401 e o nome deste teste.
     * O perfil criado é sempre FISCAL (nunca ADMIN/CI), então não há escalada de privilégio por esse caminho.
     */
    @Test
    void cadastroPublicoAbertoCriaSempreFiscal_pendenciaM6_90() throws Exception {
        int status = call(Persona.ANON, json(post("/api/auth/register"),
                "{\"name\":\"Qualquer Um\",\"email\":\"qualquer@test.local\",\"password\":\"senha123\"}"));
        assertThat(status).isEqualTo(200);
        assertThat(users.findByUsername("qualquer@test.local").orElseThrow().getPerfil()).isEqualTo(PerfilUsuario.FISCAL);
    }
}
