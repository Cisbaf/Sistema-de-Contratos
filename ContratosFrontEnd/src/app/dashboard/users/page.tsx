"use client";

import ConfirmDialog from "@/components/ConfirmDialog";
import { useAuth } from "@/components/DashboardShell";
import { Feedback, PageLoading } from "@/components/Feedback";
import PageHeader from "@/components/PageHeader";
import { deleteJson, getJson, postJson, putJson } from "@/lib/api";
import type { Sector, User } from "@/types";
import AdminPanelSettingsOutlinedIcon from "@mui/icons-material/AdminPanelSettingsOutlined";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import SearchIcon from "@mui/icons-material/Search";
import { Avatar, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, InputAdornment, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from "@mui/material";
import { FormEvent, useEffect, useMemo, useState } from "react";

type Perfil = "ADMIN" | "CONTROLE_INTERNO" | "FISCAL";
type UserForm = { name: string; email: string; cellPhone: string; sectorId: number | ""; password: string; admin: boolean; perfil: Perfil };
const emptyForm: UserForm = { name: "", email: "", cellPhone: "", sectorId: "", password: "", admin: false, perfil: "FISCAL" };
const perfilLabel: Record<Perfil, string> = { ADMIN: "Administrador", CONTROLE_INTERNO: "Controle interno", FISCAL: "Fiscal" };

export default function UsersPage() {
  const auth = useAuth();
  const [users, setUsers] = useState<User[]>([]);
  const [sectors, setSectors] = useState<Sector[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [form, setForm] = useState<UserForm>(emptyForm);
  const [editing, setEditing] = useState<User | null>(null);
  const [open, setOpen] = useState(false);
  const [removing, setRemoving] = useState<User | null>(null);
  const [feedback, setFeedback] = useState({ message: "", error: false });

  async function load() {
    try {
      const [userData, sectorData] = await Promise.all([getJson<User[]>("/users"), getJson<Sector[]>("/sectors")]);
      setUsers(userData);
      setSectors(sectorData);
    }
    catch (error) { setFeedback({ message: error instanceof Error ? error.message : "Erro ao carregar usuários", error: true }); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  const filtered = useMemo(() => {
    const term = search.toLocaleLowerCase("pt-BR");
    return users.filter(user => [user.name, user.email, user.cellPhone ?? "", user.sector?.name ?? ""]
      .some(value => value.toLocaleLowerCase("pt-BR").includes(term)));
  }, [search, users]);

  function field<K extends keyof UserForm>(key: K, value: UserForm[K]) { setForm(current => ({ ...current, [key]: value })); }

  function create() { setEditing(null); setForm(emptyForm); setOpen(true); }

  function edit(user: User) {
    const perfil = user.perfil ?? (user.admin ? "ADMIN" : "FISCAL");
    setEditing(user);
    setForm({
      name: user.name, email: user.email, cellPhone: user.cellPhone ?? "", sectorId: user.sector?.id ?? "",
      password: "", admin: perfil === "ADMIN", perfil
    }); setOpen(true);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    try {
      const payload = { ...form, admin: form.perfil === "ADMIN" };
      if (editing) await putJson(`/users/${editing.id}`, payload);
      else await postJson("/users", payload);
      setOpen(false);
      setFeedback({ message: editing ? "Usuário atualizado" : "Usuário criado", error: false });
      await load();
    }
    catch (error) { setFeedback({ message: error instanceof Error ? error.message : "Erro ao salvar usuário", error: true }); }
  }
  async function remove() {
    if (!removing) return;
    try { await deleteJson(`/users/${removing.id}`); setRemoving(null); setFeedback({ message: "Usuário excluído", error: false }); await load(); }
    catch (error) { setFeedback({ message: error instanceof Error ? error.message : "Erro ao excluir usuário", error: true }); }
  }

  return <>
    <PageHeader title="Usuários" subtitle="Gerencie responsáveis e seus setores de atuação." action={auth.admin ? "Adicionar usuário" : undefined}
      onAction={create} />
    <Paper variant="outlined" sx={{ overflow: "hidden" }}>
      <Box p={2}><TextField value={search} onChange={e => setSearch(e.target.value)}
        placeholder="Buscar por nome, e-mail ou setor" fullWidth
        slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> } }} /></Box>
      {loading ? <PageLoading /> : <TableContainer>
        <Table sx={{ minWidth: 760 }}>
          <TableHead>
            <TableRow>
              <TableCell>Usuário</TableCell>
              <TableCell>Contato</TableCell>
              <TableCell>Setor</TableCell>
              <TableCell>Perfil</TableCell>
              {auth.admin && <TableCell align="right">Ações</TableCell>}
            </TableRow>
          </TableHead>
          <TableBody>
            {filtered.map(user =>
              <TableRow key={user.id} hover>
                <TableCell>
                  <Stack direction="row" spacing={1.5} alignItems="center">
                    <Avatar>{user.name.charAt(0)}</Avatar>
                    <Box>
                      <Typography fontWeight={700}>{user.name}</Typography>
                      <Typography variant="caption" color="text.secondary">{user.email}</Typography>
                    </Box>
                  </Stack>
                </TableCell>
                <TableCell>{user.cellPhone || "—"}</TableCell>
                <TableCell><Chip label={user.sector?.name ?? "Sem setor"} size="small" variant="outlined" /></TableCell>
                <TableCell>
                  {user.perfil === "ADMIN" || user.admin ? <Chip icon={<AdminPanelSettingsOutlinedIcon />}
                    label={perfilLabel[user.perfil ?? "ADMIN"]}
                    size="small" color="primary" /> : <Chip label={perfilLabel[user.perfil] ?? "Usuário"} size="small" />}
                </TableCell>
                {auth.admin &&
                  <TableCell align="right">
                    <IconButton onClick={() => edit(user)}><EditOutlinedIcon /></IconButton>
                    <IconButton color="error" onClick={() => setRemoving(user)}><DeleteOutlineIcon /></IconButton>
                  </TableCell>
                }
              </TableRow>)}
            {filtered.length === 0 && <TableRow><TableCell colSpan={5} align="center" sx={{ py: 8, color: "text.secondary" }}>Nenhum usuário encontrado.</TableCell></TableRow>}
          </TableBody></Table></TableContainer>}
    </Paper>

    <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>{editing ? "Editar Usuário" : "Adicionar Usuário"}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} pt={1}>
          <TextField label="Nome completo" value={form.name} onChange={e => field("name", e.target.value)} required autoFocus />
          <TextField label="E-mail" value={form.email} onChange={e => field("email", e.target.value)} type="email" required />
          <TextField label="Celular" value={form.cellPhone} onChange={e => field("cellPhone", e.target.value)} />
          <TextField select label="Setor" value={form.sectorId} onChange={e => field("sectorId", Number(e.target.value))} required>{sectors.map(sector => <MenuItem key={sector.id} value={sector.id}>{sector.name}</MenuItem>)}</TextField>
          <TextField label={editing ? "Nova senha (opcional)" : "Senha"} value={form.password} onChange={e => field("password", e.target.value)} type="password" required={!editing} helperText={editing ? "Deixe em branco para manter a senha atual" : "Mínimo de 6 caracteres"} />
          <TextField select label="Perfil" value={form.perfil} onChange={e => { const perfil = e.target.value as Perfil; setForm(current => ({ ...current, perfil, admin: perfil === "ADMIN" })); }}>
            {(Object.keys(perfilLabel) as Perfil[]).map(perfil => <MenuItem key={perfil} value={perfil}>{perfilLabel[perfil]}</MenuItem>)}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 3 }}>
        <Button onClick={() => setOpen(false)}>Cancelar</Button>
        <Button type="submit" variant="contained">Salvar Usuário</Button>
      </DialogActions>
    </Dialog>
    <ConfirmDialog open={Boolean(removing)} title="Excluir usuário?"
      text={`${removing?.name ?? "Este usuário"} será removido. Usuários vinculados a contratos não podem ser excluídos.`}
      onClose={() => setRemoving(null)} onConfirm={remove} />
    <Feedback message={feedback.message} error={feedback.error} onClose={() => setFeedback({ message: "", error: false })} />
  </>;
}
