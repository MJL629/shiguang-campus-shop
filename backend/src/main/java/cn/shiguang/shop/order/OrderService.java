// AI-assisted implementation with Codex. Review transaction boundaries, stock reservation and state
// transitions.
package cn.shiguang.shop.order;

import cn.shiguang.shop.cart.CartService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
  private final JdbcClient jdbc;
  private final CartService cart;

  public OrderService(JdbcClient jdbc, CartService cart) {
    this.jdbc = jdbc;
    this.cart = cart;
  }

  public record OrderRow(
      long id,
      String orderNumber,
      long userId,
      String status,
      BigDecimal totalAmount,
      String recipientName,
      String recipientEmail,
      String recipientPhone,
      String shippingAddress,
      String trackingNumber,
      LocalDateTime createdAt,
      LocalDateTime paidAt,
      LocalDateTime shippedAt,
      boolean sample) {}

  public record Address(
      String recipientName, String recipientEmail, String recipientPhone, String shippingAddress) {}

  public record OrderPage(List<OrderView> items, long total, int page, int size) {}

  @Transactional
  public OrderView checkout(long userId, String requestKey, Address address) {
    cart.lockUser(userId);
    var previous =
        jdbc.sql("SELECT id FROM shop_order WHERE user_id=:user AND request_key=:key")
            .param("user", userId)
            .param("key", requestKey)
            .query(Long.class)
            .optional();
    if (previous.isPresent()) return get(userId, previous.get(), false);
    var lines = cart.get(userId).items();
    if (lines.isEmpty()) throw fail(HttpStatus.CONFLICT, "购物车为空，请先挑选商品");
    BigDecimal total = BigDecimal.ZERO;
    for (var line : lines) {
      // Atomic conditional decrement prevents overselling. Any failure rolls back every line.
      int changed =
          jdbc.sql(
                  "UPDATE product SET stock=stock-:quantity WHERE id=:id AND active=TRUE AND"
                      + " stock>=:quantity")
              .param("quantity", line.quantity())
              .param("id", line.productId())
              .update();
      if (changed != 1) throw fail(HttpStatus.CONFLICT, "商品库存发生变化，请返回购物车调整：" + line.name());
      total = total.add(line.price().multiply(BigDecimal.valueOf(line.quantity())));
    }
    String number =
        "SG"
            + System.currentTimeMillis()
            + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    jdbc.sql(
            "INSERT INTO"
                + " shop_order(order_number,user_id,request_key,total_amount,recipient_name,recipient_email,recipient_phone,shipping_address)"
                + " VALUES (:number,:user,:key,:total,:name,:email,:phone,:address)")
        .param("number", number)
        .param("user", userId)
        .param("key", requestKey)
        .param("total", total)
        .param("name", address.recipientName())
        .param("email", address.recipientEmail())
        .param("phone", address.recipientPhone())
        .param("address", address.shippingAddress())
        .update();
    long id =
        jdbc.sql("SELECT id FROM shop_order WHERE order_number=:number")
            .param("number", number)
            .query(Long.class)
            .single();
    for (var line : lines)
      jdbc.sql(
              "INSERT INTO"
                  + " order_item(order_id,product_id,product_name,image_url,unit_price,quantity)"
                  + " VALUES (:order,:product,:name,:image,:price,:quantity)")
          .param("order", id)
          .param("product", line.productId())
          .param("name", line.name())
          .param("image", line.imageUrl())
          .param("price", line.price())
          .param("quantity", line.quantity())
          .update();
    jdbc.sql("DELETE FROM cart_item WHERE user_id=:user").param("user", userId).update();
    return get(userId, id, false);
  }

  public OrderPage list(long userId, boolean admin, String status, int page, int size) {
    String filter =
        " FROM shop_order WHERE (:admin=TRUE OR user_id=:user) AND (:status='' OR status=:status)";
    long total =
        jdbc.sql("SELECT COUNT(*)" + filter)
            .param("admin", admin)
            .param("user", userId)
            .param("status", status)
            .query(Long.class)
            .single();
    List<OrderView> items =
        jdbc
            .sql(
                "SELECT *"
                    + filter
                    + " ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset")
            .param("admin", admin)
            .param("user", userId)
            .param("status", status)
            .param("limit", size)
            .param("offset", (long) (page - 1) * size)
            .query(OrderRow.class)
            .list()
            .stream()
            .map(this::view)
            .toList();
    return new OrderPage(items, total, page, size);
  }

  public OrderView get(long userId, long id, boolean admin) {
    return view(row(userId, id, admin, false));
  }

  private OrderRow row(long userId, long id, boolean admin, boolean lock) {
    return jdbc.sql(
            "SELECT * FROM shop_order WHERE id=:id AND (:admin=TRUE OR user_id=:user)"
                + (lock ? " FOR UPDATE" : ""))
        .param("id", id)
        .param("admin", admin)
        .param("user", userId)
        .query(OrderRow.class)
        .optional()
        .orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "订单不存在"));
  }

  private OrderView view(OrderRow row) {
    List<OrderView.Item> items =
        jdbc.sql(
                "SELECT product_id,product_name,image_url,unit_price,quantity FROM order_item WHERE"
                    + " order_id=:order ORDER BY id")
            .param("order", row.id())
            .query(OrderView.Item.class)
            .list();
    return new OrderView(
        row.id(),
        row.orderNumber(),
        row.status(),
        row.totalAmount(),
        row.recipientName(),
        row.recipientEmail(),
        row.recipientPhone(),
        row.shippingAddress(),
        row.trackingNumber(),
        row.createdAt(),
        row.paidAt(),
        row.shippedAt(),
        row.sample(),
        items);
  }

  @Transactional
  public OrderView pay(long userId, long id) {
    OrderRow order = row(userId, id, false, true);
    if (List.of("PAID", "SHIPPED", "COMPLETED").contains(order.status())) return view(order);
    require(order, "PENDING_PAYMENT");
    jdbc.sql(
            "INSERT INTO payment_record(order_id,reference_number,amount,method) VALUES"
                + " (:id,:reference,:amount,'DEMO')")
        .param("id", id)
        .param("reference", "DEMO-" + UUID.randomUUID())
        .param("amount", order.totalAmount())
        .update();
    jdbc.sql("UPDATE shop_order SET status='PAID',paid_at=CURRENT_TIMESTAMP WHERE id=:id")
        .param("id", id)
        .update();
    return get(userId, id, false);
  }

  @Transactional
  public OrderView cancel(long userId, long id) {
    OrderRow order = row(userId, id, false, true);
    if (order.status().equals("CANCELLED")) return view(order);
    require(order, "PENDING_PAYMENT");
    for (var item : view(order).items())
      jdbc.sql("UPDATE product SET stock=stock+:quantity WHERE id=:id")
          .param("quantity", item.quantity())
          .param("id", item.productId())
          .update();
    jdbc.sql("UPDATE shop_order SET status='CANCELLED' WHERE id=:id").param("id", id).update();
    return get(userId, id, false);
  }

  @Transactional
  public OrderView complete(long userId, long id) {
    OrderRow order = row(userId, id, false, true);
    if (order.status().equals("COMPLETED")) return view(order);
    require(order, "SHIPPED");
    jdbc.sql("UPDATE shop_order SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE id=:id")
        .param("id", id)
        .update();
    return get(userId, id, false);
  }

  @Transactional
  public OrderView ship(long id, String tracking) {
    OrderRow order = row(0, id, true, true);
    require(order, "PAID");
    jdbc.sql(
            "UPDATE shop_order SET"
                + " status='SHIPPED',shipped_at=CURRENT_TIMESTAMP,tracking_number=:tracking WHERE"
                + " id=:id")
        .param("id", id)
        .param("tracking", tracking)
        .update();
    String body =
        "你好，"
            + order.recipientName()
            + "：\n\n你的拾光小铺订单 "
            + order.orderNumber()
            + " 已发货。\n物流单号："
            + tracking
            + "\n订单金额：¥"
            + order.totalAmount()
            + "\n\n这是课程演示订单，不产生实际物流或扣款。\n感谢你的体验。";
    // Transactional outbox: shipping and the notification job commit together.
    jdbc.sql(
            "INSERT INTO mail_outbox(order_id,recipient,subject,body) VALUES"
                + " (:order,:recipient,:subject,:body)")
        .param("order", id)
        .param("recipient", order.recipientEmail())
        .param("subject", "拾光小铺发货确认 · " + order.orderNumber())
        .param("body", body)
        .update();
    return get(0, id, true);
  }

  @Transactional
  public void expire(long id) {
    OrderRow order = row(0, id, true, true);
    if (order.status().equals("PENDING_PAYMENT")
        && order.createdAt().isBefore(LocalDateTime.now().minusMinutes(30)))
      cancel(order.userId(), id);
  }

  private void require(OrderRow order, String expected) {
    if (!order.status().equals(expected)) throw fail(HttpStatus.CONFLICT, "订单状态已变化，请刷新后重试");
  }

  private ResponseStatusException fail(HttpStatus status, String message) {
    return new ResponseStatusException(status, message);
  }
}
