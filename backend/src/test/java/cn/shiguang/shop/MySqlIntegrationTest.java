// AI-assisted optional integration test with Codex. CI runs this against a genuine MySQL service.
package cn.shiguang.shop;
import cn.shiguang.shop.cart.CartService;
import cn.shiguang.shop.order.OrderService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches=".+")
@SpringBootTest(properties={"spring.datasource.url=${MYSQL_TEST_URL}","spring.datasource.username=${MYSQL_TEST_USER}","spring.datasource.password=${MYSQL_TEST_PASSWORD}","spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver","MAIL_INITIAL_DELAY_MS=3600000"})
class MySqlIntegrationTest {
    @Autowired JdbcClient jdbc; @Autowired CartService cart; @Autowired OrderService orders; @Autowired PlatformTransactionManager transactions;
    @Test void mysqlTransactionRollsBackOrderPaymentAndStock() {
        assertThat(jdbc.sql("SELECT VERSION()").query(String.class).single()).startsWith("8.");
        int before=jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single();
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            String name="mysql_"+UUID.randomUUID().toString().substring(0,8);
            jdbc.sql("INSERT INTO app_user(username,display_name,email,password_hash) VALUES (:name,'MySQL Test',:email,'test-only')").param("name",name).param("email",name+"@example.test").update();
            long user=jdbc.sql("SELECT id FROM app_user WHERE username=:name").param("name",name).query(Long.class).single();
            cart.add(user,1,1);var order=orders.checkout(user,UUID.randomUUID().toString(),new OrderService.Address("MySQL Test","mysql@example.test","13800000000","Fictional MySQL test address"));
            assertThat(order.totalAmount()).isEqualByComparingTo("28.00");
            orders.pay(user,order.id());assertThatThrownBy(()->orders.cancel(user,order.id())).hasMessageContaining("409");
            status.setRollbackOnly();
        });
        assertThat(jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single()).isEqualTo(before);
    }
}
