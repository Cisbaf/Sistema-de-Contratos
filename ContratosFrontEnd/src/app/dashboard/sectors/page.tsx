"use client";

import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import { Accordion, AccordionDetails, AccordionSummary, Avatar, AvatarGroup, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Paper, Stack, TextField, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { FormEvent, useEffect, useState } from "react";
import ConfirmDialog from "@/components/ConfirmDialog";
import { useAuth } from "@/components/DashboardShell";
import { Feedback, PageLoading } from "@/components/Feedback";
import PageHeader from "@/components/PageHeader";
import { deleteJson, getJson, postJson, putJson } from "@/lib/api";
import type { Sector, User } from "@/types";

export default function SectorsPage() {
  const auth = useAuth();
  const [sectors, setSectors] = useState<Sector[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Sector | null>(null);
  const [name, setName] = useState("");
  const [removing, setRemoving] = useState<Sector | null>(null);
  const [feedback, setFeedback] = useState({ message: "", error: false });

  async function load() {
    try { const [sectorData, userData] = await Promise.all([getJson<Sector[]>("/sectors"), getJson<User[]>("/users")]); setSectors(sectorData); setUsers(userData); }
    catch (error) { setFeedback({ message: error instanceof Error ? error.message : "Erro ao carregar setores", error: true }); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);
  function create() { setEditing(null); setName(""); setOpen(true); }
  function edit(sector: Sector) { setEditing(sector); setName(sector.name); setOpen(true); }
  async function submit(event: FormEvent) {
    event.preventDefault();
    try { if (editing) await putJson(`/sectors/${editing.id}`, { name }); else await postJson("/sectors", { name }); setOpen(false); setFeedback({ message: editing ? "Setor renomeado" : "Setor criado", error: false }); await load(); }
    catch (error) { setFeedback({ message: error instanceof Error ? error.message : "Erro ao salvar setor", error: true }); }
  }
  async function remove() {
    if (!removing) return;
    try { await deleteJson(`/sectors/${removing.id}`); setRemoving(null); setFeedback({ message: "Setor excluído", error: false }); await load(); }
    catch (error) { setFeedback({ message: error instanceof Error ? error.message : "Erro ao excluir setor", error: true }); }
  }

  return <>
    <PageHeader title="Setores" subtitle="Organize os fiscais por área responsável." action={auth.admin ? "Novo setor" : undefined} onAction={create} />
    {loading ? <PageLoading /> : <Paper variant="outlined" sx={{ p: { xs: 1, sm: 2 } }}>{sectors.map(sector => {
      const members = users.filter(user => user.sector?.id === sector.id);
      return <Accordion key={sector.id} disableGutters elevation={0} sx={{ border: "1px solid #E4EAF2", "&:not(:last-child)": { mb: 1 }, "&:before": { display: "none" }, borderRadius: "10px !important", overflow: "hidden" }}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}><Stack direction="row" alignItems="center" spacing={2} width="100%" pr={1}><Box flex={1}><Typography fontWeight={750}>{sector.name}</Typography><Typography variant="caption" color="text.secondary">{members.length} fiscal(is)</Typography></Box><AvatarGroup max={4} sx={{ "& .MuiAvatar-root": { width: 30, height: 30, fontSize: 13 } }}>{members.map(member => <Avatar key={member.id}>{member.name.charAt(0)}</Avatar>)}</AvatarGroup></Stack></AccordionSummary>
        <AccordionDetails sx={{ bgcolor: "#FAFBFD" }}><Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={2}><Box><Typography variant="caption" color="text.secondary">Fiscais do setor</Typography><Stack direction="row" gap={1} flexWrap="wrap" mt={1}>{members.length ? members.map(member => <Chip key={member.id} label={member.name} />) : <Typography variant="body2" color="text.secondary">Nenhum fiscal vinculado.</Typography>}</Stack></Box>{auth.admin && <Stack direction="row" alignSelf="flex-end"><IconButton onClick={() => edit(sector)}><EditOutlinedIcon /></IconButton><IconButton color="error" onClick={() => setRemoving(sector)}><DeleteOutlineIcon /></IconButton></Stack>}</Stack></AccordionDetails>
      </Accordion>;
    })}{sectors.length === 0 && <Box textAlign="center" py={8}><Typography color="text.secondary">Nenhum setor cadastrado.</Typography></Box>}</Paper>}

    <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs" component="form" onSubmit={submit}><DialogTitle>{editing ? "Renomear setor" : "Novo setor"}</DialogTitle><DialogContent><TextField label="Nome do setor" value={name} onChange={e => setName(e.target.value)} required autoFocus fullWidth sx={{ mt: 1 }} /></DialogContent><DialogActions sx={{ p: 3 }}><Button onClick={() => setOpen(false)}>Cancelar</Button><Button type="submit" variant="contained">Salvar</Button></DialogActions></Dialog>
    <ConfirmDialog open={Boolean(removing)} title="Excluir setor?" text={`O setor ${removing?.name ?? ""} será excluído. Setores com fiscais vinculados não podem ser removidos.`} onClose={() => setRemoving(null)} onConfirm={remove} />
    <Feedback message={feedback.message} error={feedback.error} onClose={() => setFeedback({ message: "", error: false })} />
  </>;
}
