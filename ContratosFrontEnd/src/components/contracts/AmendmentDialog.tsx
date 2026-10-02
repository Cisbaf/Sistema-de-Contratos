"use client";

import { postForm } from "@/lib/api";
import { ALLOWED_EXTENSIONS, tamanho, validarArquivo } from "@/lib/attachments";
import { addDaysIso, addMonthsIso, formatDateBr, isIsoDate, todayIso } from "@/lib/dates";
import type { Contract } from "@/types";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import {
    Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle,
    Stack, Step, StepLabel, Stepper, TextField, Typography,
} from "@mui/material";
import { ChangeEvent, useEffect, useRef, useState } from "react";

const STEPS = ["Nova data", "Documento", "Confirmação"];

/**
 * Registro do Termo Aditivo (TA-10): o contrato em "Renovação aberta no SEI" volta a "Em vigência" com a nova data.
 * Três passos (data, documento, confirmação); a confirmação avisa que a ação não pode ser desfeita.
 * O backend (POST /contracts/{id}/amendments) é quem garante as regras; aqui só se avisa antes de enviar.
 */
export default function AmendmentDialog({ open, contract, onClose, onRegistered }: {
    open: boolean;
    contract: Contract | null;
    onClose: () => void;
    onRegistered: () => void;
}) {
    const [step, setStep] = useState(0);
    const [newEndDate, setNewEndDate] = useState("");
    const [file, setFile] = useState<File | null>(null);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (!open) return;
        setStep(0);
        setNewEndDate("");
        setFile(null);
        setError("");
        setSubmitting(false);
    }, [open]);

    if (!contract) return null;

    const minDate = addDaysIso(contract.endDate, 1);
    const maxMonths = contract.maxExtensionMonths;
    const maxDate = maxMonths !== null ? addMonthsIso(contract.endDate, maxMonths) : null;
    const limitLabel = maxMonths !== null ? `limite de prorrogação de ${maxMonths} ${maxMonths === 1 ? "mês" : "meses"}` : null;
    const limitText = maxDate !== null && limitLabel !== null
        ? `Pelo ${limitLabel}, a data máxima é ${formatDateBr(maxDate)}.`
        : "Este contrato não tem limite de prorrogação definido.";

    function dateProblem(): string | null {
        if (!isIsoDate(newEndDate)) return "Informe a nova data de fim da vigência.";
        if (newEndDate < minDate) return `A nova data deve ser posterior à atual (${formatDateBr(contract!.endDate)}).`;
        if (maxDate !== null && newEndDate > maxDate) {
            return `A nova data não pode passar de ${formatDateBr(maxDate)} (${limitLabel}).`;
        }
        return null;
    }

    function chooseFile(event: ChangeEvent<HTMLInputElement>) {
        const chosen = event.target.files?.[0] ?? null;
        event.target.value = ""; // permite escolher o mesmo arquivo de novo depois
        if (!chosen) return;
        const problem = validarArquivo(chosen);
        if (problem) {
            setError(problem);
            return;
        }
        setError("");
        setFile(chosen);
    }

    function next() {
        setError("");
        if (step === 0) {
            const problem = dateProblem();
            if (problem) { setError(problem); return; }
        }
        if (step === 1 && !file) {
            setError("Selecione o documento do Termo Aditivo.");
            return;
        }
        setStep(current => current + 1);
    }

    function back() {
        setError("");
        setStep(current => current - 1);
    }

    async function confirm() {
        if (!contract || !file) return;
        setSubmitting(true);
        setError("");
        try {
            const form = new FormData();
            form.append("file", file);
            form.append("newEndDate", newEndDate);
            await postForm<Contract>(`/contracts/${contract.id}/amendments`, form);
            onRegistered();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Erro ao registrar o Termo Aditivo");
        } finally {
            setSubmitting(false);
        }
    }

    // Se a nova data está a menos de 6 meses de hoje, o job diário devolve o contrato à renovação na noite seguinte.
    const reopensSoon = isIsoDate(newEndDate) && newEndDate <= addMonthsIso(todayIso(), 6);

    return (
        <Dialog
            open={open}
            onClose={(_, reason) => { if (!submitting && reason !== "backdropClick") onClose(); }}
            maxWidth="sm"
            fullWidth
        >
            <DialogTitle>Registrar Termo Aditivo — {contract.numberContract}</DialogTitle>
            <DialogContent dividers>
                <Stepper activeStep={step} alternativeLabel sx={{ mb: 3 }}>
                    {STEPS.map(label => <Step key={label}><StepLabel>{label}</StepLabel></Step>)}
                </Stepper>

                {step === 0 && (
                    <Stack spacing={2}>
                        <Typography variant="body2">
                            Vigência atual: {formatDateBr(contract.startDate)} a {formatDateBr(contract.endDate)}.
                        </Typography>
                        <TextField
                            label="Nova data de fim da vigência"
                            type="date"
                            value={newEndDate}
                            onChange={event => setNewEndDate(event.target.value)}
                            required
                            autoFocus
                            helperText={`${limitText} Deve ser posterior a ${formatDateBr(contract.endDate)}.`}
                            slotProps={{
                                inputLabel: { shrink: true },
                                htmlInput: { min: minDate, ...(maxDate ? { max: maxDate } : {}) },
                            }}
                        />
                    </Stack>
                )}

                {step === 1 && (
                    <Stack spacing={2}>
                        <Typography variant="body2">
                            Anexe o documento do Termo Aditivo assinado (PDF, DOC ou DOCX, até 30 MB).
                        </Typography>
                        <input ref={inputRef} type="file" hidden accept={ALLOWED_EXTENSIONS.join(",")} onChange={chooseFile} />
                        <Box>
                            <Button variant="outlined" startIcon={<UploadFileIcon />} onClick={() => inputRef.current?.click()}>
                                {file ? "Trocar arquivo" : "Escolher arquivo"}
                            </Button>
                        </Box>
                        {file && (
                            <Typography variant="body2" sx={{ wordBreak: "break-word" }}>
                                Arquivo selecionado: <strong>{file.name}</strong> ({tamanho(file.size)})
                            </Typography>
                        )}
                    </Stack>
                )}

                {step === 2 && (
                    <Stack spacing={2}>
                        <Box>
                            <Typography variant="caption" color="text.secondary">Vigência</Typography>
                            <Typography variant="body2">
                                {formatDateBr(contract.endDate)} → <strong>{formatDateBr(newEndDate)}</strong> (fim da vigência)
                            </Typography>
                        </Box>
                        <Box>
                            <Typography variant="caption" color="text.secondary">Documento do Termo Aditivo</Typography>
                            <Typography variant="body2" sx={{ wordBreak: "break-word" }}>
                                {file?.name} ({file ? tamanho(file.size) : ""})
                            </Typography>
                        </Box>
                        <Alert severity="warning">
                            <strong>Esta ação não pode ser desfeita.</strong> O contrato voltará para &quot;Em vigência&quot;,
                            a nova data passa a valer, o número do TA aumenta e as confirmações de interesse e os pareceres
                            do ciclo anterior serão zerados.
                        </Alert>
                        {reopensSoon && (
                            <Alert severity="info">
                                A nova data está a menos de 6 meses de hoje: na próxima atualização diária (01:00) o contrato
                                volta para &quot;Aguardando e-mail de interesse&quot;. É o comportamento esperado.
                            </Alert>
                        )}
                    </Stack>
                )}

                {error && <Alert severity="error" sx={{ mt: 2 }} onClose={() => setError("")}>{error}</Alert>}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={submitting}>Cancelar</Button>
                {step > 0 && <Button onClick={back} disabled={submitting}>Voltar</Button>}
                {step < STEPS.length - 1 ? (
                    <Button variant="contained" onClick={next}>Próximo</Button>
                ) : (
                    <Button
                        variant="contained"
                        color="warning"
                        onClick={confirm}
                        disabled={submitting}
                        startIcon={submitting ? <CircularProgress size={16} color="inherit" /> : undefined}
                    >
                        Confirmar Termo Aditivo
                    </Button>
                )}
            </DialogActions>
        </Dialog>
    );
}
