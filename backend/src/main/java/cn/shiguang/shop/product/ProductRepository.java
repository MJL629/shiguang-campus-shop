// AI-assisted implementation with Codex.
package cn.shiguang.shop.product;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {
    private static final String COLUMNS = "id, name, category, price, stock, image_url, tagline, description, brand, source_type, source_url";
    // Parameter binding prevents SQL injection. ESCAPE makes %, _ and ! literal search characters.
    private static final String FILTER = " FROM product WHERE active = TRUE AND (:category = '' OR category = :category)"
        + " AND (:keyword = '' OR name LIKE :pattern ESCAPE '!' OR description LIKE :pattern ESCAPE '!')";
    private final JdbcClient jdbc;
    public ProductRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public ProductPage search(String keyword, String category, int page, int size) {
        String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total = bind(jdbc.sql("SELECT COUNT(*)" + FILTER), keyword, category, pattern).query(Long.class).single();
        List<Product> items = bind(jdbc.sql("SELECT " + COLUMNS + FILTER + " ORDER BY id LIMIT :limit OFFSET :offset"),
                keyword, category, pattern).param("limit", size).param("offset", (long) (page - 1) * size)
                .query(Product.class).list();
        return new ProductPage(items, total, page, size);
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec query, String keyword, String category, String pattern) {
        return query.param("keyword", keyword).param("category", category).param("pattern", pattern);
    }

    public Optional<Product> find(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM product WHERE id = :id AND active = TRUE")
                .param("id", id).query(Product.class).optional();
    }

    public List<String> categories() {
        return jdbc.sql("SELECT DISTINCT category FROM product WHERE active = TRUE ORDER BY category")
                .query(String.class).list();
    }
}
