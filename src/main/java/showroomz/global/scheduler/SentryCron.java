package showroomz.global.scheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SentryCron {

  @Scheduled(fixedRate = 60 * 1000L)
  @io.sentry.spring.checkin.SentryCheckIn("328698e0a0cd")
  void execute() throws InterruptedException {
    // your task code
  }
}