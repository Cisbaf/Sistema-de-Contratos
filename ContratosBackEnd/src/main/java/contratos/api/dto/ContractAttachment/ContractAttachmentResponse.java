package contratos.api.dto.ContractAttachment;

import contratos.api.dto.User.UserSummary;

import java.time.LocalDateTime;

public record ContractAttachmentResponse (
        Long id,
        String fileName,
        String contentType,
         long sizeBytes,
        LocalDateTime uploadedAt,
        UserSummary uploadedBy,
        boolean ativo,
        LocalDateTime removedAt,
        UserSummary removedBy
){
}
