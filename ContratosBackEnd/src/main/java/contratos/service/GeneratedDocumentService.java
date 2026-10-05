package contratos.service;

import contratos.api.dto.GeneratedDocument.GeneratedDocumentFile;
import contratos.api.dto.GeneratedDocument.GeneratedDocumentRequest;
import contratos.api.dto.GeneratedDocument.GeneratedDocumentResponse;
import contratos.api.dto.User.UserSummary;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.GeneratedDocument;
import contratos.domain.LancamentoFinanceiro;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;
import contratos.repository.ContractRepository;
import contratos.repository.GeneratedDocumentRepository;
import contratos.repository.LancamentoFinanceiroRepository;
import contratos.repository.UserRepository;
import contratos.security.ContractAuthorization;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GeneratedDocumentService {
    private final GeneratedDocumentRepository repository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final LancamentoFinanceiroRepository lancamentoRepository;
    private final ContractAuthorization authorization;
    private final AuditService auditService;

    // @Transactional: o AuditService.record exige transação (a linha de auditoria nasce e morre com o documento).
    @Transactional
    public GeneratedDocumentResponse store(GeneratedDocumentRequest request, String username) {
        Contract contract = contractRepository.findById(request.contractId()).orElseThrow(() -> new EntityNotFoundException("Id do contrato não existe"));
        AppUser user = userRepository.findByUsername(username).orElseThrow(() -> new EntityNotFoundException("Id do usuário não existe"));
        if (!request.format().equals(DocumentFormat.PDF)) {
            throw new IllegalArgumentException("Formato WORD ainda não é suportado");
        }
        LancamentoFinanceiro lancamento = request.lancamentoId() == null ? null
                : lancamentoRepository.findById(request.lancamentoId()).orElseThrow(() -> new EntityNotFoundException("Lançamento não existe"));
        List<GeneratedDocument> versao = repository.findByContractIdAndDocumentTypeOrderByVersionDesc(request.contractId(), request.documentType());

        LocalDateTime dataGeracao = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        int proximaVersao = versao.isEmpty() ? 1 : versao.getFirst().getVersion() + 1;
        var document = repository.save(new GeneratedDocument(
                request.fileName(),
                proximaVersao,
                request.content(),
                dataGeracao,
                user,
                request.format(),
                request.documentType(),
                contract,
                lancamento));

        // Nunca o conteúdo do PDF: só quem gerou, o tipo, a versão e o nome do arquivo.
        AuditChangeLog details = new AuditChangeLog()
                .note("Arquivo: " + document.getFileName())
                .note("Formato: " + document.getFormat());
        if (lancamento != null) {
            details.note("Lançamento (nota fiscal): " + lancamento.getNotaFiscal());
        }
        auditService.record(user, AuditAction.GENERATE_DOCUMENT, AuditEntityType.DOCUMENT, document.getId(),
                contract.getId(), "Documento gerado: " + document.getDocumentType() + " v" + document.getVersion()
                        + " do contrato " + contract.getNumberContract(), details.build());
        return mapResponse(document);
    }

    // Reaproveitar um documento já gerado pro mesmo lançamento, em vez de criar versão nova a cada clique
    // (hoje só o ateste dos fiscais / PAYMENT_CHECKLIST usa isto — outros tipos não têm lançamento associado).
    @Transactional(readOnly = true)
    public Optional<GeneratedDocumentResponse> findExistingByLancamento(DocumentTemplateType documentType, Long lancamentoId) {
        return repository.findByDocumentTypeAndLancamento_Id(documentType, lancamentoId).map(this::mapResponse);
    }

    @Transactional(readOnly = true)
    public Page<GeneratedDocumentResponse> findAll(Long contractId, DocumentTemplateType documentTemplate, LocalDateTime fromDate, LocalDateTime toDate, Long authorId, Pageable pageable){
        return repository.search(
                contractId,
                documentTemplate,
                fromDate,
                toDate,
                authorId,
                pageable
        ).map(this::mapResponse);
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocumentResponse> getHistory(Long contractId, DocumentTemplateType documentType) {
        return repository.findByContractIdAndDocumentTypeOrderByVersionDesc(contractId, documentType).stream().map(this::mapResponse).toList();
    }

    @Transactional(readOnly = true)
    public GeneratedDocumentFile downloadContent(Long documentId, Authentication authentication) {
        var document = repository.findById(documentId)
                .orElseThrow(() -> authorization.notFoundOrForbidden(authentication,
                        "Documento não encontrado com o id: " + documentId));
        if (!authorization.canRead(document.getContract().getId(), authentication)) {
            throw new AccessDeniedException("Usuário não tem permissão para baixar o arquivo");
        }
        return new GeneratedDocumentFile(document.getContract().getId(), document.getFileName(), document.getFormat(), document.getContent());
    }

    private GeneratedDocumentResponse mapResponse(GeneratedDocument document) {
        var user = document.getGeneratedBy();
        var sector = user.getSector();
        return new GeneratedDocumentResponse(document.getId(),
                document.getContract().getId(),
                document.getDocumentType(),
                document.getFormat(),
                document.getFileName(),
                document.getVersion(),
                new UserSummary(
                        user.getId(),
                        user.getName(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getCellPhone(),
                        new UserSummary.SectorSummary(sector.getId(), sector.getName()),
                        user.getPerfil().name()),
                document.getGeneratedAt());
    }

}
