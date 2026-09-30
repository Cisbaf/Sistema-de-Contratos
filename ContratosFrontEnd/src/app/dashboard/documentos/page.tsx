"use client";

import { Feedback, PageLoading } from "@/components/Feedback";
import PageHeader from "@/components/PageHeader";
import { downloadFile, getJson } from "@/lib/api";
import { TEMPLATE_TYPE_LABELS } from "@/lib/templatePlaceholders";
import type { DocumentTemplateType, GeneratedDocument, Page } from "@/types";
import DownloadIcon from "@mui/icons-material/Download";
import {
  IconButton, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TablePagination, TableRow, TextField, Tooltip, Typography,
} from "@mui/material";
import { useEffect, useState } from "react";

const dateTime = (value: string) => new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));

const documentTypeOptions: DocumentTemplateType[] = ["INTEREST_EMAIL", "TECHNICAL_OPINION", "SUPPLIER_RENEWAL_EMAIL", "PAYMENT_CHECKLIST"];

// Só dígitos: contractId/authorId são ids numéricos, sem seletor por nome ainda (mesmo padrão da auditoria).
const onlyDigits = (value: string) => value.replace(/\D/g, "");

export default function DocumentosPage() {
  const [data, setData] = useState<Page<GeneratedDocument> | null>(null);
  const [loading, setLoading] = useState(true);
  const [feedback, setFeedback] = useState({ message: "", error: false });

  const [contractId, setContractId] = useState("");
  const [documentType, setDocumentType] = useState<DocumentTemplateType | "">("");
  const [dataInicio, setDataInicio] = useState("");
  const [dataFim, setDataFim] = useState("");
  const [authorId, setAuthorId] = useState("");

  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);

  useEffect(() => {
    setLoading(true);
    const params = new URLSearchParams({ page: String(page), size: String(rowsPerPage) });
    if (contractId) params.set("contractId", contractId);
    if (documentType) params.set("documentType", documentType);
    if (dataInicio) params.set("dataInicio", dataInicio);
    if (dataFim) params.set("dataFim", dataFim);
    if (authorId) params.set("authorId", authorId);

    getJson<Page<GeneratedDocument>>(`/generate-document?${params.toString()}`)
      .then(setData)
      .catch(error => setFeedback({ message: error instanceof Error ? error.message : "Erro ao carregar os documentos", error: true }))
      .finally(() => setLoading(false));
  }, [contractId, documentType, dataInicio, dataFim, authorId, page, rowsPerPage]);

  const items = data?.content ?? [];

  async function baixar(doc: GeneratedDocument) {
    try {
      await downloadFile(`/generate-document/download?documentId=${doc.id}`, doc.fileName);
    } catch (error) {
      setFeedback({ message: error instanceof Error ? error.message : "Erro ao baixar o documento", error: true });
    }
  }

  return <>
    <PageHeader title="Documentos" subtitle="Todos os documentos gerados pelo sistema, de qualquer contrato." />

    <Paper variant="outlined" sx={{ overflow: "hidden" }}>
      {/* A busca é no servidor: qualquer filtro volta pra página 0, senão a
          página atual pode pedir um resultado que não existe mais. */}
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} p={2} flexWrap="wrap" useFlexGap>
        <TextField label="ID do contrato" value={contractId}
          onChange={event => { setContractId(onlyDigits(event.target.value)); setPage(0); }}
          sx={{ minWidth: { sm: 140 } }} />
        <TextField select label="Tipo" value={documentType}
          onChange={event => { setDocumentType(event.target.value as DocumentTemplateType | ""); setPage(0); }}
          sx={{ minWidth: { sm: 200 } }}>
          <MenuItem value="">Todos</MenuItem>
          {documentTypeOptions.map(option => <MenuItem key={option} value={option}>{TEMPLATE_TYPE_LABELS[option]}</MenuItem>)}
        </TextField>
        <TextField label="De" type="date" value={dataInicio}
          onChange={event => { setDataInicio(event.target.value); setPage(0); }}
          slotProps={{ inputLabel: { shrink: true } }} sx={{ minWidth: { sm: 160 } }} />
        <TextField label="Até" type="date" value={dataFim}
          onChange={event => { setDataFim(event.target.value); setPage(0); }}
          slotProps={{ inputLabel: { shrink: true } }} sx={{ minWidth: { sm: 160 } }} />
        <TextField label="ID do usuário (autor)" value={authorId}
          onChange={event => { setAuthorId(onlyDigits(event.target.value)); setPage(0); }}
          sx={{ minWidth: { sm: 160 } }} />
      </Stack>

      {loading ? <PageLoading /> :
        <TableContainer>
          <Table sx={{ minWidth: 900 }}>
            <TableHead>
              <TableRow>
                <TableCell>Contrato</TableCell>
                <TableCell>Tipo</TableCell>
                <TableCell>Arquivo</TableCell>
                <TableCell>Versão</TableCell>
                <TableCell>Gerado por</TableCell>
                <TableCell>Data/hora</TableCell>
                <TableCell align="right">Ações</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map(doc =>
                <TableRow key={doc.id} hover>
                  <TableCell>{doc.contractId}</TableCell>
                  <TableCell>{TEMPLATE_TYPE_LABELS[doc.documentType]}</TableCell>
                  <TableCell>{doc.fileName}</TableCell>
                  <TableCell>{doc.version}</TableCell>
                  <TableCell>
                    <Typography variant="body2">{doc.generatedBy.name}</Typography>
                  </TableCell>
                  <TableCell>{dateTime(doc.generatedAt)}</TableCell>
                  <TableCell align="right">
                    <Tooltip title="Baixar">
                      <IconButton onClick={() => baixar(doc)}><DownloadIcon /></IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>)}
              {items.length === 0 &&
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 8, color: "text.secondary" }}>Nenhum documento encontrado.</TableCell>
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
