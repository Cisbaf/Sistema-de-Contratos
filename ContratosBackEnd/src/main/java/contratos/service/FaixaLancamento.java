package contratos.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Faixa de lançamento de um contrato (LC-10): do mês de início ao mês de término da vigência, inclusive.
 * A competência de um lançamento precisa cair dentro dela e a parcela, quando informada, vai de 1 até o
 * número de meses da faixa. Usada na validação de criar/editar lançamento e na consulta de lançamentos que
 * ficariam fora de datas propostas (aviso ao editar o contrato).
 */
public record FaixaLancamento(YearMonth inicio, YearMonth fim) {
    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MM/yyyy");

    public static FaixaLancamento de(LocalDate startDate, LocalDate endDate) {
        return new FaixaLancamento(YearMonth.from(startDate), YearMonth.from(endDate));
    }

    /** Quantidade de meses da vigência, contando o mês de início e o de término. */
    public int totalParcelas() {
        return (int) ChronoUnit.MONTHS.between(inicio, fim) + 1;
    }

    public boolean competenciaDentro(LocalDate competencia) {
        YearMonth comp = YearMonth.from(competencia);
        return !comp.isBefore(inicio) && !comp.isAfter(fim);
    }

    /**
     * Parcela vazia ou nula conta como dentro (é opcional). Texto que não é um número inteiro de 1 até o total
     * (lançamentos antigos, gravados quando o campo aceitava qualquer coisa) conta como fora.
     */
    public boolean parcelaDentro(String parcela) {
        if (parcela == null || parcela.isBlank()) return true;
        try {
            int numero = Integer.parseInt(parcela.trim());
            return numero >= 1 && numero <= totalParcelas();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public String inicioFmt() {
        return inicio.format(MES_ANO);
    }

    public String fimFmt() {
        return fim.format(MES_ANO);
    }
}
