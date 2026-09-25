package showroomz.domain.groupbuy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;

import java.time.LocalDateTime;

/**
 * 공구 이력 — append-only. contract_history와 같은 모양이다(설계서 1-4).
 *
 * <p>수정 메서드를 두지 않는다. 전이와 같은 트랜잭션에서 append하고 이벤트 리스너로 분리하지 않는다(설계서 3-5).
 */
@Entity
@Table(name = "group_buy_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GroupBuyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_buy_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_buy_id", nullable = false)
    private GroupBuy groupBuy;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private GroupBuyEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private GroupBuyActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    /** 스냅샷 — 브랜드명·쇼룸명. 운영자 호칭은 읽는 서피스가 고른다. */
    @Column(name = "actor_display_name", length = 100)
    private String actorDisplayName;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
