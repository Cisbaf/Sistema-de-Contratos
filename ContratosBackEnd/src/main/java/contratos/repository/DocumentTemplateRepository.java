package contratos.repository;

import contratos.domain.DocumentTemplate;
import contratos.domain.enums.DocumentTemplateType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {
    boolean existsByTemplateType(DocumentTemplateType templateType);
    Optional<DocumentTemplate> findByTemplateType(DocumentTemplateType templateType);
}
