"use client";

import { getJson } from "@/lib/api";
import type { LancamentoHistorico } from "@/types";
import { Alert, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from "@mui/material";
import { useEffect, useState } from "react";
import { PageLoading } from "@/components/Feedback";

const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const competencia = (value: string) => `${value.slice(5, 7)}/${value.slice(0, 4)}`;
const dateTime = (value: string) => new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));

export default function LancamentoHistoryDialog({ open, contractId, onClose }: {
  open: boolean;
  contractId: number;
  onClose: () => void;
}) {
  const [items, setItems] = useState<LancamentoHistorico[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!open) return;
    setLoading(true); setError("");
    getJson<LancamentoHistorico[]>(`/contracts/${contractId}/lancamentos/historico`)
      .then(setItems)
      .catch(e => setError(e instanceof Error ? e.message : "Erro ao carregar histórico"))
      .finally(() => setLoading(false));
  }, [open, contractId]);

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
      <DialogTitle>Histórico de alterações</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" mb={2}>
          Cada linha mostra o lançamento como ele estava <strong>antes</strong> da edição ou exclusão.
        </Typography>
        {error && <Alert severity="error">{error}</Alert>}
        {loading ? <PageLoading /> : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Quando</TableCell>
                  <TableCell>Evento</TableCell>
                  <TableCell>Por</TableCell>
                  <TableCell>Nota fiscal</TableCell>
                  <TableCell>Processo</TableCell>
                  <TableCell>Competência</TableCell>
                  <TableCell>Parcela</TableCell>
                  <TableCell align="right">Valor (antes)</TableCell>
                  <TableCell>Observações</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {items.map(item => (
                  <TableRow key={item.id}>
                    <TableCell>{dateTime(item.alteradoEm)}</TableCell>
                    <TableCell>
                      <Chip size="small" label={item.tipoEvento === "EXCLUSAO" ? "Exclusão" : "Edição"}
                        color={item.tipoEvento === "EXCLUSAO" ? "error" : "info"} />
                    </TableCell>
                    <TableCell>{item.alteradoPor?.name ?? "—"}</TableCell>
                    <TableCell>{item.notaFiscal}</TableCell>
                    <TableCell>{item.numeroProcesso}</TableCell>
                    <TableCell>{competencia(item.competencia)}</TableCell>
                    <TableCell>{item.parcela || "—"}</TableCell>
                    <TableCell align="right">{money.format(item.valorNota)}</TableCell>
                    <TableCell sx={{ maxWidth: 240, wordBreak: "break-word" }}>{item.observacoes || "—"}</TableCell>
                  </TableRow>
                ))}
                {items.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={9} align="center" sx={{ py: 6, color: "text.secondary" }}>
                      Nenhuma alteração registrada.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Fechar</Button></DialogActions>
    </Dialog>
  );
}
