"use client";

import ConfirmDialog from "@/components/ConfirmDialog";
import ContractFormDialog, { ContractFormPayload } from "@/components/contracts/ContractFormDialog";
import { useAuth } from "@/components/DashboardShell";
import { Feedback, PageLoading } from "@/components/Feedback";
import PageHeader from "@/components/PageHeader";
import { deleteJson, getJson, postJson, putJson } from "@/lib/api";
import { formatCnpj } from "@/lib/formatters";
import type { Contract, ContractStatus, User } from "@/types";
import { AccountBalanceOutlined, DescriptionOutlined, EmailOutlined } from "@mui/icons-material";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import SearchIcon from "@mui/icons-material/Search";
import { Alert, Box, Chip, ChipProps, IconButton, InputAdornment, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Tooltip, Typography } from "@mui/material";
import { useEffect, useMemo, useState } from "react";

const today = () => new Date().toISOString().slice(0, 10);
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
    return contracts.filter(item => [item.numberContract, item.numberProcess, item.object, item.company, item.cnpj,
    formatCnpj(item.cnpj),
    ...item.fiscais.map(f => f.name)].some(value => value.toLocaleLowerCase("pt-BR").includes(term)));
  }, [contracts, search]);

  const active = contracts.filter(item => item.endDate >= today()).length;
  const expiring = contracts.filter(item => { const days = (new Date(item.endDate).getTime() - Date.now()) / 86400000; return days >= 0 && days <= 60; }).length;
  const monthly = contracts.reduce((sum, item) => sum + Number(item.valueMensal), 0);

  const contractStatusPresentation: Record<ContractStatus, { label: string; color: ChipProps["color"] }> = {
    EM_VIGENCIA: {
      label: "Em vigência",
      color: "success",
    },
    AGUARDANDO_EMAIL_INTERESSE: {
      label: "Aguardando e-mail de interesse",
      color: "warning",
    },
    EMAIL_ENVIADO: {
      label: "E-mail enviado",
      color: "info",
    },
    RENOVACAO_ABERTA_SEI: {
      label: "Renovação aberta no SEI",
      color: "secondary",
    },
  }


  function create() {
    setEditing(null);
    setOpen(true);
  }
  function edit(item: Contract) {
    setEditing(item);
    setOpen(true)
  }

  async function saveContract(payload: ContractFormPayload) {
    try {
      if (editing) {
        await putJson(`/contracts/${editing.id}`, payload);
      } else {
        await postJson("/contracts", payload);
      }

      setOpen(false);

      setFeedback({
        message: editing ? "Contrato atualizado" : "Contrato criado",
        error: false,
      });

      await load();
    } catch (error) {
      setFeedback({
        message:
          error instanceof Error
            ? error.message
            : "Erro ao salvar contrato",
        error: true,
      });
    }
  }
  async function remove() {
    if (!removing) return;
    try { await deleteJson(`/contracts/${removing.id}`); setRemoving(null); setFeedback({ message: "Contrato excluído", error: false }); await load(); }
    catch (error) { setFeedback({ message: error instanceof Error ? error.message : "Erro ao excluir", error: true }); }
  }

  return <>
    <PageHeader title="Contratos" subtitle="Acompanhe vigências, valores e fiscais responsáveis." action={canManageContracts ? "Novo contrato" : undefined} onAction={create} />
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(3, 1fr)" }, gap: 2, mb: 3 }}>
      {[{ label: "Total de contratos", value: contracts.length }, { label: "Contratos vigentes", value: active },
      { label: "Valor mensal", value: money.format(monthly) }].map(card =>
        <Paper key={card.label}
          variant="outlined" sx={{ p: 2.5 }}><Typography color="text.secondary" variant="body2">{card.label}
          </Typography>
          <Typography variant="h5" fontWeight={800} mt={.5}>{card.value}</Typography>
        </Paper>)}
    </Box>
    {expiring > 0 && <Alert severity="warning" sx={{ mb: 2 }}>{expiring} contrato(s) encerram nos próximos 60 dias.</Alert>}
    <Paper variant="outlined" sx={{ overflow: "hidden" }}>
      <Box p={2}>
        <TextField value={search} onChange={event => setSearch(event.target.value)}
          placeholder="Buscar contrato, empresa, processo ou fiscal" fullWidth
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> } }} />
      </Box>
      {loading ? <PageLoading /> :
        <TableContainer>
          <Table sx={{ minWidth: 1580 }}>
            <TableHead>
              <TableRow>
                <TableCell>Contrato</TableCell>
                <TableCell>Objeto / processo</TableCell>
                <TableCell>Empresa</TableCell>
                <TableCell>Valores</TableCell>
                <TableCell>Fiscais</TableCell>
                <TableCell>Vigência</TableCell>
                <TableCell>Fonte / TA</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Ações</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filtered.map(item => {

                const status = contractStatusPresentation[item.status]

                return (
                  <TableRow key={item.id} hover>
                    <TableCell>
                      <Typography fontWeight={700}>{item.numberContract}</Typography>
                      <Typography variant="caption" color="text.secondary">{formatCnpj(item.cnpj)}</Typography>
                    </TableCell>
                    <TableCell sx={{ maxWidth: 260 }}>
                      <Tooltip title={item.object}>
                        <Typography noWrap>{item.object}</Typography>
                      </Tooltip>
                      <Typography variant="caption" color="text.secondary">Processo {item.numberProcess}</Typography>
                    </TableCell>
                    <TableCell>{item.company}</TableCell>
                    <TableCell>
                      <Typography variant="body2">Global: {money.format(item.valueGlobal)}</Typography>
                      <Typography variant="caption" color="text.secondary">Mensal: {money.format(item.valueMensal)}</Typography>
                    </TableCell>
                    <TableCell><Stack direction="row" gap={.5} flexWrap="wrap">
                      {item.fiscais.length ? item.fiscais.map(fiscal => <Chip key={fiscal.id} label={fiscal.name} size="small" />) : <Typography variant="caption" color="text.secondary">Não definido</Typography>}
                    </Stack>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{date(item.startDate)} a</Typography>
                      <Typography variant="body2">{date(item.endDate)}</Typography>
                    </TableCell>
                    <TableCell>{item.font || "—"}{item.ta && <Chip label={`TA ${item.ta}`} size="small" sx={{ ml: 1 }} />}</TableCell>
                    <TableCell> <Chip label={status.label} color={status.color} size="small" /> </TableCell>

                    <TableCell align="right">
                      <Stack direction="row" spacing={0.25} justifyContent="flex-end">
                        <Tooltip title="Gerar e-mail de interesse">
                          <span>
                            <IconButton disabled aria-label="Gerar e-mail de interesse"> <EmailOutlined /></IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="Gerar parecer">
                          <span>
                            <IconButton disabled aria-label="Gerar parecer"> <DescriptionOutlined /></IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="Financeiro">
                          <span>
                            <IconButton disabled aria-label="Financeiro"> <AccountBalanceOutlined /></IconButton>
                          </span>
                        </Tooltip>

                        {canManageContracts &&
                          <Tooltip title="Editar">
                            <span>
                              <IconButton onClick={() => edit(item)}>
                                <EditOutlinedIcon />
                              </IconButton>
                            </span>
                          </Tooltip>

                        }
                        {isAdmin &&
                          <Tooltip title="Deletar contrato">
                            <span>
                              <IconButton color="error" onClick={() => setRemoving(item)}>
                                <DeleteOutlineIcon />
                              </IconButton>
                            </span>
                          </Tooltip>

                        }
                      </Stack>
                    </TableCell>
                  </TableRow>
                )
              }
              )}
              {filtered.length === 0 &&
                <TableRow>
                  <TableCell colSpan={9} align="center" sx={{ py: 8, color: "text.secondary" }}>Nenhum contrato encontrado.</TableCell>
                </TableRow>
              }
            </TableBody>
          </Table>
        </TableContainer>
      }
    </Paper>

    <ContractFormDialog
      open={open}
      contract={editing}
      users={users}
      onClose={() => setOpen(false)}
      onSubmit={saveContract}
    />

    <ConfirmDialog open={Boolean(removing)} title="Excluir contrato?" text={`O contrato ${removing?.numberContract ?? ""} será removido permanentemente.`} onClose={() => setRemoving(null)} onConfirm={remove} />
    <Feedback message={feedback.message} error={feedback.error} onClose={() => setFeedback({ message: "", error: false })} />
  </>;
}
