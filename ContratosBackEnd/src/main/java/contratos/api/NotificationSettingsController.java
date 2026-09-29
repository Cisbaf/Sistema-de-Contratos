package contratos.api;

import contratos.api.dto.NotificationSettings.NotificationSettingsRequest;
import contratos.api.dto.NotificationSettings.NotificationSettingsResponse;
import contratos.domain.AppUser;
import contratos.service.Notification.NotificationSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Parâmetros configuráveis das notificações automáticas (M6-40). Consulta liberada para
 * ADMIN/CONTROLE_INTERNO (mesmo acesso da consulta geral de notificações, M5-60); edição só para ADMIN,
 * por mexer em comportamento do sistema inteiro (mesmo padrão de setores e usuários).
 */
@RestController
@RequestMapping("/api/notificacoes/parametros")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService service;

    @GetMapping
    @PreAuthorize("@contractAuthorization.isAdminControle(authentication)")
    public ResponseEntity<NotificationSettingsResponse> get() {
        return ResponseEntity.ok(NotificationSettingsResponse.from(service.current()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationSettingsResponse> update(
            @RequestBody @Valid NotificationSettingsRequest request, Authentication authentication) {
        AppUser actor = (AppUser) authentication.getPrincipal();
        var settings = service.update(request.firstAlertMonths(), request.secondAlertMonths(), actor);
        return ResponseEntity.ok(NotificationSettingsResponse.from(settings));
    }
}
