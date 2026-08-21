package contratos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "users")
public class AppUser implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(length = 100)
    private String cellPhone;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sector_id")
    private Sector sector;

    @Column(nullable = false)
    private boolean admin;

    /**
     * Campo novo e inicialmente opcional para permitir a evolução do banco
     * existente sem quebrar usuários antigos que ainda usam apenas admin.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PerfilUsuario perfil;

    protected AppUser() {}

    public AppUser(String username, String password, String name, String email, String cellPhone, Sector sector, boolean admin) {
        this(username, password, name, email, cellPhone, sector, admin ? PerfilUsuario.ADMIN : PerfilUsuario.FISCAL);
    }

    public AppUser(String username, String password, String name, String email, String cellPhone, Sector sector,
                   PerfilUsuario perfil) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.email = email;
        this.cellPhone = cellPhone;
        this.sector = sector;
        this.perfil = perfil;
        this.admin = perfil == PerfilUsuario.ADMIN;
    }

    public Long getId() { return id; }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getCellPhone() { return cellPhone; }
    public Sector getSector() { return sector; }
    public boolean isAdmin() { return getPerfil() == PerfilUsuario.ADMIN; }
    public PerfilUsuario getPerfil() { return perfil != null ? perfil : (admin ? PerfilUsuario.ADMIN : PerfilUsuario.FISCAL); }

    public void update(String username, String name, String email, String cellPhone, Sector sector, boolean admin) {
        update(username, name, email, cellPhone, sector, admin ? PerfilUsuario.ADMIN : PerfilUsuario.FISCAL);
    }

    public void update(String username, String name, String email, String cellPhone, Sector sector,
                       PerfilUsuario perfil) {
        this.username = username;
        this.name = name;
        this.email = email;
        this.cellPhone = cellPhone;
        this.sector = sector;
        this.perfil = perfil;
        this.admin = perfil == PerfilUsuario.ADMIN;
    }

    public void setPassword(String password) { this.password = password; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + getPerfil().name()));
    }
}
