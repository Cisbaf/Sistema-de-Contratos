package contratos.repository;

import contratos.domain.ContractStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractStatusHistoryRepository extends JpaRepository<ContractStatusHistory, Long> {

}
