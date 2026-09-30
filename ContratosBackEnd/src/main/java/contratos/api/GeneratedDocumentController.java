package contratos.api;

import contratos.api.dto.GeneratedDocument.GeneratedDocumentRequest;
import contratos.api.dto.GeneratedDocument.GeneratedDocumentResponse;
import contratos.domain.enums.DocumentTemplateType;
import contratos.service.GeneratedDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    public ResponseEntity<byte[]> download(Long documentId, Authentication authentication) {
        var file = service.downloadContent(documentId, authentication);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .body(file.content());
    }

    @GetMapping
    @PreAuthorize("@contractAuthorization.isAdminControle(authentication)")
    public ResponseEntity<Page<GeneratedDocumentResponse>> search(
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) DocumentTemplateType documentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)LocalDate dataFim,
            @RequestParam(required = false) Long authorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var fromDate = dataInicio != null ? dataInicio.atStartOfDay() : null;
        var toDate = dataFim != null ? dataFim.plusDays(1).atStartOfDay() : null;

        return ResponseEntity.ok(service.findAll(contractId, documentType, fromDate, toDate, authorId,
                PageRequest.of(page, size)));
    }
}
