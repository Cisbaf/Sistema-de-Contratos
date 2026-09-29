package contratos.service;

import contratos.domain.AppUser;
import contratos.domain.AuditLog;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import contratos.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AppUser actor, AuditAction action, AuditEntityType entityType,
                       Long entityId, Long contractId, String summary, String details) {
        Long actorId = actor != null ? actor.getId() : null;
        String actorName = actor != null ? actor.getName() : null;
        String actorEmail = actor != null ? actor.getEmail() : null;
        repository.save(new AuditLog(actorId, actorName, actorEmail, action, entityType,
                entityId, contractId, summary, details));
    }
}
