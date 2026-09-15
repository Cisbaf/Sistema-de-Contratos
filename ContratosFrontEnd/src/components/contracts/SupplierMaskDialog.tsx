"use client";

import { getJson } from "@/lib/api";
import type { Contract } from "@/types";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import { Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Typography } from "@mui/material";
import { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

export default function SupplierMaskDialog({
    open,
    contract,
    onClose,
}: {
    open: boolean;
    contract: Contract | null;
    onClose: () => void;
}) {
    const [text, setText] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");
    const [copiado, setCopiado] = useState(false);

    useEffect(() => {
        if (!open || !contract) return;
        setError("");
        setText("");
        setCopiado(false);
        setLoading(true);
        getJson<{ text: string }>(`/contracts/${contract.id}/supplier-mask/preview`)
            .then(data => setText(data.text))
            .catch(err => setError(err instanceof Error ? err.message : "Erro ao carregar a máscara"))
            .finally(() => setLoading(false));
    }, [open, contract]);

    if (!contract) return null;

    async function copiarTexto() {
        try {
            await navigator.clipboard.writeText(text);
            setCopiado(true);
        } catch {
            setError("Não foi possível copiar o texto. Copie manualmente.");
        }
    }

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle>Máscara externa ao prestador — {contract.numberContract}</DialogTitle>
            <DialogContent dividers>
                <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
                    <Typography variant="subtitle2" color="text.secondary">
                        Texto para copiar e enviar ao prestador
                    </Typography>
                    <Button
                        size="small"
                        startIcon={<ContentCopyIcon fontSize="small" />}
                        onClick={copiarTexto}
                        color={copiado ? "success" : "primary"}
                        disabled={!text || loading}
                    >
                        {copiado ? "Copiado" : "Copiar texto"}
                    </Button>
                </Stack>

                <Box sx={{ "& p": { my: 1 }, minHeight: 240 }}>
                    {loading ? (
                        <Box py={4} display="grid" sx={{ placeItems: "center" }}><CircularProgress size={24} /></Box>
                    ) : (
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{text || "Nenhum conteúdo disponível."}</ReactMarkdown>
                    )}
                </Box>

                <Alert severity="info" sx={{ mt: 2 }}>
                    Este texto é apenas para copiar e enviar manualmente ao prestador. O sistema não envia e-mail automaticamente.
                </Alert>

                {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Fechar</Button>
            </DialogActions>
        </Dialog>
    );
}
