// AI-assisted security boundary tests with Codex.
package cn.shiguang.shop;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:authboundary;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "MAIL_INITIAL_DELAY_MS=3600000"
    })
@AutoConfigureMockMvc
@Transactional
class AuthBoundaryTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void roleFieldInRegistrationCannotGrantAdminAccess() throws Exception {
    String username = "role_" + UUID.randomUUID().toString().substring(0, 8);
    var result =
        mvc.perform(
                post("/api/auth/register")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "username",
                                username,
                                "displayName",
                                "Role Test",
                                "email",
                                username + "@example.test",
                                "password",
                                "RoleTest-Password-123",
                                "role",
                                "ADMIN"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andReturn();
    MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
    mvc.perform(get("/api/admin/stats").session(session)).andExpect(status().isForbidden());
  }

  @Test
  void multibytePasswordBeyondBcryptLimitIsRejectedAsInput() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("username", "missing_user", "password", "密码".repeat(25)))))
        .andExpect(status().isBadRequest());
  }
}
