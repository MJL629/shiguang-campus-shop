// AI-assisted implementation with Codex.
package cn.shiguang.shop;

import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  private final JdbcClient jdbc;
  private volatile boolean ready;

  @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
  public void markReady() { ready = true; }

  public HealthController(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/api/health")
  public Map<String, String> health() {
    jdbc.sql("SELECT 1").query(Integer.class).single();
    return Map.of("status", ready ? "UP" : "STARTING", "database", "UP", "application", "shiguang-shop");
  }
}
