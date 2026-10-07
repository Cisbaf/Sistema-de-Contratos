package contratos.service;

import contratos.api.dto.Contract.ContractRequest;
import contratos.api.dto.Contract.ContractResponse;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.enums.AuditAction;
import contratos.domain.enums.AuditEntityType;
import contratos.domain.enums.ContractStatus;
import contratos.domain.enums.PerfilUsuario;
import contratos.exception.ConflictException;
import contratos.repository.*;
import contratos.validation.DocumentoFiscal;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ContractService {
    private final ContractRepository contracts;
    private final UserRepository users;
    private final ContractStatusService contractStatusService;
    private final InterestEmailConfirmationRepository interestRepository;
    private final TechnicalOpinionRepository technicalOpinionRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final ContractAttachmentRepository attachmentRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final LancamentoFinanceiroRepository financeiroRepository;
    private final LancamentoFinanceiroHistoricoRepository financeiroHistoricoRepository;
    private final AuditService auditService;
    private final AttachmentStorage attachmentStorage;

    @Transactional(readOnly = true)
    public List<ContractResponse> findAll() {
        return contracts.findAll().stream().map(contract -> EntityMapper.contract(contract, getConfirmadosId(contract.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<ContractResponse> findMine(String username) {
        return contracts.findDistinctByFiscaisUsername(username).stream().map(contract -> EntityMapper.contract(contract, getConfirmadosId(contract.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ContractResponse findById(Long id) {
        return EntityMapper.contract(getContract(id), getConfirmadosId(id));
    }

    @Transactional
    public ContractResponse create(ContractRequest request, AppUser appUser) {
        validateDates(request);
        if (contracts.existsByNumberContractIgnoreCase(request.numberContract().trim())) {
            throw new ConflictException("Contrato já cadastrado com o numero: " + request.numberContract().trim());
        }
        Contract contract = new Contract();
        apply(contract, request);
        Contract savedContract = contracts.save(contract);

        contractStatusService.updateByDeadline(
                savedContract,
                LocalDate.now()
        );

        auditService.record(appUser, AuditAction.CREATE, AuditEntityType.CONTRACT, savedContract.getId(),
                savedContract.getId(), "Contrato " + savedContract.getNumberContract() + " criado", null);

        return EntityMapper.contract(savedContract, List.of());
    }

    @Transactional
    public ContractResponse update(Long id, ContractRequest request, AppUser appUser) {
        validateDates(request);
        Contract contract = getContract(id);
        if (contracts.existsByNumberContractIgnoreCaseAndIdNot(request.numberContract().trim(), contract.getId())) {
            throw new ConflictException("Contrato já cadastrado com o numero: " + request.numberContract().trim());
        }

        ContractStatus previousStatus = contract.getStatus();
        LocalDate previousEndDate = contract.getEndDate();
        boolean renewalInProgress = previousStatus == ContractStatus.EMAIL_ENVIADO
                || previousStatus == ContractStatus.RENOVACAO_ABERTA_SEI;
        boolean datesChanged = !previousEndDate.equals(request.endDate())
                || !contract.getStartDate().equals(request.startDate());

        // ST-10: com a renovação em andamento, só o Administrador pode mexer nas datas (mexer cancela a renovação).
        if (renewalInProgress && datesChanged && appUser.getPerfil() != PerfilUsuario.ADMIN) {
            throw new ConflictException("Com a renovação em andamento, só o Administrador pode alterar as datas da vigência.");
        }

        AuditChangeLog changes = new AuditChangeLog()
                .field("Valor global", contract.getValueGlobal(), request.valueGlobal())
                .field("Valor mensal", contract.getValueMensal(), request.valueMensal())
                .field("Início da vigência", contract.getStartDate(), request.startDate())
                .field("Término da vigência", contract.getEndDate(), request.endDate())
                .field("Fiscais", fiscalNames(contract.getFiscais()), fiscalNames(request));


        apply(contract, request);

        contractStatusService.recalculateAfterEdit(contract, previousEndDate, appUser, LocalDate.now());

        if (renewalInProgress && contract.getStatus() != previousStatus) {
            // Renovação cancelada pela mudança de término: confirmações e pareceres do ciclo não valem mais
            // (mesma limpeza do aditivo). Documentos gerados e anexos ficam.
            interestRepository.deleteByContract_Id(contract.getId());
            technicalOpinionRepository.deleteByContract_Id(contract.getId());
        }
        changes.field("Status", previousStatus, contract.getStatus());

        auditService.record(appUser, AuditAction.UPDATE, AuditEntityType.CONTRACT, contract.getId(),
                contract.getId(), "Contrato " + contract.getNumberContract() + " atualizado", changes.build());

        return EntityMapper.contract(contract, getConfirmadosId(id));
    }

    private String fiscalNames(ContractRequest request) {
        return fiscalNames(new LinkedHashSet<>(users.findAllById(request.fiscalIds())));
    }

    private String fiscalNames(Set<AppUser> fiscais) {
        return fiscais.stream().map(AppUser::getName).sorted().collect(Collectors.joining(", "));
    }

    /**
     * Exclui o contrato e todos os registros que dependem dele (histórico de
     * status, confirmações de e-mail de interesse, pareceres técnicos e
     * documentos gerados). Sem isso, o banco recusa a exclusão por violação
     * de chave estrangeira em qualquer contrato que já tenha avançado de
     * status ou gerado algum documento.
     */
    @Transactional
    public void delete(Long id, AppUser appUser) {
        Contract contract = getContract(id);
        Long contractId = contract.getId();

        auditService.record(appUser, AuditAction.DELETE, AuditEntityType.CONTRACT, contract.getId(),
                contract.getId(), "Contrato " + contract.getNumberContract() + " excluido", null);

        contractStatusService.deleteHistoryOf(contractId);
        interestRepository.deleteByContract_Id(contractId);
        technicalOpinionRepository.deleteByContract_Id(contractId);
        generatedDocumentRepository.deleteByContract_Id(contractId);
        // Os arquivos dos anexos saem do disco só depois que o banco confirmar a exclusão do contrato.
        attachmentRepository.findStoragePathsByContractId(contractId).forEach(attachmentStorage::deleteAfterCommit);
        attachmentRepository.deleteByContract_Id(contractId);
        notificationLogRepository.deleteByContract_Id(contractId);
        financeiroHistoricoRepository.deleteByContrato_Id(contractId);
        financeiroRepository.deleteByContrato_Id(contractId);
        contracts.delete(contract);
    }

    private List<Long> getConfirmadosId(Long contractId) {
        return interestRepository.findByContract_Id(contractId)
                .stream().map(confirmation -> confirmation.getFiscal().getId()).toList();
    }

    private Contract getContract(Long id) {
        return contracts.findById(id).orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
    }

    private void apply(Contract contract, ContractRequest request) {
        Set<Long> ids = request.fiscalIds();
        List<AppUser> selected = users.findAllById(ids);
        var usersAntigos = contract.getFiscais().stream().map(AppUser::getId).collect(Collectors.toSet());
        boolean match;

        if (!request.endDate().isBefore(LocalDate.now())) {
            match = selected.stream()
                    .anyMatch(user ->
                            user.getPerfil() != PerfilUsuario.FISCAL);
        } else {
            match = selected.stream()
                    .filter(user ->
                            !usersAntigos.contains(user.getId()))
                    .anyMatch(user ->
                            user.getPerfil() != PerfilUsuario.FISCAL);
        }

        if (selected.size() != ids.size()) {
            throw new IllegalArgumentException("Um ou mais fiscais não foram encontrados");
        }
        if (match) {
            throw new IllegalArgumentException("Um ou mais usuários atribuídos não são fiscais");

        }

        contract.update(request.numberContract().trim(),
                request.numberProcess().trim(),
                request.object().trim(),
                request.company().trim(),
                DocumentoFiscal.normalizar(request.cnpj()),
                request.valueGlobal(),
                request.valueMensal(),
                request.startDate(),
                request.endDate(),
                blankToNull(request.font()),
                blankToNull(request.ta()), new LinkedHashSet<>(selected),
                request.seiProcessNumber().trim(),
                request.maxExtensionMonths());
    }

    private void validateDates(ContractRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("A data final não pode ser anterior à data inicial");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
