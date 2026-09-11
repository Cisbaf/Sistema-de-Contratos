package contratos.service;

import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.ContractStatusHistory;
import contratos.domain.enums.ContractStatus;
import contratos.domain.enums.ContractStatusTrigger;
import contratos.repository.ContractRepository;
import contratos.repository.ContractStatusHistoryRepository;
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
}
