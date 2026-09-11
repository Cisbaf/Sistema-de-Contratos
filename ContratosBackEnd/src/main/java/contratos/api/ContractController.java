package contratos.api;

import contratos.api.dto.ContractRequest;
import contratos.api.dto.ContractResponse;
import contratos.api.dto.InterestEmailPreviewResponse;
import contratos.service.ContractService;
import contratos.service.InterestEmailConfirmationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts")
public class ContractController {
    private final ContractService service;
    private final InterestEmailConfirmationService confirmationService;

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

    @PreAuthorize("@contractAuthorization.isAssignedFiscal(#id, authentication)")
    @PostMapping("{id}/interest-email/confirm")
    public ResponseEntity<String> confirmInterestEmail(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(confirmationService.confirm(id, principal.getName()));
    }

    @GetMapping("/{id}/interest-email/preview")
    @PreAuthorize("@contractAuthorization.isAssignedFiscal(#id, authentication)")
    public ResponseEntity<InterestEmailPreviewResponse> previewInterestEmail(@PathVariable Long id) {
        var content = confirmationService.previewInterestEmail(id);
        return ResponseEntity.ok(new InterestEmailPreviewResponse(content));
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
