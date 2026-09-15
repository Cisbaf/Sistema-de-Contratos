package contratos.service;

import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.enums.DocumentTemplateType;
import contratos.repository.ContractRepository;
import contratos.repository.DocumentTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Texto de comunicação externa ao prestador (máscara). Diferente do e-mail de
 * interesse (M3-32) e do parecer técnico (M3-33), este documento não é rastreado:
 * não há confirmação, não há avanço de status, é só um preview para copiar e colar.
 */
@Service
@RequiredArgsConstructor
public class SupplierMaskService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{((?:\\\\?\\w)+)}}");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContractRepository contractRepository;
    private final DocumentTemplateRepository templateRepository;

    @Transactional(readOnly = true)
    public String preview(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
        var template = templateRepository.findByTemplateType(DocumentTemplateType.SUPPLIER_RENEWAL_EMAIL)
                .orElseThrow(() -> new EntityNotFoundException("Template de máscara externa não cadastrado"));
        return resolverTemplate(template.getContent(), contract);
    }

    private String resolverTemplate(String content, Contract contract) {
        var primeiroFiscal = contract.getFiscais().stream().findFirst();
        var valores = Map.of(
                "numero_contrato", contract.getNumberContract(),
                "empresa", contract.getCompany(),
                "cnpj", contract.getCnpj(),
                "objeto_contrato", contract.getObject(),
                "data_termino", contract.getEndDate().format(DATE_FMT),
                "numero_processo", contract.getNumberProcess(),
                "nome_fiscal", primeiroFiscal.map(AppUser::getName).orElse("")
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
}
