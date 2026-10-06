"use client";

import { tempoRestante } from "@/lib/vigencia";
import { Chip } from "@mui/material";

// Indicador de tempo restante da vigência; não renderiza nada enquanto o contrato está longe do fim.
export default function VigenciaChip({ endDate }: { endDate: string }) {
  const tempo = tempoRestante(endDate);
  if (!tempo) return null;
  return <Chip label={tempo.label} color={tempo.color} size="small" variant="outlined" sx={{ mt: 0.5 }} />;
}
