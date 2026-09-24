package contratos.api.dto.LancamentoFinanceiro;

import contratos.api.dto.User.UserSummary;
import contratos.domain.enums.TipoEventoLancamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Snapshot do lançamento como ele era ANTES do evento (edição ou exclusão). */
public record LancamentoFinanceiroHistoricoResponse(
        Long id,
        Long lancamentoId,
        Long contratoId,
        TipoEventoLancamento tipoEvento,
        String numeroProcesso,
        String notaFiscal,
        String parcela,
        LocalDate competencia,
        BigDecimal valorNota,
        String observacoes,
        LocalDateTime alteradoEm,
        UserSummary alteradoPor
) {
}
