package contratos.service;

import contratos.api.dto.Contract.ContractResponse;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.ContractAttachment;
import contratos.domain.enums.AttachmentType;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import contratos.domain.enums.ContractStatus;
import contratos.exception.ConflictException;
import contratos.repository.ContractAttachmentRepository;
import contratos.repository.ContractRepository;
import contratos.repository.InterestEmailConfirmationRepository;
import contratos.repository.TechnicalOpinionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TA-10.3 — registro do Termo Aditivo: o contrato em renovação volta a "Em vigência" com a nova data,
 * o documento do aditivo fica anexado ao contrato e o ciclo de renovação recomeça do zero.
 * Tudo acontece numa única transação: se qualquer passo falhar, nada muda.
 */
@Service
@RequiredArgsConstructor
public class ContractAmendmentService {
    private static final int MAX_ACTIVE_ATTACHMENTS_PER_CONTRACT = 10;
    private static final Pattern TRAILING_NUMBER = Pattern.compile("(?<!\\d)(\\d{1,6})\\s*$");
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContractRepository contracts;
    private final ContractAttachmentRepository attachments;
    private final InterestEmailConfirmationRepository interestRepository;
    private final TechnicalOpinionRepository technicalOpinionRepository;
    private final ContractStatusService contractStatusService;
    private final AuditService auditService;
    private final ContractAttachmentService attachmentService;
    private final AttachmentStorage storage;

    @Transactional
    public ContractResponse register(Long contractId, MultipartFile file, LocalDate newEndDate, AppUser actor) throws IOException {
        // Trava a linha: dois registros simultâneos não passam juntos pela checagem de status.
        Contract contract = contracts.findByIdForUpdate(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));

        if (contract.getStatus() != ContractStatus.RENOVACAO_ABERTA_SEI) {
            throw new ConflictException("O Termo Aditivo só pode ser registrado quando o contrato está com a renovação aberta no SEI.");
        }

        validateNewEndDate(contract, newEndDate);

        if (file == null) {
            throw new IllegalArgumentException("O documento do Termo Aditivo é obrigatório");
        }
        long ativos = attachments.countByContract_IdAndAtivoTrue(contractId);
        if (ativos + 1 > MAX_ACTIVE_ATTACHMENTS_PER_CONTRACT) {
            throw new IllegalArgumentException("Limite de " + MAX_ACTIVE_ATTACHMENTS_PER_CONTRACT
                    + " anexos por contrato. Este contrato já tem " + ativos
                    + "; remova um anexo comum antes de registrar o Termo Aditivo.");
        }
        // Grava o arquivo em disco (apagado sozinho se esta transação for desfeita) e monta o anexo.
        var attachment = attachmentService
                .storeNew(List.of(file), contract, actor, AttachmentType.TERMO_ADITIVO).getFirst();

        // Fotografia do "antes" para a auditoria (o contrato é alterado logo abaixo).
        LocalDate previousEndDate = contract.getEndDate();
        ContractStatus previousStatus = contract.getStatus();
        String previousTa = contract.getTa();
        long amendmentsSoFar = attachments.countByContract_IdAndAttType(contractId, AttachmentType.TERMO_ADITIVO);
        String newTa = nextTaLabel(previousTa, amendmentsSoFar);

        attachments.save(attachment);

        if (!contractStatusService.advanceAfterAmendmentRegistered(contract, actor, newEndDate, newTa)) {
            // O status já foi validado acima; chegar aqui é um erro de programação, não de uso.
            throw new IllegalStateException("Não foi possível registrar o Termo Aditivo");
        }

        // O ciclo recomeça: confirmações de interesse e pareceres do ciclo anterior não valem para o próximo
        // (a chave única contrato+fiscal bloquearia o fiscal de confirmar de novo). Documentos gerados ficam.
        interestRepository.deleteByContract_Id(contractId);
        technicalOpinionRepository.deleteByContract_Id(contractId);

        // ST-10: a data nova pode já cair na janela de 6 meses; nesse caso o contrato segue para "Aguardando e-mail".
        contractStatusService.updateByDeadline(contract, LocalDate.now());

