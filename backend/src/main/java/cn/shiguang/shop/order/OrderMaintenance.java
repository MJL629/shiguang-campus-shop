// AI-assisted implementation with Codex. Reclaims stock reserved by unpaid orders after thirty
// minutes.
package cn.shiguang.shop.order;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderMaintenance {
  private final JdbcClient jdbc;
  private final OrderService orders;

  public OrderMaintenance(JdbcClient jdbc, OrderService orders) {
    this.jdbc = jdbc;
    this.orders = orders;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 60000)
  public void expire() {
    var ids =
        jdbc.sql(
                "SELECT id FROM shop_order WHERE status='PENDING_PAYMENT' AND created_at<:cutoff"
                    + " ORDER BY id LIMIT 100")
            .param("cutoff", java.time.LocalDateTime.now().minusMinutes(30))
            .query(Long.class)
            .list();
    for (long id : ids) orders.expire(id);
  }
}
