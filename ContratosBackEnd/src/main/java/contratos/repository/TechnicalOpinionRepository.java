package contratos.repository;

import contratos.domain.TechnicalOpinionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TechnicalOpinionRepository extends JpaRepository<TechnicalOpinionEntry, Long> {
    Optional<TechnicalOpinionEntry> findByContract_IdAndFiscal_Id(Long contractId, Long fiscalId);
    int countByContract_Id(Long contractId);
    void deleteByContract_Id(Long contractId);
    List<TechnicalOpinionEntry> findByContract_Id(Long contractId);

}
