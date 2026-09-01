"use client"

import { formatCnpj } from "@/lib/formatters";
import { Contract, User } from "@/types";
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, FormHelperText, InputLabel, MenuItem, OutlinedInput, Select, TextField, Typography } from "@mui/material";
import { FormEvent, useEffect, useMemo, useState } from "react";

type ContractForm<TValue extends string | number> = {
    numberContract: string;
    numberProcess: string;
    object: string;
    company: string;
    cnpj: string;
    valueGlobal: TValue;
    valueMensal: TValue;
    startDate: string;
    endDate: string;
    font: string;
    ta: string;
    fiscalIds: number[];
};

export type ContractFormState = ContractForm<string>;

export type ContractFormPayload = ContractForm<number>;

type ContractFormDialogProps = {
    open: boolean;
    contract: Contract | null;
    users: User[];
    onClose: () => void;
    onSubmit: (payload: ContractFormPayload) => Promise<void>;
};

const today = () => new Date().toISOString().slice(0, 10);

const emptyForm = (): ContractFormState => ({
    numberContract: "",
    numberProcess: "",
    object: "",
    company: "",
    cnpj: "",
    valueGlobal: "",
    valueMensal: "",
    startDate: today(),
    endDate: today(),
    font: "",
    ta: "",
    fiscalIds: [],
});

export default function ContractFormDialog({ open, contract, users, onClose, onSubmit }: ContractFormDialogProps) {
    const [form, setForm] = useState<ContractFormState>(emptyForm());
    const [fiscalError, setFiscalError] = useState("");
    const [saving, setSaving] = useState(false);
    const profileLabels: Record<User["perfil"], string> = {
        ADMIN: "Administrador", CONTROLE_INTERNO: "Controle Interno", FISCAL: "Fiscal",
    };

    useEffect(() => {
        if (!open) {
            return;
        }
        setFiscalError("");

        if (!contract) {
            setForm(emptyForm());
            return;
        }
        setForm({
            numberContract: contract.numberContract,
            numberProcess: contract.numberProcess,
            object: contract.object,
            company: contract.company,
            cnpj: formatCnpj(contract.cnpj),
            valueGlobal: String(contract.valueGlobal),
            valueMensal: String(contract.valueMensal),
            startDate: contract.startDate,
            endDate: contract.endDate,
            font: contract.font ?? "",
            ta: contract.ta ?? "",
            fiscalIds: contract.fiscais.map(fiscais => fiscais.id),
        });
    }, [open, contract])

    function field<K extends keyof ContractFormState>(key: K, value: ContractFormState[K]) {
        setForm(current => ({
            ...current,
            [key]: value
        }));
    }
    const availableUsers = useMemo(
        () =>
            users.filter(
                user =>
                    user.perfil === "FISCAL" ||
                    form.fiscalIds.includes(user.id),
            ),
        [users, form.fiscalIds],
    );

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (form.fiscalIds.length === 0) {
            setFiscalError("Selecione pelo menos um fiscal responsável");
            return;
        }

        setFiscalError("");
        setSaving(true);

        try {
            await onSubmit({
                ...form,
                valueGlobal: Number(form.valueGlobal),
                valueMensal: Number(form.valueMensal),
            });
        } finally {
            setSaving(false);
        }
    }

    return (
        <Dialog
            open={open}
            onClose={onClose}
            fullWidth
            maxWidth="md"
        >
            <form onSubmit={handleSubmit}>

                <DialogTitle>
                    {contract ? "Editar contrato" : "Novo contrato"}
                </DialogTitle>

                <DialogContent>
                    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" }, gap: 2, pt: 1 }}>
                        <TextField label="Número do contrato" value={form.numberContract} onChange={e => field("numberContract", e.target.value)} required />
                        <TextField label="Número do processo" value={form.numberProcess} onChange={e => field("numberProcess", e.target.value)} required />
                        <TextField label="Objeto do contrato" value={form.object} onChange={e => field("object", e.target.value)} required multiline minRows={3} sx={{ gridColumn: { sm: "1 / -1" } }} />
                        <TextField label="Empresa" value={form.company} onChange={e => field("company", e.target.value)} required />
                        <TextField label="CNPJ" value={form.cnpj}
                            onChange={e => field("cnpj", formatCnpj(e.target.value))} required
                            slotProps={{ htmlInput: { inputMode: "numeric", maxLength: 18 } }}
                        />
                        <TextField label="Valor global" value={form.valueGlobal} onChange={e => field("valueGlobal", e.target.value)} type="number" required slotProps={{ htmlInput: { min: 0, step: ".01" } }} />
                        <TextField label="Valor mensal" value={form.valueMensal} onChange={e => field("valueMensal", e.target.value)} type="number" required slotProps={{ htmlInput: { min: 0, step: ".01" } }} />
                        <TextField label="Início da vigência" value={form.startDate} onChange={e => field("startDate", e.target.value)} type="date" required slotProps={{ inputLabel: { shrink: true } }} />
                        <TextField label="Fim da vigência" value={form.endDate} onChange={e => field("endDate", e.target.value)} type="date" required slotProps={{ inputLabel: { shrink: true } }} />
                        <TextField label="Fonte de recurso" value={form.font} onChange={e => field("font", e.target.value)} />
                        <TextField select label="Termo aditivo" value={form.ta} onChange={e => field("ta", e.target.value)}><MenuItem value="">Sem TA</MenuItem>{[1, 2, 3, 4, 5, 6].map(value => <MenuItem key={value} value={String(value)}>TA {value}</MenuItem>)}</TextField>
                        <FormControl required error={Boolean(fiscalError)} sx={{ gridColumn: { sm: "1 / -1" } }}>
                            <InputLabel id="fiscais-label">Fiscais responsáveis
                            </InputLabel>
                            <Select labelId="fiscais-label" multiple value={form.fiscalIds}
                                onChange={event => {
                                    field("fiscalIds", event.target.value as number[]);
                                    setFiscalError("");
                                }}
                                input={<OutlinedInput label="Fiscais responsáveis" />}
                                renderValue={selected =>
                                    selected.map(id => {
                                        const user = users.find(item => item.id === id);
                                        if (!user) {
                                            return `Usuário ${id}` //caso venaha um id maluco doido
                                        }
                                        return user.perfil === "FISCAL" ? user.name : `${user.name} (histórico)`
                                    }).join(", ")}
                            >

                                {availableUsers.map(user => {
                                    const historical = user.perfil !== "FISCAL";
                                    return (
                                        <MenuItem key={user.id} value={user.id}>
                                            <Box sx={{ display: "flex", flexDirection: "column" }}>
                                                <Typography>
                                                    {user.name}
                                                </Typography>
                                                <Typography
                                                    variant="caption"
                                                    color={historical ? "warning.main" : "text.secondary"}
                                                >
                                                    {user.sector?.name ?? "Sem setor"}

                                                    {historical &&
                                                        ` • Histórico — perfil atual: ${profileLabels[user.perfil]}`}
                                                </Typography>
                                            </Box>
                                        </MenuItem>
                                    );
                                })}
                            </Select>
                            <FormHelperText>{fiscalError}</FormHelperText>
                        </FormControl>
                    </Box>
                </DialogContent>
                <DialogActions sx={{ p: 3 }}>
                    <Button type="button" onClick={onClose} disabled={saving}>
                        Cancelar
                    </Button>

                    <Button type="submit" variant="contained" disabled={saving}>
                        {saving ? "Salvando..." : "Salvar contrato"}
                    </Button>
                </DialogActions>
            </form>

        </Dialog>
    )
}