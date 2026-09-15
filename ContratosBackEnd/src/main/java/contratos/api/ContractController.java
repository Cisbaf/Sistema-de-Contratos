package contratos.api;

import contratos.api.dto.Contract.ContractRequest;
import contratos.api.dto.Contract.ContractResponse;
import contratos.api.dto.InterestEmail.InterestEmailConfirmResponse;
import contratos.api.dto.InterestEmail.InterestEmailPreviewResponse;
import contratos.api.dto.TechnicalOpinion.TextPayload;
import contratos.service.ContractService;
import contratos.service.InterestEmailConfirmationService;
import contratos.service.SupplierMaskService;
import contratos.service.TechnicalOpinionService;
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
    private final TechnicalOpinionService technicalOpinionService;
    private final SupplierMaskService supplierMaskService;

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
    public ResponseEntity<InterestEmailConfirmResponse> confirmInterestEmail(@PathVariable Long id, Principal principal) {
        var message = confirmationService.confirm(id, principal.getName());
        return ResponseEntity.ok(new InterestEmailConfirmResponse(message));
    }

    @GetMapping("/{id}/interest-email/preview")
    @PreAuthorize("@contractAuthorization.canRead(#id, authentication)")
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

    @PostMapping("/{id}/technical-opinion/preview")
    @PreAuthorize("@contractAuthorization.canRead(#id, authentication)")
    public ResponseEntity<TextPayload> previewTechnicalOpinion(
            @PathVariable Long id, @RequestBody TextPayload payload) {
        var resolved = technicalOpinionService.previewOwnOpinion(id, payload.text());
        return ResponseEntity.ok(new TextPayload(resolved));
    }

    @GetMapping("/{id}/technical-opinion/mine")
    @PreAuthorize("@contractAuthorization.isAssignedFiscal(#id, authentication)")
    public ResponseEntity<TextPayload> myTechnicalOpinion(@PathVariable Long id, Principal principal) {
        var text = technicalOpinionService.getMyOpinion(id, principal.getName());
        return ResponseEntity.ok(new TextPayload(text));
    }

    @GetMapping("/{id}/technical-opinion/collective")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROLE_INTERNO')")
    public ResponseEntity<TextPayload> collectiveTechnicalOpinion(@PathVariable Long id) {
        var text = technicalOpinionService.previewCollective(id);
        return ResponseEntity.ok(new TextPayload(text));
    }

    @GetMapping("/{id}/technical-opinion/progress")
    @PreAuthorize("@contractAuthorization.canRead(#id, authentication)")
    public ResponseEntity<List<TechnicalOpinionService.FiscalProgress>> technicalOpinionProgress(
            @PathVariable Long id) {
        return ResponseEntity.ok(technicalOpinionService.getProgress(id));
    }

    @PostMapping("/{id}/technical-opinion/submit")
    @PreAuthorize("@contractAuthorization.isAssignedFiscal(#id, authentication)")
    public ResponseEntity<TextPayload> submitTechnicalOpinion(
            @PathVariable Long id, @RequestBody TextPayload payload, Principal principal) {
        var message = technicalOpinionService.submit(id, principal.getName(), payload.text());
        return ResponseEntity.ok(new TextPayload(message));
    }

    @GetMapping("/{id}/supplier-mask/preview")
    @PreAuthorize("@contractAuthorization.canRead(#id, authentication)")
    public ResponseEntity<TextPayload> previewSupplierMask(@PathVariable Long id) {
        var text = supplierMaskService.preview(id);
        return ResponseEntity.ok(new TextPayload(text));
    }
}
