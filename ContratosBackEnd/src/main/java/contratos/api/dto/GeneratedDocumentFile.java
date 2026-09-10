package contratos.api.dto;

import contratos.domain.enums.DocumentFormat;

public record GeneratedDocumentFile(Long contractId, String fileName, DocumentFormat format, byte[] content) {
}
