package contratos.api;

import contratos.api.dto.Notificacao.NotificationLogResponse;
import contratos.api.dto.Notificacao.NotificationSummaryResponse;
import contratos.service.Notification.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Consulta administrativa dos envios de notificação (M5-60). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class NotificationController {

    private final NotificationQueryService service;
    private static final int MAX_PAGE_SIZE = 100;


    /** Histórico de notificações de um contrato: mesmo acesso das outras abas do
     *  contrato (fiscal responsável, ADMIN, CONTROLE_INTERNO). */
    @GetMapping("/contracts/{contractId}/notificacoes")
    @PreAuthorize("@contractAuthorization.canRead(#contractId, authentication)")
    public ResponseEntity<List<NotificationLogResponse>> porContrato(@PathVariable Long contractId) {
        return ResponseEntity.ok(service.listByContract(contractId));
    }

    /** Visão administrativa geral: todos os contratos, só ADMIN/CONTROLE_INTERNO. */
    @GetMapping("/notificacoes")
    @PreAuthorize("@contractAuthorization.isAdminControle(authentication)")
    public ResponseEntity<Page<NotificationLogResponse>> todas(@RequestParam(required = false) String busca,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("O tamanho máximo da página é " + MAX_PAGE_SIZE);
        }
        return ResponseEntity.ok(service.search(busca, dataInicio,dataFim,page, size));
    }

    @GetMapping("/notificacoes/resumo")
    @PreAuthorize("@contractAuthorization.isAdminControle(authentication)")
    public ResponseEntity<NotificationSummaryResponse> summary(){
        return ResponseEntity.ok(service.summary());
    }

}
