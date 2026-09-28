package contratos.service;

import contratos.api.dto.Notificacao.NotificationLogResponse;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.NotificationLog;
import contratos.domain.Sector;
import contratos.domain.enums.NotificationAlertType;
import contratos.domain.enums.NotificationChannel;
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
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** M5-60: a consulta administrativa só lê o que o dispatcher já gravou, mapeado com os dados do contrato. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:querytest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class NotificationQueryServiceTest {

    @Autowired NotificationQueryService service;
    @Autowired NotificationLogRepository logs;
    @Autowired ContractRepository contracts;
    @Autowired UserRepository users;
    @Autowired SectorRepository sectors;

    AppUser fiscal;
    Contract contratoA;
    Contract contratoB;

    @BeforeEach
    void setUp() {
        logs.deleteAll();
        contracts.deleteAll();
        users.deleteAll();
        sectors.deleteAll();

        Sector ti = sectors.save(new Sector("TI"));
        fiscal = users.save(new AppUser("f1", "x", "Fiscal Um", "f1@cisbaf.org.br", null, ti, PerfilUsuario.FISCAL));

        contratoA = contract("A-001/2026", LocalDate.of(2026, 12, 1), fiscal);
        contratoB = contract("B-002/2026", LocalDate.of(2027, 1, 1), fiscal);

        log(contratoA, NotificationAlertType.SIX_MONTHS, RecipientRole.FISCAL, "Fiscal Um", "f1@cisbaf.org.br",
                NotificationStatus.SENT, LocalDate.of(2026, 6, 1), null);
        log(contratoB, NotificationAlertType.FOUR_MONTHS, RecipientRole.INTERNAL_CONTROL, "Roberto", "roberto@cisbaf.org.br",
                NotificationStatus.FAILED, LocalDate.of(2027, 1, 1), "erro");
    }

    private Contract contract(String number, LocalDate end, AppUser... fiscais) {
        Contract c = new Contract();
        c.update(number, "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), end, null, null, Set.of(fiscais), "SEI-" + number, null);
        return contracts.save(c);
    }

    private void log(Contract contract, NotificationAlertType alertType, RecipientRole role, String name, String address,
                     NotificationStatus status, LocalDate cycleEndDate, String errorMessage) {
        logs.save(new NotificationLog(contract, alertType, cycleEndDate, NotificationChannel.EMAIL,
                role, name, address, status, errorMessage));
    }

    @Test
    void listaPorContratoTrazSoOsDaqueleContratoComDadosDoContrato() {
        List<NotificationLogResponse> result = service.listByContract(contratoA.getId());

        assertThat(result).hasSize(1);
        NotificationLogResponse row = result.get(0);
        assertThat(row.contractNumber()).isEqualTo("A-001/2026");
        assertThat(row.seiProcessNumber()).isEqualTo("SEI-A-001/2026");
        assertThat(row.fiscais()).containsExactly("Fiscal Um");
        assertThat(row.status()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void listaGeralTrazTodosOsContratos() {
        List<NotificationLogResponse> result = service.listAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(NotificationLogResponse::contractNumber)
                .containsExactlyInAnyOrder("A-001/2026", "B-002/2026");
        assertThat(result).filteredOn(r -> r.status() == NotificationStatus.FAILED)
                .singleElement().satisfies(r -> assertThat(r.errorMessage()).isEqualTo("erro"));
    }
}
