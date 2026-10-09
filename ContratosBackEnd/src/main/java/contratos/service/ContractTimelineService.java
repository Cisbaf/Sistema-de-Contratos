package contratos.service;

import contratos.api.dto.Contract.ContractTimelineEvent;
import contratos.repository.AuditLogRepository;
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
 * Linha do tempo unificada do contrato: junta mudanças de status, documentos gerados, anexos (envio e remoção) e
 * exclusões de lançamento numa lista só, do mais recente para o mais antigo. Lê direto das tabelas de cada assunto, então
 * mostra também o que aconteceu antes da auditoria existir; só as exclusões de lançamento/ateste vêm da auditoria
 * (EXC-10), porque a linha original deixa de existir. A permissão é checada no controller (canRead).
 */
@Service
@RequiredArgsConstructor
public class ContractTimelineService {
    private final ContractRepository contractRepository;
    private final ContractStatusHistoryRepository statusHistoryRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final ContractAttachmentRepository attachmentRepository;
    private final AuditLogRepository auditLogRepository;

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
        // Exclusões não deixam linha própria nas tabelas de origem (o lançamento e o ateste somem), então vêm da auditoria.
        events.addAll(auditLogRepository.timelineDeletions(contractId));

        events.sort(Comparator.comparing(ContractTimelineEvent::occurredAt).reversed());
        return events;
    }
}
