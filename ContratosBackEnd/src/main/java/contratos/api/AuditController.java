package contratos.api;

import contratos.api.dto.Auditoria.AuditLogResponse;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import contratos.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auditoria")
@PreAuthorize("@contractAuthorization.isAdminControle(authentication)")
public class AuditController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository repository;


    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var fromDate = dataInicio != null ? dataInicio.atStartOfDay() : null;
        var toDate = dataFim != null ? dataFim.plusDays(1).atStartOfDay() : null;

        PageParams.validate(page, size);

        Page<AuditLogResponse> result = repository
                .search(fromDate, toDate, entityType, action, contractId, actorId, PageRequest.of(page, size))
                .map(AuditLogResponse::from);

        return ResponseEntity.ok(result);
    }
}
