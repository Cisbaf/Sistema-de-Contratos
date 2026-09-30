package contratos.repository;

import contratos.domain.GeneratedDocument;
import contratos.domain.enums.DocumentTemplateType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, Long> {

    List<GeneratedDocument> findByContractIdAndDocumentTypeOrderByVersionDesc(Long contractId, DocumentTemplateType documentType);

    // Reaproveitar um documento já gerado pro mesmo lançamento (hoje só o ateste/PAYMENT_CHECKLIST usa isto).
    Optional<GeneratedDocument> findByDocumentTypeAndLancamento_Id(DocumentTemplateType documentType, Long lancamentoId);

    void deleteByContract_Id(Long contractId);

    @Query(value = """
            select a from GeneratedDocument a
            where (:contractId    is null or a.contract.id      = :contractId)
              and (:documentType  is null or a.documentType     = :documentType)
              and (:fromDate      is null or a.generatedAt     >= :fromDate)
              and (:toDate        is null or a.generatedAt     <  :toDate)
              and (:authorId      is null or a.generatedBy.id   = :authorId)
            order by a.generatedAt desc, a.id desc
            """,
            countQuery = """
                    select count(a) from GeneratedDocument a
                    where (:contractId    is null or a.contract.id      = :contractId)
                      and (:documentType  is null or a.documentType     = :documentType)
                      and (:fromDate      is null or a.generatedAt     >= :fromDate)
                      and (:toDate        is null or a.generatedAt     <  :toDate)
                      and (:authorId      is null or a.generatedBy.id   = :authorId)
                    """)
    Page<GeneratedDocument> search(@Param("contractId") Long contractId,
                                   @Param("documentType") DocumentTemplateType documentType,
                                   @Param("fromDate") LocalDateTime fromDate,
                                   @Param("toDate") LocalDateTime toDate,
                                   @Param("authorId") Long authorId,
                                   Pageable pageable);
}
