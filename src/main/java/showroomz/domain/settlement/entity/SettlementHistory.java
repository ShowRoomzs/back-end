package showroomz.domain.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.settlement.type.SettlementActorType;
import showroomz.domain.settlement.type.SettlementEventType;

import java.time.LocalDateTime;

/**
 * 정산 처리 이력 — append-only(44 어드민 설계서 1-8). 07b 우측 「처리 이력 · 최신순」이 그대로다. 수정 메서드가 없다.
 * 파트너 · 스튜디오 응답에는 싣지 않는다 — 확정 근거 문장(「자동 확정 10.06 + 3영업일 · 10.09 한글날 제외」)만 detail 에서 읽는다.
 */
@Entity
@Table(name = "settlement_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementHistory {

    private static final int DETAIL_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private SettlementEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 10)
    private SettlementActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "detail", length = DETAIL_MAX)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public static SettlementHistory of(Long settlementId, SettlementEventType eventType, SettlementActorType actorType,
                                       Long actorId, String detail, LocalDateTime occurredAt) {
        SettlementHistory history = new SettlementHistory();
        history.settlementId = settlementId;
        history.eventType = eventType;
        history.actorType = actorType;
        history.actorId = actorId;
        history.detail = detail != null && detail.length() > DETAIL_MAX ? detail.substring(0, DETAIL_MAX) : detail;
        history.occurredAt = occurredAt;
        return history;
    }
}
