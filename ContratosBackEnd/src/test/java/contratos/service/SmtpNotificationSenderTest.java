package contratos.service;

import contratos.api.dto.Notificacao.Recipient;
import contratos.config.NotificationProperties;
import contratos.domain.enums.NotificationStatus;
import contratos.domain.enums.RecipientRole;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Não fala com servidor real: cobre a validação da subida e o caminho de falha de conexão. */
class SmtpNotificationSenderTest {

    private NotificationProperties props(String from) {
        return new NotificationProperties(6, 4, "Compras", new NotificationProperties.Mail(true, from));
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
