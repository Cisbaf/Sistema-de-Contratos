package contratos.api.dto.LancamentoFinanceiro;

import java.time.LocalDate;

/**
 * Lançamento ativo que ficaria fora da faixa (mês de início ao mês de término) de datas propostas para o contrato.
 * {@code competenciaFora} e {@code parcelaFora} dizem qual regra ele quebra (podem ser as duas).
 */
public record LancamentoForaDaFaixaResponse(
        Long id,
        String notaFiscal,
        LocalDate competencia,
        String parcela,
        boolean competenciaFora,
        boolean parcelaFora
) {
}
