package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.order.type.StoragePhase;
import showroomz.global.config.properties.OrderProperties;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 거절 보류 상품의 보관 기한(35 설계서 1-9) — <b>저장하지 않는 계산값</b>이다. 고지가 N회(2) 이상 쌓였을 때
 * 최종 고지일 + M개월(3)의 끝. 고지가 더해지면 기한이 밀린다(「최종 고지일 = 마지막 고지」 잠정 · §35-9 B-7 ③).
 * 목록 · 상세 · 폐기 가드 · 앱 · 어드민이 같은 값을 읽도록 여기 한 곳에 모은다.
 */
@Component
@RequiredArgsConstructor
public class ClaimStoragePolicy {

    private final OrderProperties orderProperties;

    /** 고지가 모자라면 null — 기한 미정. */
    public LocalDateTime storageDueAt(int noticeCount, LocalDateTime lastNoticeAt) {
        OrderProperties.Claim claim = orderProperties.getClaim();
        if (lastNoticeAt == null || noticeCount < claim.getStorageRequiredNotices()) {
            return null;
        }
        return lastNoticeAt.toLocalDate().plusMonths(claim.getStorageMonths()).atTime(LocalTime.of(23, 59, 59));
    }

    public StoragePhase phase(int noticeCount, LocalDateTime lastNoticeAt, LocalDateTime now) {
        LocalDateTime dueAt = storageDueAt(noticeCount, lastNoticeAt);
        if (dueAt == null) {
            return StoragePhase.NOTICE_PENDING;
        }
        return now.isAfter(dueAt) ? StoragePhase.EXPIRED : StoragePhase.STORING;
    }
}
