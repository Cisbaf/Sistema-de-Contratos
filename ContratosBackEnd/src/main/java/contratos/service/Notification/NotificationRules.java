package contratos.service.Notification;

import contratos.domain.enums.NotificationAlertType;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Regra pura (sem Spring, sem banco) de qual alerta vale para um contrato numa data.
 * Só vale o alerta mais próximo do fim: quem já está dentro da janela de 4 meses
 * não recebe o de 6.
 */
public final class NotificationRules {

    private NotificationRules() {
    }

    /**
     * @param endDate           término da vigência
     * @param today             data de referência
     * @param firstAlertMonths  antecedência do primeiro alerta (padrão 6)
     * @param secondAlertMonths antecedência do segundo alerta (padrão 4); deve ser menor que o primeiro
     * @return o alerta devido hoje, ou vazio se o contrato já venceu ou ainda está fora das janelas
     */
    public static Optional<NotificationAlertType> resolveAlert(LocalDate endDate, LocalDate today,
                                                               int firstAlertMonths, int secondAlertMonths) {
        Objects.requireNonNull(endDate, "O término da vigência é obrigatório.");
        Objects.requireNonNull(today, "A data de referência é obrigatória.");
        if (secondAlertMonths <= 0 || firstAlertMonths <= secondAlertMonths) {
            throw new IllegalArgumentException(
                    "Os prazos devem satisfazer 0 < segundo alerta < primeiro alerta (recebido: "
                            + firstAlertMonths + " e " + secondAlertMonths + ").");
        }

        if (today.isAfter(endDate)) {
            return Optional.empty();
        }
        // A janela mais próxima do fim precisa ser testada primeiro.
        if (!today.isBefore(endDate.minusMonths(secondAlertMonths))) {
            return Optional.of(NotificationAlertType.FOUR_MONTHS);
        }
        if (!today.isBefore(endDate.minusMonths(firstAlertMonths))) {
            return Optional.of(NotificationAlertType.SIX_MONTHS);
        }
        return Optional.empty();
    }
}
