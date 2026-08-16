package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.post.service.PostPurgeService;

import java.time.LocalDateTime;

/**
 * 파기 배치 (§24-6) — 비공개 보관 기간이 끝난 게시물을 물리 삭제한다.
 *
 * <p><b>기본값은 드라이런이다.</b> 되돌릴 수 없는 유일한 배치이고, {@code purge_at} 계산이 틀리면
 * 보관 의무가 있는 자료가 사라진다. 보관 기간(§24-8 ⓓ)이 확정되고 스테이징에서 대상 목록을
 * 눈으로 확인한 뒤에 {@code post.purge-enabled=true}로 켠다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostPurgeScheduler {

    private final PostPurgeService postPurgeService;

    @Scheduled(cron = "0 20 4 * * *", zone = "Asia/Seoul")
    public void purgeExpiredPosts() {
        try {
            postPurgeService.purgeExpired(LocalDateTime.now());
        } catch (Exception e) {
            log.error("게시물 파기 배치 실패", e);
        }
    }
}
