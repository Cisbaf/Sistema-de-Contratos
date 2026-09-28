package contratos.service.Notification;

import contratos.api.dto.Notificacao.NotificationLogResponse;
import contratos.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consulta administrativa dos envios (M5-60). Só leitura: quem dispara e grava
 * o log é o NotificationDispatcher; aqui é só apresentação do que já aconteceu.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationLogRepository repository;

    /** Histórico de um contrato (tela de detalhe). */
    public List<NotificationLogResponse> listByContract(Long contractId) {
        return repository.findByContract_IdOrderByAttemptedAtDesc(contractId).stream()
                .map(NotificationLogResponse::from)
                .toList();
    }

    /** Visão administrativa geral, todos os contratos, mais recente primeiro.
     *  Sem paginação nem filtro no backend: o volume da tabela é pequeno (só
     *  contratos perto do vencimento) e o resto do sistema já filtra assim,
     *  no front, sobre a lista completa. */
    public List<NotificationLogResponse> listAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "attemptedAt")).stream()
                .map(NotificationLogResponse::from)
                .toList();
    }
}
