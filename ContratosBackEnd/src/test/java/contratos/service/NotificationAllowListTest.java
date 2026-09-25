package contratos.service;

import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.Sector;
import contratos.domain.enums.PerfilUsuario;
import contratos.repository.ContractRepository;
import contratos.repository.NotificationLogRepository;
import contratos.repository.SectorRepository;
import contratos.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Com a lista de segurança preenchida, só os endereços dela recebem e os demais não geram nem log. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:allowlisttest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "notifications.mail.allowed-recipients=f1@cisbaf.org.br, CI@Cisbaf.org.br"})
@ActiveProfiles("test")
@Import(NotificationDispatcherTest.FakeSenderConfig.class)
class NotificationAllowListTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    @Autowired NotificationDispatcher dispatcher;
    @Autowired NotificationDispatcherTest.FakeSender sender;
    @Autowired NotificationLogRepository logs;
    @Autowired ContractRepository contracts;
    @Autowired UserRepository users;
    @Autowired SectorRepository sectors;

    @Test
    void soQuemEstaNaListaRecebeEOsDemaisNaoGeramLog() {
        sender.sentTo.clear();
        logs.deleteAll();
        contracts.deleteAll();
        users.deleteAll();
        sectors.deleteAll();

        Sector ti = sectors.save(new Sector("TI"));
        AppUser f1 = users.save(new AppUser("f1", "x", "Fiscal Um", "f1@cisbaf.org.br", null, ti, PerfilUsuario.FISCAL));
        users.save(new AppUser("ci", "x", "Controle", "ci@cisbaf.org.br", null, ti, PerfilUsuario.CONTROLE_INTERNO));
        users.save(new AppUser("ci2", "x", "Controle Dois", "ci2@cisbaf.org.br", null, ti, PerfilUsuario.CONTROLE_INTERNO));

        Contract c = new Contract();
        c.update("DENTRO", "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), TODAY.plusMonths(3), null, null, Set.of(f1), "SEI", null);
        contracts.save(c);

        var result = dispatcher.runDaily(TODAY);

        assertThat(sender.sentTo).containsExactlyInAnyOrder("f1@cisbaf.org.br", "ci@cisbaf.org.br");
        assertThat(result.delivered()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1); // o outro usuário do Controle Interno, fora da lista
        assertThat(logs.findAll()).hasSize(2);
    }
}
