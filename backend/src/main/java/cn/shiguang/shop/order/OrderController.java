// AI-assisted implementation with Codex.
package cn.shiguang.shop.order;
import cn.shiguang.shop.auth.ShopPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders=orders; }
    public record Checkout(@NotBlank @Size(max=50) String recipientName, @NotBlank @Email @Size(max=254) String recipientEmail,
                           @NotBlank @Pattern(regexp="[0-9+() -]{6,30}") String recipientPhone,
                           @NotBlank @Size(min=5,max=300) String shippingAddress) {}
    @GetMapping public OrderService.OrderPage list(@AuthenticationPrincipal ShopPrincipal user,
            @RequestParam(defaultValue="") @Size(max=30) String status, @RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,
            @RequestParam(defaultValue="10") @Min(1) @Max(50) int size) { return orders.list(user.id(),false,status,page,size); }
    @PostMapping public OrderView checkout(@AuthenticationPrincipal ShopPrincipal user,
            @RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9-]{16,64}") String key, @Valid @RequestBody Checkout body) {
        return orders.checkout(user.id(),key,new OrderService.Address(body.recipientName().trim(),body.recipientEmail().trim(),body.recipientPhone().trim(),body.shippingAddress().trim()));
    }
    @GetMapping("/{id}") public OrderView detail(@AuthenticationPrincipal ShopPrincipal user,@PathVariable long id) { return orders.get(user.id(),id,false); }
    @PostMapping("/{id}/pay") public OrderView pay(@AuthenticationPrincipal ShopPrincipal user,@PathVariable long id) { return orders.pay(user.id(),id); }
    @PostMapping("/{id}/cancel") public OrderView cancel(@AuthenticationPrincipal ShopPrincipal user,@PathVariable long id) { return orders.cancel(user.id(),id); }
    @PostMapping("/{id}/complete") public OrderView complete(@AuthenticationPrincipal ShopPrincipal user,@PathVariable long id) { return orders.complete(user.id(),id); }
}
