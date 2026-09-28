"use client";

import { PageLoading } from "@/components/Feedback";
import { getJson } from "@/lib/api";
import { alertTypeLabels, notificationStatusPresentation, recipientRoleLabels } from "@/lib/notifications";
import type { Contract, NotificationLogEntry } from "@/types";
import CloseIcon from "@mui/icons-material/Close";
import {
  Alert, Box, Chip, Divider, Drawer, IconButton, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Tooltip, Typography,
} from "@mui/material";
import { useCallback, useEffect, useState } from "react";

const dateTime = (value: string) => new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));

export default function ContractNotificationsDrawer({ open, contract, onClose }: {
  open: boolean;
  contract: Contract | null;
  onClose: () => void;
}) {
  const contractId = contract?.id ?? null;

  const [items, setItems] = useState<NotificationLogEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");

  const load = useCallback(async () => {
    if (contractId === null) return;
    try {
      setItems(await getJson<NotificationLogEntry[]>(`/contracts/${contractId}/notificacoes`));
      setLoadError("");
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Erro ao carregar notificações");
    } finally {
      setLoading(false);
    }
  }, [contractId]);

  // Recarrega toda vez que o painel abre (ou troca de contrato), como no painel financeiro.
  useEffect(() => {
    if (!open) return;
    setLoading(true); setItems([]);
    void load();
  }, [open, load]);

  if (!contract) return null;

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      slotProps={{ paper: { sx: { width: { xs: "100%", md: "70vw" }, maxWidth: 1100 } } }}
    >
      <Stack direction="row" alignItems="center" justifyContent="space-between" px={3} py={2}>
        <Box>
          <Typography variant="h6" fontWeight={700}>Notificações — contrato {contract.numberContract}</Typography>
          <Typography variant="body2" color="text.secondary">Histórico de alertas de vencimento (6 e 4 meses) enviados para este contrato.</Typography>
        </Box>
        <IconButton aria-label="Fechar" onClick={onClose}><CloseIcon /></IconButton>
      </Stack>
      <Divider />

      <Box px={3} py={3} sx={{ overflowY: "auto" }}>
        {loadError && <Alert severity="error" sx={{ mb: 2 }}>{loadError}</Alert>}
        {loading ? <PageLoading /> :
          <TableContainer component={Box}>
            <Table sx={{ minWidth: 800 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Data/hora</TableCell>
                  <TableCell>Alerta</TableCell>
                  <TableCell>Destinatário</TableCell>
                  <TableCell>Papel</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {items.map(item => {
                  const status = notificationStatusPresentation[item.status];
                  return (
                    <TableRow key={item.id} hover>
                      <TableCell>{dateTime(item.attemptedAt)}</TableCell>
                      <TableCell>{alertTypeLabels[item.alertType]}</TableCell>
                      <TableCell>
                        <Typography variant="body2">{item.recipientName || "—"}</Typography>
                        <Typography variant="caption" color="text.secondary">{item.recipientAddress}</Typography>
                      </TableCell>
                      <TableCell>{recipientRoleLabels[item.recipientRole]}</TableCell>
                      <TableCell>
                        <Tooltip title={item.status === "FAILED" ? (item.errorMessage ?? "") : ""}>
                          <Chip label={status.label} color={status.color} size="small" />
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  );
                })}
                {items.length === 0 &&
                  <TableRow>
                    <TableCell colSpan={5} align="center" sx={{ py: 8, color: "text.secondary" }}>
                      Nenhuma notificação registrada para este contrato ainda.
                    </TableCell>
                  </TableRow>
                }
              </TableBody>
            </Table>
          </TableContainer>
        }
      </Box>
    </Drawer>
  );
}
