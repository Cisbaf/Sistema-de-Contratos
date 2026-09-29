"use client";

import { useAuth } from "@/components/DashboardShell";
import { Feedback, PageLoading } from "@/components/Feedback";
import PageHeader from "@/components/PageHeader";
import { getJson, putJson } from "@/lib/api";
import type { NotificationSettings } from "@/types";
import { Alert, Box, Button, Paper, Stack, TextField, Typography } from "@mui/material";
import { FormEvent, useEffect, useState } from "react";

export default function NotificationSettingsPage() {
  const auth = useAuth();
  const canEdit = Boolean(auth.admin);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState({ message: "", error: false });

  const [firstAlertMonths, setFirstAlertMonths] = useState("6");
  const [secondAlertMonths, setSecondAlertMonths] = useState("4");

  function fill(settings: NotificationSettings) {
    setFirstAlertMonths(String(settings.firstAlertMonths));
    setSecondAlertMonths(String(settings.secondAlertMonths));
  }

  async function load() {
    try { fill(await getJson<NotificationSettings>("/notificacoes/parametros")); }
    catch (error) { setFeedback({ message: error instanceof Error ? error.message : "Erro ao carregar parâmetros", error: true }); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    try {
      const settings = await putJson<NotificationSettings>("/notificacoes/parametros", {
        firstAlertMonths: Number(firstAlertMonths),
        secondAlertMonths: Number(secondAlertMonths),
      });
      fill(settings);
      setFeedback({ message: "Parâmetros salvos", error: false });
    } catch (error) {
      setFeedback({ message: error instanceof Error ? error.message : "Erro ao salvar parâmetros", error: true });
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <PageLoading />;

  return <>
    <PageHeader title="Parâmetros de notificação" subtitle="Prazos de alerta dos e-mails automáticos (M6-40)." />
    <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 }, maxWidth: 640 }} component="form" onSubmit={submit}>
      <Stack spacing={3}>
        {!canEdit && <Alert severity="info">Você pode consultar os parâmetros, mas só o Administrador pode alterá-los.</Alert>}

        <Box>
          <Typography variant="subtitle1" fontWeight={700} mb={.5}>Prazos de alerta</Typography>
          <Typography variant="body2" color="text.secondary" mb={2}>
            Quantos meses antes do fim da vigência cada alerta é disparado. Vale a partir do próximo disparo do job diário, sem precisar reiniciar o backend.
          </Typography>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField label="Primeiro alerta (meses)" type="number" value={firstAlertMonths}
              onChange={e => setFirstAlertMonths(e.target.value)} disabled={!canEdit} fullWidth
              slotProps={{ htmlInput: { min: 1 } }} />
            <TextField label="Segundo alerta (meses)" type="number" value={secondAlertMonths}
              onChange={e => setSecondAlertMonths(e.target.value)} disabled={!canEdit} fullWidth
              slotProps={{ htmlInput: { min: 1 } }} />
          </Stack>
        </Box>

        {canEdit && <Stack direction="row" justifyContent="flex-end">
          <Button type="submit" variant="contained" size="large" disabled={saving}>
            {saving ? "Salvando..." : "Salvar"}
          </Button>
        </Stack>}
      </Stack>
    </Paper>

    <Feedback message={feedback.message} error={feedback.error} onClose={() => setFeedback({ message: "", error: false })} />
  </>;
}
