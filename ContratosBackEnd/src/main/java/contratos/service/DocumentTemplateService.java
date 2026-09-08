package contratos.service;

import contratos.api.dto.DocumentTemplateCreateRequest;
import contratos.api.dto.DocumentTemplateResponse;
import contratos.api.dto.DocumentTemplateUpdateRequest;
import contratos.domain.AppUser;
import contratos.domain.DocumentTemplate;
import contratos.domain.Sector;
import contratos.domain.enums.DocumentTemplateType;
import contratos.domain.enums.PerfilUsuario;
import contratos.exception.ConflictException;
import contratos.repository.DocumentTemplateRepository;
import contratos.repository.SectorRepository;
import contratos.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DocumentTemplateService {
    private final DocumentTemplateRepository docRepository;
    private final UserRepository userRepository;
    private final SectorRepository sectorRepository;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    @Transactional(readOnly = true)
    public List<DocumentTemplateResponse> findAll(String nome) {
        var user = userRepository.findByUsername(nome).orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        switch (user.getPerfil()) {
            case PerfilUsuario.ADMIN -> {
                return docRepository.findAll().stream().map(DocumentTemplateService::toResponse).toList();
            }
            case PerfilUsuario.FISCAL -> {
                return docRepository.findAllBySectorId(user.getSector().getId()).stream().map(DocumentTemplateService::toResponse).toList();
            }
            default -> throw new AccessDeniedException("Você não pode gerenciar templates deste setor");
        }

    }

    @Transactional
    public DocumentTemplateResponse createTemplate(DocumentTemplateCreateRequest request, String username) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(username);


        var sector = sectorRepository.findById(request.sectorId()).orElseThrow(() -> new EntityNotFoundException("Setor não encontrado"));

        var user = userRepository.findByUsername(username).orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        ensureCanManage(user, sector);

        validateVariables(request.content(), request.templateType());

        if (docRepository.existsBySectorIdAndTemplateType(request.sectorId(), request.templateType())) {
            throw new ConflictException(
                    "Já existe um template desse tipo para o setor"
            );
        }

        var newTemplate = new DocumentTemplate(request.templateType(), request.content(), LocalDateTime.now(ZoneId.of("America/Sao_Paulo")), user, sector);

        return toResponse(docRepository.save(newTemplate));
    }

    @Transactional
    public DocumentTemplateResponse updateTemplate(Long id, DocumentTemplateUpdateRequest request, String username) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(username);
        var user = userRepository.findByUsername(username).orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        var oldTemplate = docRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Template não encontrado"));
        ensureCanManage(user, oldTemplate.getSector());

        validateVariables(request.content(), oldTemplate.getTemplateType());

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
    private void validateVariables(String content, DocumentTemplateType templateType){

        Set<String> permitidas = allowedVariables(templateType);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        Set<String> invalidas = new HashSet<>();

        while(matcher.find()){
            var variavel = matcher.group(1);
            if (!permitidas.contains(variavel)){
                invalidas.add(variavel);
            }
        }
        if (!invalidas.isEmpty()){
            throw new IllegalArgumentException("Variáveis não permitidas para este tipo de template: " + String.join(", ", invalidas));
        }
    }

    private void ensureCanManage(AppUser user, Sector sector) {
        if (user.getPerfil() == PerfilUsuario.ADMIN) {
            return;
        }

        boolean fiscalFromSameSector = user.getPerfil() == PerfilUsuario.FISCAL && user.getSector() != null &&
                Objects.equals(user.getSector().getId(), sector.getId());

        if (!fiscalFromSameSector) {
            throw new AccessDeniedException(
                    "Você não pode gerenciar templates deste setor"
            );
        }
    }

    private Set<String> allowedVariables(DocumentTemplateType templateType) {
        return switch (templateType) {
            case INTEREST_EMAIL -> Set.of(
                    "numero_contrato",
                    "objeto_contrato",
                    "numero_processo",
                    "empresa",
                    "cnpj",
                    "data_termino",
                    "nomes_fiscais"
            );

            case TECHNICAL_OPINION -> Set.of(
                    "numero_contrato",
                    "objeto_contrato",
                    "numero_processo",
                    "empresa",
                    "cnpj",
                    "data_termino",
                    "nomes_fiscais",
                    "observacoes"
            );

            case SUPPLIER_RENEWAL_EMAIL -> Set.of(
                    "numero_contrato",
                    "empresa",
                    "cnpj",
                    "objeto_contrato",
                    "data_termino",
                    "numero_processo",
                    "nome_fiscal"
            );
        };
    }
}
