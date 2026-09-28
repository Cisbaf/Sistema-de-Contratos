package contratos.api.dto.Notificacao;

import contratos.domain.AppUser;
import contratos.domain.NotificationLog;
import contratos.domain.enums.NotificationAlertType;
import contratos.domain.enums.NotificationChannel;
import contratos.domain.enums.NotificationStatus;
import contratos.domain.enums.RecipientRole;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Uma linha do histórico de notificações (M5-60), já com os dados do contrato
 * que a tela de consulta precisa (número, SEI, fiscais) para não obrigar o
 * front a buscar o contrato de novo só para filtrar/exibir.
 */
public record NotificationLogResponse(
        Long id,
        Long contractId,
        String contractNumber,
        String seiProcessNumber,
        List<String> fiscais,
        NotificationAlertType alertType,
        LocalDate cycleEndDate,
        NotificationChannel channel,
        RecipientRole recipientRole,
        String recipientName,
        String recipientAddress,
        NotificationStatus status,
        String errorMessage,
        LocalDateTime attemptedAt) {

    public static NotificationLogResponse from(NotificationLog log) {
        var contract = log.getContract();
        return new NotificationLogResponse(
                log.getId(),
                contract.getId(),
                contract.getNumberContract(),
                contract.getSeiProcessNumber(),
                contract.getFiscais().stream().map(AppUser::getName).toList(),
                log.getAlertType(),
                log.getCycleEndDate(),
                log.getChannel(),
                log.getRecipientRole(),
                log.getRecipientName(),
                log.getRecipientAddress(),
                log.getStatus(),
                log.getErrorMessage(),
                log.getAttemptedAt());
    }
}
