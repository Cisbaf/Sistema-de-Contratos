package contratos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Parâmetros das notificações automáticas (prefixo {@code notifications.*}).
 * Valores inválidos derrubam a subida da aplicação com mensagem clara, em vez de
 * descobrir o erro só quando o job rodar.
 *
 * @param firstAlertMonths     antecedência do primeiro alerta, em meses
 * @param secondAlertMonths    antecedência do segundo alerta, em meses (menor que o primeiro)
 * @param mail                 envio por e-mail: {@code enabled=false} (padrão) só simula, sem enviar nada
 */
@ConfigurationProperties(prefix = "notifications")
public record NotificationProperties(
        @DefaultValue("6") int firstAlertMonths,
        @DefaultValue("4") int secondAlertMonths,
        @DefaultValue Mail mail) {

    /**
     * @param enabled envia de verdade quando {@code true}; quando {@code false} apenas registra a simulação
     * @param from    remetente (ex.: naoresponda@cisbaf.org.br); em branco usa {@code spring.mail.username}
     * @param allowedRecipients lista de segurança para testes: quando preenchida, SÓ estes endereços
     *                          recebem (os demais são ignorados, sem log); vazia = sem restrição
     * @param delayMs intervalo mínimo, em milissegundos, entre dois envios reais (evita rajadas que
     *                fazem provedores marcarem o remetente como spam); 0 desliga
     */
    public record Mail(@DefaultValue("false") boolean enabled,
                       @DefaultValue("") String from,
                       @DefaultValue List<String> allowedRecipients,
                       @DefaultValue("3000") long delayMs) {
        public Mail {
            if (delayMs < 0) {
                throw new IllegalArgumentException(
                        "notifications.mail.delay-ms não pode ser negativo (recebido: " + delayMs + ").");
            }
            from = from == null ? "" : from.trim();
            allowedRecipients = allowedRecipients == null ? List.of() : allowedRecipients.stream()
                    .filter(Objects::nonNull)
                    .map(a -> a.trim().toLowerCase(Locale.ROOT))
                    .filter(a -> !a.isEmpty())
                    .distinct()
                    .toList();
        }

        /** Sem lista, todos podem receber; com lista, só quem está nela (compara em minúsculas). */
        public boolean allows(String address) {
            return allowedRecipients.isEmpty() || allowedRecipients.contains(address.trim().toLowerCase(Locale.ROOT));
        }
    }

    public NotificationProperties {
        if (secondAlertMonths <= 0 || firstAlertMonths <= secondAlertMonths) {
            throw new IllegalArgumentException(
                    "notifications.first-alert-months deve ser maior que notifications.second-alert-months, "
                            + "e este maior que zero (recebido: " + firstAlertMonths + " e " + secondAlertMonths + ").");
        }
    }
}
