package contratos.api;

import contratos.api.dto.GeneratedDocumentRequest;
import contratos.api.dto.GeneratedDocumentResponse;
import contratos.domain.enums.DocumentTemplateType;
import contratos.service.GeneratedDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/generate-document")
public class GeneratedDocumentController {
    private final GeneratedDocumentService service;

    @PostMapping
    @PreAuthorize("@contractAuthorization.canRead(#request.contractId(), authentication)")
    public ResponseEntity<GeneratedDocumentResponse> storeDocument(@Valid @RequestBody GeneratedDocumentRequest request, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.store(request, principal.getName()));
    }

    @GetMapping("/history")
    @PreAuthorize("@contractAuthorization.canRead(#contractId, authentication)")
    public ResponseEntity<List<GeneratedDocumentResponse>> getHistory(Long contractId, DocumentTemplateType docType) {
        return ResponseEntity.ok(service.getHistory(contractId, docType));
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(Long documentId) {
        var file = service.downloadContent(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .body(file.content());
    }
}