        String details = new AuditChangeLog()
                .field("Término da vigência", previousEndDate, contract.getEndDate())
                .field("Status", previousStatus, contract.getStatus())
                .field("TA", previousTa, contract.getTa())
                .note("Termo Aditivo registrado (arquivo: " + attachment.getFileName() + ")")
                .build();
        auditService.record(actor, AuditAction.UPDATE, AuditEntityType.CONTRACT, contract.getId(), contract.getId(),
                "Termo Aditivo registrado no contrato " + contract.getNumberContract(), details);

        return EntityMapper.contract(contract, List.of());
    }

    /**
     * Troca o arquivo do documento de um Termo Aditivo já registrado, em qualquer status do contrato, sem mexer
     * no fluxo: data, status, "TA" e histórico de status ficam como estão. Sobrescreve a mesma linha do anexo;
     * o nome do arquivo anterior fica na auditoria.
     */
    @Transactional
    public void replaceDocument(Long contractId, Long attachmentId, MultipartFile file, AppUser actor) throws IOException {
        ContractAttachment attachment = attachments.findById(attachmentId)
                .filter(found -> found.getContract().getId().equals(contractId))
                .orElseThrow(() -> new EntityNotFoundException("Não existe anexo com o id " + attachmentId + " neste contrato"));

        if (attachment.getAttType() != AttachmentType.TERMO_ADITIVO || !attachment.isAtivo()) {
            throw new ConflictException("Só o documento de um Termo Aditivo ativo pode ser substituído por aqui.");
        }
        if (file == null) {
            throw new IllegalArgumentException("O novo documento do Termo Aditivo é obrigatório");
        }

        Contract contract = attachment.getContract();
        ContractAttachmentService.ValidatedFile replacement = ContractAttachmentService
                .validateFiles(List.of(file)).getFirst();

        String previousName = attachment.getFileName();
        String previousPath = attachment.getStoragePath();

        // O arquivo novo é gravado antes (e apagado se a transação for desfeita); o antigo só é apagado depois do
        // commit. Anexo antigo, ainda com o conteúdo no banco, não tem arquivo em disco para apagar.
        String newPath = attachmentService.store(contractId, replacement);
        attachment.replaceFile(replacement.fileName(), replacement.contentType(), replacement.sizeBytes(), newPath, actor);
        if (previousPath != null) {
            storage.deleteAfterCommit(previousPath);
        }

        String details = new AuditChangeLog()
                .note("Arquivo: " + previousName + " -> " + replacement.fileName())
                .build();
        auditService.record(actor, AuditAction.UPDATE, AuditEntityType.CONTRACT, contract.getId(), contract.getId(),
                "Documento do Termo Aditivo substituído no contrato " + contract.getNumberContract(), details);
    }

    private void validateNewEndDate(Contract contract, LocalDate newEndDate) {
        if (newEndDate == null) {
            throw new IllegalArgumentException("A nova data de fim da vigência é obrigatória");
        }
        if (!newEndDate.isAfter(contract.getEndDate())) {
            throw new IllegalArgumentException("A nova data de fim da vigência deve ser posterior à atual ("
                    + contract.getEndDate().format(BR_DATE) + ")");
        }
        Integer maxMonths = contract.getMaxExtensionMonths();
        if (maxMonths != null) {
            LocalDate limit = contract.getEndDate().plusMonths(maxMonths);
            if (newEndDate.isAfter(limit)) {
                throw new IllegalArgumentException("A nova data não pode passar de " + limit.format(BR_DATE)
                        + " (limite de prorrogação de " + maxMonths + " meses)");
            }
        }
    }

    /**
     * Número do próximo aditivo: o maior entre o número no fim do texto atual do campo "ta" (0 se vazio ou sem
     * número) e a quantidade de aditivos já registrados, mais 1. O campo guarda só o número ("1", "2"...): a tela
     * é quem escreve o prefixo "TA ". Assim "03" legado vira "4" e contrato sem nada vira "1".
     */
    static String nextTaLabel(String currentTa, long amendmentsSoFar) {
        long fromText = 0;
        if (currentTa != null) {
            Matcher matcher = TRAILING_NUMBER.matcher(currentTa);
            if (matcher.find()) {
                fromText = Long.parseLong(matcher.group(1));
            }
        }
        return String.valueOf(Math.max(fromText, amendmentsSoFar) + 1);
    }
}
