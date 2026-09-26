package showroomz.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 계약서 제출본 PDF 생성 전용 스레드 풀.
 *
 * <p>검토 요청 응답이 Chromium 렌더링·S3 업로드(10초 안팎)를 기다리지 않게 커밋 이후 여기서 만든다.
 * 렌더러가 {@code synchronized}라 한 번에 하나만 돌므로 스레드는 하나면 충분하다.
 *
 * <p>큐가 차면 <b>버린다</b>. 알림 풀처럼 호출 스레드에서 실행하면({@code CallerRunsPolicy}) 검토 요청 스레드가
 * 다시 렌더링을 떠안아 고치려던 지연이 돌아온다. 버려도 잃는 것은 없다 — 생성본은 어드민이 처음 내려받을 때
 * 만들어진다(기존 대체 경로).
 *
 * <p>{@code contract.pdf.async-enabled=false}면 호출 스레드에서 바로 실행한다 — 통합 테스트가 검토 요청 직후
 * 생성본을 확인할 수 있도록 순서를 고정하는 용도다.
 */
@Slf4j
@Configuration
public class ContractPdfAsyncConfig {

    public static final String CONTRACT_PDF_EXECUTOR = "contractPdfExecutor";

    @Bean(name = CONTRACT_PDF_EXECUTOR)
    public Executor contractPdfExecutor(@Value("${contract.pdf.async-enabled:true}") boolean asyncEnabled) {
        if (!asyncEnabled) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("contract-pdf-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("계약서 제출본 생성 대기열이 가득 차 건너뛴다 — 어드민 첫 다운로드가 만든다"));
        // 배포로 종료될 때 만들던 파일은 끝내고 내려간다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
