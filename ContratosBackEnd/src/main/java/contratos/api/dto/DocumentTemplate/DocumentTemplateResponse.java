package contratos.api.dto.DocumentTemplate;

import contratos.domain.enums.DocumentTemplateType;

import java.time.LocalDateTime;

public record DocumentTemplateResponse(
        Long id,
        DocumentTemplateType templateType,
        String content,
        LocalDateTime updatedAt,
        Long updatedById,
        String updatedByName
) {
}
