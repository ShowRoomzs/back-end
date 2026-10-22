package showroomz.global.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 택배 추적 연동 상태(1017 설계서 6-3) — 연속 실패 3회 · 마지막 성공 뒤 30분 넘게 실패만 · 키 차단이면 장애(DEGRADED).
 * 다음 성공에서 풀린다. 장애 시작 시각은 연속 실패의 첫 시각이다.
 */
@DisplayName("IntegrationHealthRegistry — 택배 추적 장애 판정")
class IntegrationHealthRegistryTest {

    private static final LocalDateTime T0 = LocalDateTime.parse("2026-10-22T10:00:00");

    @Test
    @DisplayName("기록이 없거나 성공만 있으면 정상")
    void healthyByDefault() {
        IntegrationHealthRegistry registry = new IntegrationHealthRegistry();
        assertThat(registry.isTrackingDegraded(T0)).isFalse();

        registry.recordTrackingSuccess(T0);
        assertThat(registry.trackingState(T0.plusHours(5)).degraded()).isFalse();
        assertThat(registry.trackingState(T0).lastSuccessAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("연속 실패 2회는 정상 · 3회째 장애 — 시작 시각은 첫 실패")
    void threeConsecutiveFailures() {
        IntegrationHealthRegistry registry = new IntegrationHealthRegistry();
        registry.recordTrackingSuccess(T0);
        registry.recordTrackingFailure(T0.plusMinutes(1), "조회 실패");
        registry.recordTrackingFailure(T0.plusMinutes(2), "조회 실패");
        assertThat(registry.isTrackingDegraded(T0.plusMinutes(3))).isFalse();

        registry.recordTrackingFailure(T0.plusMinutes(3), "빈 응답");
        IntegrationHealthRegistry.TrackingState state = registry.trackingState(T0.plusMinutes(3));
        assertThat(state.degraded()).isTrue();
        assertThat(state.since()).isEqualTo(T0.plusMinutes(1));
        assertThat(state.message()).isEqualTo("빈 응답");
    }

    @Test
    @DisplayName("성공 한 번이면 연속 실패가 끊긴다")
    void successResetsStreak() {
        IntegrationHealthRegistry registry = new IntegrationHealthRegistry();
        registry.recordTrackingFailure(T0, "조회 실패");
        registry.recordTrackingFailure(T0.plusMinutes(1), "조회 실패");
        registry.recordTrackingSuccess(T0.plusMinutes(2));
        registry.recordTrackingFailure(T0.plusMinutes(3), "조회 실패");
        registry.recordTrackingFailure(T0.plusMinutes(4), "조회 실패");

        assertThat(registry.isTrackingDegraded(T0.plusMinutes(5))).isFalse();
    }

    @Test
    @DisplayName("마지막 성공 뒤 30분 넘게 실패만 이어지면 장애 — 실패 횟수가 적어도")
    void staleSinceLastSuccess() {
        IntegrationHealthRegistry registry = new IntegrationHealthRegistry();
        registry.recordTrackingSuccess(T0);
        registry.recordTrackingFailure(T0.plusMinutes(10), "조회 실패");

        assertThat(registry.isTrackingDegraded(T0.plusMinutes(30))).isFalse();
        assertThat(registry.isTrackingDegraded(T0.plusMinutes(31))).isTrue();
    }

    @Test
    @DisplayName("키 차단(사용량 초과 · 키 만료)은 한 번에 장애 · 다음 성공에서 풀린다")
    void blockedUntilNextSuccess() {
        IntegrationHealthRegistry registry = new IntegrationHealthRegistry();
        registry.recordTrackingBlocked(T0, "조회 차단(103)");
        assertThat(registry.trackingState(T0).degraded()).isTrue();
        assertThat(registry.trackingState(T0).message()).isEqualTo("조회 차단(103)");

        registry.recordTrackingSuccess(T0.plusHours(1));
        IntegrationHealthRegistry.TrackingState recovered = registry.trackingState(T0.plusHours(1));
        assertThat(recovered.degraded()).isFalse();
        assertThat(recovered.since()).isNull();
        assertThat(recovered.message()).isNull();
    }
}
