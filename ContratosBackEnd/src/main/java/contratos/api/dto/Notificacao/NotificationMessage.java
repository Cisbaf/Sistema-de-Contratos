package contratos.api.dto.Notificacao;

/** Conteúdo de um alerta. Hoje só texto simples; um campo HTML entra aqui quando o e-mail for embelezado. */
public record NotificationMessage(String subject, String text) {
}
