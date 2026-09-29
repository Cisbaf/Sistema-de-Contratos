package contratos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Linha única (id fixo {@link #SINGLETON_ID}) com os prazos de alerta editáveis pela tela (M6-40).
 * Semeada a partir de {@code application.properties} (notifications.*) na primeira consulta, quando
 * ainda não existe registro — ver {@code NotificationSettingsService}. Depois disso, o banco é a fonte
 * da verdade.
 *
 * <p>Lidos a cada execução do job (via {@code NotificationPlanner}), então uma mudança pela tela vale a
 * partir do próximo disparo, sem precisar de restart do backend.</p>
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "notification_settings")
public class NotificationSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private int firstAlertMonths;

    @Column(nullable = false)
    private int secondAlertMonths;

    public NotificationSettings(int firstAlertMonths, int secondAlertMonths) {
        this.id = SINGLETON_ID;
        update(firstAlertMonths, secondAlertMonths);
    }

    /** Substitui os prazos de uma vez (mesmo padrão de PUT do resto do projeto). */
    public void update(int firstAlertMonths, int secondAlertMonths) {
        if (secondAlertMonths <= 0 || firstAlertMonths <= secondAlertMonths) {
            throw new IllegalArgumentException(
                    "O primeiro prazo de alerta deve ser maior que o segundo, e o segundo maior que zero "
                            + "(recebido: " + firstAlertMonths + " e " + secondAlertMonths + ").");
        }
        this.firstAlertMonths = firstAlertMonths;
        this.secondAlertMonths = secondAlertMonths;
    }
}
