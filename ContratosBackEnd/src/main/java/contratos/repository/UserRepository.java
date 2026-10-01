package contratos.repository;

import contratos.domain.AppUser;
import java.util.List;
import java.util.Optional;
import contratos.domain.enums.PerfilUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<AppUser> findByEmail(String email);
    long countByPerfil(PerfilUsuario perfil);
    long countBySectorId(Long sectorId);

    List<AppUser> findAllByPerfil(PerfilUsuario perfil);

}
