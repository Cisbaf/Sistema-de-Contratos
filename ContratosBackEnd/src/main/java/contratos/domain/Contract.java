package contratos.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "contracts")
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String numberContract;
    @Column(nullable = false, length = 200)
    private String numberProcess;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String object;
    @Column(nullable = false, length = 200)
    private String company;
    @Column(nullable = false, length = 30)
    private String cnpjCpf;
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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ContractStatus status = ContractStatus.EM_VIGENCIA;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "contract_fiscais",
            joinColumns = @JoinColumn(name = "contract_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<AppUser> fiscais = new LinkedHashSet<>();

    public void update(String numberContract, String numberProcess, String object, String company,
                       String cnpjCpf, BigDecimal valueGlobal, BigDecimal valueMensal,
                       LocalDate startDate, LocalDate endDate, String font, String ta, Set<AppUser> fiscais) {
        this.numberContract = numberContract;
        this.numberProcess = numberProcess;
        this.object = object;
        this.company = company;
        this.cnpjCpf = cnpjCpf;
        this.valueGlobal = valueGlobal;
        this.valueMensal = valueMensal;
        this.startDate = startDate;
        this.endDate = endDate;
        this.font = font;
        this.ta = ta;
        this.fiscais.clear();
        this.fiscais.addAll(fiscais);
    }
}
