"use client";

import ConfirmDialog from "@/components/ConfirmDialog";
import { deleteJson, downloadFile, getJson, postForm } from "@/lib/api";
import type { Contract, ContractAttachment } from "@/types";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import DownloadIcon from "@mui/icons-material/Download";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import {
    Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent,
    DialogTitle, IconButton, List, ListItem, ListItemText, Stack, Tooltip, Typography,
} from "@mui/material";
import { ChangeEvent, useCallback, useEffect, useRef, useState } from "react";

// Espelham as regras do backend (ContractAttachmentService + application.properties).
// O backend continua sendo a garantia; aqui é só para avisar antes de enviar.
const MAX_FILES = 5;
const MAX_PER_CONTRACT = 10;
const MAX_FILE_BYTES = 30 * 1024 * 1024;
const MAX_TOTAL_BYTES = 90 * 1024 * 1024;
const ALLOWED_EXTENSIONS = [".pdf", ".doc", ".docx"];

const dataHora = (value: string) =>
    new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));

function tamanho(bytes: number) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function validarArquivos(files: File[], jaAnexados: number): string | null {
    if (files.length > MAX_FILES) return `Selecione no máximo ${MAX_FILES} arquivos por vez.`;
    if (jaAnexados + files.length > MAX_PER_CONTRACT) {
        const vagas = Math.max(0, MAX_PER_CONTRACT - jaAnexados);
        return `Limite de ${MAX_PER_CONTRACT} anexos por contrato. Este contrato já tem ${jaAnexados} e comporta mais ${vagas}.`;
    }
    for (const file of files) {
        const name = file.name.toLowerCase();
        if (!ALLOWED_EXTENSIONS.some(ext => name.endsWith(ext))) {
            return `"${file.name}": apenas arquivos PDF, DOC e DOCX são permitidos.`;
        }
        if (file.size === 0) return `"${file.name}" está vazio.`;
        if (file.size > MAX_FILE_BYTES) return `"${file.name}" passa de ${tamanho(MAX_FILE_BYTES)}.`;
    }
    if (files.reduce((sum, file) => sum + file.size, 0) > MAX_TOTAL_BYTES) {
        return `O total dos arquivos passa de ${tamanho(MAX_TOTAL_BYTES)}.`;
    }
    return null;
}

