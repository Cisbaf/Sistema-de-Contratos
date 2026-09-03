package contratos.domain;

import contratos.domain.enums.ContractStatus;
import contratos.domain.enums.ContractStatusTrigger;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser changedBy;

    @JoinColumn(nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus previousStatus;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ContractStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatusTrigger statusTrigger;

    public ContractStatusHistory(LocalDateTime changedAt, AppUser changedBy, Contract contract, ContractStatus previousStatus, ContractStatus newStatus, ContractStatusTrigger statusTrigger) {
        this.changedAt = Objects.requireNonNull(changedAt);
        this.changedBy = changedBy;
        this.contract = Objects.requireNonNull(contract);
        this.previousStatus = Objects.requireNonNull(previousStatus);
        this.newStatus = Objects.requireNonNull(newStatus);
        this.statusTrigger = Objects.requireNonNull(statusTrigger);
    }
}
