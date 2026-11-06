// AI-assisted implementation with Codex.
package cn.shiguang.shop.order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public record OrderView(long id, String orderNumber, String status, BigDecimal totalAmount,
                        String recipientName, String recipientEmail, String recipientPhone, String shippingAddress,
                        String trackingNumber, LocalDateTime createdAt, LocalDateTime paidAt, LocalDateTime shippedAt,
                        List<Item> items) {
    public record Item(long productId, String productName, String imageUrl, BigDecimal unitPrice, int quantity) {}
}
