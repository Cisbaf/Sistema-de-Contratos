package contratos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationPropertiesTest {

    private NotificationProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("notifications", NotificationProperties.class);
    }

    @Test
    void semConfiguracaoUsaOsPadroes() {
        NotificationProperties p = bind(Map.of());

        assertThat(p.firstAlertMonths()).isEqualTo(6);
        assertThat(p.secondAlertMonths()).isEqualTo(4);
    }

    @Test
    void valoresConfiguradosSaoLidos() {
        NotificationProperties p = bind(Map.of(
                "notifications.first-alert-months", "8",
                "notifications.second-alert-months", "2"));

        assertThat(p.firstAlertMonths()).isEqualTo(8);
        assertThat(p.secondAlertMonths()).isEqualTo(2);
    }

    @Test
    void semListaDeSegurancaTodosPodemReceber() {
        assertThat(bind(Map.of()).mail().allowedRecipients()).isEmpty();
        assertThat(bind(Map.of()).mail().allows("qualquer@x.com")).isTrue();
        // variável de ambiente definida como vazia também significa "sem restrição"
        assertThat(bind(Map.of("notifications.mail.allowed-recipients", "")).mail().allows("qualquer@x.com")).isTrue();
    }

    @Test
    void listaDeSegurancaEhNormalizadaERestringe() {
        NotificationProperties p = bind(Map.of(
                "notifications.mail.allowed-recipients", " A@x.com, b@x.com ,,A@X.COM"));

        assertThat(p.mail().allowedRecipients()).containsExactly("a@x.com", "b@x.com");
        assertThat(p.mail().allows("B@X.COM ")).isTrue();
        assertThat(p.mail().allows("c@x.com")).isFalse();
    }

    @Test
    void intervaloEntreEnviosPadraoEhTresSegundosEPodeMudar() {
        assertThat(bind(Map.of()).mail().delayMs()).isEqualTo(3000);
        assertThat(bind(Map.of("notifications.mail.delay-ms", "0")).mail().delayMs()).isZero();
        assertThat(bind(Map.of("notifications.mail.delay-ms", "5000")).mail().delayMs()).isEqualTo(5000);
        assertThatThrownBy(() -> bind(Map.of("notifications.mail.delay-ms", "-1")))
                .isInstanceOf(BindException.class);
    }

    @Test
    void prazosInvalidosImpedemASubida() {
        assertThatThrownBy(() -> bind(Map.of(
                "notifications.first-alert-months", "4",
                "notifications.second-alert-months", "4")))
                .isInstanceOf(BindException.class);
        assertThatThrownBy(() -> bind(Map.of(
                "notifications.first-alert-months", "6",
                "notifications.second-alert-months", "0")))
                .isInstanceOf(BindException.class);
    }
}
