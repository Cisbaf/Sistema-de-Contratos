package contratos.api.dto.Notificacao;

import contratos.domain.enums.RecipientRole;

/** Destinatário já resolvido de um alerta. {@code address} vem normalizado (minúsculas, sem espaços). */
public record Recipient(RecipientRole role, String name, String address) {
}
