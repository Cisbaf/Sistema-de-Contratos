"use client";

import { useAuth } from "@/components/DashboardShell";
import { getJson, postJson } from "@/lib/api";
import type { Contract } from "@/types";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import { Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Stack, Tooltip, Typography } from "@mui/material";
import { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

export default function InterestEmailDialog({
    open,
    contract,
    onClose,
    onConfirmed,
}: {
    open: boolean;
    contract: Contract | null;
    onClose: () => void;
    onConfirmed: () => void;
}) {
    const auth = useAuth();
    const [preview, setPreview] = useState("");
    const [loading, setLoading] = useState(false);
    const [confirming, setConfirming] = useState(false);
    const [error, setError] = useState("");
    const [copiado, setCopiado] = useState(false);

    useEffect(() => {
        if (!open || !contract) return;
        setError("");
        setCopiado(false);
        setLoading(true);
        getJson<{ content: string }>(`/contracts/${contract.id}/interest-email/preview`)
            .then(data => setPreview(data.content))
            .catch(err => setError(err instanceof Error ? err.message : "Erro ao carregar prévia"))
            .finally(() => setLoading(false));
    }, [open, contract]);

    if (!contract) return null;

    const confirmados = contract.fiscaisConfirmadosEnvioInteresse ?? [];
    const meuFiscal = contract.fiscais.find(f => f.username === auth.username);
    const jaConfirmei = meuFiscal ? confirmados.includes(meuFiscal.id) : false;
    const ccTexto = contract.fiscais.map(f => `${f.name} <${f.email}>`).join(", ");

    async function copiarTexto() {
        try {
            await navigator.clipboard.writeText(preview);
            setCopiado(true);
        } catch {
            setError("Não foi possível copiar o texto. Copie manualmente antes de marcar como enviado.");
        }
    }

    async function confirmar() {
        if (!contract) return;
        setConfirming(true);
        setError("");
        try {
            await postJson<{ message: string }>(`/contracts/${contract.id}/interest-email/confirm`);
            onConfirmed();
            onClose();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Erro ao confirmar envio");
        } finally {
            setConfirming(false);
        }
    }

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle>E-mail de interesse — {contract.numberContract}</DialogTitle>
            <DialogContent dividers>
                {loading ? (
                    <Box py={4} display="grid" sx={{ placeItems: "center" }}><CircularProgress size={28} /></Box>
                ) : (
                    <>
                        <Typography variant="subtitle2" color="text.secondary" mb={1}>Cc sugerido (fiscais do contrato)</Typography>
                        <Typography variant="body2" sx={{ wordBreak: "break-word", mb: 2 }}>{ccTexto}</Typography>

                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap mb={2}>
                            {contract.fiscais.map(fiscal => (
                                <Chip
                                    key={fiscal.id}
                                    label={fiscal.name}
                                    size="small"
                                    color={confirmados.includes(fiscal.id) ? "success" : "default"}
                                    variant={confirmados.includes(fiscal.id) ? "filled" : "outlined"}
                                />
                            ))}
                        </Stack>

                        <Divider sx={{ mb: 2 }} />

                        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
                            <Typography variant="subtitle2" color="text.secondary">Texto do e-mail</Typography>
                            <Tooltip title="Copiar texto do e-mail">
                                <Button
                                    size="small"
                                    startIcon={<ContentCopyIcon fontSize="small" />}
                                    onClick={copiarTexto}
                                    color={copiado ? "success" : "primary"}
                                >
                                    {copiado ? "Copiado" : "Copiar texto"}
                                </Button>
                            </Tooltip>
                        </Stack>
                        <Box sx={{ "& p": { my: 1 }, minHeight: 320 }}>
                            <ReactMarkdown remarkPlugins={[remarkGfm]}>{preview}</ReactMarkdown>
                        </Box>

                        {meuFiscal && !jaConfirmei && !copiado && (
                            <Alert severity="info" sx={{ mt: 2 }}>
                                Copie o texto acima e envie por e-mail antes de marcar como enviado.
                            </Alert>
                        )}

                        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
                    </>
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Fechar</Button>
                {meuFiscal && !jaConfirmei && (
                    <Tooltip title={copiado ? "" : "Copie o texto do e-mail antes de confirmar"}>
                        <span>
                            <Button onClick={confirmar} variant="contained" disabled={confirming || loading || !copiado}>
                                {confirming ? "Confirmando..." : "Marcar como enviado"}
                            </Button>
                        </span>
                    </Tooltip>
                )}
                {meuFiscal && jaConfirmei && (
                    <Typography variant="body2" color="success.main" sx={{ alignSelf: "center", mr: 1 }}>
                        Você já confirmou o envio.
                    </Typography>
                )}
            </DialogActions>
        </Dialog>
    );
}