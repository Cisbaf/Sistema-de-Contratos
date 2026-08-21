package contratos.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "users")
public class AppUser implements UserDetails {
    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String username;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
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

    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private boolean admin;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    @Column(length = 30)
    private PerfilUsuario perfil;

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

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
    public void rename(String name) {
        this.name = name;
    }

    public boolean isAdmin() {
        return getPerfil() == PerfilUsuario.ADMIN;
    }

    public PerfilUsuario getPerfil() {
        return perfil != null
                ? perfil
                : (admin ? PerfilUsuario.ADMIN : PerfilUsuario.FISCAL);
    }

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


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + getPerfil().name()));
    }
}
