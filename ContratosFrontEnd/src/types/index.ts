export interface Sector { id: number; name: string; usersCount: number }

export interface User {
  id: number;
  name: string;
  username: string;
  email: string;
  cellPhone: string | null;
  sector: { id: number; name: string } | null;
  perfil: "ADMIN" | "CONTROLE_INTERNO" | "FISCAL";
}

export interface Contract {
  id: number;
  numberContract: string;
  numberProcess: string;
  object: string;
  company: string;
  cnpj: string;
  valueGlobal: number;
  valueMensal: number;
  fiscais: User[];
  startDate: string;
  endDate: string;
  font: string | null;
  ta: string | null;
  status: ContractStatus;
  fiscaisConfirmadosEnvioInteresse: number[];
  seiProcessNumber: string;
  maxExtensionMonths: number | null;
}

export type ContractStatus = "EM_VIGENCIA" | "AGUARDANDO_EMAIL_INTERESSE" | "EMAIL_ENVIADO" | "RENOVACAO_ABERTA_SEI";

export type ContractStatusTrigger = "DEADLINE" | "INTEREST_EMAIL_GENERATED" | "TECHNICAL_OPINION_GENERATED" | "ADITIVO_REGISTRADO";

export type ContractTimelineEventType = "STATUS_CHANGED" | "DOCUMENT_GENERATED" | "ATTACHMENT_UPLOADED" | "ATTACHMENT_REMOVED";

/** Evento da linha do tempo do contrato; só os campos do respectivo `type` vêm preenchidos. `actorName` nulo = sistema. */
export interface ContractTimelineEvent {
  type: ContractTimelineEventType;
  occurredAt: string;
  actorName: string | null;
  fromStatus: ContractStatus | null;
  toStatus: ContractStatus | null;
  trigger: ContractStatusTrigger | null;
  documentType: DocumentTemplateType | null;
  version: number | null;
  fileName: string | null;
  attType: AttachmentType | null;
}

export type DocumentTemplateType = "INTEREST_EMAIL" | "TECHNICAL_OPINION" | "SUPPLIER_RENEWAL_EMAIL" | "PAYMENT_CHECKLIST";

export interface DocumentTemplate {
  id: number;
  templateType: DocumentTemplateType;
  content: string;
  updatedAt: string;
  updatedById: number;
  updatedByName: string;
}

export interface AuthStatus {
  valid: boolean;
  username?: string;
  name?: string;
  perfil?: "ADMIN" | "CONTROLE_INTERNO" | "FISCAL";
}

export interface GeneratedDocument {
  id: number;
  contractId: number;
  documentType: DocumentTemplateType;
  format: "PDF" | "WORD";
  fileName: string;
  version: number;
  generatedBy: { id: number; name: string; username: string };
  generatedAt: string;
}

export type AttachmentType = "GERAL" | "TERMO_ADITIVO";

export interface ContractAttachment {
  id: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
  uploadedBy: User;
  ativo: boolean;
  removedAt: string | null;
  removedBy: User | null;
  attType: AttachmentType;
}

export interface Lancamento {
  id: number;
  contratoId: number;
  numeroProcesso: string;
  notaFiscal: string;
  parcela: string | null;
  competencia: string;
  valorNota: number;
  observacoes: string | null;
  criadoEm: string;
  criadoPor: User | null;
  atualizadoEm: string | null;
  atualizadoPor: User | null;
}

export interface LancamentoRequest {
  numeroProcesso: string;
  notaFiscal: string;
  parcela: string | null;
  competencia: string;
  valorNota: number;
  observacoes: string | null;
}

export type TipoEventoLancamento = "EDICAO" | "EXCLUSAO";

export interface LancamentoHistorico {
  id: number;
  lancamentoId: number;
  contratoId: number;
  tipoEvento: TipoEventoLancamento;
  numeroProcesso: string;
  notaFiscal: string;
  parcela: string | null;
  competencia: string;
  valorNota: number;
  observacoes: string | null;
  alteradoEm: string;
  alteradoPor: User | null;
}

export type NotificationAlertType = "SIX_MONTHS" | "FOUR_MONTHS";
export type NotificationChannel = "EMAIL";
export type RecipientRole = "FISCAL" | "INTERNAL_CONTROL";
export type NotificationStatus = "SENT" | "FAILED" | "SIMULATED";

export interface NotificationLogEntry {
  id: number;
  contractId: number;
  contractNumber: string;
  seiProcessNumber: string;
  fiscais: string[];
  alertType: NotificationAlertType;
  cycleEndDate: string;
  channel: NotificationChannel;
  recipientRole: RecipientRole;
  recipientName: string | null;
  recipientAddress: string;
  status: NotificationStatus;
  errorMessage: string | null;
  attemptedAt: string;
}

export type AuditAction = "CREATE" | "UPDATE" | "DELETE" | "GENERATE_DOCUMENT" | "UPLOAD_ATTACHMENT" | "REMOVE_ATTACHMENT";
export type AuditEntityType = "CONTRACT" | "USER" | "SECTOR" | "TEMPLATE" | "LANCAMENTO" | "ATTACHMENT" | "DOCUMENT" | "SETTING";

export interface AuditLogEntry {
  id: number;
  occurredAt: string;
  actorName: string | null;
  actorEmail: string | null;
  action: AuditAction;
  entityType: AuditEntityType;
  entityId: number | null;
  contractId: number | null;
  summary: string;
  details: string | null;
}

export interface NotificationSettings {
  firstAlertMonths: number;
  secondAlertMonths: number;
}

// Forma de Page<T> do Spring Data: só os campos que a tela usa (a resposta real
// do backend tem mais metadados de paginação, sem uso aqui).
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
