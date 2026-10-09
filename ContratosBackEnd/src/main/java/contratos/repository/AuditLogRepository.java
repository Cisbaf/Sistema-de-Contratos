package contratos.repository;

import contratos.api.dto.Contract.ContractTimelineEvent;
import contratos.domain.AuditLog;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    /** EXC-10: exclusões de lançamento (e do ateste que ia junto) para a linha do tempo do contrato. Só colunas leves. */
    @Query("""
            select new contratos.api.dto.Contract.ContractTimelineEvent(a.occurredAt, a.actorName, a.entityType, a.summary)
            from AuditLog a
            where a.contractId = :contractId
              and a.action = contratos.domain.enums.AuditAction.DELETE
              and a.entityType in (contratos.domain.enums.AuditEntityType.LANCAMENTO, contratos.domain.enums.AuditEntityType.DOCUMENT)
            order by a.occurredAt, a.id
            """)
    List<ContractTimelineEvent> timelineDeletions(@Param("contractId") Long contractId);

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
