// AI-assisted implementation with Codex. No password hash is returned to the browser.
package cn.shiguang.shop.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final JdbcClient jdbc;
  private final PasswordEncoder passwords;
  private final SecurityContextRepository contexts;

  public AuthController(
      JdbcClient jdbc, PasswordEncoder passwords, SecurityContextRepository contexts) {
    this.jdbc = jdbc;
    this.passwords = passwords;
    this.contexts = contexts;
  }

  public record SessionView(UserView user, String csrfToken) {}

  public record Register(
      @Pattern(regexp = "[A-Za-z0-9_]{3,32}") @NotBlank String username,
      @NotBlank @Size(max = 50) String displayName,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 8, max = 72) String password) {}

  public record Login(
      @NotBlank @Size(max = 32) String username, @NotBlank @Size(max = 72) String password) {}

  record Credential(long id, String username, String role, String passwordHash) {}

  @GetMapping("/session")
  public SessionView session(Authentication authentication, CsrfToken csrf) {
    return new SessionView(
        authentication == null ? null : find(((ShopPrincipal) authentication.getPrincipal()).id()),
        csrf.getToken());
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserView register(
      @Valid @RequestBody Register body, HttpServletRequest request, HttpServletResponse response) {
    if (body.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码最多为72个UTF-8字节");
    try {
      jdbc.sql(
              "INSERT INTO app_user (username, display_name, email, password_hash) VALUES"
                  + " (:username,:display,:email,:hash)")
          .param("username", body.username().trim().toLowerCase(java.util.Locale.ROOT))
          .param("display", body.displayName().trim())
          .param("email", body.email().trim().toLowerCase(java.util.Locale.ROOT))
          .param("hash", passwords.encode(body.password()))
          .update();
    } catch (DuplicateKeyException error) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名或邮箱已被使用");
    }
    Credential user = credential(body.username());
    signIn(user, request, response);
    return find(user.id());
  }

  @PostMapping("/login")
  public UserView login(
      @Valid @RequestBody Login body, HttpServletRequest request, HttpServletResponse response) {
    if (body.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码过长，请减少汉字或特殊字符数量");
    Credential user = credential(body.username());
    // Use a valid dummy hash to reduce account enumeration via response time.
    String hash =
        user == null
            ? "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW"
            : user.passwordHash();
    boolean valid = passwords.matches(body.password(), hash);
    if (user == null || !valid)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    signIn(user, request, response);
    return find(user.id());
  }

  private Credential credential(String username) {
    return jdbc.sql("SELECT id,username,role,password_hash FROM app_user WHERE username=:username")
        .param("username", username.trim().toLowerCase(java.util.Locale.ROOT))
        .query(Credential.class)
        .optional()
        .orElse(null);
  }

  private void signIn(Credential user, HttpServletRequest request, HttpServletResponse response) {
    request.getSession(true);
    request.changeSessionId();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            new ShopPrincipal(user.id(), user.username(), user.role()),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
  }

  public UserView find(long id) {
    return jdbc.sql("SELECT id,username,display_name,email,role FROM app_user WHERE id=:id")
        .param("id", id)
        .query(UserView.class)
        .single();
  }
}
