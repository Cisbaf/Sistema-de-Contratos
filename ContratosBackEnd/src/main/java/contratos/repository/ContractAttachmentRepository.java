package contratos.repository;

import contratos.api.dto.Contract.ContractTimelineEvent;
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

    /** Envios de anexo (inclusive dos já removidos) para a linha do tempo. */
    @Query("""
            select new contratos.api.dto.Contract.ContractTimelineEvent(a.uploadedAt, u.name, a.fileName, a.attType, false)
            from ContractAttachment a left join a.uploadedBy u
            where a.contract.id = :contractId
            """)
    List<ContractTimelineEvent> timelineUploads(@Param("contractId") Long contractId);

    /** Remoções de anexo para a linha do tempo. */
    @Query("""
            select new contratos.api.dto.Contract.ContractTimelineEvent(a.removedAt, r.name, a.fileName, a.attType, true)
            from ContractAttachment a left join a.removedBy r
            where a.contract.id = :contractId and a.ativo = false and a.removedAt is not null
            """)
    List<ContractTimelineEvent> timelineRemovals(@Param("contractId") Long contractId);
}
