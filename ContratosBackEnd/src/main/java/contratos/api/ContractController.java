package contratos.api;

import contratos.api.dto.ContractRequest;
import contratos.api.dto.ContractResponse;
import contratos.service.ContractService;
import jakarta.validation.Valid;
import java.security.Principal;
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
@RequestMapping("/api/contracts")
public class ContractController {
    private final ContractService service;

    public ContractController(ContractService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROLE_INTERNO')")
    public ResponseEntity<List<ContractResponse>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('FISCAL')")
    public ResponseEntity<List<ContractResponse>> getMine(Principal principal) {
        return ResponseEntity.ok(service.findMine(principal.getName()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@contractAuthorization.canRead(#id, authentication)")
    public ResponseEntity<ContractResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROLE_INTERNO')")
    public ResponseEntity<ContractResponse> create(@RequestBody @Valid ContractRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROLE_INTERNO')")
    public ResponseEntity<ContractResponse> update(@PathVariable Long id, @RequestBody @Valid ContractRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
