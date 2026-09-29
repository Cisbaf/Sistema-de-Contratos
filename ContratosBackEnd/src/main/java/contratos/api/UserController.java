package contratos.api;

import contratos.api.dto.User.UserRequest;
import contratos.api.dto.User.UserSummary;
import contratos.domain.AppUser;
import contratos.service.EntityMapper;
import contratos.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROLE_INTERNO')")
    public ResponseEntity<List<UserSummary>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummary> me(Authentication authentication) {
        return ResponseEntity.ok(EntityMapper.user((AppUser) authentication.getPrincipal()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummary> create(@RequestBody @Valid UserRequest request, Authentication authentication) {
        AppUser actor = (AppUser) authentication.getPrincipal();

        return ResponseEntity.ok(service.create(request, actor));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummary> update(@PathVariable Long id, @RequestBody @Valid UserRequest request, Authentication authentication) {
        AppUser actor = (AppUser) authentication.getPrincipal();

        return ResponseEntity.ok(service.update(id, request, actor));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        AppUser actor = (AppUser) authentication.getPrincipal();

        service.delete(id, actor);
        return ResponseEntity.noContent().build();
    }
}
