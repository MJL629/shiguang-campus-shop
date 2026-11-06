// AI-assisted implementation with Codex.
package cn.shiguang.shop.cart;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CartService {
  private final JdbcClient jdbc;

  public CartService(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public record CartLine(
      long productId,
      String name,
      String imageUrl,
      BigDecimal price,
      int stock,
      int quantity,
      boolean active) {}

  public record CartView(List<CartLine> items, BigDecimal totalAmount, int quantity) {}

  record ProductState(int stock, boolean active) {}

  public CartView get(long userId) {
    List<CartLine> items =
        jdbc.sql(
                "SELECT p.id AS product_id,p.name,p.image_url,p.price,p.stock,c.quantity,p.active"
                    + " FROM cart_item c JOIN product p ON p.id=c.product_id WHERE c.user_id=:user"
                    + " ORDER BY p.id")
            .param("user", userId)
            .query(CartLine.class)
            .list();
    BigDecimal total =
        items.stream()
            .map(p -> p.price().multiply(BigDecimal.valueOf(p.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CartView(items, total, items.stream().mapToInt(CartLine::quantity).sum());
  }

  public void lockUser(long userId) {
    jdbc.sql("SELECT id FROM app_user WHERE id=:id FOR UPDATE")
        .param("id", userId)
        .query(Long.class)
        .single();
  }

  @Transactional
  public CartView add(long userId, long productId, int quantity) {
    lockUser(userId);
    int previous =
        jdbc.sql("SELECT quantity FROM cart_item WHERE user_id=:user AND product_id=:product")
            .param("user", userId)
            .param("product", productId)
            .query(Integer.class)
            .optional()
            .orElse(0);
    return setLocked(userId, productId, previous + quantity);
  }

  @Transactional
  public CartView set(long userId, long productId, int quantity) {
    lockUser(userId);
    return setLocked(userId, productId, quantity);
  }

  private CartView setLocked(long userId, long productId, int quantity) {
    if (quantity < 1 || quantity > 99)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每种商品限购1至99件");
    ProductState product =
        jdbc.sql("SELECT stock,active FROM product WHERE id=:id")
            .param("id", productId)
            .query(ProductState.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
    if (!product.active() || product.stock() < quantity)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "商品已下架或库存不足，请调整数量");
    int updated =
        jdbc.sql(
                "UPDATE cart_item SET quantity=:quantity WHERE user_id=:user AND"
                    + " product_id=:product")
            .param("quantity", quantity)
            .param("user", userId)
            .param("product", productId)
            .update();
    if (updated == 0)
      jdbc.sql(
              "INSERT INTO cart_item(user_id,product_id,quantity) VALUES"
                  + " (:user,:product,:quantity)")
          .param("user", userId)
          .param("product", productId)
          .param("quantity", quantity)
          .update();
    return get(userId);
  }

  @Transactional
  public CartView remove(long userId, long productId) {
    lockUser(userId);
    jdbc.sql("DELETE FROM cart_item WHERE user_id=:user AND product_id=:product")
        .param("user", userId)
        .param("product", productId)
        .update();
    return get(userId);
  }
}
