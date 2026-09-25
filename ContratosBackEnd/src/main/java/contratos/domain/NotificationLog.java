package contratos.domain;

import contratos.domain.enums.NotificationAlertType;
import contratos.domain.enums.NotificationChannel;
import contratos.domain.enums.NotificationStatus;
import contratos.domain.enums.RecipientRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames =
        {"contract_id", "alert_type", "channel", "recipient_address", "cycle_end_date"}))
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationAlertType alertType;

    @Column(nullable = false)
    private LocalDate cycleEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecipientRole recipientRole;

    @Column(length = 200)
    private String recipientName;

    @Column(nullable = false, length = 200)
    private String recipientAddress;

    // --- Resultado ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime attemptedAt;

    public NotificationLog(Contract contract, NotificationAlertType alertType, LocalDate cycleEndDate, NotificationChannel channel,
                           RecipientRole recipientRole, String recipientName, String recipientAddress, NotificationStatus status, String errorMessage) {
        this.contract = Objects.requireNonNull(contract);
        this.alertType = Objects.requireNonNull(alertType);
        this.cycleEndDate = Objects.requireNonNull(cycleEndDate);
        this.channel = Objects.requireNonNull(channel);
        this.recipientRole = Objects.requireNonNull(recipientRole);
        this.recipientName = recipientName;
        this.recipientAddress = Objects.requireNonNull(recipientAddress);
        this.status = Objects.requireNonNull(status);
        this.errorMessage = truncate(errorMessage);
        this.attemptedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
    }

    /**
     * Nova tentativa sobre a mesma linha (a chave única impede uma segunda linha).
     * Um envio já concluído ({@code SENT}) nunca é sobrescrito.
     */
    public void recordAttempt(NotificationStatus status, String errorMessage) {
        if (this.status == NotificationStatus.SENT) {
            throw new IllegalStateException("O alerta já foi enviado a este destinatário neste ciclo.");
        }
        this.status = Objects.requireNonNull(status);
        this.errorMessage = truncate(errorMessage);
        this.attemptedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
    }

    private static String truncate(String message) {
        if (message == null) return null;
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }
}
