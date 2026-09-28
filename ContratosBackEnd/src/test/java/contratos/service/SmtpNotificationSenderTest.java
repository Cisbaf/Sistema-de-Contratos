package contratos.service;

import contratos.api.dto.Notificacao.NotificationMessage;
import contratos.api.dto.Notificacao.Recipient;
import contratos.config.NotificationProperties;
import contratos.domain.enums.NotificationStatus;
import contratos.domain.enums.RecipientRole;
import contratos.service.Notification.SmtpNotificationSender;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Não fala com servidor real: cobre a validação da subida e o caminho de falha de conexão. */
class SmtpNotificationSenderTest {

    private NotificationProperties props(String from) {
        return props(from, 0);
    }

    private NotificationProperties props(String from, long delayMs) {
        return new NotificationProperties(6, 4, new NotificationProperties.Mail(true, from, java.util.List.of(), delayMs));
    }

    @Test
    void semSenhaOEnvioRealNaoSobe() {
        assertThatThrownBy(() -> new SmtpNotificationSender(
                new JavaMailSenderImpl(), props("naoresponda@cisbaf.org.br"), "naoresponda@cisbaf.org.br", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_PASSWORD");
    }

    @Test
    void semRemetenteOEnvioRealNaoSobe() {
        assertThatThrownBy(() -> new SmtpNotificationSender(
                new JavaMailSenderImpl(), props(""), "", "segredo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notifications.mail.from");
    }

    @Test
    void remetenteCaiParaOUsuarioDoSmtpQuandoNaoConfigurado() {
        SmtpNotificationSender sender = new SmtpNotificationSender(
                new JavaMailSenderImpl(), props(""), "naoresponda@cisbaf.org.br", "segredo");

        assertThat(sender.successStatus()).isEqualTo(NotificationStatus.SENT);
    }

    private long millisDeDoisEnvios(long delayMs) {
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost("localhost");
        mail.setPort(1);
        SmtpNotificationSender sender = new SmtpNotificationSender(
                mail, props("naoresponda@cisbaf.org.br", delayMs), "", "segredo");
        Recipient to = new Recipient(RecipientRole.FISCAL, "Ana", "ana@cisbaf.org.br");
        NotificationMessage msg = new NotificationMessage("assunto", "corpo");

        long start = System.nanoTime();
        assertThatThrownBy(() -> sender.send(to, msg)).isInstanceOf(MailException.class);
        assertThatThrownBy(() -> sender.send(to, msg)).isInstanceOf(MailException.class);
        return (System.nanoTime() - start) / 1_000_000;
    }

    @Test
    void esperaOIntervaloConfiguradoEntreDoisEnvios() {
        assertThat(millisDeDoisEnvios(400)).isGreaterThanOrEqualTo(400);
    }

    @Test
    void semIntervaloNaoEspera() {
        assertThat(millisDeDoisEnvios(0)).isLessThan(400);
    }

    @Test
    void servidorInalcancavelViraExcecaoParaOLogMarcarFailed() {
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost("localhost");
        mail.setPort(1); // nada escuta aqui
        SmtpNotificationSender sender = new SmtpNotificationSender(
                mail, props("naoresponda@cisbaf.org.br"), "", "segredo");

        assertThatThrownBy(() -> sender.send(
                new Recipient(RecipientRole.FISCAL, "Ação Ünicode", "ana@cisbaf.org.br"),
                new NotificationMessage("[CISBAF] Contrato 1/2026 vence em 25/03/2027", "corpo com acentuação")))
                .isInstanceOf(MailException.class);
    }
}
