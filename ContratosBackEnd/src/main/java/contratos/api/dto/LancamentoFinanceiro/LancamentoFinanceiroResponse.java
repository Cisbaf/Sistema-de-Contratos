package contratos.api.dto.LancamentoFinanceiro;

import contratos.api.dto.User.UserSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LancamentoFinanceiroResponse(
        Long id,
        Long contratoId,
        String numeroProcesso,
        String notaFiscal,
        String parcela,
        LocalDate competencia,
        BigDecimal valorNota,
        String observacoes,
        LocalDateTime criadoEm,
        UserSummary criadoPor,
        LocalDateTime atualizadoEm,
        UserSummary atualizadoPor
) {
}
