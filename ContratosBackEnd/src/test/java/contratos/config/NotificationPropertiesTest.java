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
        assertThat(p.purchasingSectorName()).isEqualTo("Compras");
        assertThat(p.hasPurchasingSector()).isTrue();
    }

    @Test
    void valoresConfiguradosSaoLidos() {
        NotificationProperties p = bind(Map.of(
                "notifications.first-alert-months", "8",
                "notifications.second-alert-months", "2",
                "notifications.purchasing-sector-name", "  Setor de Compras  "));

        assertThat(p.firstAlertMonths()).isEqualTo(8);
        assertThat(p.secondAlertMonths()).isEqualTo(2);
        assertThat(p.purchasingSectorName()).isEqualTo("Setor de Compras");
    }

    @Test
    void setorEmBrancoDesligaOEnvioParaCompras() {
        NotificationProperties p = bind(Map.of("notifications.purchasing-sector-name", "   "));

        assertThat(p.hasPurchasingSector()).isFalse();
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
