package contratos.service;

import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.ContractStatusHistory;
import contratos.domain.enums.ContractStatus;
import contratos.domain.enums.ContractStatusTrigger;
import contratos.repository.ContractRepository;
import contratos.repository.ContractStatusHistoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractStatusService {
    private final ContractStatusHistoryRepository repository;
    private final ContractRepository contractRepository;

    @Transactional
    public boolean updateByDeadline(Contract contract, LocalDate referenceDate){
        Objects.requireNonNull(contract, "O contrato é obrigatório.");
        Objects.requireNonNull(referenceDate, "A data de referencia é obrigatória.");

        var previousStatus = contract.getStatus();
        var changed = contract.updateStatusByDeadline(referenceDate);

        if (!changed) return false;

        repository.save(new ContractStatusHistory(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")), null, contract,previousStatus, contract.getStatus(), ContractStatusTrigger.DEADLINE));
        return true;
    }

    /**
     * ST-10 — chamado depois de aplicar a edição do contrato. Se o término da vigência não mudou, mantém o
     * comportamento antigo (só avança por prazo, gatilho {@code DEADLINE}). Se mudou, recalcula o status nos dois
     * sentidos (inclusive cancelando a renovação) e grava uma única linha de histórico, gatilho {@code CONTRACT_EDITED}.
     * Quem chama é responsável por apagar confirmações e pareceres quando a renovação for cancelada.
     */
    @Transactional
    public boolean recalculateAfterEdit(Contract contract, LocalDate previousEndDate, AppUser actor, LocalDate referenceDate) {
        Objects.requireNonNull(contract, "O contrato é obrigatório.");
        Objects.requireNonNull(previousEndDate, "O término anterior é obrigatório.");
        Objects.requireNonNull(referenceDate, "A data de referencia é obrigatória.");

        if (previousEndDate.equals(contract.getEndDate())) {
            return updateByDeadline(contract, referenceDate);
        }

        var previousStatus = contract.getStatus();
        if (!contract.recalculateStatusForNewEndDate(referenceDate)) return false;

        repository.save(new ContractStatusHistory(
                LocalDateTime.now(ZoneId.of("America/Sao_Paulo")),
                actor, contract, previousStatus, contract.getStatus(),
                ContractStatusTrigger.CONTRACT_EDITED
        ));
        return true;
    }

    @Transactional
    public int updateAllByDeadline(LocalDate referenceDate){
        Objects.requireNonNull(referenceDate, "A data de referencia é requerida");
        var contratos = contractRepository.findAllByStatusAndEndDateGreaterThanEqual(ContractStatus.EM_VIGENCIA, referenceDate);

        int updateContratos = 0;

        for(var contract : contratos){
            if (updateByDeadline(contract, referenceDate)){
                updateContratos ++;
            }
        }
        return updateContratos;
    }

    @Transactional
    public boolean advanceAfterInterestEmailGenerated(Contract contract, AppUser user) {
        var previousStatus = contract.getStatus();
        var changed = contract.markInterestEmailSent();
        if (!changed) return false;

        repository.save(new ContractStatusHistory(
                LocalDateTime.now(ZoneId.of("America/Sao_Paulo")),
                user, contract, previousStatus, contract.getStatus(),
                ContractStatusTrigger.INTEREST_EMAIL_GENERATED
        ));
        return true;
    }

    @Transactional
    public void deleteHistoryOf(Long contractId) {
        repository.deleteByContract_Id(contractId);
    }

    @Transactional
    public boolean advanceAfterTechnicalOpinionGenerated(Contract contract, AppUser user) {
        var previousStatus = contract.getStatus();
        var changed = contract.markTechnicalOpinionGenerated();
        if (!changed) return false;

        repository.save(new ContractStatusHistory(
                LocalDateTime.now(ZoneId.of("America/Sao_Paulo")),
                user, contract, previousStatus, contract.getStatus(),
                ContractStatusTrigger.TECHNICAL_OPINION_GENERATED
        ));
        return true;
    }

    @Transactional
    public boolean advanceAfterAmendmentRegistered(Contract contract, AppUser user, LocalDate endDate, String ta) {
        var previousStatus = contract.getStatus();
        if (endDate == null || ta == null) return false;
        var changed = contract.registerAmendment(endDate, ta);
        if (!changed) return false;

        repository.save(new ContractStatusHistory(
                LocalDateTime.now(ZoneId.of("America/Sao_Paulo")),
                user, contract, previousStatus, contract.getStatus(),
                ContractStatusTrigger.ADITIVO_REGISTRADO
        ));
        return true;
    }

    @Scheduled(
            cron = "${contracts.status-update-cron:0 0 1 * * *}",
            zone = "America/Sao_Paulo"
    )
    @Transactional
    public void runDailyDeadlineUpdate() {
        LocalDate referenceDate =
                LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        int updated = updateAllByDeadline(referenceDate);

        log.info(
                "Atualização diária concluída: {} contrato(s) atualizado(s)",
                updated
        );
    }

    @PostConstruct
    public void executarAoIniciar() {
        runDailyDeadlineUpdate();
    }

}
