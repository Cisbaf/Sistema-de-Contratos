"use client";

import { Feedback, PageLoading } from "@/components/Feedback";
import PageHeader from "@/components/PageHeader";
import { getJson, postJson, putJson } from "@/lib/api";
import { fillPlaceholdersWithSampleData, TEMPLATE_TYPE_LABELS, TEMPLATE_TYPE_VARIABLES } from "@/lib/templatePlaceholders";
import type { DocumentTemplate, DocumentTemplateType } from "@/types";
import { Alert, Box, Button, Chip, CircularProgress, Paper, Stack, Tab, Tabs, Tooltip, Typography } from "@mui/material";
import dynamic from "next/dynamic";
import { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

const MarkdownEditor = dynamic(() => import("@/components/MarkdownEditor"), {
  ssr: false,
  loading: () => <Box py={8} display="grid" sx={{ placeItems: "center" }}><CircularProgress size={28} /></Box>,
});

const TEMPLATE_TYPES: DocumentTemplateType[] = ["INTEREST_EMAIL", "TECHNICAL_OPINION", "SUPPLIER_RENEWAL_EMAIL"];

export default function TemplatesPage() {
  const [templates, setTemplates] = useState<DocumentTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [activeType, setActiveType] = useState<DocumentTemplateType>("INTEREST_EMAIL");
  const [content, setContent] = useState("");
  const [editorInstanceKey, setEditorInstanceKey] = useState(0);
  const [feedback, setFeedback] = useState({ message: "", error: false });

  async function load() {
    try {
      const data = await getJson<DocumentTemplate[]>("/document-templates");
      setTemplates(data);
      // Sincroniza o conteúdo com a aba atual usando o dado recém-buscado
      // (não o estado antigo de `templates`), sem forçar o editor a remontar:
      // se o texto já bate, um remonte aqui só atrapalharia quem está digitando.
      setContent(data.find(template => template.templateType === activeType)?.content ?? "");
    } catch (error) {
      setFeedback({ message: error instanceof Error ? error.message : "Erro ao carregar templates", error: true });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  // Troca de aba: muda o tipo ativo E o conteúdo na mesma função, para que o
  // React aplique as duas mudanças no mesmo ciclo de renderização. Se isso
  // fosse feito em dois passos (mudar a aba, e um useEffect separado corrigir
  // o conteúdo depois), o MDXEditor remontava com o texto antigo antes da
  // correção chegar, e como ele ignora atualizações de conteúdo depois de
  // montado, o texto certo nunca aparecia.
  function selectType(type: DocumentTemplateType) {
    setActiveType(type);
    setContent(templates.find(template => template.templateType === type)?.content ?? "");
    setEditorInstanceKey(key => key + 1);
  }

  async function save() {
    setSaving(true);
    try {
      const existing = templates.find(template => template.templateType === activeType);
      if (existing) {
        await putJson<DocumentTemplate>(`/document-templates/${existing.id}`, { content });
      } else {
        await postJson<DocumentTemplate>("/document-templates", { templateType: activeType, content });
      }
      setFeedback({ message: "Template salvo", error: false });
      await load();
    } catch (error) {
      setFeedback({ message: error instanceof Error ? error.message : "Erro ao salvar template", error: true });
    } finally {
      setSaving(false);
    }
  }

  const previewMarkdown = fillPlaceholdersWithSampleData(content);

  async function copyVariable(variable: string) {
    try {
      await navigator.clipboard.writeText(`{{${variable}}}`);
      setFeedback({ message: `{{${variable}}} copiado`, error: false });
    } catch {
      setFeedback({ message: "Não foi possível copiar", error: true });
    }
  }

  return <>
    <PageHeader
      title="Templates de documentos"
      subtitle="Edite o e-mail de interesse, o parecer técnico e a máscara enviada ao fornecedor. Use placeholders como {{numero_contrato}}."
    />

    {loading ? <PageLoading /> : <>
      <Tabs
        value={activeType}
        onChange={(_, value: DocumentTemplateType) => selectType(value)}
        sx={{ mb: 3, borderBottom: "1px solid #E4EAF2" }}
      >
        {TEMPLATE_TYPES.map(type => (
          <Tab
            key={type}
            value={type}
            label={
              <Stack direction="row" spacing={1} alignItems="center">
                <span>{TEMPLATE_TYPE_LABELS[type]}</span>
                {!templates.some(template => template.templateType === type) && (
                  <Chip label="não criado" size="small" variant="outlined" />
                )}
              </Stack>
            }
          />
        ))}
      </Tabs>

      <Paper variant="outlined" sx={{ p: 2, mb: 3, bgcolor: "#FAFBFD" }}>
        <Typography variant="subtitle2" color="text.secondary" mb={1}>
          Variáveis disponíveis para {TEMPLATE_TYPE_LABELS[activeType]} — clique para copiar
        </Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          {TEMPLATE_TYPE_VARIABLES[activeType].map(variable => (
            <Tooltip key={variable} title="Clique para copiar">
              <Chip
                label={`{{${variable}}}`}
                size="small"
                onClick={() => copyVariable(variable)}
                sx={{ fontFamily: "monospace", cursor: "pointer" }}
              />
            </Tooltip>
          ))}
        </Stack>
      </Paper>

      <Stack direction={{ xs: "column", lg: "row" }} spacing={3}>
        <Box flex={1} minWidth={0}>
          <Typography variant="subtitle2" color="text.secondary" mb={1}>Conteúdo (Markdown)</Typography>
          <Paper variant="outlined">
            <MarkdownEditor key={`${activeType}-${editorInstanceKey}`} value={content} onChange={setContent} />
          </Paper>
        </Box>

        <Box flex={1} minWidth={0}>
          <Typography variant="subtitle2" color="text.secondary" mb={1}>
            Prévia — como vai aparecer, com dados de exemplo
          </Typography>
          <Paper variant="outlined" sx={{ p: 3, minHeight: 380 }}>
            {content.trim() ? (
              <Box sx={{
                "& table": { borderCollapse: "collapse", width: "100%", my: 2 },
                "& th, & td": { border: "1px solid #E4EAF2", padding: "6px 10px" },
                "& p": { my: 1 },
              }}>
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{previewMarkdown}</ReactMarkdown>
              </Box>
            ) : (
              <Typography color="text.secondary">O conteúdo aparece aqui conforme você escreve.</Typography>
            )}
          </Paper>
        </Box>
      </Stack>

      <Alert severity="info" variant="outlined" sx={{ mt: 3 }}>
        A prévia usa dados de exemplo apenas para visualização. Nenhum dado real de contrato é usado aqui.
      </Alert>

      <Stack direction="row" justifyContent="flex-end" mt={3}>
        <Button variant="contained" size="large" onClick={save} disabled={saving || !content.trim()}>
          {saving ? "Salvando..." : "Salvar"}
        </Button>
      </Stack>
    </>}

    <Feedback message={feedback.message} error={feedback.error} onClose={() => setFeedback({ message: "", error: false })} />
  </>;
}
