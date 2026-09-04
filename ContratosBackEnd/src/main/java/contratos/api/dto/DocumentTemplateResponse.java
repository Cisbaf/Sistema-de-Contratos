package contratos.api.dto;

import contratos.domain.enums.DocumentTemplateType;

import java.time.LocalDateTime;

public record DocumentTemplateResponse(
        Long id,
        Long sectorId,
        String sectorName,
        DocumentTemplateType templateType,
        String content,
        LocalDateTime updatedAt,
        Long updatedById,
        String updatedByName
) {
}
