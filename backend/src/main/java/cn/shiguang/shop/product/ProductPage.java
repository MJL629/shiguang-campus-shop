// AI-assisted implementation with Codex.
package cn.shiguang.shop.product;

import java.util.List;

public record ProductPage(List<Product> items, long total, int page, int size) {}
