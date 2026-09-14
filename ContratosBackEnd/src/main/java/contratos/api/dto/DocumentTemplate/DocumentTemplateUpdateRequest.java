package contratos.api.dto.DocumentTemplate;

import jakarta.validation.constraints.NotBlank;

public record DocumentTemplateUpdateRequest(
        @NotBlank String content
) {
}