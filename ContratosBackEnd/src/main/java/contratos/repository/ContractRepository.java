package contratos.repository;

import contratos.domain.Contract;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
