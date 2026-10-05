package contratos.repository;

import contratos.api.dto.Contract.ContractTimelineEvent;
import contratos.domain.ContractStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContractStatusHistoryRepository extends JpaRepository<ContractStatusHistory, Long> {
    void deleteByContract_Id(Long contractId);

    /** Mudanças de status do contrato para a linha do tempo. Sem usuário = feito pelo sistema (prazo). */
    @Query("""
            select new contratos.api.dto.Contract.ContractTimelineEvent(h.changedAt, u.name, h.previousStatus, h.newStatus, h.statusTrigger)
            from ContractStatusHistory h left join h.changedBy u
            where h.contract.id = :contractId
            """)
    List<ContractTimelineEvent> timeline(@Param("contractId") Long contractId);
}
