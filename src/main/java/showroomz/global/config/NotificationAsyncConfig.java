package showroomz.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 알림 발송 전용 스레드 풀.
 *
 * <p>스프링 기본 실행기({@code applicationTaskExecutor})를 쓰지 않는 이유 — 팔로워 수만 명의
 * 발송은 FCM 왕복 때문에 수십 초를 잡아먹는다. 공용 풀을 쓰면 그 시간 동안 다른 {@code @Async}
 * 작업(메일 발송 등)이 뒤에서 밀린다. 알림이 늦는 것은 참을 수 있지만 그것 때문에 다른 일이
 * 멈추면 안 된다.
 *
 * <p>큐가 차면 <b>호출 스레드에서 실행한다</b>({@code CallerRunsPolicy}). 버리는 선택지도 있지만
 * 그러면 이력에는 발송한 것처럼 남고 실제로는 아무 데도 가지 않는다. 밀리더라도 나가는 편이 낫다 —
 * 커밋 이후 스레드라 호출부(게시 API)는 이미 응답을 끝낸 상태다.
 */
@Slf4j
@Configuration
public class NotificationAsyncConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    @Bean(name = NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 배포로 종료될 때 보내던 묶음은 끝내고 내려간다 — 중간에 끊기면 절반만 받은 상태로 남는다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
