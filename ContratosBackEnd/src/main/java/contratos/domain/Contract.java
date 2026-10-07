package contratos.domain;

import contratos.domain.enums.ContractStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "contracts")
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200, unique = true)
    private String numberContract;
    @Column(nullable = false, length = 200)
    private String numberProcess;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String object;
    @Column(nullable = false, length = 200)
    private String company;
    @Column(nullable = false, length = 14)
    private String cnpj;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valueGlobal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valueMensal;
    @Column(nullable = false)
    private LocalDate startDate;
    @Column(nullable = false)
    private LocalDate endDate;
    @Column(length = 200)
    private String font;
    @Column(length = 10)
    private String ta;
    @Column(nullable = false)
    private String seiProcessNumber;
    private Integer maxExtensionMonths;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ContractStatus status = ContractStatus.EM_VIGENCIA;

    @BatchSize(size = 50)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "contract_fiscais",
            joinColumns = @JoinColumn(name = "contract_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<AppUser> fiscais = new LinkedHashSet<>();

    public void update(String numberContract, String numberProcess, String object, String company,
                       String cnpj, BigDecimal valueGlobal, BigDecimal valueMensal,
                       LocalDate startDate, LocalDate endDate, String font, String ta, Set<AppUser> fiscais,
                       String seiProcessNumber, Integer maxExtensionMonths) {
        this.numberContract = numberContract;
        this.numberProcess = numberProcess;
        this.object = object;
        this.company = company;
        this.cnpj = cnpj;
        this.valueGlobal = valueGlobal;
        this.valueMensal = valueMensal;
        this.startDate = startDate;
        this.endDate = endDate;
        this.font = font;
        this.ta = ta;
        this.fiscais.clear();
        this.fiscais.addAll(fiscais);
        this.seiProcessNumber = seiProcessNumber;
        this.maxExtensionMonths = maxExtensionMonths;
    }

    public boolean updateStatusByDeadline(LocalDate referenceDate) {
        Objects.requireNonNull(referenceDate, "A data de referência é obrigatória");

        if (status != ContractStatus.EM_VIGENCIA) {
            return false;
        }

        if (referenceDate.isAfter(endDate)) {
            return false;
        }

        LocalDate sixMonthsBeforeEnd = endDate.minusMonths(6);

        if (referenceDate.isBefore(sixMonthsBeforeEnd)) {
            return false;
        }

        status = ContractStatus.AGUARDANDO_EMAIL_INTERESSE;
        return true;
    }

    /**
     * Recalcula o status depois que o término da vigência foi alterado na edição do contrato (ST-10).
     * <ul>
     *   <li>{@code EM_VIGENCIA}: avança para {@code AGUARDANDO_EMAIL_INTERESSE} se o novo término cair na janela de 6 meses;</li>
     *   <li>{@code AGUARDANDO_EMAIL_INTERESSE}: volta para {@code EM_VIGENCIA} se o novo término ficar a mais de 6 meses;</li>
     *   <li>{@code EMAIL_ENVIADO} / {@code RENOVACAO_ABERTA_SEI}: a renovação é cancelada (o ciclo era do término antigo)
     *       e o status é recalculado como se o contrato estivesse em vigência.</li>
     * </ul>
     * Devolve {@code true} se o status mudou.
     */
    public boolean recalculateStatusForNewEndDate(LocalDate referenceDate) {
        Objects.requireNonNull(referenceDate, "A data de referência é obrigatória");
        ContractStatus before = status;

        switch (status) {
            case AGUARDANDO_EMAIL_INTERESSE -> {
                if (referenceDate.isBefore(endDate.minusMonths(6))) {
                    status = ContractStatus.EM_VIGENCIA;
                }
            }
            case EMAIL_ENVIADO, RENOVACAO_ABERTA_SEI -> {
                status = ContractStatus.EM_VIGENCIA;
                updateStatusByDeadline(referenceDate);
            }
            case EM_VIGENCIA -> updateStatusByDeadline(referenceDate);
        }
        return status != before;
    }

    public boolean markInterestEmailSent() {
        if (status != ContractStatus.AGUARDANDO_EMAIL_INTERESSE) {
            return false;
        }
        status = ContractStatus.EMAIL_ENVIADO;
        return true;
    }

    public boolean markTechnicalOpinionGenerated() {
        if (this.status != ContractStatus.EMAIL_ENVIADO) return false;
        this.status = ContractStatus.RENOVACAO_ABERTA_SEI;
        return true;
    }

    public boolean registerAmendment(LocalDate newEndDate, String taLabel) {
        if (this.status != ContractStatus.RENOVACAO_ABERTA_SEI) return false;
        this.status = ContractStatus.EM_VIGENCIA;
        this.endDate = newEndDate;
        this.ta = taLabel;
        return true;
    }
}
