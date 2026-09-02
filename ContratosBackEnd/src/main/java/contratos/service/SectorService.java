package contratos.service;

import contratos.api.dto.SectorRequest;
import contratos.api.dto.SectorResponse;
import contratos.domain.Sector;
import contratos.exception.ConflictException;
import contratos.repository.SectorRepository;
import contratos.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SectorService {
    private final SectorRepository sectors;
    private final UserRepository users;

    public SectorService(SectorRepository sectors, UserRepository users) {
        this.sectors = sectors;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<SectorResponse> findAll() {
        return sectors.findAll().stream()
                .map(sector -> response(sector, users.countBySectorId(sector.getId())))
                .toList();
    }

    @Transactional
    public SectorResponse create(SectorRequest request) {
        ensureUnique(request.name());
        Sector sector = sectors.save(new Sector(request.name().trim()));
        return response(sector, 0);
    }

    @Transactional
    public SectorResponse update(Long id, SectorRequest request) {
        Sector sector = get(id);
        if (!sector.getName().equalsIgnoreCase(request.name().trim())) ensureUnique(request.name());
        sector.rename(request.name().trim());
        return response(sector, users.countBySectorId(id));
    }

    @Transactional
    public void delete(Long id) {
        Sector sector = get(id);
        if (users.countBySectorId(id) > 0) {
            throw new ConflictException("O setor possui fiscais vinculados");
        }
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
