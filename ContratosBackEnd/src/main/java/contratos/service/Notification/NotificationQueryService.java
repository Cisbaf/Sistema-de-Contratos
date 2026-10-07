package contratos.service.Notification;

import contratos.api.dto.Notificacao.NotificationLogResponse;
import contratos.api.dto.Notificacao.NotificationSummaryResponse;
import contratos.domain.enums.NotificationStatus;
import contratos.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Consulta administrativa dos envios (M5-60). Só leitura: quem dispara e grava
 * o log é o NotificationDispatcher; aqui é só apresentação do que já aconteceu.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationLogRepository repository;

    /**
     * Histórico de um contrato (tela de detalhe).
     */
    public List<NotificationLogResponse> listByContract(Long contractId) {
        return repository.findByContract_IdOrderByAttemptedAtDesc(contractId).stream()
                .map(NotificationLogResponse::from)
                .toList();
    }

    /**
     * Visão administrativa geral, todos os contratos, mais recente primeiro (PG-10.1).
     * Paginada e filtrada no banco: {@code busca} procura (sem diferenciar maiúsculas) no número do contrato,
     * no número SEI, no nome e no e-mail do destinatário e no nome dos fiscais do contrato; {@code dataInicio}
     * e {@code dataFim} são inclusivas e filtram pelo dia da tentativa de envio. Qualquer filtro pode ser nulo.
     */
    public Page<NotificationLogResponse> search(String busca, LocalDate dataInicio, LocalDate dataFim, int page, int size) {
        String term = (busca == null || busca.isBlank()) ? null : likePattern(busca);
        LocalDateTime fromDate = dataInicio != null ? dataInicio.atStartOfDay() : null;
        LocalDateTime toDate = dataFim != null ? dataFim.plusDays(1).atStartOfDay() : null;

        return repository.search(fromDate, toDate, term, PageRequest.of(page, size))
                .map(NotificationLogResponse::from);
    }

    /**
     * Padrão do LIKE para a busca: minúsculas e com {@code %}, {@code _} e o próprio {@code !} escapados
     * (a consulta usa {@code escape '!'}), para que o usuário possa digitar esses caracteres literalmente
     * em vez de virarem curinga.
     */
    private static String likePattern(String busca) {
        String escaped = busca.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    public NotificationSummaryResponse summary(){
        var total = repository.count();
        var sent = repository.countByStatus(NotificationStatus.SENT);
        var failed = repository.countByStatus(NotificationStatus.FAILED);
        return new NotificationSummaryResponse(total, sent, failed);
    }
}
