package contratos.api.dto;

import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record GeneratedDocumentResponse(
        @NotNull
        Long id,
        @NotNull
        Long contractId,
        @NotNull
        DocumentTemplateType documentType,
        @NotNull
        DocumentFormat format,
        @NotBlank
        String fileName,
        int version,
        UserSummary generatedBy,
        LocalDateTime generatedAt) {
}
