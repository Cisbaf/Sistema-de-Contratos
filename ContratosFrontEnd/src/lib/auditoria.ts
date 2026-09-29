import type { AuditAction, AuditEntityType } from "@/types";
import type { ChipProps } from "@mui/material";

export const auditActionPresentation: Record<AuditAction, { label: string; color: ChipProps["color"] }> = {
  CREATE: { label: "Criação", color: "success" },
  UPDATE: { label: "Edição", color: "info" },
  DELETE: { label: "Exclusão", color: "error" },
  GENERATE_DOCUMENT: { label: "Geração de documento", color: "default" },
  UPLOAD_ATTACHMENT: { label: "Envio de anexo", color: "default" },
  REMOVE_ATTACHMENT: { label: "Remoção de anexo", color: "default" },
};

export const auditEntityTypeLabels: Record<AuditEntityType, string> = {
  CONTRACT: "Contrato",
  USER: "Usuário",
  SECTOR: "Setor",
  TEMPLATE: "Template",
  LANCAMENTO: "Lançamento financeiro",
  ATTACHMENT: "Anexo",
  DOCUMENT: "Documento gerado",
  SETTING: "Parâmetro",
};
