// AI-assisted API tests with Codex. Sorting is global database ordering, not a client-side page
// reorder.
package cn.shiguang.shop;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:sort;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "MAIL_INITIAL_DELAY_MS=3600000"
    })
@AutoConfigureMockMvc
class ProductSortTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void pricesAreSortedAcrossPageBoundaries() throws Exception {
    var first =
        json.readTree(
            mvc.perform(get("/api/products?sort=price_asc&size=3&page=1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    var second =
        json.readTree(
            mvc.perform(get("/api/products?sort=price_asc&size=3&page=2"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    BigDecimal previous = BigDecimal.ZERO;
    for (var item : first.get("items")) {
      assertThat(item.get("price").decimalValue()).isGreaterThanOrEqualTo(previous);
      previous = item.get("price").decimalValue();
    }
    for (var item : second.get("items")) {
      assertThat(item.get("price").decimalValue()).isGreaterThanOrEqualTo(previous);
      previous = item.get("price").decimalValue();
    }
  }

  @Test
  void arbitrarySqlCannotBeUsedAsASortExpression() throws Exception {
    mvc.perform(get("/api/products").param("sort", "price DESC; DROP TABLE product"))
        .andExpect(status().isBadRequest());
  }
}
