package br.org.cisbaf.contratos;

import br.org.cisbaf.contratos.repository.ContractRepository;
import br.org.cisbaf.contratos.security.ContractAuthorization;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractAuthorizationTest {
    @Mock
    private ContractRepository contracts;

    @InjectMocks
    private ContractAuthorization authorization;

    @Test
    void adminPodeLerQualquerContrato() {
        Authentication authentication = authentication("admin@cisbaf.org.br", "ROLE_ADMIN");

        boolean allowed = authorization.canRead(99L, authentication);

        assertThat(allowed).isTrue();
        verifyNoInteractions(contracts);
    }

    @Test
    void fiscalAssociadoPodeLerContrato() {
        Authentication authentication = authentication("fiscal@cisbaf.org.br", "ROLE_FISCAL");
        when(contracts.existsForFiscal(99L, "fiscal@cisbaf.org.br")).thenReturn(true);

        boolean allowed = authorization.canRead(99L, authentication);

        assertThat(allowed).isTrue();
        verify(contracts).existsForFiscal(99L, "fiscal@cisbaf.org.br");
    }

    @Test
    void fiscalNaoAssociadoNaoPodeLerContrato() {
        Authentication authentication = authentication("outro@cisbaf.org.br", "ROLE_FISCAL");
        when(contracts.existsForFiscal(99L, "outro@cisbaf.org.br")).thenReturn(false);

        boolean allowed = authorization.canRead(99L, authentication);

        assertThat(allowed).isFalse();
        verify(contracts).existsForFiscal(99L, "outro@cisbaf.org.br");
    }

    private Authentication authentication(String username, String role) {
        return new UsernamePasswordAuthenticationToken(username, "ignored",
                List.of(new SimpleGrantedAuthority(role)));
    }
}
