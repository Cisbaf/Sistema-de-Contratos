"use client";

import { downloadFile, getJson } from "@/lib/api";
import type { Contract, GeneratedDocument } from "@/types";
import DownloadIcon from "@mui/icons-material/Download";
import {
    Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent,
    DialogTitle, Divider, IconButton, List, ListItem, ListItemText,
    Stack, Tooltip, Typography,
} from "@mui/material";
import { useEffect, useState } from "react";

const dataHora = (value: string) =>
    new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));

function Categoria({ titulo, documentos }: { titulo: string; documentos: GeneratedDocument[] }) {
    async function baixar(doc: GeneratedDocument) {
        await downloadFile(`/generate-document/download?documentId=${doc.id}`, doc.fileName);
    }
    return (
        <Box mb={3}>
            <Typography variant="subtitle2" color="text.secondary" mb={1}>{titulo}</Typography>
            {documentos.length === 0 ? (
                <Typography variant="body2" color="text.secondary">Nenhum documento gerado ainda.</Typography>
            ) : (
                <List dense disablePadding>
                    {documentos.map(doc => (
                        <ListItem
                            key={doc.id}
                            disablePadding
                            secondaryAction={
                                <Tooltip title="Baixar PDF">
                                    <IconButton edge="end" onClick={() => baixar(doc)}>
                                        <DownloadIcon />
                                    </IconButton>
                                </Tooltip>
                            }
                        >
                            <ListItemText
                                primary={`Versão ${doc.version}`}
                                secondary={`${doc.generatedBy.name} · ${dataHora(doc.generatedAt)}`}
                            />
                        </ListItem>
                    ))}
                </List>
            )}
        </Box>
    );
}

export default function GeneratedDocumentsDialog({ open, contract, onClose, }:
    {
        open: boolean;
        contract: Contract | null;
        onClose: () => void;
    }) {
    const [emails, setEmails] = useState<GeneratedDocument[]>([]);
    const [pareceres, setPareceres] = useState<GeneratedDocument[]>([]);
    const [atestes, setAtestes] = useState<GeneratedDocument[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!open || !contract) return;
        setError("");
        setLoading(true);

        Promise.all([
            getJson<GeneratedDocument[]>(`/generate-document/history?contractId=${contract.id}&docType=INTEREST_EMAIL`),
            getJson<GeneratedDocument[]>(`/generate-document/history?contractId=${contract.id}&docType=TECHNICAL_OPINION`),
            getJson<GeneratedDocument[]>(`/generate-document/history?contractId=${contract.id}&docType=PAYMENT_CHECKLIST`),
        ])
            .then(([emailData, parecerData, atesteData]) => {
                setEmails(emailData);
                setPareceres(parecerData);
                setAtestes(atesteData);
            })
            .catch(err => setError(err instanceof Error ? err.message : "Erro ao carregar documentos"))
            .finally(() => setLoading(false));
    }, [open, contract]);

    if (!contract) return null;

    return (
        <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
            <DialogTitle>Documentos gerados — {contract.numberContract}</DialogTitle>
            <DialogContent dividers>
                {loading ? (
                    <Box py={4} display="grid" sx={{ placeItems: "center" }}><CircularProgress size={24} /></Box>
                ) : (
                    <Stack>
                        <Categoria titulo="E-mail de interesse" documentos={emails} />
                        <Divider sx={{ mb: 2 }} />
                        <Categoria titulo="Parecer técnico" documentos={pareceres} />
                        <Divider sx={{ mb: 2 }} />
                        <Categoria titulo="Ateste dos fiscais (checklist)" documentos={atestes} />
                    </Stack>
                )}
                {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Fechar</Button>
            </DialogActions>
        </Dialog>
    );
}