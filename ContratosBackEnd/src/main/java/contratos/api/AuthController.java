package contratos.api;

import contratos.api.dto.LoginRequest;
import contratos.api.dto.RegisterRequest;
import contratos.domain.AppUser;
import contratos.domain.Sector;
import contratos.repository.SectorRepository;
import contratos.repository.UserRepository;
import contratos.security.JwtAuthenticationFilter;
import contratos.security.JwtService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository users;
    private final SectorRepository sectors;
    private final PasswordEncoder passwordEncoder;
    private final boolean secureCookie;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService, UserRepository users,
                          SectorRepository sectors, PasswordEncoder passwordEncoder,
                          @Value("${jwt.cookie-secure:false}") boolean secureCookie) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.users = users;
        this.sectors = sectors;
        this.passwordEncoder = passwordEncoder;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody @Valid LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim().toLowerCase(), request.password()));
        AppUser user = (AppUser) authentication.getPrincipal();
        String token = jwtService.generate(user.getUsername(), user.getName());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(token, jwtService.expirationSeconds()).toString())
                .body(Map.of("message", "Login successful", "username", user.getUsername(), "name", user.getName()));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody @Valid RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByUsername(email)) throw new DataIntegrityViolationException("Usuário já cadastrado");
        Sector sector = sectors.findAll().stream().findFirst().orElseGet(() -> sectors.save(new Sector("Geral")));
        users.save(new AppUser(email, passwordEncoder.encode(request.password()), request.name().trim(), email, null, sector, false));
        return ResponseEntity.ok(Map.of("message", "User registered successfully", "username", email));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString())
                .body(Map.of("message", "Logout realizado com sucesso"));
    }

    @GetMapping("/validate")
    public Map<String, Object> validate(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUser user)) {
            return Map.of("valid", false);
        }
        return Map.of("valid", true, "username", user.getUsername(), "name", user.getName(),
                "admin", user.isAdmin(), "perfil", user.getPerfil().name());
    }

    private ResponseCookie cookie(String value, long maxAge) {
        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, value).httpOnly(true).secure(secureCookie)
                .sameSite("Strict").path("/").maxAge(Duration.ofSeconds(maxAge)).build();
    }
}
