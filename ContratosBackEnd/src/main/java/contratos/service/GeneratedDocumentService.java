package contratos.service;

import contratos.api.dto.GeneratedDocumentFile;
import contratos.api.dto.GeneratedDocumentRequest;
import contratos.api.dto.GeneratedDocumentResponse;
import contratos.api.dto.UserSummary;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.GeneratedDocument;
import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;
import contratos.repository.ContractRepository;
import contratos.repository.GeneratedDocumentRepository;
import contratos.repository.UserRepository;
import contratos.security.ContractAuthorization;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GeneratedDocumentService {
    private final GeneratedDocumentRepository repository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final ContractAuthorization authorization;

    public GeneratedDocumentResponse store(GeneratedDocumentRequest request, String username) {
        Contract contract = contractRepository.findById(request.contractId()).orElseThrow(() -> new EntityNotFoundException("Id do contrato não existe"));
        AppUser user = userRepository.findByUsername(username).orElseThrow(() -> new EntityNotFoundException("Id do usuário não existe"));
        if (!request.format().equals(DocumentFormat.PDF)) {
            throw new IllegalArgumentException("Formato WORD ainda não é suportado");
        }
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
                contract));
        return mapResponse(document);
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocumentResponse> getHistory(Long contractId, DocumentTemplateType documentType) {
        return repository.findByContractIdAndDocumentTypeOrderByVersionDesc(contractId, documentType).stream().map(this::mapResponse).toList();
    }

    @PostAuthorize("@contractAuthorization.canRead(returnObject.contractId(), authentication)")
    @Transactional(readOnly = true)
    public GeneratedDocumentFile downloadContent(Long documentId) {
        var document = repository.findById(documentId).orElseThrow(() -> new EntityNotFoundException("Documento não encontrado com o id: " + documentId));
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
                        user.getEmail(),
                        user.getCellPhone(),
                        new UserSummary.SectorSummary(sector.getId(), sector.getName()),
                        user.isAdmin(),
                        user.getPerfil().name()),
                document.getGeneratedAt());
    }

}
