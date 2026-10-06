package contratos.api.dto.LancamentoFinanceiro;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LancamentoFinanceiroRequest(
        @NotBlank @Size(max = 255) String numeroProcesso,
        @NotBlank @Size(max = 255) String notaFiscal,
        @Size(max = 255) @Pattern(regexp = "^([1-9]\\d{0,2})?$", message = "Parcela deve ser um número de 1 a 999") String parcela,
        @NotNull LocalDate competencia,
        @NotNull @Positive BigDecimal valorNota,
        String observacoes
) {
}
