"use client";

import type { Lancamento, LancamentoRequest } from "@/types";
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField } from "@mui/material";
import { useEffect, useState } from "react";

const empty = { numeroProcesso: "", notaFiscal: "", parcela: "", competencia: "", valorNota: "", observacoes: "" };

export default function LancamentoFormDialog({ open, lancamento, saving, onClose, onSubmit }: {
  open: boolean;
  lancamento: Lancamento | null;
  saving: boolean;
  onClose: () => void;
  onSubmit: (payload: LancamentoRequest) => void;
}) {
  const [form, setForm] = useState(empty);

  useEffect(() => {
    if (!open) return;
    setForm(lancamento ? {
      numeroProcesso: lancamento.numeroProcesso,
      notaFiscal: lancamento.notaFiscal,
      parcela: lancamento.parcela ?? "",
      competencia: lancamento.competencia.slice(0, 7), // yyyy-MM, formato do input type="month"
      valorNota: String(lancamento.valorNota),
      observacoes: lancamento.observacoes ?? "",
    } : empty);
  }, [open, lancamento]);

  const set = (field: keyof typeof empty) => (event: React.ChangeEvent<HTMLInputElement>) =>
    setForm(current => ({ ...current, [field]: event.target.value }));

  const valor = Number(form.valorNota.replace(",", "."));
  const valid = form.numeroProcesso.trim() && form.notaFiscal.trim() && form.competencia && valor > 0;

  function submit() {
    onSubmit({
      numeroProcesso: form.numeroProcesso.trim(),
      notaFiscal: form.notaFiscal.trim(),
      parcela: form.parcela.trim() || null,
      competencia: `${form.competencia}-01`,
      valorNota: valor,
      observacoes: form.observacoes.trim() || null,
    });
  }

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle>{lancamento ? "Editar lançamento" : "Novo lançamento"}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} pt={1}>
          <TextField label="Nº do processo" value={form.numeroProcesso} onChange={set("numeroProcesso")} required fullWidth />
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField label="Nota fiscal" value={form.notaFiscal} onChange={set("notaFiscal")} required fullWidth />
            <TextField label="Parcela" value={form.parcela} onChange={set("parcela")} fullWidth />
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField label="Competência" type="month" value={form.competencia} onChange={set("competencia")}
              required fullWidth slotProps={{ inputLabel: { shrink: true } }} />
            <TextField label="Valor da nota (R$)" type="number" value={form.valorNota} onChange={set("valorNota")}
              required fullWidth slotProps={{ htmlInput: { min: 0, step: "0.01" } }} />
          </Stack>
          <TextField label="Observações" value={form.observacoes} onChange={set("observacoes")} multiline minRows={2} fullWidth />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Cancelar</Button>
        <Button onClick={submit} variant="contained" disabled={!valid || saving}>{saving ? "Salvando..." : "Salvar"}</Button>
      </DialogActions>
    </Dialog>
  );
}
