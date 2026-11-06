// AI-assisted implementation with Codex.
package cn.shiguang.shop.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ProductController {
  private final ProductRepository products;

  public ProductController(ProductRepository products) {
    this.products = products;
  }

  @GetMapping("/products")
  public ProductPage list(
      @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
      @RequestParam(defaultValue = "") @Size(max = 40) String category,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "9") @Min(1) @Max(50) int size) {
    return products.search(keyword.trim(), category.trim(), page, size);
  }

  @GetMapping("/products/{id}")
  public Product detail(@PathVariable @Min(1) long id) {
    return products
        .find(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在或已下架"));
  }

  @GetMapping("/categories")
  public List<String> categories() {
    return products.categories();
  }
}
