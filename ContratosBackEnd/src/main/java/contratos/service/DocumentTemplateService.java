package contratos.service;

import contratos.api.dto.DocumentTemplateCreateRequest;
import contratos.api.dto.DocumentTemplateResponse;
import contratos.api.dto.DocumentTemplateUpdateRequest;
import contratos.domain.DocumentTemplate;
import contratos.exception.ConflictException;
import contratos.repository.DocumentTemplateRepository;
import contratos.repository.SectorRepository;
import contratos.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DocumentTemplateService {
    private final DocumentTemplateRepository docRepository;
    private final UserRepository userRepository;
    private final SectorRepository sectorRepository;

    @Transactional(readOnly = true)
    public List<DocumentTemplateResponse> findAll() {

        return docRepository.findAll().stream().map(DocumentTemplateService::toResponse).toList();
    }

    @Transactional
    public DocumentTemplateResponse createTemplate(DocumentTemplateCreateRequest request, String username) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(username);
        if (docRepository.existsBySectorIdAndTemplateType(
                request.sectorId(),
                request.templateType()
        )) {
            throw new ConflictException(
                    "Já existe um template desse tipo para o setor"
            );
        }

        var sector = sectorRepository.findById(request.sectorId()).orElseThrow(() ->new EntityNotFoundException("Setor não encontrado"));

        var user = userRepository.findByUsername(username).orElseThrow(() ->new EntityNotFoundException("Usuário não encontrado"));

        var newTemplate = new DocumentTemplate(request.templateType(), request.content(), LocalDateTime.now(ZoneId.of("America/Sao_Paulo")), user, sector);

        return toResponse(docRepository.save(newTemplate));
    }

    @Transactional
    public DocumentTemplateResponse updateTemplate(Long id, DocumentTemplateUpdateRequest request, String username){
        Objects.requireNonNull(request);
        Objects.requireNonNull(username);
        var user = userRepository.findByUsername(username).orElseThrow(() ->new EntityNotFoundException("Usuário não encontrado"));
        var oldTemplate = docRepository.findById(id).orElseThrow(() ->new EntityNotFoundException("Template não encontrado"));
        oldTemplate.updateContent(request.content(), LocalDateTime.now(ZoneId.of("America/Sao_Paulo")), user);

        return toResponse(oldTemplate);
    }

    private static DocumentTemplateResponse toResponse(DocumentTemplate template) {
        return new DocumentTemplateResponse(
                template.getId(),
                template.getSector().getId(),
                template.getSector().getName(),
                template.getTemplateType(),
                template.getContent(),
                template.getUpdatedAt(),
                template.getUpdatedBy().getId(),
                template.getUpdatedBy().getName()
        );
    }
}
