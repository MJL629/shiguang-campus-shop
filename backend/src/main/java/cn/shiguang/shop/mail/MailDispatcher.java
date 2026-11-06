// AI-assisted implementation with Codex. Outbox survives application restarts; SMTP failures are visible to admins.
package cn.shiguang.shop.mail;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class MailDispatcher {
    private final JdbcClient jdbc;private final JavaMailSender mail;private final String from;private final String mode;
    public record Job(long id,String recipient,String subject,String body) {}
    public MailDispatcher(JdbcClient jdbc,JavaMailSender mail,@Value("${MAIL_FROM:noreply@shiguang.example}") String from,@Value("${MAIL_MODE:LOCAL_CAPTURE}") String mode) {
        this.jdbc=jdbc;this.mail=mail;this.from=from;this.mode=mode;
    }
    @Scheduled(fixedDelayString="${MAIL_POLL_MS:5000}",initialDelay=5000)
    public void dispatch() {
        List<Job> jobs=jdbc.sql("SELECT id,recipient,subject,body FROM mail_outbox WHERE status IN ('PENDING','FAILED') AND attempts<3 ORDER BY id LIMIT 10").query(Job.class).list();
        for(Job job:jobs) {
            int claimed=jdbc.sql("UPDATE mail_outbox SET status='SENDING',attempts=attempts+1 WHERE id=:id AND status IN ('PENDING','FAILED') AND attempts<3").param("id",job.id()).update();
            if(claimed!=1)continue;
            try {
                SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(job.recipient());message.setSubject(job.subject());message.setText(job.body());mail.send(message);
                jdbc.sql("UPDATE mail_outbox SET status=:status,sent_at=CURRENT_TIMESTAMP,last_error=NULL WHERE id=:id")
                        .param("status",mode.equals("LOCAL_CAPTURE")?"CAPTURED":"SENT").param("id",job.id()).update();
            } catch(Exception error) {
                jdbc.sql("UPDATE mail_outbox SET status='FAILED',last_error=:error WHERE id=:id")
                        .param("error","SMTP delivery failed ("+error.getClass().getSimpleName()+")").param("id",job.id()).update();
            }
        }
    }
}
