package contratos.api.dto.NotificationSettings;

import jakarta.validation.constraints.Min;

public record NotificationSettingsRequest(
        @Min(1) int firstAlertMonths,
        @Min(1) int secondAlertMonths) {
}
