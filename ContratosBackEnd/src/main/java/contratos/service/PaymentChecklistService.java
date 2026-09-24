package contratos.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import contratos.api.dto.GeneratedDocument.GeneratedDocumentRequest;
import contratos.api.dto.GeneratedDocument.GeneratedDocumentResponse;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.DocumentTemplate;
import contratos.domain.LancamentoFinanceiro;
import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;
import contratos.exception.ConflictException;
import contratos.repository.DocumentTemplateRepository;
import contratos.repository.LancamentoFinanceiroRepository;
import contratos.repository.UserRepository;
import contratos.security.ContractAuthorization;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ateste dos fiscais ("Gerar Checklist" da spec, item 7.3): documento por lançamento financeiro, gerado a
 * partir do template PAYMENT_CHECKLIST. Assina só o fiscal que gera (nome e setor do usuário logado): cada fiscal gera e assina o seu ateste.
 * Cada geração vira uma nova versão em GeneratedDocument; o nome do arquivo identifica contrato e nota fiscal.
 */
@Service
@RequiredArgsConstructor
public class PaymentChecklistService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{((?:\\\\?\\w)+)}}");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final LancamentoFinanceiroRepository lancamentoRepository;
    private final UserRepository userRepository;
    private final DocumentTemplateRepository templateRepository;
    private final GeneratedDocumentService generatedDocumentService;
    private final ContractAuthorization authorization;

    @Transactional(readOnly = true)
    public String preview(Long lancamentoId, Authentication authentication) {
        LancamentoFinanceiro lancamento = buscarLancamentoAtivo(lancamentoId, authentication);
        AppUser usuario = buscarUsuario(authentication.getName());
        return resolverPlaceholders(buscarTemplate().getContent(), lancamento, usuario);
    }

    @Transactional
    public GeneratedDocumentResponse gerar(Long lancamentoId, Authentication authentication) {
        LancamentoFinanceiro lancamento = buscarLancamentoAtivo(lancamentoId, authentication);
        AppUser usuario = buscarUsuario(authentication.getName());
        Contract contrato = lancamento.getContrato();

        String markdown = resolverPlaceholders(buscarTemplate().getContent(), lancamento, usuario);
        byte[] pdf = renderizarPdf(markdown);

        String fileName = "ateste-" + limparNomeArquivo(contrato.getNumberContract())
                + "-nf-" + limparNomeArquivo(lancamento.getNotaFiscal()) + ".pdf";
        return generatedDocumentService.store(new GeneratedDocumentRequest(
                contrato.getId(), DocumentTemplateType.PAYMENT_CHECKLIST, DocumentFormat.PDF, fileName, pdf),
                usuario.getUsername());
    }

    private DocumentTemplate buscarTemplate() {
        return templateRepository.findByTemplateType(DocumentTemplateType.PAYMENT_CHECKLIST)
                .orElseThrow(() -> new EntityNotFoundException("Template do ateste (checklist) não cadastrado"));
    }

    private AppUser buscarUsuario(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
    }

    /** Mesma regra de editar/excluir: autoriza pelo contrato do lançamento antes de olhar o estado dele. */
    private LancamentoFinanceiro buscarLancamentoAtivo(Long lancamentoId, Authentication authentication) {
        LancamentoFinanceiro lancamento = lancamentoRepository.findById(lancamentoId)
                .orElseThrow(() -> new EntityNotFoundException("Lançamento não encontrado com o id: " + lancamentoId));
        if (!authorization.canRead(lancamento.getContrato().getId(), authentication)) {
            throw new AccessDeniedException("Usuário não tem permissão para gerar o ateste deste lançamento");
        }
        if (!lancamento.isAtivo()) {
            throw new ConflictException("Este lançamento foi excluído");
        }
        return lancamento;
    }

    private String resolverPlaceholders(String content, LancamentoFinanceiro lancamento, AppUser usuario) {
        Contract contrato = lancamento.getContrato();
        var competencia = lancamento.getCompetencia();
        String mesReferencia = competencia.getMonth().getDisplayName(TextStyle.FULL, PT_BR).toUpperCase(PT_BR)
                + " " + competencia.getYear();
        var valores = Map.of(
                "numero_contrato", contrato.getNumberContract(),
                "data_inicio", contrato.getStartDate().format(DATE_FMT),
                "data_termino", contrato.getEndDate().format(DATE_FMT),
                "mes_referencia", mesReferencia,
                "numero_nota_fiscal", lancamento.getNotaFiscal(),
                "nome_fiscal", usuario.getName(),
                "setor_fiscal", usuario.getSector() == null ? "" : usuario.getSector().getName()
        );
        var matcher = PLACEHOLDER.matcher(content);
        var sb = new StringBuilder();
        while (matcher.find()) {
            String variavel = matcher.group(1).replace("\\", "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(valores.getOrDefault(variavel, "")));
        }
        matcher.appendTail(sb);
        return removerEscapeMarkdown(sb.toString());
    }

    private String removerEscapeMarkdown(String texto) {
        return texto.replaceAll("\\\\([\\\\`*_{}\\[\\]()#+\\-.!>])", "$1");
    }

    private String limparNomeArquivo(String texto) {
        return texto.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
    }

    private byte[] renderizarPdf(String markdown) {
        // O ateste usa tabela (caixa com contrato/vigência/parcela/nota fiscal), então precisa da extensão de tabelas.
        var options = new MutableDataSet().set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        var parser = Parser.builder(options).build();
        var renderer = HtmlRenderer.builder(options).build();
        var html = renderer.render(parser.parse(markdown));
        var htmlCompleto = "<html><head><style>" + CSS_FIXO + "</style></head><body>" + html + "</body></html>";

        try (var out = new ByteArrayOutputStream()) {
            var builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(htmlCompleto, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar PDF do ateste", e);
        }
    }

    private static final String CSS_FIXO = """
            body { font-family: sans-serif; font-size: 12pt; }
            table { width: 100%; border-collapse: collapse; margin: 18px 0 26px 0; }
            th, td { border: 1px solid #cccccc; padding: 10px 8px; text-align: left; font-weight: normal; }
            """;
}
