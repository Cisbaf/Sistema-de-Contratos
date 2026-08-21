package contratos.api;

import contratos.api.dto.UserRequest;
import contratos.api.dto.UserSummary;
import contratos.domain.AppUser;
import contratos.service.EntityMapper;
import contratos.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROLE_INTERNO')")
    public ResponseEntity<List<UserSummary>> getAll() { return ResponseEntity.ok(service.findAll()); }

    @GetMapping("/me")
    public ResponseEntity<UserSummary> me(org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(EntityMapper.user((AppUser) authentication.getPrincipal()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummary> create(@RequestBody @Valid UserRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummary> update(@PathVariable Long id, @RequestBody @Valid UserRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
