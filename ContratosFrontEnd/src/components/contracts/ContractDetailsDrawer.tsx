"use client";

import { formatCnpj } from "@/lib/formatters";
import type { Contract } from "@/types";
import {
    AttachFileOutlined, DescriptionOutlined, EmailOutlined, FolderOutlined, PaymentsOutlined, PersonOutlineOutlined,
} from "@mui/icons-material";
import CloseIcon from "@mui/icons-material/Close";
import {
    Box, Chip, ChipProps, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon,
    ListItemText, Stack, Typography,
} from "@mui/material";
import type { ReactNode } from "react";

const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const date = (value: string) => new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`));

function Campo({ label, children }: { label: string; children: ReactNode }) {
    return (
        <Box>
            <Typography variant="caption" color="text.secondary">{label}</Typography>
            <Typography variant="body2" component="div" sx={{ wordBreak: "break-word" }}>{children}</Typography>
        </Box>
    );
}

function Acao({ icon, titulo, disabledHint, disabled, onClick }: {
    icon: ReactNode;
    titulo: string;
    disabled?: boolean;
    disabledHint?: string;
    onClick: () => void;
}) {
    return (
        <ListItemButton disabled={disabled} onClick={onClick} sx={{ borderRadius: 1 }}>
            <ListItemIcon sx={{ minWidth: 40 }}>{icon}</ListItemIcon>
            <ListItemText primary={titulo} secondary={disabled ? disabledHint : undefined} />
        </ListItemButton>
    );
}

export default function ContractDetailsDrawer({
    open, contract, status, onClose, onEmail, onOpinion, onMask, onDocuments, onAttachments, onFinancial,
}: {
    open: boolean;
    contract: Contract | null;
    status: { label: string; color: ChipProps["color"] } | null;
    onClose: () => void;
    onEmail: (contract: Contract) => void;
    onOpinion: (contract: Contract) => void;
    onMask: (contract: Contract) => void;
    onDocuments: (contract: Contract) => void;
    onAttachments: (contract: Contract) => void;
    onFinancial: (contract: Contract) => void;
}) {
    if (!contract) return null;

    return (
        <Drawer
            anchor="right"
            open={open}
            onClose={onClose}
            slotProps={{
                paper: { sx: { width: { xs: "100%", md: "50vw" }, minWidth: { md: 480 }, maxWidth: { md: 900 } } },
            }}
        >
            <Stack direction="row" alignItems="center" justifyContent="space-between" px={3} py={2}>
                <Box>
                    <Typography variant="h6" fontWeight={700}>Contrato {contract.numberContract}</Typography>
                    {status && <Chip label={status.label} color={status.color} size="small" sx={{ mt: 0.5 }} />}
                </Box>
                <IconButton aria-label="Fechar" onClick={onClose}><CloseIcon /></IconButton>
            </Stack>
            <Divider />

            <Box px={3} py={2} sx={{ overflowY: "auto" }}>
                <Typography variant="subtitle2" color="text.secondary" mb={1.5}>Dados do contrato</Typography>
                <Box
                    display="grid"
                    gap={2}
                    sx={{ gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" } }}
                >
                    <Campo label="Empresa">{contract.company}</Campo>
                    <Campo label="CNPJ">{formatCnpj(contract.cnpj)}</Campo>
                    <Box sx={{ gridColumn: { sm: "1 / -1" } }}>
                        <Campo label="Objeto">{contract.object}</Campo>
                    </Box>
                    <Campo label="Processo">{contract.numberProcess}</Campo>
                    <Campo label="Número SEI">{contract.seiProcessNumber || "—"}</Campo>
                    <Campo label="Valor global">{money.format(contract.valueGlobal)}</Campo>
                    <Campo label="Valor mensal">{money.format(contract.valueMensal)}</Campo>
                    <Campo label="Vigência">{date(contract.startDate)} a {date(contract.endDate)}</Campo>
                    <Campo label="Limite de prorrogação">
                        {contract.maxExtensionMonths === null
                            ? "—"
                            : `${contract.maxExtensionMonths} ${contract.maxExtensionMonths === 1 ? "mês" : "meses"}`}
                    </Campo>
                    <Campo label="Fonte / TA">
                        {contract.font || "—"}{contract.ta ? ` · TA ${contract.ta}` : ""}
                    </Campo>
                    <Box sx={{ gridColumn: { sm: "1 / -1" } }}>
                        <Campo label="Fiscais">
                            {contract.fiscais.length ? (
                                <Stack direction="row" gap={0.5} flexWrap="wrap" mt={0.5}>
                                    {contract.fiscais.map(fiscal => <Chip key={fiscal.id} label={fiscal.name} size="small" />)}
                                </Stack>
                            ) : "Não definido"}
                        </Campo>
                    </Box>
                </Box>

                <Divider sx={{ my: 3 }} />

                <Typography variant="subtitle2" color="text.secondary" mb={1}>Ações</Typography>
                <List disablePadding>
                    <Acao
                        icon={<EmailOutlined />}
                        titulo="Gerar e-mail de interesse"
                        disabled={contract.status !== "AGUARDANDO_EMAIL_INTERESSE"}
                        disabledHint="Disponível quando o contrato estiver aguardando o e-mail de interesse."
                        onClick={() => onEmail(contract)}
                    />
                    <Acao
                        icon={<DescriptionOutlined />}
                        titulo="Gerar parecer"
                        disabled={contract.status !== "EMAIL_ENVIADO"}
                        disabledHint="Disponível depois que o e-mail de interesse for enviado."
                        onClick={() => onOpinion(contract)}
                    />
                    <Acao
                        icon={<PersonOutlineOutlined />}
                        titulo="Máscara externa ao prestador"
                        disabled={contract.status === "EM_VIGENCIA"}
                        disabledHint="Disponível a partir do início da renovação."
                        onClick={() => onMask(contract)}
                    />
                    <Acao
                        icon={<FolderOutlined />}
                        titulo="Documentos gerados"
                        onClick={() => onDocuments(contract)}
                    />
                    <Acao
                        icon={<PaymentsOutlined />}
                        titulo="Financeiro"
                        onClick={() => onFinancial(contract)}
                    />
                    <Acao
                        icon={<AttachFileOutlined />}
                        titulo="Anexos"
                        onClick={() => onAttachments(contract)}
                    />
                </List>
            </Box>
        </Drawer>
    );
}
