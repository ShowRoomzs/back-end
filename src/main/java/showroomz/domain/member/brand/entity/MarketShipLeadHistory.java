package showroomz.domain.member.brand.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발송 기한 N 변경 이력(52 설계서 0-14) — 발송 기한은 주문 생성 때 하위주문에 스냅샷되므로 「왜 이 주문은 2영업일인가」를 이 행이 설명한다.
 * 값이 바뀔 때만 쓴다. 배송비 · 반품비는 이력을 두지 않는다.
 */
@Entity
@Table(name = "market_ship_lead_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketShipLeadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    /** 최초 설정이면 null. */
    @Column(name = "previous_days")
    private Integer previousDays;

    @Column(name = "new_days", nullable = false)
    private Integer newDays;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public static MarketShipLeadHistory of(Long marketId, Integer previousDays, Integer newDays, LocalDateTime now) {
        MarketShipLeadHistory history = new MarketShipLeadHistory();
        history.marketId = marketId;
        history.previousDays = previousDays;
        history.newDays = newDays;
        history.changedAt = now;
        return history;
    }
}
