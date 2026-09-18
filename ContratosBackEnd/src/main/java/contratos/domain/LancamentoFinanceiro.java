package contratos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    private String parcela;
    private LocalDate competencia;

    @Column(nullable = false)
    private BigDecimal valorNota;
    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Contract contrato;


    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private AppUser criadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser atualizadoPor;


}
