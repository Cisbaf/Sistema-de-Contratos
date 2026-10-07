"use client";

import { Feedback, PageLoading } from "@/components/Feedback";
import PageHeader from "@/components/PageHeader";
import { getJson } from "@/lib/api";
import { alertTypeLabels, notificationStatusPresentation, recipientRoleLabels } from "@/lib/notifications";
import type { NotificationLogEntry, NotificationSummary, Page } from "@/types";
import SearchIcon from "@mui/icons-material/Search";
import {
  Box, Chip, InputAdornment, Paper, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TablePagination, TableRow, TextField, Tooltip, Typography,
} from "@mui/material";
import { useEffect, useState } from "react";

const dateTime = (value: string) => new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));

export default function NotificationsPage() {
  const [data, setData] = useState<Page<NotificationLogEntry> | null>(null);
  const [summary, setSummary] = useState<NotificationSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState(""); // o que está na caixa de busca
  const [search, setSearch] = useState("");           // o que vai para o servidor (depois do debounce)
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [feedback, setFeedback] = useState({ message: "", error: false });
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  // Cards: totais gerais, buscados uma vez (não dependem do filtro nem da página).
  useEffect(() => {
    getJson<NotificationSummary>("/notificacoes/resumo")
      .then(setSummary)
      .catch(error => setFeedback({ message: error instanceof Error ? error.message : "Erro ao carregar o resumo", error: true }));
  }, []);

  // Debounce: a busca só vai ao servidor 400 ms depois da última tecla, e volta para a primeira página.
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(searchInput.trim());
      setPage(0);
    }, 400);
    return () => clearTimeout(timer);
  }, [searchInput]);

  // A página atual, filtrada e paginada no servidor. `ignore` descarta a resposta de uma
  // chamada antiga que chegue depois de uma mais nova (ex.: digitou "ro" e logo "rob").
  useEffect(() => {
    let ignore = false;
    setLoading(true);
    const params = new URLSearchParams({ page: String(page), size: String(rowsPerPage) });
    if (search) params.set("busca", search);
    if (from) params.set("dataInicio", from);
    if (to) params.set("dataFim", to);

    getJson<Page<NotificationLogEntry>>(`/notificacoes?${params.toString()}`)
      .then(result => { if (!ignore) setData(result); })
      .catch(error => { if (!ignore) setFeedback({ message: error instanceof Error ? error.message : "Erro ao carregar notificações", error: true }); })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [search, from, to, page, rowsPerPage]);

  const items = data?.content ?? [];

  return <>
    <PageHeader title="Notificações" subtitle="Alertas de vencimento de contrato (6 e 4 meses) enviados por e-mail ao fiscal e ao Controle Interno." />
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(3, 1fr)" }, gap: 2, mb: 3 }}>
      {[{ label: "Total de tentativas", value: summary?.total ?? "—" }, { label: "Enviados", value: summary?.enviados ?? "—" },
      { label: "Falhas", value: summary?.falhas ?? "—" }].map(card =>
        <Paper key={card.label} variant="outlined" sx={{ p: 2.5 }}>
          <Typography color="text.secondary" variant="body2">{card.label}</Typography>
          <Typography variant="h5" fontWeight={800} mt={.5}>{card.value}</Typography>
        </Paper>)}
    </Box>

    <Paper variant="outlined" sx={{ overflow: "hidden" }}>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} p={2}>
        <TextField value={searchInput} onChange={event => setSearchInput(event.target.value)}
          placeholder="Buscar por contrato, SEI, fiscal ou destinatário" fullWidth
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> } }} />
        <TextField label="De" type="date" value={from} onChange={event => { setFrom(event.target.value); setPage(0); }}
          slotProps={{ inputLabel: { shrink: true } }} sx={{ minWidth: { sm: 160 } }} />
        <TextField label="Até" type="date" value={to} onChange={event => { setTo(event.target.value); setPage(0); }}
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
              {items.map(item => {
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
              {items.length === 0 &&
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 8, color: "text.secondary" }}>Nenhuma notificação encontrada.</TableCell>
                </TableRow>
              }
            </TableBody>
          </Table>
        </TableContainer>
      }
      {!loading && data && data.totalElements > 0 &&
        <TablePagination
          component="div"
          count={data.totalElements}
          page={page}
          onPageChange={(_event, newPage) => setPage(newPage)}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={event => { setRowsPerPage(parseInt(event.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[10, 25, 50]}
          labelRowsPerPage="Linhas por página"
          labelDisplayedRows={({ from: rowFrom, to: rowTo, count }) => `${rowFrom}–${rowTo} de ${count}`}
        />
      }
    </Paper>
    <Feedback message={feedback.message} error={feedback.error} onClose={() => setFeedback({ message: "", error: false })} />
  </>;
}
