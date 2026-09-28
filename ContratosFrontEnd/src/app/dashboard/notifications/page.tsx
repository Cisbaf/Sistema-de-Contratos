"use client";

import { Feedback, PageLoading } from "@/components/Feedback";
import PageHeader from "@/components/PageHeader";
import { getJson } from "@/lib/api";
import { alertTypeLabels, notificationStatusPresentation, recipientRoleLabels } from "@/lib/notifications";
import type { NotificationLogEntry } from "@/types";
import SearchIcon from "@mui/icons-material/Search";
import {
  Box, Chip, InputAdornment, Paper, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, TextField, Tooltip, Typography,
} from "@mui/material";
import { useEffect, useMemo, useState } from "react";

const dateTime = (value: string) => new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));

export default function NotificationsPage() {
  const [items, setItems] = useState<NotificationLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [feedback, setFeedback] = useState({ message: "", error: false });

  useEffect(() => {
    getJson<NotificationLogEntry[]>("/notificacoes")
      .then(setItems)
      .catch(error => setFeedback({ message: error instanceof Error ? error.message : "Erro ao carregar notificações", error: true }))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase("pt-BR");
    return items.filter(item => {
      if (term) {
        const haystack = [item.contractNumber, item.seiProcessNumber, item.recipientName ?? "", item.recipientAddress, ...item.fiscais];
        if (!haystack.some(value => value.toLocaleLowerCase("pt-BR").includes(term))) return false;
      }
      const day = item.attemptedAt.slice(0, 10);
      if (from && day < from) return false;
      if (to && day > to) return false;
      return true;
    });
  }, [items, search, from, to]);

  const total = items.length;
  const enviados = items.filter(item => item.status === "SENT").length;
  const falhas = items.filter(item => item.status === "FAILED").length;

  return <>
    <PageHeader title="Notificações" subtitle="Alertas de vencimento de contrato (6 e 4 meses) enviados por e-mail ao fiscal e ao Controle Interno." />
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(3, 1fr)" }, gap: 2, mb: 3 }}>
      {[{ label: "Total de tentativas", value: total }, { label: "Enviados", value: enviados },
      { label: "Falhas", value: falhas }].map(card =>
        <Paper key={card.label} variant="outlined" sx={{ p: 2.5 }}>
          <Typography color="text.secondary" variant="body2">{card.label}</Typography>
          <Typography variant="h5" fontWeight={800} mt={.5}>{card.value}</Typography>
        </Paper>)}
    </Box>

    <Paper variant="outlined" sx={{ overflow: "hidden" }}>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} p={2}>
        <TextField value={search} onChange={event => setSearch(event.target.value)}
          placeholder="Buscar por contrato, SEI, fiscal ou destinatário" fullWidth
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> } }} />
        <TextField label="De" type="date" value={from} onChange={event => setFrom(event.target.value)}
          slotProps={{ inputLabel: { shrink: true } }} sx={{ minWidth: { sm: 160 } }} />
        <TextField label="Até" type="date" value={to} onChange={event => setTo(event.target.value)}
          slotProps={{ inputLabel: { shrink: true } }} sx={{ minWidth: { sm: 160 } }} />
      </Stack>

      {loading ? <PageLoading /> :
        <TableContainer>
          <Table sx={{ minWidth: 1100 }}>
            <TableHead>
              <TableRow>
                <TableCell>Data/hora</TableCell>
                <TableCell>Contrato</TableCell>
                <TableCell>Número SEI</TableCell>
                <TableCell>Fiscais</TableCell>
                <TableCell>Alerta</TableCell>
                <TableCell>Destinatário</TableCell>
                <TableCell>Papel</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filtered.map(item => {
                const status = notificationStatusPresentation[item.status];
                return (
                  <TableRow key={item.id} hover>
                    <TableCell>{dateTime(item.attemptedAt)}</TableCell>
                    <TableCell><Typography fontWeight={700}>{item.contractNumber}</Typography></TableCell>
                    <TableCell>{item.seiProcessNumber}</TableCell>
                    <TableCell>{item.fiscais.join(", ") || "—"}</TableCell>
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
              {filtered.length === 0 &&
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 8, color: "text.secondary" }}>Nenhuma notificação encontrada.</TableCell>
                </TableRow>
              }
            </TableBody>
          </Table>
        </TableContainer>
      }
    </Paper>
    <Feedback message={feedback.message} error={feedback.error} onClose={() => setFeedback({ message: "", error: false })} />
  </>;
}
