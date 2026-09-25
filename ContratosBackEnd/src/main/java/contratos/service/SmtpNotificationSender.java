package contratos.service;

import contratos.api.dto.Notificacao.Recipient;
import contratos.config.NotificationProperties;
import contratos.domain.enums.NotificationStatus;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Envio real por SMTP. Só existe quando {@code notifications.mail.enabled=true}. */
@Component
@ConditionalOnProperty(name = "notifications.mail.enabled", havingValue = "true")
public class SmtpNotificationSender implements NotificationSender {

    private static final String FROM_NAME = "CISBAF - Sistema de Contratos";

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpNotificationSender(JavaMailSender mailSender,
                                  NotificationProperties properties,
                                  @Value("${spring.mail.username:}") String smtpUsername,
                                  @Value("${spring.mail.password:}") String smtpPassword) {
        this.mailSender = mailSender;
        this.from = properties.mail().from().isEmpty() ? smtpUsername : properties.mail().from();

        // Falha na subida, não na primeira madrugada em que o job rodar.
        if (from == null || from.isBlank()) {
            throw new IllegalStateException(
                    "notifications.mail.enabled=true exige notifications.mail.from ou spring.mail.username.");
        }
        if (smtpPassword == null || smtpPassword.isBlank()) {
            throw new IllegalStateException(
                    "notifications.mail.enabled=true exige a senha do SMTP na variável de ambiente MAIL_PASSWORD.");
        }
    }

    @Override
    public void send(Recipient recipient, NotificationMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(from, FROM_NAME, StandardCharsets.UTF_8.name()));
            helper.setTo(new InternetAddress(recipient.address(), recipient.name(), StandardCharsets.UTF_8.name()));
            helper.setSubject(message.subject());
            helper.setText(message.text(), false);
            mailSender.send(mime);
        } catch (jakarta.mail.MessagingException | java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("Não foi possível montar o e-mail: " + e.getMessage(), e);
        }
    }

    @Override
    public NotificationStatus successStatus() {
        return NotificationStatus.SENT;
    }
}
