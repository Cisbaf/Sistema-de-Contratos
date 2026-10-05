package contratos.api.dto.Contract;

import contratos.domain.enums.AttachmentType;
import contratos.domain.enums.ContractStatus;
import contratos.domain.enums.ContractStatusTrigger;
import contratos.domain.enums.DocumentTemplateType;
import contratos.domain.enums.TimelineEventType;

import java.time.LocalDateTime;

/**
 * Um evento da linha do tempo do contrato. Só os campos do respectivo {@code type} vêm preenchidos:
 * <ul>
 *   <li>{@code STATUS_CHANGED}: {@code fromStatus}, {@code toStatus}, {@code trigger};</li>
 *   <li>{@code DOCUMENT_GENERATED}: {@code documentType}, {@code version}, {@code fileName};</li>
 *   <li>{@code ATTACHMENT_UPLOADED} / {@code ATTACHMENT_REMOVED}: {@code fileName}, {@code attType}.</li>
 * </ul>
 * {@code actorName} nulo significa "sistema" (por exemplo, a virada de status por prazo). Nunca carrega conteúdo de
 * arquivo, caminho em disco nem valores financeiros.
 * <p>
 * Os três construtores extras existem para o JPQL ({@code select new ...}) montar o evento direto da consulta, sem
 * carregar a entidade inteira (o documento gerado guarda o PDF numa coluna LONGBLOB).
 */
public record ContractTimelineEvent(
        TimelineEventType type,
        LocalDateTime occurredAt,
        String actorName,
        ContractStatus fromStatus,
        ContractStatus toStatus,
        ContractStatusTrigger trigger,
        DocumentTemplateType documentType,
        Integer version,
        String fileName,
        AttachmentType attType
) {
    /** Mudança de status. */
    public ContractTimelineEvent(LocalDateTime occurredAt, String actorName, ContractStatus fromStatus,
                                 ContractStatus toStatus, ContractStatusTrigger trigger) {
        this(TimelineEventType.STATUS_CHANGED, occurredAt, actorName, fromStatus, toStatus, trigger, null, null, null, null);
    }

    /** Documento gerado. */
    public ContractTimelineEvent(LocalDateTime occurredAt, String actorName, DocumentTemplateType documentType,
                                 int version, String fileName) {
        this(TimelineEventType.DOCUMENT_GENERATED, occurredAt, actorName, null, null, null, documentType, version, fileName, null);
    }

    /** Anexo enviado ({@code removed = false}) ou removido ({@code removed = true}). */
    public ContractTimelineEvent(LocalDateTime occurredAt, String actorName, String fileName, AttachmentType attType,
                                 boolean removed) {
        this(removed ? TimelineEventType.ATTACHMENT_REMOVED : TimelineEventType.ATTACHMENT_UPLOADED,
                occurredAt, actorName, null, null, null, null, null, fileName, attType);
    }
}
