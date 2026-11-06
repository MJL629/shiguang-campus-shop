// AI-assisted implementation with Codex.
package cn.shiguang.shop;

import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final JdbcClient jdbc;
    public HealthController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        jdbc.sql("SELECT 1").query(Integer.class).single();
        return Map.of("status", "UP", "database", "UP");
    }
}
