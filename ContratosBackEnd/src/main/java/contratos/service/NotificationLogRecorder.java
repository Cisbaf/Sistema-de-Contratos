package contratos.service;

import contratos.api.dto.Notificacao.Recipient;
import contratos.domain.Contract;
import contratos.domain.NotificationLog;
import contratos.domain.enums.NotificationAlertType;
import contratos.domain.enums.NotificationChannel;
import contratos.domain.enums.NotificationStatus;
import contratos.repository.ContractRepository;
import contratos.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Grava o resultado de cada tentativa numa transação curta e própria.
 * Fica em classe separada para o {@code @Transactional} valer (chamada de outra classe passa pelo proxy)
 * e para nunca manter transação aberta durante a conversa com o servidor SMTP.
 */
@Component
@RequiredArgsConstructor
public class NotificationLogRecorder {

    private static final NotificationChannel CHANNEL = NotificationChannel.EMAIL;

    private final NotificationLogRepository logs;
    private final ContractRepository contracts;

    /** Cria a linha do log ou, se já existe (tentativa anterior que falhou/simulou), atualiza a mesma. */
    @Transactional
    public void record(Contract contract, NotificationAlertType alertType, LocalDate cycleEndDate,
                       Recipient recipient, NotificationStatus status, String errorMessage) {
        Optional<NotificationLog> existing = logs.findEntry(
                contract.getId(), alertType, CHANNEL, recipient.address(), cycleEndDate);

        if (existing.isPresent()) {
            existing.get().recordAttempt(status, errorMessage);
            return;
        }
        logs.save(new NotificationLog(
                contracts.getReferenceById(contract.getId()), alertType, cycleEndDate, CHANNEL,
                recipient.role(), recipient.name(), recipient.address(), status, errorMessage));
    }
}
