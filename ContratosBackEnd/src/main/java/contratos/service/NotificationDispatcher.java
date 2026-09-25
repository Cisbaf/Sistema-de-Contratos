package contratos.service;

import contratos.api.dto.Notificacao.PlannedNotification;
import contratos.api.dto.Notificacao.Recipient;
import contratos.domain.enums.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executa o envio do dia: pede ao planner o que falta, envia um e-mail por destinatário e registra cada
 * resultado no log. Não mantém transação aberta durante o envio.
 *
 * <p>Uma falha (ex.: SMTP fora do ar) fica como FAILED e é tentada de novo na próxima execução.
 * Limite conhecido: se o backend cair entre o envio real e a gravação do log, o destinatário pode
 * receber o mesmo alerta duas vezes.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    /** @param delivered enviados (ou simulados) nesta execução; @param failed falhas nesta execução */
    public record Result(int delivered, int failed) {
    }

    private final NotificationPlanner planner;
    private final NotificationSender sender;
    private final NotificationLogRecorder recorder;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public Result runDaily(LocalDate today) {
        Objects.requireNonNull(today, "A data de referência é obrigatória.");
        if (!running.compareAndSet(false, true)) {
            log.warn("Envio de notificações já em andamento; execução ignorada.");
            return new Result(0, 0);
        }
        try {
            int delivered = 0;
            int failed = 0;
            for (PlannedNotification planned : planner.planAll(today)) {
                for (Recipient recipient : planned.recipients()) {
                    if (deliver(planned, recipient, today)) {
                        delivered++;
                    } else {
                        failed++;
                    }
                }
            }
            return new Result(delivered, failed);
        } finally {
            running.set(false);
        }
    }

    private boolean deliver(PlannedNotification planned, Recipient recipient, LocalDate today) {
        NotificationStatus status;
        String error = null;
        try {
            NotificationMessage message = NotificationMessages.build(
                    planned.contract(), planned.alertType(), recipient, today);
            sender.send(recipient, message);
            status = sender.successStatus();
        } catch (Exception e) {
            status = NotificationStatus.FAILED;
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Falha ao notificar {} sobre o contrato {}: {}",
                    recipient.address(), planned.contract().getNumberContract(), error);
        }

        try {
            recorder.record(planned.contract(), planned.alertType(), planned.cycleEndDate(),
                    recipient, status, error);
        } catch (Exception e) {
            log.error("Não foi possível gravar o log da notificação para {} (contrato {}, status {}): {}",
                    recipient.address(), planned.contract().getNumberContract(), status, e.getMessage(), e);
        }
        return status != NotificationStatus.FAILED;
    }
}
