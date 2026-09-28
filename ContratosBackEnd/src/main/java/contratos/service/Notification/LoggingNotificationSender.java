package contratos.service.Notification;

import contratos.api.dto.Notificacao.NotificationMessage;
import contratos.api.dto.Notificacao.Recipient;
import contratos.domain.enums.NotificationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Modo simulado (padrão): não envia nada, só registra em log. Evita disparar e-mail real em desenvolvimento. */
@Slf4j
@Component
@ConditionalOnProperty(name = "notifications.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public void send(Recipient recipient, NotificationMessage message) {
        log.info("Notificação SIMULADA ({}) para {} — assunto: {}",
                recipient.role(), recipient.address(), message.subject());
    }

    @Override
    public NotificationStatus successStatus() {
        return NotificationStatus.SIMULATED;
    }
}
