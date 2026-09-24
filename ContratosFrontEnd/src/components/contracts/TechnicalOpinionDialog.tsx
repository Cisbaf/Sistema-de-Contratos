"use client";

import { useAuth } from "@/components/DashboardShell";
import { copyRenderedContent } from "@/lib/clipboard";
import { getJson, postJson } from "@/lib/api";
import type { Contract } from "@/types";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import { Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Stack, TextField, Tooltip, Typography } from "@mui/material";
import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

type FiscalProgress = { fiscalName: string; submitted: boolean };

export default function TechnicalOpinionDialog({
    open,
    contract,
    onClose,
    onSubmitted,
}: {
    open: boolean;
    contract: Contract | null;
    onClose: () => void;
    onSubmitted: () => void;
}) {
    const auth = useAuth();
    const [observations, setObservations] = useState("");
    const [preview, setPreview] = useState("");
    const previewRef = useRef<HTMLDivElement>(null);
    const [progress, setProgress] = useState<FiscalProgress[]>([]);
    const [collective, setCollective] = useState("");
    const [loadingPreview, setLoadingPreview] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");
    const [copiado, setCopiado] = useState(false);

    const souFiscal = Boolean(contract?.fiscais.find(f => f.username === auth.username));

    useEffect(() => {
        if (!open || !contract) return;
        setError("");
        setObservations("");
        setPreview("");
        setCollective("");
        setCopiado(false);
        getJson<FiscalProgress[]>(`/contracts/${contract.id}/technical-opinion/progress`)
            .then(setProgress)
            .catch(err => setError(err instanceof Error ? err.message : "Erro ao carregar progresso"));

        if (souFiscal) {
            getJson<{ text: string }>(`/contracts/${contract.id}/technical-opinion/mine`)
                .then(data => setObservations(data.text))
                .catch(() => {});
        } else {
            getJson<{ text: string }>(`/contracts/${contract.id}/technical-opinion/collective`)
                .then(data => setCollective(data.text))
                .catch(err => setError(err instanceof Error ? err.message : "Erro ao carregar pareceres"));
        }
    }, [open, contract, souFiscal]);

    useEffect(() => {
        if (!contract) return;
        if (!observations.trim()) {
            setPreview("");
            return;
        }

        const timer = setTimeout(() => {
            setLoadingPreview(true);
            setError("");
            postJson<{ text: string }>(
                `/contracts/${contract.id}/technical-opinion/preview`,
                { text: observations }
            )
                .then(data => setPreview(data.text))
                .catch(err => setError(err instanceof Error ? err.message : "Erro ao gerar prévia"))
                .finally(() => setLoadingPreview(false));
        }, 1000);

        return () => clearTimeout(timer);
    }, [observations, contract]);

    if (!contract) return null;

    const meuFiscal = contract.fiscais.find(f => f.username === auth.username);

    function atualizarPreview(texto: string) {
        setObservations(texto);
        setCopiado(false);
    }

    async function copiarTexto() {
        try {
            if (!previewRef.current) throw new Error("Prévia indisponível");
            await copyRenderedContent(previewRef.current);
            setCopiado(true);
        } catch {
            setError("Não foi possível copiar o texto. Copie manualmente antes de enviar.");
        }
    }

    async function enviar() {
        setSubmitting(true);
        setError("");
        try {
            await postJson<{ text: string }>(
                `/contracts/${contract!.id}/technical-opinion/submit`,
                { text: observations }
            );
            onSubmitted();
            onClose();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Erro ao enviar parecer");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle>Parecer técnico — {contract.numberContract}</DialogTitle>
            <DialogContent dividers>
                <Typography variant="subtitle2" color="text.secondary" mb={1}>Progresso dos fiscais</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap mb={2}>
                    {progress.map(p => (
                        <Chip
                            key={p.fiscalName}
                            label={p.fiscalName}
                            size="small"
                            color={p.submitted ? "success" : "default"}
                            variant={p.submitted ? "filled" : "outlined"}
                        />
                    ))}
                </Stack>

                <Divider sx={{ mb: 2 }} />

                {meuFiscal ? (
                    <>
                        <TextField
                            label="Sua opinião/parecer"
                            multiline
                            minRows={4}
                            fullWidth
                            value={observations}
                            onChange={e => atualizarPreview(e.target.value)}
                            sx={{ mb: 2 }}
                        />

                        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
                            <Typography variant="subtitle2" color="text.secondary">Prévia do seu parecer</Typography>
                            <Tooltip title="Copiar parecer completo">
                                <Button
                                    size="small"
                                    startIcon={<ContentCopyIcon fontSize="small" />}
                                    onClick={copiarTexto}
                                    color={copiado ? "success" : "primary"}
                                    disabled={!preview}
                                >
                                    {copiado ? "Copiado" : "Copiar template inteiro"}
                                </Button>
                            </Tooltip>
                        </Stack>

                        <Box ref={previewRef} sx={{ "& p": { my: 1 }, minHeight: 240 }}>
                            {loadingPreview ? (
                                <Box py={4} display="grid" sx={{ placeItems: "center" }}><CircularProgress size={24} /></Box>
                            ) : (
                                <ReactMarkdown remarkPlugins={[remarkGfm]}>{preview || "Digite sua opinião para ver a prévia."}</ReactMarkdown>
                            )}
                        </Box>

                        {!copiado && observations.trim() && (
                            <Alert severity="info" sx={{ mt: 2 }}>
                                Copie o texto acima antes de enviar.
                            </Alert>
                        )}
                    </>
                ) : (
                    <>
                        <Typography variant="subtitle2" color="text.secondary" mb={1}>
                            Pareceres enviados pelos fiscais
                        </Typography>
                        <Box sx={{ "& p": { my: 1 }, minHeight: 240 }}>
                            <ReactMarkdown remarkPlugins={[remarkGfm]}>{collective || "Nenhum fiscal enviou parecer ainda."}</ReactMarkdown>
                        </Box>
                    </>
                )}

                {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Fechar</Button>
                {meuFiscal && (
                    <Tooltip title={copiado ? "" : "Copie o texto antes de enviar"}>
                        <span>
                            <Button
                                onClick={enviar}
                                variant="contained"
                                disabled={submitting || !copiado || !observations.trim()}
                            >
                                {submitting ? "Enviando..." : "Enviar parecer"}
                            </Button>
                        </span>
                    </Tooltip>
                )}
            </DialogActions>
        </Dialog>
    );
}