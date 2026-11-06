// AI-assisted tests with Codex. Verify persistent outbox transitions and retry delays.
package cn.shiguang.shop;

import cn.shiguang.shop.mail.MailDispatcher;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:mail;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","MAIL_INITIAL_DELAY_MS=3600000"})
@Transactional
class MailReliabilityTest {
    @Autowired JdbcClient jdbc; @Autowired MailDispatcher dispatcher;
    @MockitoBean JavaMailSender sender;
    long enqueue() {
        String key=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO app_user(username,display_name,email,password_hash) VALUES (:name,'Mail Test',:email,'test-only')").param("name","mail_"+key.substring(0,8)).param("email",key+"@example.test").update();
        long user=jdbc.sql("SELECT id FROM app_user WHERE email=:email").param("email",key+"@example.test").query(Long.class).single();
        jdbc.sql("INSERT INTO shop_order(order_number,user_id,request_key,total_amount,recipient_name,recipient_email,recipient_phone,shipping_address,status) VALUES (:key,:user,:key,28,'Mail Test','mail@example.test','13800000000','Fictional test address','SHIPPED')").param("key",key).param("user",user).update();
        long order=jdbc.sql("SELECT id FROM shop_order WHERE order_number=:key").param("key",key).query(Long.class).single();
        jdbc.sql("INSERT INTO mail_outbox(order_id,recipient,subject,body) VALUES (:order,'mail@example.test','Test shipment','Test message')").param("order",order).update();
        return jdbc.sql("SELECT id FROM mail_outbox WHERE order_id=:order").param("order",order).query(Long.class).single();
    }
    @Test void successfulLocalSmtpIsMarkedCaptured() {
        long id=enqueue();dispatcher.dispatch();
        assertThat(jdbc.sql("SELECT status FROM mail_outbox WHERE id=:id").param("id",id).query(String.class).single()).isEqualTo("CAPTURED");
        verify(sender,times(1)).send(any(SimpleMailMessage.class));
    }
    @Test void failuresAreRetriedWithDelayRatherThanImmediately() {
        long id=enqueue();doThrow(new MailSendException("Test failure")).when(sender).send(any(SimpleMailMessage.class));
        dispatcher.dispatch();dispatcher.dispatch();
        assertThat(jdbc.sql("SELECT attempts FROM mail_outbox WHERE id=:id").param("id",id).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM mail_outbox WHERE id=:id").param("id",id).query(String.class).single()).isEqualTo("FAILED");
        verify(sender,times(1)).send(any(SimpleMailMessage.class));
    }
    @Test void staleClaimCanBeRecoveredAfterAWorkerCrash() {
        long id=enqueue();jdbc.sql("UPDATE mail_outbox SET status='SENDING',locked_at=:time WHERE id=:id").param("time",java.time.LocalDateTime.now().minusMinutes(6)).param("id",id).update();
        dispatcher.dispatch();
        assertThat(jdbc.sql("SELECT status FROM mail_outbox WHERE id=:id").param("id",id).query(String.class).single()).isEqualTo("CAPTURED");
    }
}
