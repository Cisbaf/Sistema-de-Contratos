package contratos.api;

import contratos.api.dto.GeneratedDocument.GeneratedDocumentResponse;
import contratos.domain.enums.DocumentTemplateType;
import contratos.service.GeneratedDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/generate-document")
public class GeneratedDocumentController {
    private static final int MAX_PAGE_SIZE = 100;

    private final GeneratedDocumentService service;

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
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("O tamanho máximo da página é " + MAX_PAGE_SIZE);
        }
        var fromDate = dataInicio != null ? dataInicio.atStartOfDay() : null;
        var toDate = dataFim != null ? dataFim.plusDays(1).atStartOfDay() : null;

        return ResponseEntity.ok(service.findAll(contractId, documentType, fromDate, toDate, authorId,
                PageRequest.of(page, size)));
    }
}
