"use client";

import { Feedback, PageLoading } from "@/components/Feedback";
import PageHeader from "@/components/PageHeader";
import { getJson } from "@/lib/api";
import { auditActionPresentation, auditEntityTypeLabels } from "@/lib/auditoria";
import type { AuditAction, AuditEntityType, AuditLogEntry, Page } from "@/types";
import {
  Chip, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TablePagination, TableRow, TextField, Tooltip, Typography,
} from "@mui/material";
import { useEffect, useState } from "react";

const dateTime = (value: string) => new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));

const entityTypeOptions: AuditEntityType[] = ["CONTRACT", "USER", "SECTOR", "TEMPLATE", "LANCAMENTO", "ATTACHMENT", "DOCUMENT", "SETTING"];
const actionOptions: AuditAction[] = ["CREATE", "UPDATE", "DELETE", "GENERATE_DOCUMENT", "UPLOAD_ATTACHMENT", "REMOVE_ATTACHMENT"];

// Só dígitos: contractId/actorId são ids numéricos, sem seletor por nome ainda.
const onlyDigits = (value: string) => value.replace(/\D/g, "");

export default function AuditoriaPage() {
  const [data, setData] = useState<Page<AuditLogEntry> | null>(null);
  const [loading, setLoading] = useState(true);
  const [feedback, setFeedback] = useState({ message: "", error: false });

  const [dataInicio, setDataInicio] = useState("");
  const [dataFim, setDataFim] = useState("");
  const [entityType, setEntityType] = useState<AuditEntityType | "">("");
  const [action, setAction] = useState<AuditAction | "">("");
  const [contractId, setContractId] = useState("");
  const [actorId, setActorId] = useState("");

  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);

  useEffect(() => {
    setLoading(true);
    const params = new URLSearchParams({ page: String(page), size: String(rowsPerPage) });
    if (dataInicio) params.set("dataInicio", dataInicio);
    if (dataFim) params.set("dataFim", dataFim);
    if (entityType) params.set("entityType", entityType);
    if (action) params.set("action", action);
    if (contractId) params.set("contractId", contractId);
    if (actorId) params.set("actorId", actorId);

    getJson<Page<AuditLogEntry>>(`/auditoria?${params.toString()}`)
      .then(setData)
      .catch(error => setFeedback({ message: error instanceof Error ? error.message : "Erro ao carregar a auditoria", error: true }))
      .finally(() => setLoading(false));
  }, [dataInicio, dataFim, entityType, action, contractId, actorId, page, rowsPerPage]);

  const items = data?.content ?? [];

  return <>
    <PageHeader title="Auditoria" subtitle="Trilha de ações sensíveis do sistema: quem fez, quando e o quê." />

    <Paper variant="outlined" sx={{ overflow: "hidden" }}>
      {/* A busca é no servidor: qualquer filtro volta pra página 0, senão a
          página atual pode pedir um resultado que não existe mais. */}
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} p={2} flexWrap="wrap" useFlexGap>
        <TextField label="De" type="date" value={dataInicio}
          onChange={event => { setDataInicio(event.target.value); setPage(0); }}
          slotProps={{ inputLabel: { shrink: true } }} sx={{ minWidth: { sm: 160 } }} />
        <TextField label="Até" type="date" value={dataFim}
          onChange={event => { setDataFim(event.target.value); setPage(0); }}
          slotProps={{ inputLabel: { shrink: true } }} sx={{ minWidth: { sm: 160 } }} />
        <TextField select label="Entidade" value={entityType}
          onChange={event => { setEntityType(event.target.value as AuditEntityType | ""); setPage(0); }}
          sx={{ minWidth: { sm: 180 } }}>
          <MenuItem value="">Todas</MenuItem>
          {entityTypeOptions.map(option => <MenuItem key={option} value={option}>{auditEntityTypeLabels[option]}</MenuItem>)}
        </TextField>
        <TextField select label="Ação" value={action}
          onChange={event => { setAction(event.target.value as AuditAction | ""); setPage(0); }}
          sx={{ minWidth: { sm: 180 } }}>
          <MenuItem value="">Todas</MenuItem>
          {actionOptions.map(option => <MenuItem key={option} value={option}>{auditActionPresentation[option].label}</MenuItem>)}
        </TextField>
        <TextField label="ID do contrato" value={contractId}
          onChange={event => { setContractId(onlyDigits(event.target.value)); setPage(0); }}
          sx={{ minWidth: { sm: 140 } }} />
        <TextField label="ID do usuário (ator)" value={actorId}
          onChange={event => { setActorId(onlyDigits(event.target.value)); setPage(0); }}
          sx={{ minWidth: { sm: 160 } }} />
      </Stack>

      {loading ? <PageLoading /> :
        <TableContainer>
          <Table sx={{ minWidth: 1100 }}>
            <TableHead>
              <TableRow>
                <TableCell>Data/hora</TableCell>
                <TableCell>Ator</TableCell>
                <TableCell>Ação</TableCell>
                <TableCell>Entidade</TableCell>
                <TableCell>Contrato</TableCell>
                <TableCell>Resumo</TableCell>
                <TableCell>Detalhes</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map(item => {
                const presentation = auditActionPresentation[item.action];
                return (
                  <TableRow key={item.id} hover>
                    <TableCell>{dateTime(item.occurredAt)}</TableCell>
                    <TableCell>
                      <Typography variant="body2">{item.actorName ?? "Sistema"}</Typography>
                      {item.actorEmail && <Typography variant="caption" color="text.secondary">{item.actorEmail}</Typography>}
                    </TableCell>
                    <TableCell><Chip label={presentation.label} color={presentation.color} size="small" /></TableCell>
                    <TableCell>{auditEntityTypeLabels[item.entityType]}</TableCell>
                    <TableCell>{item.contractId ?? "—"}</TableCell>
                    <TableCell>{item.summary}</TableCell>
                    <TableCell sx={{ maxWidth: 260 }}>
                      {item.details
                        ? <Tooltip title={item.details} arrow>
                            <Typography variant="body2" noWrap>{item.details}</Typography>
                          </Tooltip>
                        : "—"}
                    </TableCell>
                  </TableRow>
                );
              })}
              {items.length === 0 &&
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 8, color: "text.secondary" }}>Nenhum registro encontrado.</TableCell>
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
          rowsPerPageOptions={[20, 50, 100]}
          labelRowsPerPage="Linhas por página"
          labelDisplayedRows={({ from: rowFrom, to: rowTo, count }) => `${rowFrom}–${rowTo} de ${count}`}
        />
      }
    </Paper>
    <Feedback message={feedback.message} error={feedback.error} onClose={() => setFeedback({ message: "", error: false })} />
  </>;
}
