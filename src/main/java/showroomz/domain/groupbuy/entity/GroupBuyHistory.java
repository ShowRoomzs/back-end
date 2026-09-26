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

    /**
     * 이 이력을 만든 사실 행의 id — 요청({@code group_buy_change_request})·통지·확인. 읽는 서피스가 detail 원문 대신
     * 원천 행에서 문구를 다시 만들 때 쓴다(31 설계 6-2). 「사유 라벨만」 내려야 하는 이벤트를 detail 문자열 가공으로
     * 자르면 detail 문형이 바뀌는 날 메모가 조용히 샌다.
     */
    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
