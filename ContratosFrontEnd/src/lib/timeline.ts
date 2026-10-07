import type {
  AttachmentType, ContractStatus, ContractStatusTrigger, ContractTimelineEvent, DocumentTemplateType,
} from "@/types";

export const timelineStatusLabels: Record<ContractStatus, string> = {
  EM_VIGENCIA: "Em vigência",
  AGUARDANDO_EMAIL_INTERESSE: "Aguardando e-mail de interesse",
  EMAIL_ENVIADO: "E-mail enviado",
  RENOVACAO_ABERTA_SEI: "Renovação aberta no SEI",
};

export const timelineTriggerLabels: Record<ContractStatusTrigger, string> = {
  DEADLINE: "Por prazo",
  INTEREST_EMAIL_GENERATED: "E-mail de interesse gerado",
  TECHNICAL_OPINION_GENERATED: "Parecer técnico gerado",
  ADITIVO_REGISTRADO: "Termo Aditivo registrado",
  CONTRACT_EDITED: "Contrato editado",
};

export const timelineDocumentLabels: Record<DocumentTemplateType, string> = {
  INTEREST_EMAIL: "E-mail de interesse",
  TECHNICAL_OPINION: "Parecer técnico",
  SUPPLIER_RENEWAL_EMAIL: "E-mail de renovação ao prestador",
  PAYMENT_CHECKLIST: "Ateste dos fiscais",
};

export const timelineAttachmentTypeLabels: Record<AttachmentType, string> = {
  GERAL: "Geral",
  TERMO_ADITIVO: "Termo Aditivo",
};

/** Texto principal e secundário de um evento. `actorName` nulo = feito pelo sistema (ex.: virada de status por prazo). */
export function describeTimelineEvent(event: ContractTimelineEvent): { title: string; detail: string } {
  switch (event.type) {
    case "STATUS_CHANGED":
      return {
        title: `Status: ${event.fromStatus ? timelineStatusLabels[event.fromStatus] : "—"} → ${event.toStatus ? timelineStatusLabels[event.toStatus] : "—"}`,
        detail: event.trigger ? timelineTriggerLabels[event.trigger] : "",
      };
    case "DOCUMENT_GENERATED":
      return {
        title: `${event.documentType ? timelineDocumentLabels[event.documentType] : "Documento"} gerado (versão ${event.version ?? "?"})`,
        detail: event.fileName ?? "",
      };
    case "ATTACHMENT_UPLOADED":
      return { title: "Anexo enviado", detail: event.fileName ?? "" };
    case "ATTACHMENT_REMOVED":
      return { title: "Anexo removido", detail: event.fileName ?? "" };
  }
}
