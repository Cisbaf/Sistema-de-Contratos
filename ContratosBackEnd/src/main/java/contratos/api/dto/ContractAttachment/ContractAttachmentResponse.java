package contratos.api.dto.ContractAttachment;

import contratos.api.dto.User.UserSummary;
import contratos.domain.enums.AttachmentType;

import java.time.LocalDateTime;

public record ContractAttachmentResponse(
        Long id,
        String fileName,
        String contentType,
        long sizeBytes,
        LocalDateTime uploadedAt,
        UserSummary uploadedBy,
        boolean ativo,
        LocalDateTime removedAt,
        UserSummary removedBy,
        AttachmentType attType
) {
}
