package contratos.repository;

import contratos.domain.InterestEmailConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InterestEmailConfirmationRepository extends JpaRepository<InterestEmailConfirmation, Long> {
    Long countByContract_Id(Long contractId);

    boolean existsByContract_IdAndFiscal_Id(Long contractId, Long fiscalId);

    List<InterestEmailConfirmation> findByContract_Id(Long contractId);

    void deleteByContract_Id(Long contractId);

    interface Confirmacao {
        Long getContractId();
        Long getFiscalId();
    }

    @Query("select c.contract.id as contractId, c.fiscal.id as fiscalId "
            + "from InterestEmailConfirmation c where c.contract.id in :contractIds order by c.id")
    List<Confirmacao> findConfirmacoes(@Param("contractIds") Collection<Long> contractIds);
}
