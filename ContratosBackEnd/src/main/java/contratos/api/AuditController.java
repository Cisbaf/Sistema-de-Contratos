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

/**
 * Consulta da auditoria geral (M6-30). Só ADMIN/CONTROLE_INTERNO (decisão de
 * 29/09/2026); fiscal nunca. Paginação no servidor porque o volume aqui cresce
 * sem parar, diferente das listas pequenas do resto do sistema.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auditoria")
@PreAuthorize("@contractAuthorization.isAdminControle(authentication)")
public class AuditController {

    private final AuditLogRepository repository;

    /**
     * dataInicio/dataFim são dias inteiros (não hora): dataFim inclui o dia
     * inteiro porque a query do repository usa "< toDate" (limite exclusivo),
     * então aqui viramos o fim do intervalo para o início do dia seguinte.
     * page/size em vez de Pageable: a ordenação já é fixa na query do
     * AuditLogRepository (occurredAt desc, id desc) e não deve vir do cliente.
     */
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

        Page<AuditLogResponse> result = repository
                .search(fromDate, toDate, entityType, action, contractId, actorId, PageRequest.of(page, size))
                .map(AuditLogResponse::from);

        return ResponseEntity.ok(result);
    }
}
