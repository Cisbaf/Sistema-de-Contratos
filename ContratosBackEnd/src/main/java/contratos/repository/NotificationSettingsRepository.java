package contratos.repository;

import contratos.domain.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {
}
