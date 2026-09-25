package contratos.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Confere qual canal o Spring escolhe conforme {@code notifications.mail.enabled}. */
class NotificationWiringTest {

    @Nested
    @SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:wiring_off;MODE=MySQL;DB_CLOSE_DELAY=-1")
    @ActiveProfiles("test")
    class Desligado {
        @Autowired NotificationSender sender;

        @Test
        void padraoEhSimulacao() {
            assertThat(sender).isInstanceOf(LoggingNotificationSender.class);
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.datasource.url=jdbc:h2:mem:wiring_on;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "notifications.mail.enabled=true",
            "spring.mail.password=senha-de-teste"})
    @ActiveProfiles("test")
    class Ligado {
        @Autowired NotificationSender sender;

        @Test
        void ligadoUsaSmtp() {
            assertThat(sender).isInstanceOf(SmtpNotificationSender.class);
        }
    }
}
