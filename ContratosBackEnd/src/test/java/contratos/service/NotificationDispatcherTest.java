package contratos.service;

import contratos.api.dto.Notificacao.Recipient;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.NotificationLog;
import contratos.domain.Sector;
import contratos.domain.enums.NotificationStatus;
import contratos.domain.enums.PerfilUsuario;
import contratos.domain.enums.RecipientRole;
import contratos.repository.ContractRepository;
import contratos.repository.NotificationLogRepository;
import contratos.repository.SectorRepository;
import contratos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Fluxo completo (planner + envio + log) contra um H2 próprio, com um remetente falso no lugar do SMTP. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:dispatchtest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Import(NotificationDispatcherTest.FakeSenderConfig.class)
class NotificationDispatcherTest {

    static class FakeSender implements NotificationSender {
        final List<String> sentTo = new ArrayList<>();
        final Set<String> failFor = new HashSet<>();

        @Override
        public void send(Recipient recipient, NotificationMessage message) {
            if (failFor.contains(recipient.address())) {
                throw new IllegalStateException("SMTP fora do ar");
            }
            sentTo.add(recipient.address());
        }

        @Override
        public NotificationStatus successStatus() {
            return NotificationStatus.SENT;
        }
    }

    @TestConfiguration
    static class FakeSenderConfig {
        @Bean
        @Primary
        FakeSender fakeSender() {
            return new FakeSender();
        }
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    @Autowired NotificationDispatcher dispatcher;
    @Autowired FakeSender sender;
    @Autowired NotificationLogRepository logs;
    @Autowired ContractRepository contracts;
    @Autowired UserRepository users;
    @Autowired SectorRepository sectors;

    AppUser f1, f2, ci, buyer;

    @BeforeEach
    void setUp() {
        sender.sentTo.clear();
        sender.failFor.clear();
        logs.deleteAll();
        contracts.deleteAll();
        users.deleteAll();
        sectors.deleteAll();

        Sector compras = sectors.save(new Sector("Compras"));
        Sector ti = sectors.save(new Sector("TI"));
        f1 = users.save(new AppUser("f1", "x", "Fiscal Um", "f1@cisbaf.org.br", null, ti, PerfilUsuario.FISCAL));
        // f2 é fiscal do contrato E do setor Compras E escreve o e-mail com maiúsculas: deve receber 1 só
        f2 = users.save(new AppUser("f2", "x", "Fiscal Dois", "F2@Cisbaf.org.br ", null, compras, PerfilUsuario.FISCAL));
        ci = users.save(new AppUser("ci", "x", "Controle", "ci@cisbaf.org.br", null, ti, PerfilUsuario.CONTROLE_INTERNO));
        buyer = users.save(new AppUser("buyer", "x", "Comprador", "compras@cisbaf.org.br", null, compras, PerfilUsuario.FISCAL));
    }

    private Contract contract(String number, LocalDate end, AppUser... fiscais) {
        Contract c = new Contract();
        c.update(number, "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), end, null, null, Set.of(fiscais), "SEI", null);
        return contracts.save(c);
    }

    @Test
    void enviaUmEmailPorPessoaSoParaContratosDentroDaJanela() {
        contract("DENTRO", TODAY.plusMonths(3), f1, f2);       // janela de 4 meses
        contract("FORA", TODAY.plusMonths(8), f1);             // ainda longe
        contract("VENCIDO", TODAY.minusDays(1), f1);           // já venceu

        var result = dispatcher.runDaily(TODAY);

        assertThat(result.delivered()).isEqualTo(4);
        assertThat(result.failed()).isZero();
        // f2 aparece como fiscal, Compras e com e-mail em maiúsculas: um destinatário só, normalizado
        assertThat(sender.sentTo).containsExactlyInAnyOrder(
                "f1@cisbaf.org.br", "f2@cisbaf.org.br", "ci@cisbaf.org.br", "compras@cisbaf.org.br");

        List<NotificationLog> rows = logs.findAll();
        assertThat(rows).hasSize(4).allMatch(r -> r.getStatus() == NotificationStatus.SENT);
        assertThat(rows).filteredOn(r -> r.getRecipientAddress().equals("f2@cisbaf.org.br"))
                .singleElement().satisfies(r -> assertThat(r.getRecipientRole()).isEqualTo(RecipientRole.FISCAL));
        assertThat(rows).filteredOn(r -> r.getRecipientAddress().equals("compras@cisbaf.org.br"))
                .singleElement().satisfies(r -> assertThat(r.getRecipientRole()).isEqualTo(RecipientRole.PURCHASING));
    }

    @Test
    void segundaExecucaoNoMesmoDiaNaoReenvia() {
        contract("DENTRO", TODAY.plusMonths(3), f1);

        dispatcher.runDaily(TODAY);
        sender.sentTo.clear();
        var again = dispatcher.runDaily(TODAY);

        assertThat(again.delivered()).isZero();
        assertThat(sender.sentTo).isEmpty();
    }

    @Test
    void falhaFicaRegistradaESoEsseDestinatarioEhTentadoDeNovo() {
        contract("DENTRO", TODAY.plusMonths(3), f1);
        sender.failFor.add("ci@cisbaf.org.br");

        var first = dispatcher.runDaily(TODAY);

        assertThat(first.failed()).isEqualTo(1);
        assertThat(first.delivered()).isEqualTo(3); // f1 e os dois usuários de Compras (f2 e comprador)
        assertThat(logs.findAll()).filteredOn(r -> r.getStatus() == NotificationStatus.FAILED)
                .singleElement().satisfies(r -> {
                    assertThat(r.getRecipientAddress()).isEqualTo("ci@cisbaf.org.br");
                    assertThat(r.getErrorMessage()).contains("SMTP fora do ar");
                });

        // SMTP volta: só o Controle Interno é reenviado, na mesma linha do log
        sender.failFor.clear();
        sender.sentTo.clear();
        var second = dispatcher.runDaily(TODAY.plusDays(1));

        assertThat(sender.sentTo).containsExactly("ci@cisbaf.org.br");
        assertThat(second.failed()).isZero();
        assertThat(logs.findAll()).hasSize(4).allMatch(r -> r.getStatus() == NotificationStatus.SENT);
    }

    @Test
    void mudarAVigenciaIniciaUmNovoCiclo() {
        Contract c = contract("DENTRO", TODAY.plusMonths(3), f1);
        dispatcher.runDaily(TODAY);
        int before = logs.findAll().size();

        // vigência prorrogada para 3 meses depois: novo endDate = novo ciclo, mas ainda dentro da janela
        c.update("DENTRO", "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), TODAY.plusMonths(3).plusDays(10), null, null, Set.of(f1), "SEI", null);
        contracts.save(c);
        sender.sentTo.clear();
        dispatcher.runDaily(TODAY);

        assertThat(before).isEqualTo(4); // f1, f2 e comprador (Compras) e Controle Interno
        assertThat(sender.sentTo).hasSize(4);
        assertThat(logs.findAll()).hasSize(before * 2);
    }
}
