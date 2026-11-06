// AI-assisted integration tests with Codex. Tests database transactions and user ownership.
package cn.shiguang.shop;
import cn.shiguang.shop.auth.ShopPrincipal;
import cn.shiguang.shop.cart.CartService;
import cn.shiguang.shop.order.OrderService;
import cn.shiguang.shop.order.OrderView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:flow;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","MAIL_POLL_MS=3600000"})
@AutoConfigureMockMvc
@Transactional
class ShoppingFlowTest {
    @Autowired MockMvc mvc; @Autowired JdbcClient jdbc; @Autowired CartService cart; @Autowired OrderService orders; @Autowired ObjectMapper json;
    long user(String suffix) {
        String name="test_"+suffix+UUID.randomUUID().toString().substring(0,8);
        jdbc.sql("INSERT INTO app_user(username,display_name,email,password_hash) VALUES (:name,'Test',:email,'test-only')")
                .param("name",name).param("email",name+"@example.test").update();
        return jdbc.sql("SELECT id FROM app_user WHERE username=:name").param("name",name).query(Long.class).single();
    }
    OrderService.Address address() { return new OrderService.Address("测试顾客","test@example.test","13800000000","校园测试地址第一教学楼"); }
    @Test void checkoutIsIdempotentAndPaymentCannotBeDuplicated() {
        long user=user("a");int before=jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single();
        cart.add(user,1,2);String key=UUID.randomUUID().toString();
        var first=orders.checkout(user,key,address());var second=orders.checkout(user,key,address());
        assertThat(second.id()).isEqualTo(first.id());assertThat(first.totalAmount()).isEqualByComparingTo("56.00");
        assertThat(cart.get(user).items()).isEmpty();
        assertThat(jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single()).isEqualTo(before-2);
        orders.pay(user,first.id());orders.pay(user,first.id());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM payment_record WHERE order_id=:id").param("id",first.id()).query(Long.class).single()).isEqualTo(1);
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void failedCheckoutRollsBackPreviouslyReservedStock() {
        long user=user("b");int before=jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single();
        int secondBefore=jdbc.sql("SELECT stock FROM product WHERE id=2").query(Integer.class).single();
        try {
            cart.add(user,1,2);cart.add(user,2,2);jdbc.sql("UPDATE product SET stock=0 WHERE id=2").update();
            assertThatThrownBy(()->orders.checkout(user,UUID.randomUUID().toString(),address())).hasMessageContaining("409");
            assertThat(jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single()).isEqualTo(before);
            assertThat(cart.get(user).items()).hasSize(2);
        } finally { jdbc.sql("UPDATE product SET stock=:stock WHERE id=2").param("stock",secondBefore).update(); }
    }
    @Test void cancellationRestoresStockExactlyOnce() {
        long user=user("c");int before=jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single();
        cart.add(user,1,1);var order=orders.checkout(user,UUID.randomUUID().toString(),address());
        orders.cancel(user,order.id());orders.cancel(user,order.id());
        assertThat(jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single()).isEqualTo(before);
        assertThatThrownBy(()->orders.pay(user,order.id())).hasMessageContaining("409");
    }
    @Test void shippingRequiresPaymentAndQueuesMailAtomically() {
        long user=user("d");cart.add(user,1,1);var order=orders.checkout(user,UUID.randomUUID().toString(),address());
        assertThatThrownBy(()->orders.ship(order.id(),"DEMO-001")).hasMessageContaining("409");
        orders.pay(user,order.id());orders.ship(order.id(),"DEMO-001");
        assertThat(orders.get(user,order.id(),false).status()).isEqualTo("SHIPPED");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM mail_outbox WHERE order_id=:id").param("id",order.id()).query(Long.class).single()).isEqualTo(1);
        assertThatThrownBy(()->orders.ship(order.id(),"DEMO-002")).hasMessageContaining("409");
        assertThat(orders.complete(user,order.id()).status()).isEqualTo("COMPLETED");
    }
    @Test void customerCannotReadAnotherCustomersOrderOrAccessAdmin() throws Exception {
        long owner=user("e"),other=user("f");cart.add(owner,1,1);var order=orders.checkout(owner,UUID.randomUUID().toString(),address());
        var auth=UsernamePasswordAuthenticationToken.authenticated(new ShopPrincipal(other,"other","CUSTOMER"),null,List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        mvc.perform(get("/api/orders/"+order.id()).with(authentication(auth))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/stats").with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    }
    @Test void registrationLoginLogoutAndCsrfWork() throws Exception {
        String username="member_"+UUID.randomUUID().toString().substring(0,8);
        String body=json.writeValueAsString(java.util.Map.of("username",username,"displayName","测试用户","email",username+"@example.test","password","TestPassword-123"));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body)).andExpect(status().isForbidden());
        var result=mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn();
        MockHttpSession session=(MockHttpSession)result.getRequest().getSession(false);
        mvc.perform(get("/api/auth/session").session(session)).andExpect(jsonPath("$.user.username").value(username));
        String hash=jdbc.sql("SELECT password_hash FROM app_user WHERE username=:name").param("name",username).query(String.class).single();
        assertThat(hash).startsWith("$2").doesNotContain("TestPassword");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(java.util.Map.of("username",username,"password","wrong-password")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(java.util.Map.of("username",username,"password","TestPassword-123")))).andExpect(status().isOk());
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void onlyOneBuyerCanReserveTheLastItem() throws Exception {
        long first=user("race1"),second=user("race2");
        int before=jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single();
        jdbc.sql("UPDATE product SET stock=1 WHERE id=1").update();
        cart.add(first,1,1);cart.add(second,1,1);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> buyFirst=()->{ gate.await();try { orders.checkout(first,UUID.randomUUID().toString(),address());return true; } catch(org.springframework.web.server.ResponseStatusException error) { return false; } };
            java.util.concurrent.Callable<Boolean> buySecond=()->{ gate.await();try { orders.checkout(second,UUID.randomUUID().toString(),address());return true; } catch(org.springframework.web.server.ResponseStatusException error) { return false; } };
            var a=executor.submit(buyFirst);var b=executor.submit(buySecond);gate.countDown();
            int successes=(a.get(10,java.util.concurrent.TimeUnit.SECONDS)?1:0)+(b.get(10,java.util.concurrent.TimeUnit.SECONDS)?1:0);
            assertThat(successes).isEqualTo(1);
            assertThat(jdbc.sql("SELECT stock FROM product WHERE id=1").query(Integer.class).single()).isZero();
        } finally { executor.shutdownNow();jdbc.sql("UPDATE product SET stock=:stock WHERE id=1").param("stock",before).update(); }
    }
}
