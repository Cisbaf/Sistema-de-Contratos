"use client";

import PageHeader from "@/components/PageHeader";
import { useAuth } from "@/components/DashboardShell";
import { ajudaAbas, type AjudaSecao } from "@/lib/ajudaConteudo";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { Box, Button, List, ListItemButton, ListItemText, Paper, Stack, Tab, Tabs, Typography } from "@mui/material";
import Link from "next/link";
import { useState } from "react";
import ReactMarkdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";

// Tabelas largas rolam para o lado em vez de estourar a tela.
const markdownComponents: Components = {
  table: ({ children }) => <Box sx={{ overflowX: "auto", my: 2 }}><table>{children}</table></Box>,
};

const markdownSx = {
  fontSize: 15,
  lineHeight: 1.65,
  "& p": { my: 1.25 },
  "& ul, & ol": { pl: 3, my: 1.25 },
  "& li": { mb: .5 },
  "& table": { borderCollapse: "collapse", width: "100%", fontSize: 14 },
  "& th, & td": { border: "1px solid #E4EAF2", px: 1.5, py: 1, textAlign: "left", verticalAlign: "top" },
  "& th": { background: "#F8FAFC", fontWeight: 700, color: "#526071" },
} as const;

function irPara(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function Secao({ secao }: { secao: AjudaSecao }) {
  return <Paper id={secao.id} variant="outlined" sx={{ p: { xs: 2, md: 3 }, scrollMarginTop: { xs: 80, md: 24 } }}>
    <Typography variant="h6" gutterBottom>{secao.titulo}</Typography>
    <Box sx={markdownSx}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>{secao.texto}</ReactMarkdown>
    </Box>
    {secao.imagem && <Box component="figure" sx={{ m: 0, mt: 2 }}>
      <Box component="img" src={secao.imagem.src} alt={secao.imagem.legenda} loading="lazy"
        sx={{ display: "block", maxWidth: "100%", height: "auto", border: "1px solid #E4EAF2", borderRadius: 2 }} />
      <Typography component="figcaption" variant="caption" color="text.secondary" sx={{ display: "block", mt: .75 }}>
        {secao.imagem.legenda}
      </Typography>
    </Box>}
  </Paper>;
}

export default function AjudaPage() {
  const auth = useAuth();
  const abaInicial = auth.perfil === "FISCAL" ? "fiscal" : "comecar";
  const [abaId, setAbaId] = useState(abaInicial);
  const aba = ajudaAbas.find(a => a.id === abaId) ?? ajudaAbas[0];

  function trocarAba(id: string) {
    setAbaId(id);
    window.scrollTo({ top: 0 });
  }

  return <Box maxWidth={1200} mx="auto">
    {auth.perfil === "FISCAL" && <Button component={Link} href="/dashboard/contracts" startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>
      Voltar para contratos
    </Button>}
    <PageHeader title="Ajuda" subtitle="Passo a passo do sistema, por tarefa. Escolha uma aba e, dentro dela, o assunto no índice." />

    <Paper variant="outlined" sx={{ mb: 3 }}>
      <Tabs value={aba.id} onChange={(_, id: string) => trocarAba(id)} variant="scrollable" scrollButtons="auto" allowScrollButtonsMobile>
        {ajudaAbas.map(a => <Tab key={a.id} value={a.id} label={a.titulo} />)}
      </Tabs>
    </Paper>

    <Stack direction={{ xs: "column", md: "row" }} spacing={3} alignItems="flex-start">
      <Paper variant="outlined" component="nav" aria-label="Índice da aba"
        sx={{ width: { xs: "100%", md: 270 }, flexShrink: 0, position: { md: "sticky" }, top: { md: 24 }, p: 1 }}>
        <Typography variant="overline" color="text.secondary" sx={{ px: 1.5 }}>Neste tópico</Typography>
        <List dense disablePadding>
          {aba.secoes.map(s => <ListItemButton key={s.id} onClick={() => irPara(s.id)} sx={{ borderRadius: 2 }}>
            <ListItemText primary={s.titulo} />
          </ListItemButton>)}
        </List>
      </Paper>

      <Stack spacing={2.5} flex={1} minWidth={0} width="100%">
        <Typography color="text.secondary">{aba.resumo}</Typography>
        {aba.secoes.map(s => <Secao key={s.id} secao={s} />)}
      </Stack>
    </Stack>
  </Box>;
}
