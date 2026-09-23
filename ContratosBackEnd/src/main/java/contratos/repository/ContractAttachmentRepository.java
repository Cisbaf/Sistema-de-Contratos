package contratos.repository;

import contratos.domain.ContractAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractAttachmentRepository extends JpaRepository<ContractAttachment, Long> {
    List<ContractAttachment> findByContract_IdAndAtivoTrueOrderByUploadedAtDesc(Long contractId);

    List<ContractAttachment> findByContract_IdOrderByUploadedAtAsc(Long contractId);

    void deleteByContract_Id(Long contractId);
}
