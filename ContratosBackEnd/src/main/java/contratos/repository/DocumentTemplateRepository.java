package contratos.repository;

import contratos.domain.DocumentTemplate;
import contratos.domain.enums.DocumentTemplateType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {
    boolean existsBySectorIdAndTemplateType(Long sectorId, DocumentTemplateType templateType);
}
