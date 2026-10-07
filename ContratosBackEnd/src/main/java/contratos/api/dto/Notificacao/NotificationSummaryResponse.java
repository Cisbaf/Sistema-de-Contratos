package contratos.api.dto.Notificacao;

public record NotificationSummaryResponse(
        long total, long enviados, long falhas
) {
}
