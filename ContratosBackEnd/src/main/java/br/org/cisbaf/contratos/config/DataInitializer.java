package br.org.cisbaf.contratos.config;

import br.org.cisbaf.contratos.domain.AppUser;
import br.org.cisbaf.contratos.domain.Sector;
import br.org.cisbaf.contratos.repository.SectorRepository;
import br.org.cisbaf.contratos.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class DataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository users;
    private final SectorRepository sectors;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;
    private final String adminSector;

    public DataInitializer(UserRepository users, SectorRepository sectors, PasswordEncoder passwordEncoder,
                           @Value("${app.bootstrap-admin.email}") String adminEmail,
                           @Value("${app.bootstrap-admin.password}") String adminPassword,
                           @Value("${app.bootstrap-admin.name:Administrador}") String adminName,
                           @Value("${app.bootstrap-admin.sector:Administração}") String adminSector) {
        this.users = users;
        this.sectors = sectors;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminName = adminName;
        this.adminSector = adminSector;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = adminEmail.trim().toLowerCase();
        if (email.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException("ADMIN_EMAIL e ADMIN_PASSWORD são obrigatórios quando ADMIN_BOOTSTRAP_ENABLED=true");
        }
        if (adminPassword.length() < 6) {
            throw new IllegalStateException("ADMIN_PASSWORD deve ter no mínimo 6 caracteres");
        }
        if (users.existsByUsername(email)) {
            log.info("Bootstrap administrativo ignorado: usuário {} já existe", email);
            return;
        }
        String sectorName = adminSector.trim().isBlank() ? "Administração" : adminSector.trim();
        Sector sector = sectors.findByNameIgnoreCase(sectorName)
                .orElseGet(() -> sectors.save(new Sector(sectorName)));
        users.save(new AppUser(email, passwordEncoder.encode(adminPassword), adminName.trim(), email, null, sector, true));
        log.info("Administrador inicial {} criado no setor {}", email, sectorName);
    }
}
