package contratos.service;

import contratos.api.dto.User.UserRequest;
import contratos.api.dto.User.UserSummary;
import contratos.domain.AppUser;
import contratos.domain.Sector;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import contratos.domain.enums.PerfilUsuario;
import contratos.exception.ConflictException;
import contratos.repository.ContractRepository;
import contratos.repository.SectorRepository;
import contratos.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final SectorRepository sectors;
    private final ContractRepository contracts;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;


    @Transactional(readOnly = true)
    public List<UserSummary> findAll() {
        return userRepository.findAll().stream().map(EntityMapper::user).toList();
    }

    @Transactional
    public UserSummary create(UserRequest request, AppUser appUser) {
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("A senha é obrigatória ao criar um fiscal");
        }
        ensureUnique(request.email(), null);
        Sector sector = getSector(request.sectorId());
        PerfilUsuario perfil = resolvePerfil(request);
        AppUser user = new AppUser(request.email(), passwordEncoder.encode(request.password()), request.name().trim(),
                request.email().trim().toLowerCase(), request.cellPhone(), sector, perfil);

        ensureActorCanManage(appUser, null, user.getPerfil());

        var newUser = userRepository.save(user);

        auditService.record(appUser, AuditAction.CREATE, AuditEntityType.USER, newUser.getId(),
                null, "Usuário " + newUser.getName() + " criado", null);
        return EntityMapper.user(newUser);
    }

    @Transactional
    public UserSummary update(Long id, UserRequest request, AppUser appUser) {
        AppUser user = getUser(id);
        ensureUnique(request.email(), id);
        PerfilUsuario perfil = resolvePerfil(request);

        ensureActorCanManage(appUser, perfil, user.getPerfil());

        if (contracts.existsByFiscaisIdAndEndDateGreaterThanEqual(user.getId(), LocalDate.now()) && !perfil.equals(PerfilUsuario.FISCAL)) {
            throw new ConflictException("Fiscais com contratos ativos não podem mudar de perfil");
        }

        AuditChangeLog changes = new AuditChangeLog()
                .field("Nome", user.getName(), request.name().trim())
                .field("E-mail", user.getEmail(), request.email().trim().toLowerCase())
                .field("Celular", user.getCellPhone(), request.cellPhone())
                .field("Setor", user.getSector().getName(), getSector(request.sectorId()).getName())
                .field("Perfil", user.getPerfil(), perfil);

        user.update(request.email().trim().toLowerCase(), request.name().trim(), request.email().trim().toLowerCase(),
                request.cellPhone(), getSector(request.sectorId()), perfil);
        if (request.password() != null && !request.password().isBlank()) {
            user.changePassword(passwordEncoder.encode(request.password()));
            changes.note("Senha redefinida");
        }
        auditService.record(appUser, AuditAction.UPDATE, AuditEntityType.USER, user.getId(),
                null, "Usuário " + user.getName() + " atualizado", changes.build());
        return EntityMapper.user(user);
    }

    @Transactional
    public void delete(Long id, AppUser appUser) {
        AppUser user = getUser(id);
        if (contracts.countByFiscaisId(id) > 0) {
            throw new ConflictException("O fiscal está vinculado a contratos");
        }
        if (Objects.equals(user.getId(), appUser.getId())) {
            throw new ConflictException("Não é permitido excluir o próprio usuário");
        }
        if (user.isAdmin() && userRepository.countByPerfil(PerfilUsuario.ADMIN) <= 1) {
            throw new ConflictException("Não é permitido excluir o único Administrador");
        }
        auditService.record(appUser, AuditAction.DELETE, AuditEntityType.USER, user.getId(),
                null, "Usuário " + user.getName() + " apagado", null);
        userRepository.delete(user);
    }

    private void ensureActorCanManage(AppUser actor, PerfilUsuario currentPerfil, PerfilUsuario requestedPerfil) {
        if (actor.isAdmin()) return;
        boolean touchesAdmin = requestedPerfil == PerfilUsuario.ADMIN || currentPerfil == PerfilUsuario.ADMIN;
        if (touchesAdmin) {
            throw new AccessDeniedException("Apenas administradores podem criar ou alterar administradores");
        }
    }

    public AppUser getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Fiscal não encontrado"));
    }

    private Sector getSector(Long id) {
        return sectors.findById(id).orElseThrow(() -> new EntityNotFoundException("Setor não encontrado"));
    }

    private void ensureUnique(String email, Long ignoredId) {
        userRepository.findByUsername(email.trim().toLowerCase()).ifPresent(existing -> {
            if (!existing.getId().equals(ignoredId)) throw new ConflictException("E-mail já cadastrado");
        });
    }

    private PerfilUsuario resolvePerfil(UserRequest request) {
        if (request.perfil() == null || request.perfil().isBlank()) {
            return PerfilUsuario.FISCAL;
        }
        try {
            return PerfilUsuario.valueOf(request.perfil().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Perfil inválido. Use ADMIN, CONTROLE_INTERNO ou FISCAL");
        }
    }
}
