// AI-assisted tests with Codex. Only unpaid expired reservations may release stock.
package cn.shiguang.shop;

import static org.assertj.core.api.Assertions.*;

import cn.shiguang.shop.cart.CartService;
import cn.shiguang.shop.order.OrderService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:expiration;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "MAIL_INITIAL_DELAY_MS=3600000"
    })
@Transactional
class OrderExpirationTest {
  @Autowired JdbcClient jdbc;
  @Autowired CartService cart;
  @Autowired OrderService orders;

  long user() {
    String name = "expiry_" + UUID.randomUUID().toString().substring(0, 8);
    jdbc.sql(
            "INSERT INTO app_user(username,display_name,email,password_hash) VALUES (:name,'Expiry"
                + " Test',:email,'test-only')")
        .param("name", name)
        .param("email", name + "@example.test")
        .update();
    return jdbc.sql("SELECT id FROM app_user WHERE username=:name")
        .param("name", name)
        .query(Long.class)
        .single();
  }

  @Test
  void expiredUnpaidOrderReleasesItsReservationExactlyOnce() {
    int before = jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single();
    long user = user();
    cart.add(user, 1, 2);
    var order =
        orders.checkout(
            user,
            UUID.randomUUID().toString(),
            new OrderService.Address(
                "Expiry Test", "expiry@example.test", "13800000000", "Fictional campus address"));
    jdbc.sql("UPDATE shop_order SET created_at=:created WHERE id=:id")
        .param("created", LocalDateTime.now().minusMinutes(31))
        .param("id", order.id())
        .update();
    orders.expire(order.id());
    orders.expire(order.id());
    assertThat(orders.get(user, order.id(), false).status()).isEqualTo("CANCELLED");
    assertThat(jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single())
        .isEqualTo(before);
  }

  @Test
  void paidOrderKeepsItsStockReservationAfterTheTimeout() {
    int before = jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single();
    long user = user();
    cart.add(user, 1, 1);
    var order =
        orders.checkout(
            user,
            UUID.randomUUID().toString(),
            new OrderService.Address(
                "Expiry Test", "expiry@example.test", "13800000000", "Fictional campus address"));
    orders.pay(user, order.id());
    jdbc.sql("UPDATE shop_order SET created_at=:created WHERE id=:id")
        .param("created", LocalDateTime.now().minusMinutes(31))
        .param("id", order.id())
        .update();
    orders.expire(order.id());
    assertThat(orders.get(user, order.id(), false).status()).isEqualTo("PAID");
    assertThat(jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single())
        .isEqualTo(before - 1);
  }
}
