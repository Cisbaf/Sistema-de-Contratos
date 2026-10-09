"use client";

import { getJson } from "@/lib/api";
import { describeTimelineEvent, timelineAttachmentTypeLabels } from "@/lib/timeline";
import type { Contract, ContractTimelineEvent } from "@/types";
import {
    AttachFileOutlined, DescriptionOutlined, DeleteOutline, SwapHorizOutlined,
} from "@mui/icons-material";
import {
    Alert, Avatar, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
    DialogTitle, List, ListItem, ListItemAvatar, ListItemText, Typography,
} from "@mui/material";
import { useEffect, useState, type ReactNode } from "react";

const dataHora = (value: string) =>
    new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));

const icones: Record<ContractTimelineEvent["type"], ReactNode> = {
    STATUS_CHANGED: <SwapHorizOutlined fontSize="small" />,
    DOCUMENT_GENERATED: <DescriptionOutlined fontSize="small" />,
    ATTACHMENT_UPLOADED: <AttachFileOutlined fontSize="small" />,
    ATTACHMENT_REMOVED: <DeleteOutline fontSize="small" />,
    LANCAMENTO_DELETED: <DeleteOutline fontSize="small" />,
    DOCUMENT_DELETED: <DeleteOutline fontSize="small" />,
};

export default function ContractTimelineDialog({ open, contract, onClose }: {
    open: boolean;
    contract: Contract | null;
    onClose: () => void;
}) {
    const [events, setEvents] = useState<ContractTimelineEvent[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!open || !contract) return;
        setError("");
        setEvents([]);
        setLoading(true);
        getJson<ContractTimelineEvent[]>(`/contracts/${contract.id}/timeline`)
            .then(setEvents)
            .catch(err => setError(err instanceof Error ? err.message : "Erro ao carregar o histórico"))
            .finally(() => setLoading(false));
    }, [open, contract]);

    if (!contract) return null;

    return (
        <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
            <DialogTitle>Histórico do contrato — {contract.numberContract}</DialogTitle>
            <DialogContent dividers>
                {loading ? (
                    <Box py={4} display="grid" sx={{ placeItems: "center" }}><CircularProgress size={24} /></Box>
                ) : events.length === 0 && !error ? (
                    <Typography variant="body2" color="text.secondary">Nenhum evento registrado ainda.</Typography>
                ) : (
                    <List disablePadding>
                        {events.map((event, index) => {
                            const { title, detail } = describeTimelineEvent(event);
                            const quem = event.actorName ?? "Sistema";
                            return (
                                <ListItem key={`${event.type}-${event.occurredAt}-${index}`} alignItems="flex-start" disableGutters>
                                    <ListItemAvatar sx={{ minWidth: 48 }}>
                                        <Avatar sx={{ width: 32, height: 32 }}>{icones[event.type]}</Avatar>
                                    </ListItemAvatar>
                                    <ListItemText
                                        primary={
                                            <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                                                <Typography variant="body2" fontWeight={600}>{title}</Typography>
                                                {event.attType === "TERMO_ADITIVO" && (
                                                    <Chip size="small" label={timelineAttachmentTypeLabels.TERMO_ADITIVO} />
                                                )}
                                            </Box>
                                        }
                                        secondary={
                                            <>
                                                {detail && <Typography variant="body2" component="span" display="block" sx={{ wordBreak: "break-word" }}>{detail}</Typography>}
                                                <Typography variant="caption" color="text.secondary" component="span">
                                                    {quem} · {dataHora(event.occurredAt)}
                                                </Typography>
                                            </>
                                        }
                                    />
                                </ListItem>
                            );
                        })}
                    </List>
                )}
                {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Fechar</Button>
            </DialogActions>
        </Dialog>
    );
}
