package contratos.domain.enums;

/** Tipos de evento da linha do tempo do contrato (status, documentos gerados e anexos). */
public enum TimelineEventType {
    STATUS_CHANGED,
    DOCUMENT_GENERATED,
    ATTACHMENT_UPLOADED,
    ATTACHMENT_REMOVED
}
