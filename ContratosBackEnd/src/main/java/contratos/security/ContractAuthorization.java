package contratos.security;

import contratos.repository.ContractRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

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
                .anyMatch(authority -> Objects.equals(authority.getAuthority(), "ROLE_ADMIN")
                        || Objects.equals(authority.getAuthority(), "ROLE_CONTROLE_INTERNO"));
        if (privileged) return true;

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> Objects.equals(authority.getAuthority(), "ROLE_FISCAL"))
                && contracts.existsForFiscal(contractId, authentication.getName());
    }

    @Transactional(readOnly = true)
    public boolean isAssignedFiscal(Long contractId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;

        boolean hasFiscalRole = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_FISCAL".equals(authority.getAuthority()));


        return hasFiscalRole && contracts.existsForFiscal(contractId, authentication.getName());    }

}
