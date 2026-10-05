package contratos.service;

import contratos.api.dto.Contract.ContractTimelineEvent;
import contratos.repository.ContractAttachmentRepository;
import contratos.repository.ContractRepository;
import contratos.repository.ContractStatusHistoryRepository;
import contratos.repository.GeneratedDocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Linha do tempo unificada do contrato: junta mudanças de status, documentos gerados e anexos (envio e remoção) numa
 * lista só, do mais recente para o mais antigo. Lê direto das tabelas de cada assunto (e não da auditoria), então
 * mostra também o que aconteceu antes da auditoria existir. A permissão é checada no controller (canRead).
 */
@Service
@RequiredArgsConstructor
public class ContractTimelineService {
    private final ContractRepository contractRepository;
    private final ContractStatusHistoryRepository statusHistoryRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final ContractAttachmentRepository attachmentRepository;

    @Transactional(readOnly = true)
    public List<ContractTimelineEvent> timeline(Long contractId) {
        Objects.requireNonNull(contractId, "O id do contrato não pode ser nulo");
        if (!contractRepository.existsById(contractId)) {
            throw new EntityNotFoundException("Não existe contrato atrelado ao id: " + contractId);
        }

        // Ordem de inserção importa só no empate de horário (a ordenação é estável): numa lista do mais recente para o
        // mais antigo, a mudança de status (efeito) aparece acima do documento ou anexo que a provocou, e a remoção de um
        // anexo acima do seu envio.
        List<ContractTimelineEvent> events = new ArrayList<>();
        events.addAll(statusHistoryRepository.timeline(contractId));
        events.addAll(documentRepository.timeline(contractId));
        events.addAll(attachmentRepository.timelineRemovals(contractId));
        events.addAll(attachmentRepository.timelineUploads(contractId));

        events.sort(Comparator.comparing(ContractTimelineEvent::occurredAt).reversed());
        return events;
    }
}
