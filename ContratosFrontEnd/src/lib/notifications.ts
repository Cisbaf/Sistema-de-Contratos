import type { NotificationAlertType, NotificationStatus, RecipientRole } from "@/types";
import type { ChipProps } from "@mui/material";

export const alertTypeLabels: Record<NotificationAlertType, string> = {
  SIX_MONTHS: "6 meses",
  FOUR_MONTHS: "4 meses",
};

export const recipientRoleLabels: Record<RecipientRole, string> = {
  FISCAL: "Fiscal",
  INTERNAL_CONTROL: "Controle Interno",
};

export const notificationStatusPresentation: Record<NotificationStatus, { label: string; color: ChipProps["color"] }> = {
  SENT: { label: "Enviado", color: "success" },
  FAILED: { label: "Falhou", color: "error" },
  SIMULATED: { label: "Simulado", color: "default" },
};
