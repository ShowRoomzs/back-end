package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.post.service.PostLifecycleService;

import java.time.LocalDateTime;

/**
 * 이의 신청 기한 만료 배치 (§24-5) — 기한 내 미신청 게시물을 영구 삭제로 넘긴다.
 *
 * <p>1시간 주기인 이유 — 기한이 일 단위(시안 7일)라 분 단위 정밀도가 필요 없고, 반대로 하루에 한 번이면
 * 기한이 지난 게시물이 최대 하루 더 떠 있게 된다. 노출 중지는 "기한 내 대응하지 않으면 영구 삭제로
 * 끝나는 상태"라 기한과 결과 사이가 벌어지면 상태 표시가 사실과 어긋난다.
 *
 * <p>배포가 단일 인스턴스라 분산 락은 두지 않는다({@link MessageAttachmentCleanupScheduler}와 같은 전제).
 * 다중 인스턴스로 가면 이 전제가 깨진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostAppealDeadlineScheduler {

    /** 1회 실행 상한 — 한 트랜잭션이 통째로 길어지지 않게 한다. 남은 건 다음 회차가 처리한다 */
    private static final int MAX_PER_RUN = 200;

    private final PostLifecycleService postLifecycleService;

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Seoul")
    public void expireOverdueAppeals() {
        try {
            int expired = postLifecycleService.expireOverdueSuspensions(LocalDateTime.now(), MAX_PER_RUN);
            if (expired > 0) {
                log.info("이의 신청 기한 만료 처리 완료 - {}건", expired);
            }
        } catch (Exception e) {
            // 다음 회차가 다시 시도한다 — 한 번 실패로 이후 스케줄이 막히지 않게 삼킨다
            log.error("이의 신청 기한 만료 처리 실패", e);
        }
    }
}
