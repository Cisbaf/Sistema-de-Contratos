"use client";

import { downloadFile, getJson, postJson } from "@/lib/api";
import { copyRenderedContent } from "@/lib/clipboard";
import type { Lancamento } from "@/types";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import DownloadIcon from "@mui/icons-material/Download";
import { Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Typography } from "@mui/material";
import { useEffect, useRef, useState } from "react";
import ReactMarkdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";

// Estilos INLINE de propósito: ao copiar, só o HTML vai para a área de transferência (sem as classes/CSS da tela).
// Sem isso a tabela do ateste chegaria ao SEI/Gmail sem bordas.
const cell = { border: "1px solid #ccc", padding: "10px 8px", textAlign: "left", fontWeight: "normal" } as const;
const markdownComponents: Components = {
  table: ({ children }) => <table style={{ width: "100%", borderCollapse: "collapse", margin: "18px 0 26px 0" }}>{children}</table>,
  th: ({ children }) => <th style={cell}>{children}</th>,
  td: ({ children }) => <td style={cell}>{children}</td>,
};

export default function PaymentChecklistDialog({ open, lancamento, onClose, onGenerated }: {
  open: boolean;
  lancamento: Lancamento | null;
  onClose: () => void;
  onGenerated: (message: string) => void;
}) {
  const [text, setText] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [copiado, setCopiado] = useState(false);
  const [baixando, setBaixando] = useState(false);
  const previewRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open || !lancamento) return;
    setError(""); setText(""); setCopiado(false); setLoading(true);
    getJson<{ content: string }>(`/lancamentos/${lancamento.id}/checklist/preview`)
      .then(data => setText(data.content))
      .catch(err => setError(err instanceof Error ? err.message : "Erro ao carregar o ateste"))
      .finally(() => setLoading(false));
  }, [open, lancamento]);

  if (!lancamento) return null;

  async function copiar() {
    try {
      if (!previewRef.current) throw new Error("Prévia indisponível");
      await copyRenderedContent(previewRef.current);
      setCopiado(true);
    } catch {
      setError("Não foi possível copiar o texto. Selecione e copie manualmente.");
    }
  }

  // Baixar o PDF gera uma nova versão guardada em "Documentos gerados"; copiar não guarda nada.
  async function baixarPdf() {
    if (!lancamento) return;
    setBaixando(true); setError("");
    try {
      const doc = await postJson<{ id: number; fileName: string; version: number }>(`/lancamentos/${lancamento.id}/checklist`);
      await downloadFile(`/generate-document/download?documentId=${doc.id}`, doc.fileName);
      onGenerated(`Ateste gerado em PDF (versão ${doc.version})`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Erro ao gerar o PDF");
    } finally {
      setBaixando(false);
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Ateste dos fiscais — NF {lancamento.notaFiscal}</DialogTitle>
      <DialogContent dividers>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
          <Typography variant="subtitle2" color="text.secondary">
            Copie e cole no SEI (ou no e-mail), ou baixe em PDF
          </Typography>
          <Button size="small" startIcon={<ContentCopyIcon fontSize="small" />} onClick={() => void copiar()}
            color={copiado ? "success" : "primary"} disabled={!text || loading}>
            {copiado ? "Copiado" : "Copiar texto"}
          </Button>
        </Stack>

        <Box ref={previewRef} sx={{ minHeight: 240, "& p": { my: 1 } }}>
          {loading
            ? <Box py={4} display="grid" sx={{ placeItems: "center" }}><CircularProgress size={24} /></Box>
            : <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>{text || "Nenhum conteúdo disponível."}</ReactMarkdown>}
        </Box>

        <Alert severity="info" sx={{ mt: 2 }}>
          O ateste leva o nome e o setor de quem está logado: cada fiscal gera e assina o seu.
        </Alert>
        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Fechar</Button>
        <Button variant="contained" startIcon={<DownloadIcon />} onClick={() => void baixarPdf()} disabled={!text || loading || baixando}>
          {baixando ? "Gerando..." : "Baixar PDF"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
