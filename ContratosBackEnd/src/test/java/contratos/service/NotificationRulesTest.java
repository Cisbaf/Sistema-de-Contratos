package contratos.service;

import contratos.domain.enums.NotificationAlertType;
import contratos.service.Notification.NotificationRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationRulesTest {

    private static final LocalDate END = LocalDate.of(2027, 6, 30);

    private Optional<NotificationAlertType> alert(LocalDate end, LocalDate today) {
        return NotificationRules.resolveAlert(end, today, 6, 4);
    }

    @Test
    void foraDasJanelasNaoAlerta() {
        // 6 meses antes de 30/06/2027 é 30/12/2026
        assertThat(alert(END, LocalDate.of(2026, 12, 29))).isEmpty();
    }

    @Test
    void noDiaExatoDosSeisMesesAlertaSeis() {
        assertThat(alert(END, LocalDate.of(2026, 12, 30))).contains(NotificationAlertType.SIX_MONTHS);
    }

    @Test
    void umDiaAntesDosQuatroMesesAindaAlertaSeis() {
        // 4 meses antes de 30/06/2027 é 28/02/2027
        assertThat(alert(END, LocalDate.of(2027, 2, 27))).contains(NotificationAlertType.SIX_MONTHS);
    }

    @Test
    void noDiaExatoDosQuatroMesesAlertaQuatro() {
        assertThat(alert(END, LocalDate.of(2027, 2, 28))).contains(NotificationAlertType.FOUR_MONTHS);
    }

    @Test
    void dentroDaJanelaDeQuatroNaoAlertaSeis() {
        // contrato a 3 meses do fim: só o mais próximo
        assertThat(alert(END, LocalDate.of(2027, 3, 30))).contains(NotificationAlertType.FOUR_MONTHS);
    }

    @Test
    void noDiaDoFimAindaAlerta() {
        assertThat(alert(END, END)).contains(NotificationAlertType.FOUR_MONTHS);
    }

    @Test
    void umDiaDepoisDoFimNaoAlerta() {
        assertThat(alert(END, END.plusDays(1))).isEmpty();
    }

    @Test
    void fimDeMesUsaOUltimoDiaDoMesMenor() {
        // 31/08/2027 menos 6 meses cai em 28/02/2027 (fevereiro não tem 31)
        LocalDate end = LocalDate.of(2027, 8, 31);
        assertThat(alert(end, LocalDate.of(2027, 2, 27))).isEmpty();
        assertThat(alert(end, LocalDate.of(2027, 2, 28))).contains(NotificationAlertType.SIX_MONTHS);
    }

    @Test
    void prazosConfiguraveisSaoRespeitados() {
        // 3 e 1 meses: 1 mês antes de 30/06/2027 é 30/05/2027
        assertThat(NotificationRules.resolveAlert(END, LocalDate.of(2027, 5, 30), 3, 1))
                .contains(NotificationAlertType.FOUR_MONTHS);
        assertThat(NotificationRules.resolveAlert(END, LocalDate.of(2027, 5, 29), 3, 1))
                .contains(NotificationAlertType.SIX_MONTHS);
        assertThat(NotificationRules.resolveAlert(END, LocalDate.of(2027, 3, 29), 3, 1)).isEmpty();
    }

    @Test
    void prazosInvalidosSaoRejeitados() {
        assertThatThrownBy(() -> NotificationRules.resolveAlert(END, END, 4, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationRules.resolveAlert(END, END, 6, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationRules.resolveAlert(END, END, 3, 6))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
