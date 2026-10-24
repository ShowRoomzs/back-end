package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.domain.member.brand.repository.BrandSanctionRepository;
import showroomz.domain.member.brand.service.BrandSanctionService;
import showroomz.domain.member.creator.repository.CreatorSanctionRepository;
import showroomz.domain.member.creator.service.CreatorSanctionService;
import showroomz.domain.member.user.repository.UserSanctionRepository;
import showroomz.domain.member.user.service.UserSanctionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 정지 기간 종료 자동 해제(1021 결정 노트 기-02 【잠정】 · 설계서 7-2) — 매일 00:05 KST + 기동 시 1회. 종료일이 지난 열린 정지를 시스템이
 * 해제한다(사유 「정지 기간 종료」 · 운영자 해제와 같은 훅 — 계약 보류 해제 · 알림 · 카드 · 지급 보류 해제 다음 영업일). 한 건 실패가 나머지를
 * 막지 않는다 — 다음 회차가 다시 시도한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "member.sanction", name = "auto-release-enabled", havingValue = "true",
        matchIfMissing = true)
public class SanctionAutoReleaseScheduler {

    private final BrandSanctionRepository brandSanctions;
    private final CreatorSanctionRepository creatorSanctions;
    private final UserSanctionRepository userSanctions;
    private final BrandSanctionService brandSanctionService;
    private final CreatorSanctionService creatorSanctionService;
    private final UserSanctionService userSanctionService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        tick();
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int released = run("브랜드", brandSanctions.findMemberIdsToAutoRelease(now.toLocalDate()),
                (id, at) -> brandSanctionService.releaseByTerm(id, at) != null, now)
                + run("인플루언서", creatorSanctions.findMemberIdsToAutoRelease(now.toLocalDate()),
                (id, at) -> creatorSanctionService.releaseByTerm(id, at) != null, now)
                + run("소비자", userSanctions.findMemberIdsToAutoRelease(now.toLocalDate()),
                (id, at) -> userSanctionService.releaseByTerm(id, at) != null, now);
        if (released > 0) {
            log.info("정지 기간 종료 자동 해제 - {}건", released);
        }
    }

    private int run(String kind, List<Long> memberIds, BiFunction<Long, LocalDateTime, Boolean> release,
                    LocalDateTime now) {
        int released = 0;
        for (Long memberId : memberIds) {
            try {
                if (Boolean.TRUE.equals(release.apply(memberId, now))) {
                    released++;
                }
            } catch (Exception e) {
                log.error("정지 기간 종료 자동 해제 실패 - {} id: {}", kind, memberId, e);
            }
        }
        return released;
    }
}
