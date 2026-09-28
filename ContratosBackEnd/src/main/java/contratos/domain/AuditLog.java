package contratos.domain;

import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {@Index(name = "idx_occurred", columnList = "occurred_at"),
        @Index(name = "idx_contract", columnList = "contract_id"),
        @Index(name = "idx_entity", columnList = "entity_type"),
        @Index(name = "idx_entity_id", columnList = "entity_id")})
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private LocalDateTime occurredAt;
    private Long actorId;
    @Column(length = 200)
    private String actorName;
    @Column(length = 200)
    private String actorEmail;
    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private AuditAction action;
    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private AuditEntityType entityType;
    private Long entityId;
    private Long contractId;
    @Column(nullable = false, length = 500)
    private String summary;
    @Column(columnDefinition = "TEXT")
    private String details;

    public AuditLog(Long actorId, String actorName, String actorEmail, AuditAction action,
                    AuditEntityType entityType, Long entityId, Long contractId, String summary, String details) {
        this.occurredAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorEmail = actorEmail;
        this.action = Objects.requireNonNull(action);
        this.entityType = Objects.requireNonNull(entityType);
        this.entityId = entityId;
        this.contractId = contractId;
        this.summary = Objects.requireNonNull(summary);
        this.details = details;
    }
}
