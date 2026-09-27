package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.market.entity.Market;

/**
 * 주문의 배송 그룹 — 키는 <b>공구</b>다(결제 계획서 3-1). 배송비·마감일·발송 시점이 공구마다 다르고, 정산·판매 관리가
 * 「이 공구 배송비가 얼마였나」를 나중에 복원해야 한다.
 *
 * <p>{@code group_buy_id}는 기존 리뷰용 시드 행 백필(마켓별 한 그룹)에서만 NULL이고 신규 주문은 항상 채운다.
 * NULL은 UK 중복을 허용하므로 백필 행에는 {@code (order_id, group_buy_id)} UK가 의미 없다.
 * 쇼룸명·공구번호는 스냅샷이다 — 쇼룸명 변경·마켓 비활성 뒤에도 주문 상세가 그대로다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "order_delivery_group",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_delivery_group_order_group_buy",
                columnNames = {"order_id", "group_buy_id"})
)
public class OrderDeliveryGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_id")
    private GroupBuy groupBuy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    /** 이 그룹의 판매가 합. */
    @Column(name = "product_total", nullable = false)
    private Integer productTotal;

    /** 실제 부과 배송비 — 무료배송이면 0. */
    @Column(name = "delivery_fee", nullable = false)
    private Integer deliveryFee;

    @Column(name = "free_shipping_applied", nullable = false)
    private boolean freeShippingApplied;

    @Column(name = "market_name", length = 100)
    private String marketName;

    @Column(name = "group_buy_number", length = 30)
    private String groupBuyNumber;

    @Builder
    public OrderDeliveryGroup(Order order, GroupBuy groupBuy, Market market, Integer productTotal, Integer deliveryFee,
                              boolean freeShippingApplied, String marketName, String groupBuyNumber) {
        this.order = order;
        this.groupBuy = groupBuy;
        this.market = market;
        this.productTotal = productTotal;
        this.deliveryFee = deliveryFee;
        this.freeShippingApplied = freeShippingApplied;
        this.marketName = marketName;
        this.groupBuyNumber = groupBuyNumber;
    }

    public Long getGroupBuyId() {
        return groupBuy == null ? null : groupBuy.getId();
    }

    public Long getMarketId() {
        return market == null ? null : market.getId();
    }
}
