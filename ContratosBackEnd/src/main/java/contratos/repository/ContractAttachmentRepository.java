package contratos.repository;

import contratos.domain.ContractAttachment;
import contratos.domain.enums.AttachmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContractAttachmentRepository extends JpaRepository<ContractAttachment, Long> {
    List<ContractAttachment> findByContract_IdAndAtivoTrueOrderByUploadedAtDesc(Long contractId);

    List<ContractAttachment> findByContract_IdOrderByUploadedAtAsc(Long contractId);

    long countByContract_IdAndAtivoTrue(Long contractId);

    long countByContract_IdAndAttType(Long contractId, AttachmentType attType);

    void deleteByContract_Id(Long contractId);

    @Query("select a.storagePath from ContractAttachment a where a.contract.id = :contractId and a.storagePath is not null")
    List<String> findStoragePathsByContractId(@Param("contractId") Long contractId);
}
