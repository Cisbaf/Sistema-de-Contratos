package contratos.service;

import contratos.domain.PerfilUsuario;
import contratos.api.dto.ContractRequest;
import contratos.api.dto.ContractResponse;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.repository.ContractRepository;
import contratos.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class ContractService {
    private final ContractRepository contracts;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<ContractResponse> findAll() {
        return contracts.findAll().stream().map(EntityMapper::contract).toList();
    }

    @Transactional(readOnly = true)
    public List<ContractResponse> findMine(String username) {
        return contracts.findDistinctByFiscaisUsername(username).stream().map(EntityMapper::contract).toList();
    }

    @Transactional(readOnly = true)
    public ContractResponse findById(Long id) {
        return EntityMapper.contract(getContract(id));
    }

    @Transactional
    public ContractResponse create(ContractRequest request) {
        validateDates(request);
        if (contracts.existsByNumberContractIgnoreCase(request.numberContract().trim())) {
            throw new DataIntegrityViolationException("Contrato já cadastrado com o numero: " + request.numberContract().trim());
        }
        Contract contract = new Contract();
        apply(contract, request);
        return EntityMapper.contract(contracts.save(contract));
    }

    @Transactional
    public ContractResponse update(Long id, ContractRequest request) {
        validateDates(request);
        Contract contract = getContract(id);
        if (contracts.existsByNumberContractIgnoreCaseAndIdNot(request.numberContract().trim(), contract.getId())) {
            throw new DataIntegrityViolationException("Contrato já cadastrado com o numero: " + request.numberContract().trim());
        }
        apply(contract, request);
        return EntityMapper.contract(contract);
    }

    @Transactional
    public void delete(Long id) {
        contracts.delete(getContract(id));
    }

    private Contract getContract(Long id) {
        return contracts.findById(id).orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
    }

    private void apply(Contract contract, ContractRequest request) {
        Set<Long> ids = request.fiscalIds();
        List<AppUser> selected = users.findAllById(ids);

        var match = selected.stream().anyMatch(select -> select.getPerfil() != PerfilUsuario.FISCAL);

        if (selected.size() != ids.size() ) {
            throw new IllegalArgumentException("Um ou mais fiscais não foram encontrados");
        }
        if (match){
            throw new IllegalArgumentException("Um ou mais usuários atribuídos não são fiscais");

        }
        
        contract.update(request.numberContract().trim(),
                request.numberProcess().trim(),
                request.object().trim(),
                request.company().trim(),
                normalizeCnpj(request.cnpj().trim()),
                request.valueGlobal(),
                request.valueMensal(),
                request.startDate(),
                request.endDate(),
                blankToNull(request.font()),
                blankToNull(request.ta()), new LinkedHashSet<>(selected));
    }

    private void validateDates(ContractRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("A data final não pode ser anterior à data inicial");
        }
    }

    private String normalizeCnpj(String value) {
        return value.replaceAll("\\D", "");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
