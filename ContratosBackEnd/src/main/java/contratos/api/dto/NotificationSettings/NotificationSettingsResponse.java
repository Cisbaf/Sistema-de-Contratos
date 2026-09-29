package contratos.api.dto.NotificationSettings;

import contratos.domain.NotificationSettings;

public record NotificationSettingsResponse(
        int firstAlertMonths,
        int secondAlertMonths) {

    public static NotificationSettingsResponse from(NotificationSettings settings) {
        return new NotificationSettingsResponse(
                settings.getFirstAlertMonths(),
                settings.getSecondAlertMonths());
    }
}
