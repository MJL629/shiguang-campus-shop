// AI-assisted implementation with Codex. Demo passwords are provided by environment, never tracked
// in source.
package cn.shiguang.shop.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@org.springframework.core.annotation.Order(-20)
public class DemoAccounts implements ApplicationRunner {
  private final JdbcClient jdbc;
  private final PasswordEncoder passwords;
  private final String adminPassword;
  private final String demoPassword;

  public DemoAccounts(
      JdbcClient jdbc,
      PasswordEncoder passwords,
      @Value("${DEMO_ADMIN_PASSWORD:}") String adminPassword,
      @Value("${DEMO_CUSTOMER_PASSWORD:}") String demoPassword) {
    this.jdbc = jdbc;
    this.passwords = passwords;
    this.adminPassword = adminPassword;
    this.demoPassword = demoPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!adminPassword.isBlank())
      create("admin", "拾光店长", "admin@example.test", "ADMIN", adminPassword);
    if (!demoPassword.isBlank())
      create("demo", "体验顾客", "demo@example.test", "CUSTOMER", demoPassword);
  }

  private void create(String username, String name, String email, String role, String password) {
    if (password.length() < 12)
      throw new IllegalStateException("Demo passwords must contain at least 12 characters");
    if (jdbc.sql("SELECT COUNT(*) FROM app_user WHERE username=:user")
            .param("user", username)
            .query(Long.class)
            .single()
        > 0) return;
    jdbc.sql(
            "INSERT INTO app_user(username,display_name,email,password_hash,role) VALUES"
                + " (:user,:name,:email,:hash,:role)")
        .param("user", username)
        .param("name", name)
        .param("email", email)
        .param("hash", passwords.encode(password))
        .param("role", role)
        .update();
  }
}
