package contratos.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentTemplateUpdateRequest(
        @NotBlank String content
) {
}