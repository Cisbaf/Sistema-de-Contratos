package contratos.api.dto;

import contratos.domain.enums.DocumentTemplateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentTemplateCreateRequest(
        @NotNull Long sectorId,
        @NotNull DocumentTemplateType templateType,
        @NotBlank String content
) {
}