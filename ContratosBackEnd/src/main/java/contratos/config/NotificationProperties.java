package contratos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parâmetros das notificações automáticas (prefixo {@code notifications.*}).
 * Valores inválidos derrubam a subida da aplicação com mensagem clara, em vez de
 * descobrir o erro só quando o job rodar.
 *
 * @param firstAlertMonths     antecedência do primeiro alerta, em meses
 * @param secondAlertMonths    antecedência do segundo alerta, em meses (menor que o primeiro)
 * @param purchasingSectorName nome do setor cadastrado que representa o Setor de Compras;
 *                             em branco desliga o envio para Compras
 * @param mail                 envio por e-mail: {@code enabled=false} (padrão) só simula, sem enviar nada
 */
@ConfigurationProperties(prefix = "notifications")
public record NotificationProperties(
        @DefaultValue("6") int firstAlertMonths,
        @DefaultValue("4") int secondAlertMonths,
        @DefaultValue("Compras") String purchasingSectorName,
        @DefaultValue Mail mail) {

    /**
     * @param enabled envia de verdade quando {@code true}; quando {@code false} apenas registra a simulação
     * @param from    remetente (ex.: naoresponda@cisbaf.org.br); em branco usa {@code spring.mail.username}
     */
    public record Mail(@DefaultValue("false") boolean enabled, @DefaultValue("") String from) {
        public Mail {
            from = from == null ? "" : from.trim();
        }
    }

    public NotificationProperties {
        if (secondAlertMonths <= 0 || firstAlertMonths <= secondAlertMonths) {
            throw new IllegalArgumentException(
                    "notifications.first-alert-months deve ser maior que notifications.second-alert-months, "
                            + "e este maior que zero (recebido: " + firstAlertMonths + " e " + secondAlertMonths + ").");
        }
        purchasingSectorName = purchasingSectorName == null ? "" : purchasingSectorName.trim();
    }

    public boolean hasPurchasingSector() {
        return !purchasingSectorName.isEmpty();
    }
}
