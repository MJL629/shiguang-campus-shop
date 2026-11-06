// AI-assisted implementation with Codex. Outbox survives application restarts; SMTP failures are
// visible to admins.
package cn.shiguang.shop.mail;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class MailDispatcher {
  private final JdbcClient jdbc;
  private final JavaMailSender mail;
  private final String from;
  private final String mode;

  public record Job(long id, String recipient, String subject, String body, int attempts) {}

  public MailDispatcher(
      JdbcClient jdbc,
      JavaMailSender mail,
      @Value("${MAIL_FROM:noreply@shiguang.example}") String from,
      @Value("${MAIL_MODE:LOCAL_CAPTURE}") String mode) {
    this.jdbc = jdbc;
    this.mail = mail;
    this.from = from;
    this.mode = mode;
  }

  @Scheduled(fixedDelayString = "${MAIL_POLL_MS:5000}", initialDelayString = "${MAIL_INITIAL_DELAY_MS:5000}")
  public void dispatch() {
    jdbc.sql(
            "UPDATE mail_outbox SET"
                + " status='FAILED',locked_at=NULL,next_attempt_at=CURRENT_TIMESTAMP WHERE"
                + " status='SENDING' AND locked_at<:cutoff")
        .param("cutoff", java.time.LocalDateTime.now().minusMinutes(5))
        .update();
    List<Job> jobs =
        jdbc.sql(
                "SELECT id,recipient,subject,body,attempts FROM mail_outbox WHERE status IN"
                    + " ('PENDING','FAILED') AND attempts<3 AND next_attempt_at<=CURRENT_TIMESTAMP"
                    + " ORDER BY id LIMIT 10")
            .query(Job.class)
            .list();
    for (Job job : jobs) {
      int claimed =
          jdbc.sql(
                  "UPDATE mail_outbox SET"
                      + " status='SENDING',attempts=attempts+1,locked_at=CURRENT_TIMESTAMP WHERE"
                      + " id=:id AND status IN ('PENDING','FAILED') AND attempts<3")
              .param("id", job.id())
              .update();
      if (claimed != 1) continue;
      try {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(job.recipient());
        message.setSubject(job.subject());
        message.setText(job.body());
        mail.send(message);
        jdbc.sql(
                "UPDATE mail_outbox SET"
                    + " status=:status,sent_at=CURRENT_TIMESTAMP,last_error=NULL,locked_at=NULL"
                    + " WHERE id=:id")
            .param("status", mode.equals("LOCAL_CAPTURE") ? "CAPTURED" : "SENT")
            .param("id", job.id())
            .update();
      } catch (Exception error) {
        jdbc.sql(
                "UPDATE mail_outbox SET"
                    + " status='FAILED',last_error=:error,locked_at=NULL,next_attempt_at=:next"
                    + " WHERE id=:id")
            .param("error", "SMTP delivery failed (" + error.getClass().getSimpleName() + ")")
            .param(
                "next",
                java.time.LocalDateTime.now().plusSeconds(30L << Math.min(job.attempts(), 5)))
            .param("id", job.id())
            .update();
      }
    }
  }
}
