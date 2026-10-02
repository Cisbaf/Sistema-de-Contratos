// Datas "yyyy-MM-dd" (o formato do backend e do <input type="date">) tratadas sempre em UTC,
// para o fuso do navegador nunca deslocar um dia.

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export const isIsoDate = (value: string) => ISO_DATE.test(value);

/** Hoje no fuso de São Paulo, no formato yyyy-MM-dd. */
export function todayIso(): string {
  return new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Sao_Paulo" }).format(new Date());
}

export function addDaysIso(iso: string, days: number): string {
  const date = new Date(`${iso}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/**
 * Soma meses como o `LocalDate.plusMonths` do Java: se o dia não existir no mês de destino, corta no último dia
 * (31/01 + 1 mês = 28/02). O `setMonth` do JavaScript estouraria para março, e a tela e o backend discordariam.
 */
export function addMonthsIso(iso: string, months: number): string {
  const [year, month, day] = iso.split("-").map(Number);
  const target = new Date(Date.UTC(year, month - 1 + months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(day, lastDay));
  return target.toISOString().slice(0, 10);
}

export function formatDateBr(iso: string): string {
  return new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(new Date(`${iso}T00:00:00Z`));
}
