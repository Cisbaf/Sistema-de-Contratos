package contratos.domain;

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
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"nota_fiscal", "contrato_id"}))
public class LancamentoFinanceiro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String numeroProcesso;
    @Column(nullable = false)
    private String notaFiscal;
    @Column(nullable = false)
    private LocalDate competencia;

    private String parcela;
    private boolean ativo = true;

    @Column(nullable = false)
    private BigDecimal valorNota;
    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Contract contrato;

    @Column(nullable = false)
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private AppUser criadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser atualizadoPor;

    public LancamentoFinanceiro(String numeroProcesso, String notaFiscal, LocalDate competencia, String parcela,
                                BigDecimal valorNota, String observacoes, Contract contrato,
                                AppUser criadoPor) {
        this.numeroProcesso = numeroProcesso;
        this.notaFiscal = notaFiscal;
        this.competencia = competencia;
        this.parcela = parcela;
        this.valorNota = valorNota;
        this.observacoes = observacoes;
        this.contrato = contrato;
        this.criadoEm = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.criadoPor = criadoPor;
    }

    public void updateLancamento(String numeroProcesso, String notaFiscal, LocalDate competencia, String parcela,
                                 BigDecimal valorNota, String observacoes,
                                 AppUser atualizadoPor) {
        this.numeroProcesso = numeroProcesso;
        this.notaFiscal = notaFiscal;
        this.competencia = competencia;
        this.parcela = parcela;
        this.valorNota = valorNota;
        this.observacoes = observacoes;
        this.atualizadoEm = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.atualizadoPor = atualizadoPor;
    }

    public void desativaLancamento(AppUser atualizadoPor) {
        this.ativo = false;
        this.atualizadoEm = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.atualizadoPor = atualizadoPor;
    }
}
