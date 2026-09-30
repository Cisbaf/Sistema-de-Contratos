package contratos.api.dto.Auditoria;

import java.time.LocalDateTime;

import contratos.domain.AuditLog;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;


public record AuditLogResponse(
        Long id,
        LocalDateTime occurredAt,
        String actorName,
        String actorEmail,
        AuditAction action,
        AuditEntityType entityType,
        Long entityId,
        Long contractId,
        String summary,
        String details) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getOccurredAt(),
                log.getActorName(),
                log.getActorEmail(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getContractId(),
                log.getSummary(),
                log.getDetails());
    }
}
