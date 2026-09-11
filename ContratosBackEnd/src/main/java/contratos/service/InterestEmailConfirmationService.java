package contratos.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

import contratos.api.dto.GeneratedDocumentRequest;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.InterestEmailConfirmation;
import contratos.domain.enums.ContractStatus;
import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;
import contratos.exception.ConflictException;
import contratos.repository.ContractRepository;
import contratos.repository.DocumentTemplateRepository;
import contratos.repository.InterestEmailConfirmationRepository;
import contratos.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InterestEmailConfirmationService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH_YEAR_FMT = DateTimeFormatter.ofPattern("MM/yyyy");

    private final InterestEmailConfirmationRepository confirmationRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final DocumentTemplateRepository templateRepository;
    private final GeneratedDocumentService generatedDocumentService;
    private final ContractStatusService contractStatusService;

    @Transactional
    public String confirm(Long contractId, String username) {
        var contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
        var fiscal = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));


                        if (contract.getStatus() != ContractStatus.AGUARDANDO_EMAIL_INTERESSE) {
    throw new ConflictException("Contrato não está aguardando envio de e-mail de interesse.");
}

        if (confirmationRepository.existsByContract_IdAndFiscal_Id(contractId, fiscal.getId())) {
            throw new ConflictException("Você já confirmou o envio deste e-mail.");
        }

        confirmationRepository.save(new InterestEmailConfirmation(
                contract, fiscal, LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))
        ));

        long confirmadas = confirmationRepository.countByContract_Id(contractId);
        long totalFiscais = contract.getFiscais().size();



        if (confirmadas < totalFiscais) {
            return "Confirmação registrada (%d de %d fiscais confirmaram).".formatted(confirmadas, totalFiscais);
        }

        gerarDocumentoFinal(contract, fiscal);
        return "Todos os fiscais confirmaram. E-mail de interesse gerado e contrato avançado.";
    }

    private void gerarDocumentoFinal(Contract contract, AppUser ultimoConfirmante) {
        var template = templateRepository.findByTemplateType(DocumentTemplateType.INTEREST_EMAIL)
                .orElseThrow(() -> new EntityNotFoundException("Template de e-mail de interesse não cadastrado"));

        var confirmacoes = confirmationRepository.findByContract_Id(contract.getId());

        String corpoResolvido = resolverPlaceholders(template.getContent(), contract);
        String blocoConfirmacao = montarBlocoConfirmacao(contract, confirmacoes);
        String markdownFinal = corpoResolvido + "\n\n---\n\n" + blocoConfirmacao;

        byte[] pdf = renderizarPdf(markdownFinal);

        var fileName = "email-interesse-" + contract.getNumberContract() + ".pdf";
        var request = new GeneratedDocumentRequest(
                contract.getId(), DocumentTemplateType.INTEREST_EMAIL, DocumentFormat.PDF, fileName, pdf
        );
        generatedDocumentService.store(request, ultimoConfirmante.getUsername());

        contractStatusService.advanceAfterInterestEmailGenerated(contract, ultimoConfirmante);
    }

    private String resolverPlaceholders(String content, Contract contract) {
        var valores = Map.of(
                "numero_contrato", contract.getNumberContract(),
                "objeto_contrato", contract.getObject(),
                "numero_processo", contract.getNumberProcess(),
                "empresa", contract.getCompany(),
                "cnpj", contract.getCnpj(),
                "data_termino", contract.getEndDate().format(DATE_FMT),
                "nomes_fiscais", contract.getFiscais().stream()
                        .map(AppUser::getName).collect(Collectors.joining(", "))
        );
        var matcher = PLACEHOLDER.matcher(content);
        var sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(valores.getOrDefault(matcher.group(1), "")));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String montarBlocoConfirmacao(Contract contract, List<InterestEmailConfirmation> confirmacoes) {
        var nomes = confirmacoes.stream().map(c -> c.getFiscal().getName()).collect(Collectors.joining(", "));
        var hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        return """
                **Confirmação de envio**

                Contrato: %s
                Empresa: %s
                CNPJ: %s
                Mês/ano de geração: %s

                E-mail de interesse confirmado como enviado por: %s
                """.formatted(contract.getNumberContract(), contract.getCompany(), contract.getCnpj(),
                hoje.format(MONTH_YEAR_FMT), nomes);
    }

    private byte[] renderizarPdf(String markdown) {
        var parser = Parser.builder().build();
        var renderer = HtmlRenderer.builder().build();
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
            throw new RuntimeException("Erro ao gerar PDF do e-mail de interesse", e);
        }
    }

    private static final String CSS_FIXO = """
            body { font-family: sans-serif; font-size: 12pt; }
            hr { margin: 24px 0; }
            """;
}