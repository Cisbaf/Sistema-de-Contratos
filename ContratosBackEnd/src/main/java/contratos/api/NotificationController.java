package contratos.api;

import contratos.api.dto.Notificacao.NotificationLogResponse;
import contratos.service.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Consulta administrativa dos envios de notificação (M5-60). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class NotificationController {

    private final NotificationQueryService service;

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
    public ResponseEntity<List<NotificationLogResponse>> todas() {
        return ResponseEntity.ok(service.listAll());
    }
}
