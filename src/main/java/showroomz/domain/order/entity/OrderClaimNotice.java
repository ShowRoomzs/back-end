package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.FulfillmentActorType;

import java.time.LocalDateTime;

/**
 * 거절 보류 미결제 고지의 회차별 기록(35 설계서 1-9). {@code order_claim.notice_count · last_notice_at}은 이 테이블의
 * 집계 캐시다. {@code UNIQUE (claim_id, seq)} — 발송 재시도가 같은 회차를 두 번 세지 않는다.
 */
@Entity
@Table(name = "order_claim_notice",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_claim_notice_seq", columnNames = {"claim_id", "seq"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderClaimNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private OrderClaim claim;

    /** 회차 — 1부터. */
    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt;

    @Column(name = "channel", length = 20)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private FulfillmentActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;
}
