package contratos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"contract_id", "fiscal_id"}))
public class TechnicalOpinionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser fiscal;

    @Column(columnDefinition = "TEXT")
    private String observations;

    private LocalDateTime updatedAt;

    public TechnicalOpinionEntry(Contract contract, AppUser fiscal, String observations) {
        this.contract = contract;
        this.fiscal = fiscal;
        this.observations = observations;
        this.updatedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
    }
    public void updateObservations(String observations){
        this.observations = observations;
        this.updatedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
    }
}
