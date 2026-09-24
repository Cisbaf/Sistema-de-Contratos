export interface Sector { id: number; name: string; usersCount: number }

export interface User {
  id: number;
  name: string;
  username: string;
  email: string;
  cellPhone: string | null;
  sector: { id: number; name: string } | null;
  admin: boolean;
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
  admin?: boolean;
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
