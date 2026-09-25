package showroomz.domain.groupbuy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.groupbuy.type.FulfillmentResult;
import showroomz.domain.groupbuy.type.FulfillmentSide;

import java.time.LocalDateTime;

/**
 * 계약 이행 확인 — 측별 1회 · 2지 · 불가역(§29-10 · 설계서 1-9). 수정 메서드를 두지 않는다.
 *
 * <p>행이 없음 = 확인 전이다. 「체크 안 함」과 「미이행」은 뜻이 다르다.
 */
@Entity
@Table(name = "group_buy_fulfillment_check",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_buy_fulfillment_side",
                columnNames = {"group_buy_id", "checker_side"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GroupBuyFulfillmentCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fulfillment_check_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_buy_id", nullable = false)
    private GroupBuy groupBuy;

    @Enumerated(EnumType.STRING)
    @Column(name = "checker_side", nullable = false, length = 16)
    private FulfillmentSide checkerSide;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private FulfillmentResult result;

    @Column(name = "reason", length = 2000)
    private String reason;

    /** 실제로 확인한 것과 기한이 지나 자동 처리된 것은 뜻이 다르다(어드민 B5b). */
    @Column(name = "auto_confirmed", nullable = false)
    private boolean autoConfirmed;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Column(name = "checked_by")
    private Long checkedBy;

    @Column(name = "thread_id")
    private Long threadId;

    public static GroupBuyFulfillmentCheck manual(GroupBuy groupBuy, FulfillmentSide side, FulfillmentResult result,
                                                  String reason, Long checkedBy, Long threadId, LocalDateTime now) {
        return GroupBuyFulfillmentCheck.builder()
                .groupBuy(groupBuy)
                .checkerSide(side)
                .result(result)
                .reason(reason)
                .autoConfirmed(false)
                .checkedAt(now)
                .checkedBy(checkedBy)
                .threadId(threadId)
                .build();
    }

    /** 무응답 자동 이행 — 스위치가 켜졌을 때만 스케줄러가 만든다(설계서 1-9 · 기본 꺼짐). */
    public static GroupBuyFulfillmentCheck autoConfirmed(GroupBuy groupBuy, FulfillmentSide side, LocalDateTime now) {
        return GroupBuyFulfillmentCheck.builder()
                .groupBuy(groupBuy)
                .checkerSide(side)
                .result(FulfillmentResult.FULFILLED)
                .autoConfirmed(true)
                .checkedAt(now)
                .build();
    }

    public boolean isUnfulfilled() {
        return result == FulfillmentResult.UNFULFILLED;
    }
}
