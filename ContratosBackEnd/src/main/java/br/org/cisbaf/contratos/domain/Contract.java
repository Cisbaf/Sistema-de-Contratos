package br.org.cisbaf.contratos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "contract_fiscais",
            joinColumns = @JoinColumn(name = "contract_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<AppUser> fiscais = new LinkedHashSet<>();

    public Contract() {}

    public Long getId() { return id; }
    public String getNumberContract() { return numberContract; }
    public String getNumberProcess() { return numberProcess; }
    public String getObject() { return object; }
    public String getCompany() { return company; }
    public String getCnpjCpf() { return cnpjCpf; }
    public BigDecimal getValueGlobal() { return valueGlobal; }
    public BigDecimal getValueMensal() { return valueMensal; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getFont() { return font; }
    public String getTa() { return ta; }
    public Set<AppUser> getFiscais() { return fiscais; }

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
