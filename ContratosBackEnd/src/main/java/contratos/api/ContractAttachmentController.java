package contratos.api;

import contratos.api.dto.ContractAttachment.ContractAttachmentResponse;
import contratos.service.ContractAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/attachment")
public class ContractAttachmentController {
    private final ContractAttachmentService attachmentService;

    @GetMapping("/ativos/{contractId}")
    @PreAuthorize("@contractAuthorization.canRead(#contractId, authentication)")
    public ResponseEntity<List<ContractAttachmentResponse>> listarAtivos(@PathVariable Long contractId) {
        return ResponseEntity.ok(attachmentService.listarAtivos(contractId));
    }

    @GetMapping("/time_line/{contractId}")
    @PreAuthorize("@contractAuthorization.canRead(#contractId, authentication)")
    public ResponseEntity<List<ContractAttachmentResponse>> listarTimeline(@PathVariable Long contractId) {
        return ResponseEntity.ok(attachmentService.listarTimeline(contractId));
    }

    @PostMapping("/{contractId}")
    @PreAuthorize("@contractAuthorization.isAdminControle(authentication)")
    public ResponseEntity<List<ContractAttachmentResponse>> uploadFiles(@PathVariable Long contractId, @RequestParam("files") List<MultipartFile> files, Authentication authentication) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.uploadFiles(contractId, files, authentication.getName()));
    }

    @GetMapping("/baixar/{attId}")
    public ResponseEntity<byte[]> baixarConteudo(@PathVariable Long attId, Authentication authentication) {
        var file = attachmentService.baixarConteudo(attId, authentication);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.fileName() + "\"").body(file.content());
    }

    @DeleteMapping("/{attId}")
    @PreAuthorize("@contractAuthorization.isAdminControle(authentication)")
    public ResponseEntity<Void> removeFile(@PathVariable Long attId, Authentication authentication) {
        attachmentService.removeFiles(attId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
