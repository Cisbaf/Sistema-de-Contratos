package contratos.api.dto;

import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;

public record GeneratedDocumentRequest(
        Long contractId,
        DocumentTemplateType documentType,
        DocumentFormat format,
        String fileName,
        byte[] content) {
}
