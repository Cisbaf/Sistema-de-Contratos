package contratos.service.Notification;

import contratos.config.NotificationProperties;
import contratos.domain.AppUser;
import contratos.domain.NotificationSettings;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import contratos.repository.NotificationSettingsRepository;
import contratos.service.AuditChangeLog;
import contratos.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fonte da verdade dos prazos de alerta (M6-40): o banco, semeado a partir de
 * {@code application.properties} na primeira consulta (ver {@link #seed()}).
 *
 * <p>Lidos aqui a cada execução do job ({@link NotificationPlanner}/{@link NotificationDispatcher}),
 * então uma mudança pela tela vale a partir do próximo disparo, sem restart.</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final NotificationSettingsRepository repository;
    private final NotificationProperties seedProperties;
    private final AuditService auditService;

    @Transactional
    public NotificationSettings current() {
        return repository.findById(NotificationSettings.SINGLETON_ID).orElseGet(this::seed);
    }

    /** Primeira subida (ou linha apagada do banco): copia os valores de application.properties. */
    private NotificationSettings seed() {
        return repository.save(new NotificationSettings(
                seedProperties.firstAlertMonths(),
                seedProperties.secondAlertMonths()));
    }

    @Transactional
    public NotificationSettings update(int firstAlertMonths, int secondAlertMonths, AppUser actor) {
        NotificationSettings settings = current();

        int beforeFirst = settings.getFirstAlertMonths();
        int beforeSecond = settings.getSecondAlertMonths();

        settings.update(firstAlertMonths, secondAlertMonths);

        String details = new AuditChangeLog()
                .field("Primeiro alerta (meses)", beforeFirst, firstAlertMonths)
                .field("Segundo alerta (meses)", beforeSecond, secondAlertMonths)
                .build();

        auditService.record(actor, AuditAction.UPDATE, AuditEntityType.SETTING, NotificationSettings.SINGLETON_ID,
                null, "Parâmetros de notificação atualizados", details);

        return settings;
    }
}
