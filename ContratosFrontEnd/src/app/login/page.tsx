"use client";

import LockOutlinedIcon from "@mui/icons-material/LockOutlined";
import { Alert, Avatar, Box, Button, CircularProgress, Paper, Stack, TextField, Typography } from "@mui/material";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { postJson } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setLoading(true); setError("");
    try {
      await postJson("/auth/login", { username: form.get("username"), password: form.get("password") });
      router.replace("/dashboard/contracts");
      router.refresh();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Não foi possível entrar");
    } finally { setLoading(false); }
  }

  return <Box sx={{ minHeight: "100vh", display: "grid", placeItems: "center", p: 2,
    background: "radial-gradient(circle at top left, #DCE8FF 0, transparent 38%), #F4F7FB" }}>
    <Paper component="form" onSubmit={submit} elevation={0} sx={{ width: "100%", maxWidth: 430, p: { xs: 3, sm: 5 }, border: "1px solid #E4EAF2" }}>
      <Stack spacing={3} alignItems="stretch">
        <Avatar sx={{ bgcolor: "primary.main", width: 52, height: 52, alignSelf: "center" }}><LockOutlinedIcon /></Avatar>
        <Box textAlign="center"><Typography variant="h4">Controle de Contratos</Typography><Typography color="text.secondary" mt={1}>Acesse sua área de gestão CISBAF</Typography></Box>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField name="username" label="E-mail" type="email" autoComplete="username" required autoFocus fullWidth />
        <TextField name="password" label="Senha" type="password" autoComplete="current-password" required fullWidth />
        <Button type="submit" variant="contained" size="large" disabled={loading}>{loading ? <CircularProgress size={24} color="inherit" /> : "Entrar"}</Button>
      </Stack>
    </Paper>
  </Box>;
}
