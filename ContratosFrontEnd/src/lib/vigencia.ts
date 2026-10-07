import type { ChipProps } from "@mui/material";

// Contagem só aparece depois que o contrato entra na janela do alerta de 4 meses (mesmo prazo padrão das notificações).
const JANELA_MESES = 4;
const DIA_MS = 86_400_000;

export type TempoRestante = { label: string; color: ChipProps["color"] };

// Data de término "yyyy-MM-dd" menos N meses, em UTC, sem estourar o mês (31/03 - 1 mês = 28/02, como o minusMonths do Java).
function menosMeses(fim: Date, meses: number) {
  const ano = fim.getUTCFullYear();
  const mes = fim.getUTCMonth() - meses;
  const ultimoDia = new Date(Date.UTC(ano, mes + 1, 0)).getUTCDate();
  return Date.UTC(ano, mes, Math.min(fim.getUTCDate(), ultimoDia));
}

/**
 * Indicador de quanto falta para o fim da vigência. Devolve null fora da janela (ainda longe do fim).
 * Neutro com mais de 8 semanas, amarelo de 8 semanas até 1 semana, vermelho na última semana e depois de vencido.
 */
export function tempoRestante(endDate: string, hoje: Date = new Date()): TempoRestante | null {
  const fim = new Date(`${endDate}T00:00:00Z`);
  if (Number.isNaN(fim.getTime())) return null;

  // "Hoje" pela data local do usuário, comparada em UTC com a data de término (que não tem horário).
  const hojeUtc = Date.UTC(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());
  const dias = Math.round((fim.getTime() - hojeUtc) / DIA_MS);

  if (estaVencido(endDate, hoje)) return { label: "Vencido", color: "error" };
  if (hojeUtc < menosMeses(fim, JANELA_MESES)) return null;

  if (dias === 0) return { label: "Vence hoje", color: "error" };
  if (dias === 1) return { label: "Falta 1 dia", color: "error" };
  if (dias <= 7) return { label: `Faltam ${dias} dias`, color: "error" };

  const semanas = Math.floor(dias / 7);
  const label = semanas === 1 ? "Falta 1 semana" : `Faltam ${semanas} semanas`;
  return { label, color: dias <= 56 ? "warning" : "default" };
}

export function estaVencido(endDate: string, hoje: Date = new Date()): boolean {
  const fim = new Date(`${endDate}T00:00:00Z`);
  if (Number.isNaN(fim.getTime())) return false;
  const hojeUtc = Date.UTC(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());
  return fim.getTime() < hojeUtc;
}


// Faixa permitida para os lançamentos financeiros: do mês de início ao mês de término da vigência (inclusive).
// Espelha a regra do backend (LancamentoFinanceiroService); o backend continua sendo quem decide.
// Os meses viram um índice (ano * 12 + mês) para comparar sem mexer com datas e fusos.
export function faixaDeLancamento(startDate: string, endDate: string) {
  const indice = (iso: string) => Number(iso.slice(0, 4)) * 12 + Number(iso.slice(5, 7)) - 1;
  const rotulo = (i: number) => `${String((i % 12) + 1).padStart(2, "0")}/${Math.floor(i / 12)}`;
  const inicio = indice(startDate);
  const fim = indice(endDate);
  return { inicio, fim, inicioLabel: rotulo(inicio), fimLabel: rotulo(fim), totalParcelas: fim - inicio + 1 };
}

// LC-10: o lançamento já gravado está fora da faixa do contrato (competência fora dos meses da vigência ou
// parcela que não é um inteiro de 1 até o total de meses)? Mesma regra do backend (`FaixaLancamento`); parcela
// vazia vale; texto de lançamento antigo ("1/12", "única") conta como fora.
export function lancamentoForaDaFaixa(competencia: string, parcela: string | null | undefined, startDate: string, endDate: string): boolean {
  const faixa = faixaDeLancamento(startDate, endDate);
  const comp = Number(competencia.slice(0, 4)) * 12 + Number(competencia.slice(5, 7)) - 1;
  if (comp < faixa.inicio || comp > faixa.fim) return true;
  const p = (parcela ?? "").trim();
  if (p === "") return false;
  if (!/^\d+$/.test(p)) return true;
  const numero = Number(p);
  return numero < 1 || numero > faixa.totalParcelas;
}
