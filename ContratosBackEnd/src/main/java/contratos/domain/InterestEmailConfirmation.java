package contratos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"contract_id", "fiscal_id"}))
public class InterestEmailConfirmation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Contract contract;
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser fiscal;
    private LocalDateTime confirmedAt;

    public InterestEmailConfirmation(Contract contract, AppUser fiscal, LocalDateTime confirmedAt) {
        this.contract = contract;
        this.fiscal = fiscal;
        this.confirmedAt = confirmedAt;
    }
}
