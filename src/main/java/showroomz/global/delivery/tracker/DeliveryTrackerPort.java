package showroomz.global.delivery.tracker;

import showroomz.domain.order.type.DeliveryCarrier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 택배 연동 포트(34 설계서 0-4) — 형식 검증 · 추적 조회 두 책임만 건다. 연동이 꺼져 있으면
 * {@link NoopDeliveryTracker}가 「판정 불가」를 돌려주고, 켜면 스마트택배 어댑터가 붙는다(택배 추적 설계서).
 *
 * <p>택배 연동 = 배송 추적만이다(§34-0) — 송장 자동 발번 없음 · 반품 회수 접수 없음.
 * 「송장은 사람이 넣고, 그다음부터는 시스템이 따라간다.」
 */
public interface DeliveryTrackerPort {

    enum ValidationResult {
        VALID,
        /** 자릿수·체크디지트 불합격 — 하드 차단(§34-5 rev.4). 오타를 통과시킬 이유가 없다. */
        INVALID,
        /** 연동 전·장애 — 통과로 간주하지 않고 「검증 생략」으로 기록한다. */
        UNAVAILABLE
    }

    /**
     * 스캔 이력 한 줄 — 위치·문구는 연동 업체 원문 그대로다(앱 클레임 설계서 1-6).
     *
     * @param level 진행 단계 0~6 — 업체가 주지 않으면 null
     */
    record TrackEvent(LocalDateTime occurredAt, String location, String description, Integer level) {
    }

    /**
     * @param lastEventAt     마지막 추적 이벤트 시각 — 이벤트가 아직 없으면 null(집화 전은 데이터가 없는 게 정상)
     * @param deliveredAt     배송완료 시각 — 완료 전이면 null
     * @param returnDetected  반송 코드 감지 — 사유는 받지 않는다(API 가 코드·시각만 준다 · §34-6)
     * @param returnCompleted 반송 완료(입고) 감지
     * @param events          스캔 이력 전체 — 시간순(오래된 것부터). 이력을 주지 않는 구현은 빈 목록
     * @param level           현재 진행 단계 0~6 — 모르면 null
     */
    record TrackSnapshot(LocalDateTime lastEventAt, LocalDateTime deliveredAt, boolean returnDetected,
                         boolean returnCompleted, List<TrackEvent> events, Integer level) {

        public TrackSnapshot {
            events = events == null ? List.of() : List.copyOf(events);
        }

        public TrackSnapshot(LocalDateTime lastEventAt, LocalDateTime deliveredAt, boolean returnDetected,
                             boolean returnCompleted) {
            this(lastEventAt, deliveredAt, returnDetected, returnCompleted, List.of(), null);
        }
    }

    ValidationResult validateInvoice(DeliveryCarrier carrier, String trackingNumber);

    /**
     * 조회 실패(통신 오류 등)는 empty — 판정하지 않고 다음 회차에 맡긴다.
     *
     * @throws DeliveryTrackerBlockedException 키 사용량 초과·키 무효 — 이어서 불러도 전부 실패하므로 회차를 멈춘다
     */
    Optional<TrackSnapshot> track(DeliveryCarrier carrier, String trackingNumber);
}
