package contratos.repository;

import contratos.domain.AuditLog;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query(value = """
            select a from AuditLog a
            where (:fromDate   is null or a.occurredAt >= :fromDate)
              and (:toDate     is null or a.occurredAt <  :toDate)
              and (:entityType is null or a.entityType = :entityType)
              and (:action     is null or a.action     = :action)
              and (:contractId is null or a.contractId = :contractId)
              and (:actorId    is null or a.actorId    = :actorId)
            order by a.occurredAt desc, a.id desc
            """,
            countQuery = """
                    select count(a) from AuditLog a
                    where (:fromDate   is null or a.occurredAt >= :fromDate)
                      and (:toDate     is null or a.occurredAt <  :toDate)
                      and (:entityType is null or a.entityType = :entityType)
                      and (:action     is null or a.action     = :action)
                      and (:contractId is null or a.contractId = :contractId)
                      and (:actorId    is null or a.actorId    = :actorId)
                    """)
    Page<AuditLog> search(@Param("fromDate") LocalDateTime fromDate,
                          @Param("toDate") LocalDateTime toDate,
                          @Param("entityType") AuditEntityType entityType,
                          @Param("action") AuditAction action,
                          @Param("contractId") Long contractId,
                          @Param("actorId") Long actorId,
                          Pageable pageable);
}
