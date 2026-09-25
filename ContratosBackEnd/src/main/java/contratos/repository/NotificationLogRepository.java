package contratos.repository;

import contratos.domain.NotificationLog;
import contratos.domain.enums.NotificationAlertType;
import contratos.domain.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
}