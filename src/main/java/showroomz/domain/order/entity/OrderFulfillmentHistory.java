package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;

import java.time.LocalDateTime;

/**
 * 하위주문 처리 이력 — append-only(34 설계서 1-4). 상세 모달 「처리 이력」과 송장 수정 이력의 원본이다.
 *
 * <p>수정 메서드를 두지 않는다. 전이와 같은 트랜잭션에서 append 하고 이벤트 리스너로 분리하지 않는다 —
 * 이력이 빠진 전이는 분쟁에서 전이가 없던 일이 된다.
 */
@Entity
@Table(name = "order_fulfillment_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderFulfillmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fulfillment_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_group_id", nullable = false)
    private OrderDeliveryGroup deliveryGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private FulfillmentEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private FulfillmentActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    /** 송장 수정 「{구} → {신}」 · 직권 취소 사유 등. */
    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
