package contratos.api.dto.DocumentTemplate;

import contratos.domain.enums.DocumentTemplateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentTemplateCreateRequest(
        @NotNull DocumentTemplateType templateType,
        @NotBlank String content
) {
}
