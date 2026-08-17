package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.terms.service.TermsEffectuationService;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 약관 시행 전환 배치 (기획 §21-6) — 시행일 00:00에 새 버전이 시행중이 되고 기존 버전은 과거 버전이 된다.
 *
 * <p>배포가 단일 인스턴스라 분산 락은 두지 않는다({@link MessageAttachmentCleanupScheduler}와 같은 전제).
 * 전환 자체가 멱등이라 중복 실행되어도 결과는 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TermsEffectuationScheduler {

    /** 시행일 판단 기준 시간대 — 시행일 00:00은 한국 시간이다 */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TermsEffectuationService termsEffectuationService;

    /**
     * `zone`을 명시하는 이유 — application.yml의 `time-zone: UTC`는 Jackson 직렬화 설정이라
     * 스케줄러 표현식에는 적용되지 않는다. 명시하지 않으면 JVM 기본 시간대에 좌우된다.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void effectuateScheduledVersions() {
        run("정기 실행");
    }

    /**
     * 자정에 서버가 내려가 있었다면 그 날의 전환이 통째로 빠진다 — 시행일이 지난 약관이 소비자 화면에
     * 계속 구버전으로 보이게 되므로, 기동 직후에도 한 번 맞춰 둔다. 멱등이라 중복 전환은 일어나지 않는다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void effectuateOnStartup() {
        run("기동 보정");
    }

    private void run(String trigger) {
        try {
            int effectuated = termsEffectuationService.effectuateDueVersions(LocalDate.now(KST));
            if (effectuated > 0) {
                log.info("약관 시행 전환 완료({}) - {}건", trigger, effectuated);
            }
        } catch (Exception e) {
            // 전환 실패로 애플리케이션 기동이나 다음 스케줄이 막히지 않도록 삼킨다 — 다음 회차가 다시 시도한다
            log.error("약관 시행 전환 실패({})", trigger, e);
        }
    }
}
