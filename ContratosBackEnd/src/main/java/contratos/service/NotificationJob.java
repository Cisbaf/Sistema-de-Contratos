package contratos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Job diário das notificações, separado do job de status (a spec pede fluxos independentes).
 * Não roda ao subir a aplicação: o planner é idempotente, então perder um horário só adia o envio ao
 * próximo dia, sem duplicar nem perder alertas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationJob {

    private final NotificationDispatcher dispatcher;

    @Scheduled(cron = "${notifications.cron:0 0 8 * * *}", zone = "America/Sao_Paulo")
    public void runDaily() {
        try {
            var result = dispatcher.runDaily(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
            log.info("Notificações do dia concluídas: {} enviada(s)/simulada(s), {} falha(s)",
                    result.delivered(), result.failed());
        } catch (Exception e) {
            log.error("Falha ao executar o job de notificações: {}", e.getMessage(), e);
        }
    }
}
