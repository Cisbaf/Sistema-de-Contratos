package contratos.repository;

import contratos.domain.InterestEmailConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterestEmailConfirmationRepository extends JpaRepository<InterestEmailConfirmation, Long> {
    Long countByContract_Id(Long contractId);

    boolean existsByContract_IdAndFiscal_Id(Long contractId, Long fiscalId);

    List<InterestEmailConfirmation> findByContract_Id(Long contractId);
}
