package contratos.service;

import contratos.api.dto.Sector.SectorRequest;
import contratos.api.dto.Sector.SectorResponse;
import contratos.domain.AppUser;
import contratos.domain.Sector;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import contratos.exception.ConflictException;
import contratos.repository.SectorRepository;
import contratos.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SectorService {
    private final SectorRepository sectors;
    private final UserRepository users;
    private final AuditService auditService;


    @Transactional(readOnly = true)
    public List<SectorResponse> findAll() {
        return sectors.findAll().stream()
                .map(sector -> response(sector, users.countBySectorId(sector.getId())))
                .toList();
    }

    @Transactional
    public SectorResponse create(SectorRequest request, AppUser appUser) {
        ensureUnique(request.name());
        Sector sector = sectors.save(new Sector(request.name().trim()));

        auditService.record(appUser, AuditAction.CREATE, AuditEntityType.SECTOR, sector.getId(),
                null, "Setor " + sector.getName() + " criado", null);
        return response(sector, 0);
    }

    @Transactional
    public SectorResponse update(Long id, SectorRequest request, AppUser appUser) {
        Sector sector = get(id);
        String before = sector.getName();
        if (!before.equalsIgnoreCase(request.name().trim())) ensureUnique(request.name());
        sector.rename(request.name().trim());

        String details = new AuditChangeLog().field("Nome", before, sector.getName()).build();

        auditService.record(appUser, AuditAction.UPDATE, AuditEntityType.SECTOR, sector.getId(),
                null, "Setor " + sector.getName() + " atualizado", details);

        return response(sector, users.countBySectorId(id));
    }

    @Transactional
    public void delete(Long id, AppUser appUser) {
        Sector sector = get(id);
        if (users.countBySectorId(id) > 0) {
            throw new ConflictException("O setor possui fiscais vinculados");
        }
        auditService.record(appUser, AuditAction.DELETE, AuditEntityType.SECTOR, sector.getId(),
                null, "Setor " + sector.getName() + " apagado", null);
        sectors.delete(sector);
    }

    private Sector get(Long id) {
        return sectors.findById(id).orElseThrow(() -> new EntityNotFoundException("Setor não encontrado"));
    }

    private void ensureUnique(String name) {
        if (sectors.existsByNameIgnoreCase(name.trim())) {
            throw new ConflictException("Setor já cadastrado");
        }
    }

    private SectorResponse response(Sector sector, long count) {
        return new SectorResponse(sector.getId(), sector.getName(), count);
    }
}
