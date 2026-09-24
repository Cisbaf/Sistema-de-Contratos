package contratos.api.dto.LancamentoFinanceiro;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LancamentoFinanceiroRequest(
        @NotBlank @Size(max = 255) String numeroProcesso,
        @NotBlank @Size(max = 255) String notaFiscal,
        @Size(max = 255) String parcela,
        @NotNull LocalDate competencia,
        @NotNull @Positive BigDecimal valorNota,
        String observacoes
) {
}
