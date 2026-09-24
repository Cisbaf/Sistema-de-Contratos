"use client";

import ConfirmDialog from "@/components/ConfirmDialog";
import LancamentoFormDialog from "@/components/contracts/LancamentoFormDialog";
import PaymentChecklistDialog from "@/components/contracts/PaymentChecklistDialog";
import LancamentoHistoryDialog from "@/components/contracts/LancamentoHistoryDialog";
import { Feedback, PageLoading } from "@/components/Feedback";
import { deleteJson, getJson, postJson, putJson } from "@/lib/api";
import type { Contract, Lancamento, LancamentoRequest } from "@/types";
import AddIcon from "@mui/icons-material/Add";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import DescriptionOutlinedIcon from "@mui/icons-material/DescriptionOutlined";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import HistoryIcon from "@mui/icons-material/History";
import CloseIcon from "@mui/icons-material/Close";
import { Alert, Box, Button, Divider, Drawer, IconButton, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip, Typography } from "@mui/material";
import { useCallback, useEffect, useMemo, useState } from "react";

const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const date = (value: string) => new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`));
const competenciaLabel = (value: string) => `${value.slice(5, 7)}/${value.slice(0, 4)}`;

// Mesma coluna fixa da tabela de contratos: a tabela rola na horizontal e os botões precisam ficar visíveis.
const stickyActions = {
  position: "sticky",
  right: 0,
  zIndex: 2,
  boxShadow: "-8px 0 8px -8px rgba(15, 23, 42, 0.18)",
} as const;

export default function ContractFinancialDrawer({ open, contract, onClose }: {
  open: boolean;
  contract: Contract | null;
  onClose: () => void;
}) {
  const contractId = contract?.id ?? null;

  const [lancamentos, setLancamentos] = useState<Lancamento[]>([]);
  const [saldo, setSaldo] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [feedback, setFeedback] = useState({ message: "", error: false });
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Lancamento | null>(null);
  const [saving, setSaving] = useState(false);
  const [removing, setRemoving] = useState<Lancamento | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [checklistLancamento, setChecklistLancamento] = useState<Lancamento | null>(null);

  const load = useCallback(async () => {
    if (contractId === null) return;
    try {
      const [lancamentosData, saldoData] = await Promise.all([
        getJson<Lancamento[]>(`/contracts/${contractId}/lancamentos`),
        getJson<{ saldo: number }>(`/contracts/${contractId}/lancamentos/saldo`),
      ]);
      setLancamentos(lancamentosData);
      setSaldo(saldoData.saldo);
      setLoadError("");
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Erro ao carregar dados financeiros");
    } finally {
      setLoading(false);
    }
  }, [contractId]);

  // Recarrega toda vez que o painel abre (ou troca de contrato), para nunca mostrar dados de outro contrato.
  useEffect(() => {
    if (!open) return;
    setLoading(true); setLancamentos([]); setSaldo(null);
    void load();
  }, [open, load]);

  const fail = (error: unknown, fallback: string) =>
    setFeedback({ message: error instanceof Error ? error.message : fallback, error: true });

  async function save(payload: LancamentoRequest) {
    setSaving(true);
    try {
      if (editing) await putJson(`/lancamentos/${editing.id}`, payload);
      else await postJson(`/contracts/${contractId}/lancamentos`, payload);
      setFormOpen(false);
      setFeedback({ message: editing ? "Lançamento atualizado" : "Lançamento criado", error: false });
      await load();
    } catch (error) {
      fail(error, "Erro ao salvar lançamento");
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    if (!removing) return;
    try {
      await deleteJson(`/lancamentos/${removing.id}`);
      setRemoving(null);
      setFeedback({ message: "Lançamento excluído", error: false });
      await load();
    } catch (error) {
      setRemoving(null);
      fail(error, "Erro ao excluir lançamento");
      await load();
    }
  }

  // "Valor restante do contrato" de cada linha (spec 7.2): valor global menos as notas até aquela linha, na ordem
  // da competência (desempate pela data de criação). Em centavos inteiros para não acumular erro de ponto flutuante.
  const linhas = useMemo(() => {
    const ordenadas = [...lancamentos].sort((a, b) =>
      a.competencia.localeCompare(b.competencia) || a.criadoEm.localeCompare(b.criadoEm) || a.id - b.id);
    let restanteCentavos = Math.round(Number(contract?.valueGlobal ?? 0) * 100);
    return ordenadas.map(item => {
      restanteCentavos -= Math.round(Number(item.valorNota) * 100);
      return { item, restante: restanteCentavos / 100 };
    });
  }, [lancamentos, contract?.valueGlobal]);

  if (!contract) return null;

  const saldoNegativoOuZero = saldo !== null && saldo <= 0;

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      slotProps={{ paper: { sx: { width: { xs: "100%", md: "85vw" }, maxWidth: 1400 } } }}
    >
      <Stack direction="row" alignItems="center" justifyContent="space-between" px={3} py={2}>
        <Box>
          <Typography variant="h6" fontWeight={700}>Financeiro — contrato {contract.numberContract}</Typography>
          <Typography variant="body2" color="text.secondary">{contract.company}</Typography>
        </Box>
        <Stack direction="row" spacing={1} alignItems="center">
          <Button startIcon={<HistoryIcon />} onClick={() => setHistoryOpen(true)}>Histórico</Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditing(null); setFormOpen(true); }}>
            Novo lançamento
          </Button>
          <IconButton aria-label="Fechar" onClick={onClose}><CloseIcon /></IconButton>
        </Stack>
      </Stack>
      <Divider />

      <Box px={3} py={3} sx={{ overflowY: "auto" }}>
        {loadError && <Alert severity="error" sx={{ mb: 2 }}>{loadError}</Alert>}
        {loading ? <PageLoading /> : <>
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)", md: "repeat(4, 1fr)" }, gap: 2, mb: 3 }}>
      {[
        { label: "Valor global", value: money.format(contract.valueGlobal) },
        { label: "Valor mensal", value: money.format(contract.valueMensal) },
        { label: "Vigência", value: `${date(contract.startDate)} a ${date(contract.endDate)}` },
        { label: "Saldo do contrato", value: saldo === null ? "—" : money.format(saldo), highlight: true },
      ].map(card => (
        <Paper key={card.label} variant="outlined" sx={{ p: 2.5 }}>
          <Typography color="text.secondary" variant="body2">{card.label}</Typography>
          <Typography variant="h6" fontWeight={800} mt={.5}
            color={card.highlight ? (saldoNegativoOuZero ? "error.main" : "success.main") : undefined}>
            {card.value}
          </Typography>
        </Paper>
      ))}
    </Box>

    <Paper variant="outlined" sx={{ overflow: "hidden" }}>
      <TableContainer>
        <Table sx={{ minWidth: 1150 }}>
          <TableHead>
            <TableRow>
              <TableCell>Competência</TableCell>
              <TableCell>Nota fiscal</TableCell>
              <TableCell>Processo</TableCell>
              <TableCell>Parcela</TableCell>
              <TableCell align="right">Valor da NF</TableCell>
              <TableCell align="right">Valor restante do contrato</TableCell>
              <TableCell>Observações</TableCell>
              <TableCell>Lançado por</TableCell>
              <TableCell align="right" sx={stickyActions}>Ações</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {linhas.map(({ item, restante }) => (
              <TableRow key={item.id} hover>
                <TableCell>{competenciaLabel(item.competencia)}</TableCell>
                <TableCell><Typography fontWeight={700}>{item.notaFiscal}</Typography></TableCell>
                <TableCell>{item.numeroProcesso}</TableCell>
                <TableCell>{item.parcela || "—"}</TableCell>
                <TableCell align="right">{money.format(item.valorNota)}</TableCell>
                <TableCell align="right">{money.format(restante)}</TableCell>
                <TableCell sx={{ maxWidth: 260, wordBreak: "break-word" }}>{item.observacoes || "—"}</TableCell>
                <TableCell>{item.criadoPor?.name ?? "—"}</TableCell>
                <TableCell align="right" sx={{ ...stickyActions, bgcolor: "background.paper" }}>
                  <Stack direction="row" spacing={0.25} justifyContent="flex-end">
                    <Tooltip title="Gerar checklist (ateste)">
                      <span>
                        <IconButton aria-label="Gerar checklist" onClick={() => setChecklistLancamento(item)}>
                          <DescriptionOutlinedIcon />
                        </IconButton>
                      </span>
                    </Tooltip>
                    <Tooltip title="Editar">
                      <IconButton aria-label="Editar" onClick={() => { setEditing(item); setFormOpen(true); }}>
                        <EditOutlinedIcon />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Excluir">
                      <IconButton aria-label="Excluir" color="error" onClick={() => setRemoving(item)}>
                        <DeleteOutlineIcon />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {lancamentos.length === 0 && (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ py: 8, color: "text.secondary" }}>
                  Nenhum lançamento neste contrato.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
        </>}
      </Box>

    <LancamentoFormDialog open={formOpen} lancamento={editing} saving={saving} onClose={() => setFormOpen(false)} onSubmit={payload => void save(payload)} />
    <PaymentChecklistDialog
      open={Boolean(checklistLancamento)}
      lancamento={checklistLancamento}
      onClose={() => setChecklistLancamento(null)}
      onGenerated={message => setFeedback({ message, error: false })}
    />
    <LancamentoHistoryDialog open={historyOpen} contractId={contract.id} onClose={() => setHistoryOpen(false)} />
    <ConfirmDialog
      open={Boolean(removing)}
      title="Excluir lançamento?"
      text={`A nota fiscal ${removing?.notaFiscal ?? ""} (${removing ? money.format(removing.valorNota) : ""}) será removida e o valor volta para o saldo do contrato. Se ela já foi editada antes, a exclusão fica registrada no histórico e o número da nota não pode ser reutilizado.`}
      onClose={() => setRemoving(null)}
      onConfirm={() => void remove()}
    />
    <Feedback message={feedback.message} error={feedback.error} onClose={() => setFeedback({ message: "", error: false })} />
    </Drawer>
  );
}
