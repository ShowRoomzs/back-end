package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.ClaimEventType;
import showroomz.domain.order.type.FulfillmentActorType;

import java.time.LocalDateTime;

/**
 * 클레임 처리 이력 — append-only(35 설계서 1-9). {@code order_fulfillment_history}와 같은 모양이다.
 * 전이와 같은 트랜잭션에서 append 한다 — 이력이 빠진 전이는 분쟁에서 전이가 없던 일이 된다.
 */
@Entity
@Table(name = "order_claim_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderClaimHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "claim_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private OrderClaim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private ClaimEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private FulfillmentActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
