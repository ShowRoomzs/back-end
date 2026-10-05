package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.DeliveryCarrier;

import java.time.LocalDateTime;

/**
 * 택배 스캔 이력 — append-only(앱 클레임 설계서 1-6). <b>송장이 키다</b> — 주문 송장 · 회수 송장 · 재발송 송장이
 * 한 테이블을 쓰고 소유자 FK 를 두지 않는다. 위치·문구는 연동 업체 원문 그대로다(앱이 번역하면 오역이 곧 문의가 된다).
 */
@Entity
@Table(name = "delivery_tracking_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_delivery_tracking_event",
                columnNames = {"carrier", "tracking_number", "seq"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DeliveryTrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier", nullable = false, length = 30)
    private DeliveryCarrier carrier;

    @Column(name = "tracking_number", nullable = false, length = 50)
    private String trackingNumber;

    /** 그 송장 이력의 시간순 번호(0부터). */
    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "description", length = 100)
    private String description;

    /** 진행 단계 0~6. */
    @Column(name = "level", columnDefinition = "TINYINT")
    private Integer level;
}
