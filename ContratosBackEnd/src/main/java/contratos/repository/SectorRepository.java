package contratos.repository;

import contratos.domain.Sector;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorRepository extends JpaRepository<Sector, Long> {
    boolean existsByNameIgnoreCase(String name);
    Optional<Sector> findByNameIgnoreCase(String name);
}
