package contratos.repository;

import contratos.domain.NotificationLog;
import contratos.domain.enums.NotificationAlertType;
import contratos.domain.enums.NotificationChannel;
import contratos.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /**
     * Idempotência: este destinatário já recebeu (SENT) este alerta neste ciclo?
     * FAILED e SIMULATED não contam: são tentativas que o próximo ciclo do job refaz.
     */
    @Query("""
            select count(n) > 0 from NotificationLog n
            where n.contract.id = :contractId
              and n.alertType = :alertType
              and n.channel = :channel
              and n.recipientAddress = :address
              and n.cycleEndDate = :cycleEndDate
              and n.status = contratos.domain.enums.NotificationStatus.SENT
            """)
    boolean alreadyNotified(@Param("contractId") Long contractId,
                            @Param("alertType") NotificationAlertType alertType,
                            @Param("channel") NotificationChannel channel,
                            @Param("address") String address,
                            @Param("cycleEndDate") LocalDate cycleEndDate);

    /** A linha do log de uma chave (contrato, tipo, canal, destinatário, ciclo), se existir. */
    @Query("""
            select n from NotificationLog n
            where n.contract.id = :contractId
              and n.alertType = :alertType
              and n.channel = :channel
              and n.recipientAddress = :address
              and n.cycleEndDate = :cycleEndDate
            """)
    Optional<NotificationLog> findEntry(@Param("contractId") Long contractId,
                                        @Param("alertType") NotificationAlertType alertType,
                                        @Param("channel") NotificationChannel channel,
                                        @Param("address") String address,
                                        @Param("cycleEndDate") LocalDate cycleEndDate);

    /** Histórico do contrato, mais recente primeiro. */
    List<NotificationLog> findByContract_IdOrderByAttemptedAtDesc(Long contractId);

    /** Limpeza na exclusão do contrato. */
    void deleteByContract_Id(Long contractId);

    @Query(value = """
        select n from NotificationLog n join fetch n.contract c
        where (:fromDate is null or n.attemptedAt >= :fromDate)
          and (:toDate   is null or n.attemptedAt <  :toDate)
          and (:term is null
               or lower(c.numberContract)   like :term escape '!'
               or lower(c.seiProcessNumber) like :term escape '!'
               or lower(n.recipientName)    like :term escape '!'
               or lower(n.recipientAddress) like :term escape '!'
               or exists (select f.id from c.fiscais f where lower(f.name) like :term escape '!'))
        order by n.attemptedAt desc, n.id desc
        """,
            countQuery = """
        select count(n) from NotificationLog n join n.contract c
        where (:fromDate is null or n.attemptedAt >= :fromDate)
          and (:toDate   is null or n.attemptedAt <  :toDate)
          and (:term is null
               or lower(c.numberContract)   like :term escape '!'
               or lower(c.seiProcessNumber) like :term escape '!'
               or lower(n.recipientName)    like :term escape '!'
               or lower(n.recipientAddress) like :term escape '!'
               or exists (select f.id from c.fiscais f where lower(f.name) like :term escape '!'))
        """)
    Page<NotificationLog> search(@Param("fromDate") LocalDateTime fromDate,
                                 @Param("toDate") LocalDateTime toDate,
                                 @Param("term") String term,
                                 Pageable pageable);

    long countByStatus(NotificationStatus status);
}