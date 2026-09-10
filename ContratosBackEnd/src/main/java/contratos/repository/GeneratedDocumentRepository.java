package contratos.repository;

import contratos.domain.GeneratedDocument;
import contratos.domain.enums.DocumentTemplateType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, Long> {

    List<GeneratedDocument> findByContractIdAndDocumentTypeOrderByVersionDesc(Long contractId, DocumentTemplateType documentType);
}
