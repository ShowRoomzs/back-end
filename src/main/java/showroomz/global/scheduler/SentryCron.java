package showroomz.global.scheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.sentry.spring.jakarta.checkin.SentryCheckIn;


@Component
public class SentryCron {

  @Scheduled(fixedRate = 60 * 1000L)
  @SentryCheckIn("328698e0a0cd")
  public void execute() throws InterruptedException {
    // your task code
  }
}