package showroomz.global.health;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 외부 연동 상태(1017 설계서 6-3) — 어드민 홈 「연동 상태」 배지와 배송 예외 판정 일시 중지가 읽는다.
 *
 * <p>배송 추적만 여기 기록한다 — 호출이 실패해도 결과가 「이벤트 없음」으로 보여, 상태를 따로 들고 있지 않으면 운영자가
 * 연동 장애와 정상 대기를 구별하지 못한다. 연속 실패 {@value #FAILURE_THRESHOLD}회 · 마지막 성공 뒤
 * {@value #STALE_MINUTES}분 넘게 실패만 이어짐 · 키 차단이면 {@code DEGRADED} 다. 다음 성공에서 풀린다.
 *
 * <p>인메모리 — 단일 인스턴스 전제다(다중 인스턴스면 Redis 로 옮긴다 · 설계서 12절).
 */
@Component
public class IntegrationHealthRegistry {

    static final int FAILURE_THRESHOLD = 3;
    static final long STALE_MINUTES = 30;

    private LocalDateTime trackingLastSuccessAt;
    private LocalDateTime trackingFailingSince;
    private int trackingConsecutiveFailures;
    private boolean trackingBlocked;
    private String trackingMessage;

    public synchronized void recordTrackingSuccess(LocalDateTime now) {
        trackingLastSuccessAt = now;
        trackingFailingSince = null;
        trackingConsecutiveFailures = 0;
        trackingBlocked = false;
        trackingMessage = null;
    }

    public synchronized void recordTrackingFailure(LocalDateTime now, String message) {
        if (trackingFailingSince == null) {
            trackingFailingSince = now;
        }
        trackingConsecutiveFailures++;
        trackingMessage = message;
    }

    /** 키 단위 차단(사용량 초과 · 키 만료) — 남은 호출이 전부 실패한다. 다음 성공까지 DEGRADED. */
    public synchronized void recordTrackingBlocked(LocalDateTime now, String message) {
        if (trackingFailingSince == null) {
            trackingFailingSince = now;
        }
        trackingBlocked = true;
        trackingMessage = message;
    }

    public synchronized boolean isTrackingDegraded(LocalDateTime now) {
        if (trackingFailingSince == null) {
            return false;
        }
        if (trackingBlocked || trackingConsecutiveFailures >= FAILURE_THRESHOLD) {
            return true;
        }
        LocalDateTime base = trackingLastSuccessAt != null ? trackingLastSuccessAt : trackingFailingSince;
        return Duration.between(base, now).toMinutes() > STALE_MINUTES;
    }

    public synchronized TrackingState trackingState(LocalDateTime now) {
        boolean degraded = isTrackingDegraded(now);
        return new TrackingState(degraded, degraded ? trackingFailingSince : null, degraded ? trackingMessage : null,
                trackingLastSuccessAt);
    }

    /**
     * @param since 장애가 시작된 시각(DEGRADED 일 때만)
     */
    public record TrackingState(boolean degraded, LocalDateTime since, String message, LocalDateTime lastSuccessAt) {
    }
}
