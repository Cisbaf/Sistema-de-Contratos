package contratos.service;

import contratos.api.dto.Notificacao.NotificationMessage;
import contratos.api.dto.Notificacao.Recipient;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.enums.NotificationAlertType;
import contratos.domain.enums.PerfilUsuario;
import contratos.domain.enums.RecipientRole;
import contratos.service.Notification.NotificationMessages;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMessagesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
    private static final Recipient ANA = new Recipient(RecipientRole.FISCAL, "Ana Souza", "ana@cisbaf.org.br");

    private Contract contract(String number, LocalDate end) {
        AppUser bruno = new AppUser("bruno", "x", "Bruno Lima", "bruno@cisbaf.org.br", null, null, PerfilUsuario.FISCAL);
        AppUser ana = new AppUser("ana", "x", "Ana Souza", "ana@cisbaf.org.br", null, null, PerfilUsuario.FISCAL);
        Contract c = new Contract();
        c.update(number, "PROC-77", "Serviço de limpeza", "Limpa Tudo Ltda", "11222333000181",
                new BigDecimal("120000.00"), new BigDecimal("10000.00"),
                LocalDate.of(2026, 1, 1), end, null, null, Set.of(bruno, ana), "SEI-1", 12);
        return c;
    }

    @Test
    void alertaDeSeisMesesTemAssuntoETextoDeAviso() {
        Contract c = contract("12/2026", LocalDate.of(2027, 3, 25)); // 6 meses exatos
        NotificationMessage m = NotificationMessages.build(c, NotificationAlertType.SIX_MONTHS, ANA, TODAY);

        assertThat(m.subject()).isEqualTo("[CISBAF] Contrato 12/2026 vence em 25/03/2027");
        assertThat(m.text())
                .contains("Prezado(a) Ana Souza,")
                .contains("aviso automático")
                .contains("Contrato: 12/2026")
                .contains("Empresa: Limpa Tudo Ltda (CNPJ 11222333000181)")
                .contains("Objeto: Serviço de limpeza")
                .contains("Processo: PROC-77")
                .contains("Término da vigência: 25/03/2027")
                .contains("Fiscal(is): Ana Souza, Bruno Lima")
                .contains("Faltam cerca de 6 meses para o término.")
                .contains("Não responda este e-mail.")
                .doesNotContain("REFORÇO");
    }

    @Test
    void alertaDeQuatroMesesEhReforco() {
        Contract c = contract("12/2026", LocalDate.of(2027, 1, 25)); // 4 meses exatos
        NotificationMessage m = NotificationMessages.build(c, NotificationAlertType.FOUR_MONTHS, ANA, TODAY);

        assertThat(m.subject()).startsWith("[CISBAF] REFORÇO: Contrato 12/2026");
        assertThat(m.text())
                .contains("REFORÇO do aviso automático")
                .contains("Faltam cerca de 4 meses para o término.")
                .contains("é hora de agir");
    }

    @Test
    void mesesRestantesArredondamParaCima() {
        // 3 meses e 20 dias => 4
        assertThat(NotificationMessages.monthsRemaining(TODAY, LocalDate.of(2027, 1, 15))).isEqualTo(4);
        assertThat(NotificationMessages.monthsRemaining(TODAY, LocalDate.of(2026, 12, 25))).isEqualTo(3);
        assertThat(NotificationMessages.monthsRemaining(TODAY, LocalDate.of(2026, 10, 1))).isEqualTo(1);
        assertThat(NotificationMessages.monthsRemaining(TODAY, TODAY)).isEqualTo(0);
    }

    @Test
    void umMesUsaSingularEHojeTemFrasepropria() {
        Contract umMes = contract("1/2026", LocalDate.of(2026, 10, 25));
        assertThat(NotificationMessages.build(umMes, NotificationAlertType.FOUR_MONTHS, ANA, TODAY).text())
                .contains("Faltam cerca de 1 mês para o término.");

        Contract hoje = contract("2/2026", TODAY);
        assertThat(NotificationMessages.build(hoje, NotificationAlertType.FOUR_MONTHS, ANA, TODAY).text())
                .contains("O término da vigência é hoje.");
    }

    @Test
    void assuntoNuncaTemQuebraDeLinha() {
        Contract c = contract("12/2026\r\nBcc: alguem@x.com", LocalDate.of(2027, 1, 25));
        NotificationMessage m = NotificationMessages.build(c, NotificationAlertType.FOUR_MONTHS, ANA, TODAY);

        assertThat(m.subject()).doesNotContain("\r").doesNotContain("\n");
    }
}
