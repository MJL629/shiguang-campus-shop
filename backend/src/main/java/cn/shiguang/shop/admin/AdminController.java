// AI-assisted implementation with Codex. All routes require the ADMIN role in
// SecurityConfiguration.
package cn.shiguang.shop.admin;

import cn.shiguang.shop.order.OrderService;
import cn.shiguang.shop.order.OrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final JdbcClient jdbc;
  private final OrderService orders;

  public AdminController(JdbcClient jdbc, OrderService orders) {
    this.jdbc = jdbc;
    this.orders = orders;
  }

  public record ProductEdit(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 40) String category,
      @NotNull @DecimalMin("0.01") @DecimalMax("999999.99") BigDecimal price,
      @Min(0) @Max(1000000) int stock,
      @NotBlank @Size(max = 255) String imageUrl,
      @Size(max = 100) @NotNull String tagline,
      @Size(max = 2000) @NotNull String description,
      boolean active) {}

  public record Shipping(@NotBlank @Size(max = 100) String trackingNumber) {}

  @GetMapping("/products")
  public Map<String, Object> products(
      @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
    String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    var items =
        jdbc.sql(
                "SELECT"
                    + " id,name,category,price,stock,image_url,tagline,description,active,brand,source_type,source_url"
                    + " FROM product WHERE name LIKE :pattern ESCAPE '!' ORDER BY id DESC LIMIT"
                    + " :size OFFSET :offset")
            .param("pattern", pattern)
            .param("size", size)
            .param("offset", (long) (page - 1) * size)
            .query()
            .listOfRows();
    long total =
        jdbc.sql("SELECT COUNT(*) FROM product WHERE name LIKE :pattern ESCAPE '!'")
            .param("pattern", pattern)
            .query(Long.class)
            .single();
    return Map.of("items", items, "total", total, "page", page, "size", size);
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@Valid @RequestBody ProductEdit p) {
    checkImage(p.imageUrl());
    // The generated key avoids assuming any consecutive ID sequence.
    org.springframework.jdbc.support.GeneratedKeyHolder keys =
        new org.springframework.jdbc.support.GeneratedKeyHolder();
    jdbc.sql(
            "INSERT INTO product(name,category,price,stock,image_url,tagline,description,active)"
                + " VALUES (:name,:category,:price,:stock,:image,:tagline,:description,:active)")
        .param("name", p.name().trim())
        .param("category", p.category().trim())
        .param("price", p.price())
        .param("stock", p.stock())
        .param("image", p.imageUrl())
        .param("tagline", p.tagline())
        .param("description", p.description())
        .param("active", p.active())
        .update(keys);
    return Map.of("id", keys.getKey().longValue());
  }

  @PutMapping("/products/{id}")
  public Map<String, Boolean> update(@PathVariable long id, @Valid @RequestBody ProductEdit p) {
    checkImage(p.imageUrl());
    int changed =
        jdbc.sql(
                "UPDATE product SET"
                    + " name=:name,category=:category,price=:price,stock=:stock,image_url=:image,tagline=:tagline,description=:description,active=:active"
                    + " WHERE id=:id")
            .param("id", id)
            .param("name", p.name().trim())
            .param("category", p.category().trim())
            .param("price", p.price())
            .param("stock", p.stock())
            .param("image", p.imageUrl())
            .param("tagline", p.tagline())
            .param("description", p.description())
            .param("active", p.active())
            .update();
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
    return Map.of("saved", true);
  }

  @DeleteMapping("/products/{id}")
  public Map<String, Boolean> remove(@PathVariable long id) {
    int changed = jdbc.sql("UPDATE product SET active=FALSE WHERE id=:id").param("id", id).update();
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
    return Map.of("removed", true);
  }

  private void checkImage(String image) {
    if (!image.matches("/images/[A-Za-z0-9_./-]+")
        && !image.matches("https://covers\\.openlibrary\\.org/b/id/[0-9]+-[SML]\\.jpg"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请使用本站图片路径或 Open Library 封面地址");
    if (image.contains("..")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片路径无效");
  }

  @GetMapping("/orders")
  public OrderService.OrderPage orders(
      @RequestParam(defaultValue = "") @Size(max = 30) String status,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
    return orders.list(0, true, status, page, size);
  }

  @PostMapping("/orders/{id}/ship")
  public OrderView ship(@PathVariable long id, @Valid @RequestBody Shipping body) {
    return orders.ship(id, body.trackingNumber().trim());
  }

  @GetMapping("/stats")
  public Map<String, Object> stats() {
    var summary =
        jdbc.sql(
                "SELECT COUNT(*) AS totalOrders,COALESCE(SUM(CASE WHEN status IN"
                    + " ('PAID','SHIPPED','COMPLETED') THEN total_amount ELSE 0 END),0) AS"
                    + " revenue,COALESCE(SUM(CASE WHEN status='PENDING_PAYMENT' THEN 1 ELSE 0"
                    + " END),0) AS pendingPayment,COALESCE(SUM(CASE WHEN status='PAID' THEN 1 ELSE"
                    + " 0 END),0) AS pendingShipment FROM shop_order")
            .query()
            .singleRow();
    long products =
        jdbc.sql("SELECT COUNT(*) FROM product WHERE active=TRUE").query(Long.class).single();
    long customers =
        jdbc.sql("SELECT COUNT(*) FROM app_user WHERE role='CUSTOMER'").query(Long.class).single();
    var trend =
        jdbc.sql(
                "SELECT CAST(paid_at AS DATE) AS day,SUM(total_amount) AS revenue,COUNT(*) AS"
                    + " orders FROM shop_order WHERE status IN ('PAID','SHIPPED','COMPLETED') AND"
                    + " paid_at>=:start GROUP BY CAST(paid_at AS DATE) ORDER BY day")
            .param("start", LocalDate.now().minusDays(13).atStartOfDay())
            .query()
            .listOfRows();
    var top =
        jdbc.sql(
                "SELECT i.product_id,i.product_name,SUM(i.quantity) AS"
                    + " units,SUM(i.quantity*i.unit_price) AS revenue FROM order_item i JOIN"
                    + " shop_order o ON o.id=i.order_id WHERE o.status IN"
                    + " ('PAID','SHIPPED','COMPLETED') GROUP BY i.product_id,i.product_name ORDER"
                    + " BY revenue DESC LIMIT 6")
            .query()
            .listOfRows();
    var distribution =
        jdbc.sql("SELECT status,COUNT(*) AS count FROM shop_order GROUP BY status")
            .query()
            .listOfRows();
    return Map.of(
        "summary",
        summary,
        "activeProducts",
        products,
        "customers",
        customers,
        "trend",
        trend,
        "topProducts",
        top,
        "statusDistribution",
        distribution);
  }

  @GetMapping("/mail")
  public List<Map<String, Object>> mail() {
    return jdbc.sql(
            "SELECT"
                + " m.id,m.order_id,o.order_number,m.recipient,m.subject,m.body,m.status,m.attempts,m.created_at,m.sent_at,m.last_error"
                + " FROM mail_outbox m JOIN shop_order o ON o.id=m.order_id ORDER BY m.id DESC"
                + " LIMIT 100")
        .query()
        .listOfRows();
  }

  @PostMapping("/mail/{id}/retry")
  public Map<String, Boolean> retry(@PathVariable long id) {
    int changed =
        jdbc.sql(
                "UPDATE mail_outbox SET"
                    + " status='PENDING',attempts=0,last_error=NULL,next_attempt_at=CURRENT_TIMESTAMP"
                    + " WHERE id=:id AND status='FAILED'")
            .param("id", id)
            .update();
    if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "仅失败的邮件可以重试");
    return Map.of("queued", true);
  }
}
