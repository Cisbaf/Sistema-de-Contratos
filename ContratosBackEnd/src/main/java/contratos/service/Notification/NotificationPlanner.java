package contratos.service.Notification;

import contratos.api.dto.Notificacao.PlannedNotification;
import contratos.api.dto.Notificacao.Recipient;
import contratos.config.NotificationProperties;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.enums.NotificationAlertType;
import contratos.domain.enums.NotificationChannel;
import contratos.domain.enums.PerfilUsuario;
import contratos.domain.enums.RecipientRole;
import contratos.repository.ContractRepository;
import contratos.repository.NotificationLogRepository;
import contratos.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Decide o que falta enviar hoje. Não envia nada, não grava log e não tem job:
 * só devolve a lista pronta para o envio (M5-40).
 */
@Service
public class NotificationPlanner {

    private static final NotificationChannel CHANNEL = NotificationChannel.EMAIL;

    private final ContractRepository contracts;
    private final UserRepository users;
    private final NotificationLogRepository logs;
    private final NotificationProperties properties;

    public NotificationPlanner(ContractRepository contracts,
                               UserRepository users,
                               NotificationLogRepository logs,
                               NotificationProperties properties) {
        this.contracts = contracts;
        this.users = users;
        this.logs = logs;
        this.properties = properties;
    }

    /** Todos os contratos com algo a enviar em {@code today}, independentemente do status do processo. */
    @Transactional(readOnly = true)
    public List<PlannedNotification> planAll(LocalDate today) {
        Objects.requireNonNull(today, "A data de referência é obrigatória.");
        return contracts.findAllByEndDateGreaterThanEqual(today).stream()
                .map(contract -> plan(contract, today))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * O contrato precisa chegar com {@code fiscais} carregados (ou estar numa transação aberta).
     * Devolve vazio se não há alerta devido ou se todos os destinatários já foram avisados neste ciclo.
     */
    @Transactional(readOnly = true)
    public Optional<PlannedNotification> plan(Contract contract, LocalDate today) {
        Objects.requireNonNull(contract, "O contrato é obrigatório.");
        Objects.requireNonNull(today, "A data de referência é obrigatória.");

        Optional<NotificationAlertType> alert =
                NotificationRules.resolveAlert(contract.getEndDate(), today,
                        properties.firstAlertMonths(), properties.secondAlertMonths());
        if (alert.isEmpty()) {
            return Optional.empty();
        }
        NotificationAlertType alertType = alert.get();

        List<Recipient> pending = resolveRecipients(contract).stream()
                .filter(r -> !logs.alreadyNotified(
                        contract.getId(), alertType, CHANNEL, r.address(), contract.getEndDate()))
                .toList();
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PlannedNotification(contract, alertType, contract.getEndDate(), pending));
    }

    /** Fiscais do contrato e todo o Controle Interno; quem aparece nos dois papéis fica com o primeiro (fiscal). */
    private List<Recipient> resolveRecipients(Contract contract) {
        Map<String, Recipient> byAddress = new LinkedHashMap<>();
        add(byAddress, RecipientRole.FISCAL, contract.getFiscais());
        add(byAddress, RecipientRole.INTERNAL_CONTROL, users.findAllByPerfil(PerfilUsuario.CONTROLE_INTERNO));
        return new ArrayList<>(byAddress.values());
    }

    private void add(Map<String, Recipient> byAddress, RecipientRole role, Collection<AppUser> source) {
        for (AppUser user : source) {
            String address = normalize(user.getEmail());
            if (address.isEmpty()) {
                continue;
            }
            byAddress.putIfAbsent(address, new Recipient(role, user.getName(), address));
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
