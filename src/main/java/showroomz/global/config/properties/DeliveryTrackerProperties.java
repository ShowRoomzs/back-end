package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 배송 추적 운영값(34 설계서 3-3). 24시간·7일은 §34-13 #17(김화창 확인 대기)이라 코드 상수로 박지 않는다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "delivery.tracker")
public class DeliveryTrackerProperties {

    /** false 면 Noop 스텁 — 송장 형식 검증 생략 · 감시 배치 미기동. 연동 업체 스펙 확정 후 켠다. */
    private boolean enabled = false;

    /** 폴링 주기(ms) — 잠정 30분. 연동 업체 과금·쿼터 구조 확정과 함께 조정한다(설계서 7절 N1). */
    private long pollIntervalMs = 1_800_000L;

    /** 1회 폴링 대상 상한 — 남은 건 다음 회차가 처리한다. */
    private int batchSize = 300;

    /** 집화 확인 필요 — 송장 등록 후 N시간 추적 미조회. */
    private int pickupAlertHours = 24;

    /** 추적 정지 — 마지막 이벤트 후 N일 갱신 없음. */
    private int stallAlertDays = 7;
}
