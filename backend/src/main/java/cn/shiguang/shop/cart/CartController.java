// AI-assisted implementation with Codex.
package cn.shiguang.shop.cart;

import cn.shiguang.shop.auth.ShopPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {
  private final CartService cart;

  public CartController(CartService cart) {
    this.cart = cart;
  }

  public record Add(@Min(1) long productId, @Min(1) @Max(99) int quantity) {}

  public record Quantity(@Min(1) @Max(99) int quantity) {}

  @GetMapping
  public CartService.CartView get(@AuthenticationPrincipal ShopPrincipal user) {
    return cart.get(user.id());
  }

  @PostMapping
  public CartService.CartView add(
      @AuthenticationPrincipal ShopPrincipal user, @Valid @RequestBody Add body) {
    return cart.add(user.id(), body.productId(), body.quantity());
  }

  @PutMapping("/{id}")
  public CartService.CartView set(
      @AuthenticationPrincipal ShopPrincipal user,
      @PathVariable long id,
      @Valid @RequestBody Quantity body) {
    return cart.set(user.id(), id, body.quantity());
  }

  @DeleteMapping("/{id}")
  public CartService.CartView remove(
      @AuthenticationPrincipal ShopPrincipal user, @PathVariable long id) {
    return cart.remove(user.id(), id);
  }
}
