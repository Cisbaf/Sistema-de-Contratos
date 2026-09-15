package contratos.service;

import contratos.api.dto.GeneratedDocument.GeneratedDocumentRequest;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.TechnicalOpinionEntry;
import contratos.domain.enums.ContractStatus;
import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;
import contratos.exception.ConflictException;
import contratos.repository.ContractRepository;
import contratos.repository.DocumentTemplateRepository;
import contratos.repository.TechnicalOpinionRepository;
import contratos.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TechnicalOpinionService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{((?:\\\\?\\w)+)}}");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TechnicalOpinionRepository opinionRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final DocumentTemplateRepository templateRepository;
    private final GeneratedDocumentService generatedDocumentService;
    private final ContractStatusService contractStatusService;

    // Preview individual: só a opinião que ESSE fiscal está digitando, sem persistir nada.
    @Transactional(readOnly = true)
    public String previewOwnOpinion(Long contractId, String observations) {
        var contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
        var template = templateRepository.findByTemplateType(DocumentTemplateType.TECHNICAL_OPINION)
                .orElseThrow(() -> new EntityNotFoundException("Template de parecer técnico não cadastrado"));
        return resolverTemplate(template.getContent(), contract, observations);
    }

    // Opinião já salva do PRÓPRIO fiscal autenticado (vazio se ele ainda não enviou nada).
    @Transactional(readOnly = true)
    public String getMyOpinion(Long contractId, String username) {
        var fiscal = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        return opinionRepository.findByContract_IdAndFiscal_Id(contractId, fiscal.getId())
                .map(TechnicalOpinionEntry::getObservations)
                .orElse("");
    }

    // Visão coletiva para Admin/Controle Interno: o texto de cada fiscal que já enviou,
    // reunido no mesmo formato do documento final — mas consultável ANTES de fechar.
    @Transactional(readOnly = true)
    public String previewCollective(Long contractId) {
        var opinioes = opinionRepository.findByContract_Id(contractId);
        if (opinioes.isEmpty()) {
            return "Nenhum fiscal enviou parecer ainda.";
        }
        return montarBlocoOpinioes(opinioes);
    }

    // Progresso: quem já enviou, quem falta. Sem expor o texto de ninguém.
    @Transactional(readOnly = true)
    public List<FiscalProgress> getProgress(Long contractId) {
        var contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
        var entregues = opinionRepository.findByContract_Id(contractId).stream()
                .map(e -> e.getFiscal().getId())
                .collect(Collectors.toSet());

        return contract.getFiscais().stream()
                .map(fiscal -> new FiscalProgress(fiscal.getName(), entregues.contains(fiscal.getId())))
                .toList();
    }

    @Transactional
    public String submit(Long contractId, String username, String observations) {
        var contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
        var fiscal = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        if (contract.getStatus() != ContractStatus.EMAIL_ENVIADO) {
            throw new ConflictException("Contrato não está no status correto para receber parecer técnico.");
        }

        var existente = opinionRepository.findByContract_IdAndFiscal_Id(contractId, fiscal.getId());
        if (existente.isPresent()) {
            existente.get().updateObservations(observations);
        } else {
            opinionRepository.save(new TechnicalOpinionEntry(contract, fiscal, observations));
        }

        long enviadas = opinionRepository.countByContract_Id(contractId);
        long totalFiscais = contract.getFiscais().size();

        if (enviadas < totalFiscais) {
            return "Opinião registrada (%d de %d fiscais enviaram).".formatted(enviadas, totalFiscais);
        }

        gerarParecerFinal(contract, fiscal);
        return "Todos os fiscais enviaram. Parecer técnico gerado e contrato avançado.";
    }

    private void gerarParecerFinal(Contract contract, AppUser ultimoFiscal) {
        var template = templateRepository.findByTemplateType(DocumentTemplateType.TECHNICAL_OPINION)
                .orElseThrow(() -> new EntityNotFoundException("Template de parecer técnico não cadastrado"));

        var opinioes = opinionRepository.findByContract_Id(contract.getId());
        String blocoOpinioes = montarBlocoOpinioes(opinioes);

        String textoFinal = resolverTemplate(template.getContent(), contract, blocoOpinioes);
        byte[] pdf = renderizarPdf(textoFinal);

        var fileName = "parecer-tecnico-" + contract.getNumberContract() + ".pdf";
        var request = new GeneratedDocumentRequest(
                contract.getId(), DocumentTemplateType.TECHNICAL_OPINION, DocumentFormat.PDF, fileName, pdf
        );
        generatedDocumentService.store(request, ultimoFiscal.getUsername());

        contractStatusService.advanceAfterTechnicalOpinionGenerated(contract, ultimoFiscal);
    }

    private String montarBlocoOpinioes(List<TechnicalOpinionEntry> opinioes) {
        return opinioes.stream()
                .map(o -> "**%s:**\n\n%s".formatted(o.getFiscal().getName(), o.getObservations()))
                .collect(Collectors.joining("\n\n"));
    }

    // Reaproveita o mesmo mecanismo do InterestEmailConfirmationService, mas o valor
    // de "observacoes" é passado como parâmetro em vez de calculado dentro do método —
    // aqui é ora o texto individual (preview), ora o bloco coletivo (documento final).
    private String resolverTemplate(String content, Contract contract, String observacoesTexto) {
        var valores = Map.of(
                "numero_contrato", contract.getNumberContract(),
                "objeto_contrato", contract.getObject(),
                "numero_processo", contract.getNumberProcess(),
                "empresa", contract.getCompany(),
                "cnpj", contract.getCnpj(),
                "data_termino", contract.getEndDate().format(DATE_FMT),
                "nomes_fiscais", contract.getFiscais().stream()
                        .map(AppUser::getName).collect(Collectors.joining(", ")),
                "observacoes", observacoesTexto == null ? "" : observacoesTexto
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

    private byte[] renderizarPdf(String markdown) {
        var parser = com.vladsch.flexmark.parser.Parser.builder().build();
        var renderer = com.vladsch.flexmark.html.HtmlRenderer.builder().build();
        var html = renderer.render(parser.parse(markdown));
        var htmlCompleto = "<html><head><style>body{font-family:sans-serif;font-size:12pt;}</style></head><body>" + html + "</body></html>";

        try (var out = new java.io.ByteArrayOutputStream()) {
            var builder = new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(htmlCompleto, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Erro ao gerar PDF do parecer técnico", e);
        }
    }

    public record FiscalProgress(String fiscalName, boolean submitted) {
    }
}