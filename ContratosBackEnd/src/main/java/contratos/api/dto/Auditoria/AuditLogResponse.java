package contratos.api.dto.Auditoria;

import contratos.domain.AuditLog;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;

import java.time.LocalDateTime;

/** Linha da consulta de auditoria (M6-30). Espelha o AuditLog; sem relação
 *  com AppUser porque ator já é snapshot (nome/e-mail) dentro do próprio log. */
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
