// AI-assisted implementation with Codex.
package cn.shiguang.shop.product;

import java.math.BigDecimal;

public record Product(
    long id,
    String name,
    String category,
    BigDecimal price,
    int stock,
    String imageUrl,
    String tagline,
    String description,
    String brand,
    String sourceType,
    String sourceUrl,
    String originalName) {}
