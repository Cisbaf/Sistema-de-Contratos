package contratos.api.dto.Notificacao;

import contratos.domain.Contract;
import contratos.domain.enums.NotificationAlertType;

import java.time.LocalDate;
import java.util.List;

/** O que ainda falta enviar para um contrato: um alerta, o ciclo e os destinatários que não o receberam. */
public record PlannedNotification(Contract contract,
                                  NotificationAlertType alertType,
                                  LocalDate cycleEndDate,
                                  List<Recipient> recipients) {
}
