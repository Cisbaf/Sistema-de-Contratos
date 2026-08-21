"use client";

import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import SearchIcon from "@mui/icons-material/Search";
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, IconButton, InputAdornment, InputLabel, MenuItem, OutlinedInput, Paper, Select, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Tooltip, Typography } from "@mui/material";
import { FormEvent, useEffect, useMemo, useState } from "react";
import ConfirmDialog from "@/components/ConfirmDialog";
import { Feedback, PageLoading } from "@/components/Feedback";
import PageHeader from "@/components/PageHeader";
import { useAuth } from "@/components/DashboardShell";
import { deleteJson, getJson, postJson, putJson } from "@/lib/api";
import type { Contract, User } from "@/types";

type ContractForm = {
  numberContract: string; numberProcess: string; object: string; company: string; cnpjCpf: string;
  valueGlobal: string; valueMensal: string; startDate: string; endDate: string; font: string; ta: string; fiscalIds: number[];
};

const today = () => new Date().toISOString().slice(0, 10);
const emptyForm = (): ContractForm => ({ numberContract: "", numberProcess: "", object: "", company: "", cnpjCpf: "", valueGlobal: "", valueMensal: "", startDate: today(), endDate: today(), font: "", ta: "", fiscalIds: [] });
const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const date = (value: string) => new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`));

export default function ContractsPage() {
  const auth = useAuth();
  const canManageContracts = auth.perfil === "ADMIN" || auth.perfil === "CONTROLE_INTERNO" || (!auth.perfil && Boolean(auth.admin));
  const isAdmin = auth.perfil === "ADMIN" || (!auth.perfil && Boolean(auth.admin));
  const [contracts, setContracts] = useState<Contract[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Contract | null>(null);
  const [form, setForm] = useState<ContractForm>(emptyForm());
  const [removing, setRemoving] = useState<Contract | null>(null);
  const [feedback, setFeedback] = useState({ message: "", error: false });

  async function load() {
    try {
      const contractPath = canManageContracts ? "/contracts" : "/contracts/mine";
      const [contractData, userData] = await Promise.all([
        getJson<Contract[]>(contractPath),
        canManageContracts ? getJson<User[]>("/users") : Promise.resolve([] as User[]),
      ]);
      setContracts(contractData); setUsers(userData);
    } catch (error) { setFeedback({ message: error instanceof Error ? error.message : "Erro ao carregar dados", error: true }); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase("pt-BR");
    if (!term) return contracts;
    return contracts.filter(item => [item.numberContract, item.numberProcess, item.object, item.company, item.cnpjCpf, ...item.fiscais.map(f => f.name)].some(value => value.toLocaleLowerCase("pt-BR").includes(term)));
  }, [contracts, search]);

  const active = contracts.filter(item => item.endDate >= today()).length;
  const expiring = contracts.filter(item => { const days = (new Date(item.endDate).getTime() - Date.now()) / 86400000; return days >= 0 && days <= 60; }).length;
  const monthly = contracts.reduce((sum, item) => sum + Number(item.valueMensal), 0);

  function create() { setEditing(null); setForm(emptyForm()); setOpen(true); }
  function edit(item: Contract) {
    setEditing(item); setForm({ numberContract: item.numberContract, numberProcess: item.numberProcess, object: item.object, company: item.company, cnpjCpf: item.cnpjCpf, valueGlobal: String(item.valueGlobal), valueMensal: String(item.valueMensal), startDate: item.startDate, endDate: item.endDate, font: item.font ?? "", ta: item.ta ?? "", fiscalIds: item.fiscais.map(f => f.id) }); setOpen(true);
  }
  function field<K extends keyof ContractForm>(key: K, value: ContractForm[K]) { setForm(current => ({ ...current, [key]: value })); }

  async function submit(event: FormEvent) {
    event.preventDefault();
    try {
      const payload = { ...form, valueGlobal: Number(form.valueGlobal), valueMensal: Number(form.valueMensal) };
      if (editing) await putJson(`/contracts/${editing.id}`, payload); else await postJson("/contracts", payload);
      setOpen(false); setFeedback({ message: editing ? "Contrato atualizado" : "Contrato criado", error: false }); await load();
    } catch (error) { setFeedback({ message: error instanceof Error ? error.message : "Erro ao salvar contrato", error: true }); }
  }

  async function remove() {
    if (!removing) return;
    try { await deleteJson(`/contracts/${removing.id}`); setRemoving(null); setFeedback({ message: "Contrato excluído", error: false }); await load(); }
    catch (error) { setFeedback({ message: error instanceof Error ? error.message : "Erro ao excluir", error: true }); }
  }

  return <>
    <PageHeader title="Contratos" subtitle="Acompanhe vigências, valores e fiscais responsáveis." action={canManageContracts ? "Novo contrato" : undefined} onAction={create} />
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(3, 1fr)" }, gap: 2, mb: 3 }}>
      {[{ label: "Total de contratos", value: contracts.length }, { label: "Contratos vigentes", value: active }, { label: "Valor mensal", value: money.format(monthly) }].map(card => <Paper key={card.label} variant="outlined" sx={{ p: 2.5 }}><Typography color="text.secondary" variant="body2">{card.label}</Typography><Typography variant="h5" fontWeight={800} mt={.5}>{card.value}</Typography></Paper>)}
    </Box>
    {expiring > 0 && <Alert severity="warning" sx={{ mb: 2 }}>{expiring} contrato(s) encerram nos próximos 60 dias.</Alert>}
    <Paper variant="outlined" sx={{ overflow: "hidden" }}>
      <Box p={2}><TextField value={search} onChange={event => setSearch(event.target.value)} placeholder="Buscar contrato, empresa, processo ou fiscal" fullWidth slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> } }} /></Box>
      {loading ? <PageLoading /> : <TableContainer><Table sx={{ minWidth: 1180 }}>
        <TableHead><TableRow><TableCell>Contrato</TableCell><TableCell>Objeto / processo</TableCell><TableCell>Empresa</TableCell><TableCell>Valores</TableCell><TableCell>Fiscais</TableCell><TableCell>Vigência</TableCell><TableCell>Fonte / TA</TableCell><TableCell align="right">Ações</TableCell></TableRow></TableHead>
        <TableBody>{filtered.map(item => <TableRow key={item.id} hover>
          <TableCell><Typography fontWeight={700}>{item.numberContract}</Typography><Typography variant="caption" color="text.secondary">{item.cnpjCpf}</Typography></TableCell>
          <TableCell sx={{ maxWidth: 260 }}><Tooltip title={item.object}><Typography noWrap>{item.object}</Typography></Tooltip><Typography variant="caption" color="text.secondary">Processo {item.numberProcess}</Typography></TableCell>
          <TableCell>{item.company}</TableCell>
          <TableCell><Typography variant="body2">Global: {money.format(item.valueGlobal)}</Typography><Typography variant="caption" color="text.secondary">Mensal: {money.format(item.valueMensal)}</Typography></TableCell>
          <TableCell><Stack direction="row" gap={.5} flexWrap="wrap">{item.fiscais.length ? item.fiscais.map(fiscal => <Chip key={fiscal.id} label={fiscal.name} size="small" />) : <Typography variant="caption" color="text.secondary">Não definido</Typography>}</Stack></TableCell>
          <TableCell><Typography variant="body2">{date(item.startDate)} a</Typography><Typography variant="body2">{date(item.endDate)}</Typography></TableCell>
          <TableCell>{item.font || "—"}{item.ta && <Chip label={`TA ${item.ta}`} size="small" sx={{ ml: 1 }} />}</TableCell>
          <TableCell align="right">{canManageContracts && <IconButton onClick={() => edit(item)}><EditOutlinedIcon /></IconButton>}{isAdmin && <IconButton color="error" onClick={() => setRemoving(item)}><DeleteOutlineIcon /></IconButton>}</TableCell>
        </TableRow>)}{filtered.length === 0 && <TableRow><TableCell colSpan={8} align="center" sx={{ py: 8, color: "text.secondary" }}>Nenhum contrato encontrado.</TableCell></TableRow>}</TableBody>
      </Table></TableContainer>}
    </Paper>

    <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="md" component="form" onSubmit={submit}>
      <DialogTitle>{editing ? "Editar contrato" : "Novo contrato"}</DialogTitle>
      <DialogContent><Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" }, gap: 2, pt: 1 }}>
        <TextField label="Número do contrato" value={form.numberContract} onChange={e => field("numberContract", e.target.value)} required />
        <TextField label="Número do processo" value={form.numberProcess} onChange={e => field("numberProcess", e.target.value)} required />
        <TextField label="Objeto do contrato" value={form.object} onChange={e => field("object", e.target.value)} required multiline minRows={3} sx={{ gridColumn: { sm: "1 / -1" } }} />
        <TextField label="Empresa" value={form.company} onChange={e => field("company", e.target.value)} required />
        <TextField label="CNPJ / CPF" value={form.cnpjCpf} onChange={e => field("cnpjCpf", e.target.value)} required />
        <TextField label="Valor global" value={form.valueGlobal} onChange={e => field("valueGlobal", e.target.value)} type="number" required slotProps={{ htmlInput: { min: 0, step: ".01" } }} />
        <TextField label="Valor mensal" value={form.valueMensal} onChange={e => field("valueMensal", e.target.value)} type="number" required slotProps={{ htmlInput: { min: 0, step: ".01" } }} />
        <TextField label="Início da vigência" value={form.startDate} onChange={e => field("startDate", e.target.value)} type="date" required slotProps={{ inputLabel: { shrink: true } }} />
        <TextField label="Fim da vigência" value={form.endDate} onChange={e => field("endDate", e.target.value)} type="date" required slotProps={{ inputLabel: { shrink: true } }} />
        <TextField label="Fonte de recurso" value={form.font} onChange={e => field("font", e.target.value)} />
        <TextField select label="Termo aditivo" value={form.ta} onChange={e => field("ta", e.target.value)}><MenuItem value="">Sem TA</MenuItem>{[1,2,3,4,5,6].map(value => <MenuItem key={value} value={String(value)}>TA {value}</MenuItem>)}</TextField>
        <FormControl sx={{ gridColumn: { sm: "1 / -1" } }}><InputLabel id="fiscais-label">Fiscais responsáveis</InputLabel><Select labelId="fiscais-label" multiple value={form.fiscalIds} onChange={event => field("fiscalIds", event.target.value as number[])} input={<OutlinedInput label="Fiscais responsáveis" />} renderValue={selected => selected.map(id => users.find(user => user.id === id)?.name).filter(Boolean).join(", ")}>
          {users.map(user => <MenuItem key={user.id} value={user.id}>{user.name} ({user.sector?.name ?? "Sem setor"})</MenuItem>)}
        </Select></FormControl>
      </Box></DialogContent>
      <DialogActions sx={{ p: 3 }}><Button onClick={() => setOpen(false)}>Cancelar</Button><Button type="submit" variant="contained">Salvar contrato</Button></DialogActions>
    </Dialog>
    <ConfirmDialog open={Boolean(removing)} title="Excluir contrato?" text={`O contrato ${removing?.numberContract ?? ""} será removido permanentemente.`} onClose={() => setRemoving(null)} onConfirm={remove} />
    <Feedback message={feedback.message} error={feedback.error} onClose={() => setFeedback({ message: "", error: false })} />
  </>;
}
