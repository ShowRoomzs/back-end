package showroomz.domain.member.common.entity;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 본인확인 정보 열람(마스킹 해제) 세션 1개 = 열람 로그 1행 — 회원 유형별 로그 엔티티(인플루언서 · 소비자)가 구현한다(55 계획서 M3-3 ·
 * {@code AdminMemo}와 같은 방식). 세션은 <b>회원 1건 · 운영자 1명</b>에 묶이고, 무조작 10분은 {@code accessed_at + 10분} 고정 만료다
 * (슬라이딩이면 조회마다 쓰기가 든다 · 53 설계서 9-2 #7). [지금 잠금]은 {@code locked_at}.
 */
public interface PrivacyAccessSession {

    /** 고정 만료 — 시안 「무조작 10분 경과 시 자동 재마스킹」. */
    Duration TTL = Duration.ofMinutes(10);

    Long getId();

    Long getOperatorId();

    LocalDateTime getAccessedAt();

    LocalDateTime getExpiresAt();

    LocalDateTime getLockedAt();

    /** [지금 잠금] — 세션을 닫는다. */
    void lock(LocalDateTime now);

    /** 잠그지 않았고 만료 전이면 유효 — 유효한 세션이 있으면 다시 해제해도 새 행을 만들지 않는다. */
    default boolean isActive(LocalDateTime now) {
        return getLockedAt() == null && getExpiresAt().isAfter(now);
    }

    /** 만료까지 남은 초 — 「09:41 후 잠금」 카운트다운. 지났으면 0. */
    default long remainingSeconds(LocalDateTime now) {
        return Math.max(0, Duration.between(now, getExpiresAt()).getSeconds());
    }
}
