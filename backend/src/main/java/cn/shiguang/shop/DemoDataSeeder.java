// AI-assisted showcase fixture loader with Codex. Explicit sample flag distinguishes fixtures from
// user-created orders.
package cn.shiguang.shop;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
public class DemoDataSeeder implements ApplicationRunner {
  private final JdbcClient jdbc;
  private final boolean enabled;

  record Item(long id, String name, String imageUrl, BigDecimal price, int stock) {}

  public DemoDataSeeder(JdbcClient jdbc, @Value("${DEMO_SEED_ORDERS:false}") boolean enabled) {
    this.jdbc = jdbc;
    this.enabled = enabled;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!enabled) return;
    var user =
        jdbc.sql("SELECT id FROM app_user WHERE username='demo'").query(Long.class).optional();
    if (user.isEmpty()) return;
    List<Item> products =
        jdbc.sql(
                "SELECT id,name,image_url,price,stock FROM product WHERE active=TRUE AND stock>=10"
                    + " ORDER BY id LIMIT 40")
            .query(Item.class)
            .list();
    if (products.size() < 5) return;
    String[] states = {"COMPLETED", "COMPLETED", "SHIPPED", "PAID", "CANCELLED"};
    for (int i = 0; i < 32; i++) {
      String key = "showcase-v1-" + i;
      if (jdbc.sql("SELECT COUNT(*) FROM shop_order WHERE user_id=:user AND request_key=:key")
              .param("user", user.get())
              .param("key", key)
              .query(Long.class)
              .single()
          > 0) continue;
      Item product = products.get((i * 7) % products.size());
      int quantity = 1 + i % 3;
      String state = states[i % states.length];
      LocalDateTime created =
          LocalDateTime.now()
              .minusDays(i % 14)
              .withHour(9 + (i % 9))
              .withMinute(12 + i % 30)
              .withSecond(0)
              .withNano(0);
      if (created.isAfter(LocalDateTime.now())) created = LocalDateTime.now().minusMinutes(20);
      boolean paid = !state.equals("CANCELLED");
      BigDecimal amount = product.price().multiply(BigDecimal.valueOf(quantity));
      if (paid
          && jdbc.sql("UPDATE product SET stock=stock-:quantity WHERE id=:id AND stock>=:quantity")
                  .param("quantity", quantity)
                  .param("id", product.id())
                  .update()
              != 1) continue;
      String number =
          "SAMPLE-"
              + created.toLocalDate().toString().replace("-", "")
              + "-"
              + String.format("%03d", i + 1);
      jdbc.sql(
              "INSERT INTO"
                  + " shop_order(order_number,user_id,request_key,status,total_amount,recipient_name,recipient_email,recipient_phone,shipping_address,tracking_number,created_at,paid_at,shipped_at,completed_at,sample)"
                  + " VALUES"
                  + " (:number,:user,:key,:status,:amount,'预置体验顾客','demo@example.test','13800000000','虚构校园展示地址',:tracking,:created,:paid,:shipped,:completed,TRUE)")
          .param("number", number)
          .param("user", user.get())
          .param("key", key)
          .param("status", state)
          .param("amount", amount)
          .param(
              "tracking",
              List.of("SHIPPED", "COMPLETED").contains(state) ? "SAMPLE-TRACK-" + i : null)
          .param("created", created)
          .param("paid", paid ? created.plusMinutes(2) : null)
          .param(
              "shipped",
              List.of("SHIPPED", "COMPLETED").contains(state) ? created.plusMinutes(5) : null)
          .param("completed", state.equals("COMPLETED") ? created.plusMinutes(8) : null)
          .update();
      long id =
          jdbc.sql("SELECT id FROM shop_order WHERE order_number=:number")
              .param("number", number)
              .query(Long.class)
              .single();
      jdbc.sql(
              "INSERT INTO"
                  + " order_item(order_id,product_id,product_name,image_url,unit_price,quantity)"
                  + " VALUES (:order,:product,:name,:image,:price,:quantity)")
          .param("order", id)
          .param("product", product.id())
          .param("name", product.name())
          .param("image", product.imageUrl())
          .param("price", product.price())
          .param("quantity", quantity)
          .update();
      if (paid)
        jdbc.sql(
                "INSERT INTO payment_record(order_id,reference_number,amount,method,created_at)"
                    + " VALUES (:order,:reference,:amount,'FIXTURE',:created)")
            .param("order", id)
            .param("reference", "FIXTURE-" + id)
            .param("amount", amount)
            .param("created", created.plusMinutes(2))
            .update();
    }
  }
}
