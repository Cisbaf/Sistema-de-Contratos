"use client";

import type { Lancamento, LancamentoRequest } from "@/types";
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField } from "@mui/material";
import { useEffect, useState } from "react";

// Máscara MM/AAAA (ano com 2 ou 4 dígitos): aceita só dígitos e põe a barra sozinha. Não usa <input type="month">
// porque o Firefox desktop não suporta e o valor digitado chegava ao backend fora do formato.
const maskCompetencia = (value: string) => {
  const digits = value.replace(/\D/g, "").slice(0, 6);
  return digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits;
};
// Aceita ano com 2 dígitos (ex. "10/26") ou 4 dígitos (ex. "10/2026") — o de 2 dígitos vira 20XX na hora de mandar pro backend.
const competenciaValida = (value: string) => {
  const match = /^(0[1-9]|1[0-2])\/(\d{2}|\d{4})$/.exec(value);
  if (!match) return false;
  const ano = match[2].length === 2 ? Number(`20${match[2]}`) : Number(match[2]);
  return ano >= 2000;
};
// Só dígitos: Parcela é um número (1, 2...), não texto livre.
const onlyDigits = (value: string) => value.replace(/\D/g, "");

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
      competencia: `${lancamento.competencia.slice(5, 7)}/${lancamento.competencia.slice(0, 4)}`, // yyyy-MM-dd -> MM/AAAA
      valorNota: String(lancamento.valorNota),
      observacoes: lancamento.observacoes ?? "",
    } : empty);
  }, [open, lancamento]);

  const set = (field: keyof typeof empty) => (event: React.ChangeEvent<HTMLInputElement>) =>
    setForm(current => ({ ...current, [field]: event.target.value }));

  const valor = Number(form.valorNota.replace(",", "."));
  const valid = form.numeroProcesso.trim() && form.notaFiscal.trim() && competenciaValida(form.competencia) && valor > 0;
  // "Completa" pros dois formatos aceitos: MM/AA (5 chars) ou MM/AAAA (7 chars) — enquanto o ano tá "no meio"
  // (3 ou 4 dígitos digitados, comprimento 4 ou 6) não mostra erro, senão o campo pisca vermelho a cada tecla.
  const competenciaCompleta = form.competencia.length === 5 || form.competencia.length === 7;

  function submit() {
    const anoDigitado = form.competencia.slice(3);
    const ano = anoDigitado.length === 2 ? `20${anoDigitado}` : anoDigitado; // "26" -> "2026"
    onSubmit({
      numeroProcesso: form.numeroProcesso.trim(),
      notaFiscal: form.notaFiscal.trim(),
      parcela: form.parcela.trim() || null,
      competencia: `${ano}-${form.competencia.slice(0, 2)}-01`, // MM/AAAA -> yyyy-MM-01
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
            <TextField label="Parcela" value={form.parcela}
              onChange={event => setForm(current => ({ ...current, parcela: onlyDigits(event.target.value) }))}
              fullWidth slotProps={{ htmlInput: { inputMode: "numeric" } }} />
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField label="Competência" placeholder="MM/AA ou MM/AAAA" value={form.competencia}
              onChange={event => setForm(current => ({ ...current, competencia: maskCompetencia(event.target.value) }))}
              required fullWidth
              error={competenciaCompleta && !competenciaValida(form.competencia)}
              helperText={competenciaCompleta && !competenciaValida(form.competencia) ? "Use um mês de 01 a 12" : undefined}
              slotProps={{ htmlInput: { inputMode: "numeric", maxLength: 7 } }} />
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
