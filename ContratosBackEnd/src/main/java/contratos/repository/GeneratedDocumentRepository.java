package contratos.repository;

import contratos.api.dto.Contract.ContractTimelineEvent;
import contratos.domain.GeneratedDocument;
import contratos.domain.enums.DocumentTemplateType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Só id, arquivo e versão (projeção): serve para registrar na auditoria sem carregar o PDF (LONGBLOB). */
    interface DocumentoResumo {
        Long getId();

        String getFileName();

        int getVersion();
    }

    List<DocumentoResumo> findByLancamento_Id(Long lancamentoId);

    /** EXC-10: apaga os documentos (o ateste) ligados ao lançamento, antes de o lançamento ser excluído de verdade. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GeneratedDocument d where d.lancamento.id = :lancamentoId")
    int excluirPorLancamento(@Param("lancamentoId") Long lancamentoId);

    /** Documentos gerados do contrato para a linha do tempo. Seleciona só as colunas leves: nunca carrega o PDF (LONGBLOB). */
    @Query("""
            select new contratos.api.dto.Contract.ContractTimelineEvent(d.generatedAt, u.name, d.documentType, d.version, d.fileName)
            from GeneratedDocument d left join d.generatedBy u
            where d.contract.id = :contractId
            """)
    List<ContractTimelineEvent> timeline(@Param("contractId") Long contractId);

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
