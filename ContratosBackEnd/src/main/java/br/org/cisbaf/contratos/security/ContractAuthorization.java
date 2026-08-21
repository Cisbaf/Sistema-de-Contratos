package br.org.cisbaf.contratos.security;

import br.org.cisbaf.contratos.repository.ContractRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Regras de autorização que dependem do contrato específico. */
@Component("contractAuthorization")
public class ContractAuthorization {
    private final ContractRepository contracts;

    public ContractAuthorization(ContractRepository contracts) {
        this.contracts = contracts;
    }

    @Transactional(readOnly = true)
    public boolean canRead(Long contractId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;

        boolean privileged = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_CONTROLE_INTERNO"));
        if (privileged) return true;

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_FISCAL"))
                && contracts.existsForFiscal(contractId, authentication.getName());
    }
}
