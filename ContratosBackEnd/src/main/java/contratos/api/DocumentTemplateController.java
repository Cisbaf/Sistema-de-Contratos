package contratos.api;

import contratos.api.dto.DocumentTemplateCreateRequest;
import contratos.api.dto.DocumentTemplateResponse;
import contratos.api.dto.DocumentTemplateUpdateRequest;
import contratos.service.DocumentTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/document-templates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'FISCAL')")
public class DocumentTemplateController {

    private final DocumentTemplateService service;

    @GetMapping
    public ResponseEntity<List<DocumentTemplateResponse>> findAll(Principal principal) {

        return ResponseEntity.ok(service.findAll(principal.getName()));
    }

    @PostMapping
    public ResponseEntity<DocumentTemplateResponse> create(
            @RequestBody @Valid DocumentTemplateCreateRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(
                service.createTemplate(request, principal.getName())
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentTemplateResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid DocumentTemplateUpdateRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(
                service.updateTemplate(id, request, principal.getName())
        );
    }
}
