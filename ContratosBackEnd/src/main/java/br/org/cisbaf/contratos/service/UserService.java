package br.org.cisbaf.contratos.service;

import br.org.cisbaf.contratos.api.dto.UserRequest;
import br.org.cisbaf.contratos.api.dto.UserSummary;
import br.org.cisbaf.contratos.domain.AppUser;
import br.org.cisbaf.contratos.domain.Sector;
import br.org.cisbaf.contratos.domain.PerfilUsuario;
import br.org.cisbaf.contratos.repository.ContractRepository;
import br.org.cisbaf.contratos.repository.SectorRepository;
import br.org.cisbaf.contratos.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final SectorRepository sectors;
    private final ContractRepository contracts;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, SectorRepository sectors, ContractRepository contracts, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.sectors = sectors;
        this.contracts = contracts;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> findAll() {
        return users.findAll().stream().map(EntityMapper::user).toList();
    }

    @Transactional
    public UserSummary create(UserRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("A senha é obrigatória ao criar um fiscal");
        }
        ensureUnique(request.email(), null);
        Sector sector = getSector(request.sectorId());
        PerfilUsuario perfil = resolvePerfil(request);
        AppUser user = new AppUser(request.email(), passwordEncoder.encode(request.password()), request.name().trim(),
                request.email().trim().toLowerCase(), request.cellPhone(), sector, perfil);
        return EntityMapper.user(users.save(user));
    }

    @Transactional
    public UserSummary update(Long id, UserRequest request) {
        AppUser user = getUser(id);
        ensureUnique(request.email(), id);
        PerfilUsuario perfil = resolvePerfil(request);
        user.update(request.email().trim().toLowerCase(), request.name().trim(), request.email().trim().toLowerCase(),
                request.cellPhone(), getSector(request.sectorId()), perfil);
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        return EntityMapper.user(user);
    }

    @Transactional
    public void delete(Long id) {
        AppUser user = getUser(id);
        if (contracts.countByFiscaisId(id) > 0) {
            throw new DataIntegrityViolationException("O fiscal está vinculado a contratos");
        }
        users.delete(user);
    }

    public AppUser getUser(Long id) {
        return users.findById(id).orElseThrow(() -> new EntityNotFoundException("Fiscal não encontrado"));
    }

    private Sector getSector(Long id) {
        return sectors.findById(id).orElseThrow(() -> new EntityNotFoundException("Setor não encontrado"));
    }

    private void ensureUnique(String email, Long ignoredId) {
        users.findByUsername(email.trim().toLowerCase()).ifPresent(existing -> {
            if (!existing.getId().equals(ignoredId)) throw new DataIntegrityViolationException("E-mail já cadastrado");
        });
    }

    private PerfilUsuario resolvePerfil(UserRequest request) {
        if (request.perfil() == null || request.perfil().isBlank()) {
            return request.admin() ? PerfilUsuario.ADMIN : PerfilUsuario.FISCAL;
        }
        try {
            return PerfilUsuario.valueOf(request.perfil().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Perfil inválido. Use ADMIN, CONTROLE_INTERNO ou FISCAL");
        }
    }
}