export default function ContractAttachmentsDialog({ open, contract, canManage, onClose }: {
    open: boolean;
    contract: Contract | null;
    canManage: boolean;
    onClose: () => void;
}) {
    const [items, setItems] = useState<ContractAttachment[]>([]);
    const [loading, setLoading] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const [removing, setRemoving] = useState<ContractAttachment | null>(null);
    const inputRef = useRef<HTMLInputElement>(null);

    const carregar = useCallback(async () => {
        if (!contract) return;
        try {
            setItems(await getJson<ContractAttachment[]>(`/attachment/ativos/${contract.id}`));
        } catch (err) {
            setError(err instanceof Error ? err.message : "Erro ao carregar anexos");
        }
    }, [contract]);

    useEffect(() => {
        if (!open || !contract) return;
        setError("");
        setSuccess("");
        setItems([]);
        setLoading(true);
        carregar().finally(() => setLoading(false));
    }, [open, contract, carregar]);

    async function enviar(event: ChangeEvent<HTMLInputElement>) {
        const files = Array.from(event.target.files ?? []);
        event.target.value = ""; // permite escolher o mesmo arquivo de novo depois
        if (!contract || files.length === 0) return;

        setError("");
        setSuccess("");
        const problem = validarArquivos(files, items.length);
        if (problem) {
            setError(problem);
            return;
        }

        setUploading(true);
        try {
            const form = new FormData();
            files.forEach(file => form.append("files", file));
            await postForm(`/attachment/${contract.id}`, form);
            setSuccess(files.length === 1 ? "Arquivo anexado." : `${files.length} arquivos anexados.`);
            await carregar();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Erro ao enviar arquivos");
        } finally {
            setUploading(false);
        }
    }

    async function baixar(item: ContractAttachment) {
        setError("");
        try {
            await downloadFile(`/attachment/baixar/${item.id}`, item.fileName);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Erro ao baixar arquivo");
        }
    }

    async function remover() {
        if (!removing) return;
        const target = removing;
        setRemoving(null);
        setError("");
        setSuccess("");
        try {
            await deleteJson(`/attachment/${target.id}`);
            setSuccess(`"${target.fileName}" removido.`);
            await carregar();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Erro ao remover anexo");
        }
    }

    if (!contract) return null;

    return (
        <>
            <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
                <DialogTitle>Anexos — {contract.numberContract}</DialogTitle>
                <DialogContent dividers>
                    {canManage && (
                        <Box mb={2}>
                            <input
                                ref={inputRef}
                                type="file"
                                multiple
                                hidden
                                accept={ALLOWED_EXTENSIONS.join(",")}
                                onChange={enviar}
                            />
                            <Stack direction="row" spacing={2} alignItems="center">
                                <Button
                                    variant="outlined"
                                    startIcon={uploading ? <CircularProgress size={16} /> : <UploadFileIcon />}
                                    disabled={uploading || items.length >= MAX_PER_CONTRACT}
                                    onClick={() => inputRef.current?.click()}
                                >
                                    Adicionar arquivos
                                </Button>
                                <Typography variant="caption" color="text.secondary">
                                    {items.length >= MAX_PER_CONTRACT
                                        ? `Limite de ${MAX_PER_CONTRACT} anexos atingido. Remova um para adicionar outro.`
                                        : `Até ${MAX_FILES} arquivos por vez, ${items.length} de ${MAX_PER_CONTRACT} anexos em uso (PDF, DOC ou DOCX, até 30 MB cada).`}
                                </Typography>
                            </Stack>
                        </Box>
                    )}

                    {loading ? (
                        <Box py={4} display="grid" sx={{ placeItems: "center" }}><CircularProgress size={24} /></Box>
                    ) : items.length === 0 ? (
                        <Typography variant="body2" color="text.secondary">Nenhum anexo neste contrato.</Typography>
                    ) : (
                        <List dense disablePadding>
                            {items.map(item => (
                                <ListItem
                                    key={item.id}
                                    disablePadding
                                    sx={{ pr: canManage ? 12 : 7 }}
                                    secondaryAction={
                                        <Stack direction="row" spacing={0.5}>
                                            <Tooltip title="Baixar">
                                                <IconButton aria-label={`Baixar ${item.fileName}`} onClick={() => baixar(item)}>
                                                    <DownloadIcon />
                                                </IconButton>
                                            </Tooltip>
                                            {canManage && (
                                                <Tooltip title="Remover">
                                                    <IconButton color="error" aria-label={`Remover ${item.fileName}`} onClick={() => setRemoving(item)}>
                                                        <DeleteOutlineIcon />
                                                    </IconButton>
                                                </Tooltip>
                                            )}
                                        </Stack>
                                    }
                                >
                                    <ListItemText
                                        sx={{ wordBreak: "break-word" }}
                                        primary={item.fileName}
                                        secondary={`${tamanho(item.sizeBytes)} · ${item.uploadedBy.name} · ${dataHora(item.uploadedAt)}`}
                                    />
                                </ListItem>
                            ))}
                        </List>
                    )}

                    {success && <Alert severity="success" sx={{ mt: 2 }} onClose={() => setSuccess("")}>{success}</Alert>}
                    {error && <Alert severity="error" sx={{ mt: 2 }} onClose={() => setError("")}>{error}</Alert>}
                </DialogContent>
                <DialogActions>
                    <Button onClick={onClose}>Fechar</Button>
                </DialogActions>
            </Dialog>

            <ConfirmDialog
                open={Boolean(removing)}
                title="Remover anexo?"
                text={`O arquivo "${removing?.fileName ?? ""}" será removido e o conteúdo dele será apagado definitivamente. Fica registrado apenas que ele existiu (nome, quem enviou e quem removeu).`}
                confirmLabel="Remover"
                onClose={() => setRemoving(null)}
                onConfirm={remover}
            />
        </>
    );
}
