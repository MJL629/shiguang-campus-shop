// AI-assisted tests with Codex; verify business boundaries rather than implementation details.
package cn.shiguang.shop;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:catalog;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver"
    })
@AutoConfigureMockMvc
class ProductApiTest {
  @Autowired MockMvc mvc;
  @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;

  @Test
  void paginationKeepsTotalAndLimitsItems() throws Exception {
    long total =
        jdbc.sql("SELECT COUNT(*) FROM product WHERE active=TRUE").query(Long.class).single();
    mvc.perform(get("/api/products").param("size", "1").param("page", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(total))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(2));
  }

  @Test
  void combinedSearchAndCategoryReturnMatchingProduct() throws Exception {
    mvc.perform(get("/api/products").param("keyword", "笔记").param("category", "原创文创"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].price").value(28.00));
  }

  @Test
  void sqlAndWildcardCharactersAreLiteralSearchInput() throws Exception {
    for (String keyword : new String[] {"%", "_", "' OR 1=1 --"}) {
      mvc.perform(get("/api/products").param("keyword", keyword))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(0));
    }
  }

  @Test
  void invalidPagingIsRejected() throws Exception {
    mvc.perform(get("/api/products").param("page", "0")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/products").param("size", "51")).andExpect(status().isBadRequest());
  }

  @Test
  void missingProductReturns404() throws Exception {
    mvc.perform(get("/api/products/99999")).andExpect(status().isNotFound());
  }

  @Test
  void databaseHealthIsChecked() throws Exception {
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.database").value("UP"));
  }
}
