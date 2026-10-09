package contratos.domain.enums;

/** Tipos de evento da linha do tempo do contrato (status, documentos gerados, anexos e exclusões de lançamento/documento). */
public enum TimelineEventType {
    STATUS_CHANGED,
    DOCUMENT_GENERATED,
    ATTACHMENT_UPLOADED,
    ATTACHMENT_REMOVED,
    LANCAMENTO_DELETED,
    DOCUMENT_DELETED
}
