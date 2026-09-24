package contratos.domain;

import contratos.domain.enums.TipoEventoLancamento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@NoArgsConstructor
public class LancamentoFinanceiroHistorico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private LancamentoFinanceiro lancamento;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Contract contrato;
    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private TipoEventoLancamento tipoEvento;

    // Snapshot dos dados do lançamento ANTES do evento
    @Column(nullable = false)
    private String numeroProcesso;
    @Column(nullable = false)
    private String notaFiscal;
    private String parcela;
    @Column(nullable = false)
    private LocalDate competencia;
    @Column(nullable = false)
    private BigDecimal valorNota;
    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(nullable = false)
    private LocalDateTime alteradoEm;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private AppUser alteradoPor;

    /**
     * Deve ser criado ANTES de aplicar a alteração no lançamento: copia os valores que ele tem neste momento.
     */
    public LancamentoFinanceiroHistorico(LancamentoFinanceiro lancamento, TipoEventoLancamento tipoEvento,
                                         AppUser alteradoPor) {
        this.lancamento = lancamento;
        this.contrato = lancamento.getContrato();
        this.tipoEvento = tipoEvento;

        this.numeroProcesso = lancamento.getNumeroProcesso();
        this.notaFiscal = lancamento.getNotaFiscal();
        this.parcela = lancamento.getParcela();
        this.competencia = lancamento.getCompetencia();
        this.valorNota = lancamento.getValorNota();
        this.observacoes = lancamento.getObservacoes();

        this.alteradoEm = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.alteradoPor = alteradoPor;
    }
}
