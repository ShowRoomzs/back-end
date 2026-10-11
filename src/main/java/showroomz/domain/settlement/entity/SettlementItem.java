package showroomz.domain.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.settlement.type.SettlementItemStatus;

import java.math.BigDecimal;

/**
 * 명세 — 주문 항목 1행(44 어드민 설계서 1-3). 정산 반영액 · 리워드를 같이 저장해 다운로드 · 차감 추적 · 「왜 이 주문이 0원인가」가
 * 전부 이 행에서 나온다. 상품명 · 소비자 · 단가는 스냅샷이다 — 생성 뒤 상품명이 바뀌어도 명세는 그대로다.
 *
 * <p>합의로 리워드가 바뀌어도 이 행의 {@link #rewardAmount}는 그대로다(4-3) — 합의 금액은 회차 총액이다.
 */
@Entity
@Table(name = "settlement_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_item_id")
    private Long id;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "delivery_group_id", nullable = false)
    private Long deliveryGroupId;

    /** 한 항목은 한 정산에만 — 06a ④ · 06c 정산 자리 · 차감 추적의 키다. */
    @Column(name = "order_product_id", nullable = false, unique = true)
    private Long orderProductId;

    @Column(name = "order_number", nullable = false, length = 30)
    private String orderNumber;

    @Column(name = "sub_order_number", length = 40)
    private String subOrderNumber;

    @Column(name = "consumer_user_id")
    private Long consumerUserId;

    /** 어드민 · 파트너 명세에만 내린다 — 스튜디오 DTO 에 없다. */
    @Column(name = "consumer_name_masked", length = 64)
    private String consumerNameMasked;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "option_name", length = 255)
    private String optionName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "returned_quantity", nullable = false)
    private int returnedQuantity;

    @Column(name = "settled_quantity", nullable = false)
    private int settledQuantity;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "paid_amount", nullable = false)
    private long paidAmount;

    @Column(name = "settled_amount", nullable = false)
    private long settledAmount;

    @Column(name = "reward_rate", nullable = false, precision = 4, scale = 1)
    private BigDecimal rewardRate;

    @Column(name = "unit_reward", nullable = false)
    private long unitReward;

    @Column(name = "reward_amount", nullable = false)
    private long rewardAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementItemStatus status;

    @Builder
    private SettlementItem(Long settlementId, Long orderId, Long deliveryGroupId, Long orderProductId,
                           String orderNumber, String subOrderNumber, Long consumerUserId, String consumerNameMasked,
                           Long productId, String productName, String optionName, int quantity, int returnedQuantity,
                           int settledQuantity, long unitPrice, long paidAmount, long settledAmount,
                           BigDecimal rewardRate, long unitReward, long rewardAmount, SettlementItemStatus status) {
        this.settlementId = settlementId;
        this.orderId = orderId;
        this.deliveryGroupId = deliveryGroupId;
        this.orderProductId = orderProductId;
        this.orderNumber = orderNumber;
        this.subOrderNumber = subOrderNumber;
        this.consumerUserId = consumerUserId;
        this.consumerNameMasked = consumerNameMasked;
        this.productId = productId;
        this.productName = productName;
        this.optionName = optionName;
        this.quantity = quantity;
        this.returnedQuantity = returnedQuantity;
        this.settledQuantity = settledQuantity;
        this.unitPrice = unitPrice;
        this.paidAmount = paidAmount;
        this.settledAmount = settledAmount;
        this.rewardRate = rewardRate;
        this.unitReward = unitReward;
        this.rewardAmount = rewardAmount;
        this.status = status;
    }
}
