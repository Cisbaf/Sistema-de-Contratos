package contratos.repository;

import contratos.domain.Contract;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    @Override
    @EntityGraph(attributePaths = {"fiscais", "fiscais.sector"})
    List<Contract> findAll();

    @EntityGraph(attributePaths = {"fiscais", "fiscais.sector"})
    List<Contract> findDistinctByFiscaisUsername(String username);

    @Query("select case when count(c) > 0 then true else false end "
            + "from Contract c join c.fiscais fiscal "
            + "where c.id = :contractId and fiscal.username = :username")
    boolean existsForFiscal(@Param("contractId") Long contractId, @Param("username") String username);

    long countByFiscaisId(Long userId);

    boolean existsByNumberContractIgnoreCase(String numberContract);

    boolean existsByNumberContractIgnoreCaseAndIdNot(String numberContract, Long id);

    boolean existsByFiscaisId(Long id);
}
