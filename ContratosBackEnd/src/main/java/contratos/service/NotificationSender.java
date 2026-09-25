package contratos.service;

import contratos.api.dto.Notificacao.Recipient;
import contratos.domain.enums.NotificationStatus;

/**
 * Canal de envio dos alertas. Há duas implementações, escolhidas por
 * {@code notifications.mail.enabled}: SMTP real ({@code true}) ou simulação ({@code false}, padrão).
 */
public interface NotificationSender {

    /** Envia (ou simula) a mensagem. Qualquer exceção significa falha e vira {@code FAILED} no log. */
    void send(Recipient recipient, NotificationMessage message);

    /** Status gravado no log quando {@link #send} termina sem exceção. */
    NotificationStatus successStatus();
}
