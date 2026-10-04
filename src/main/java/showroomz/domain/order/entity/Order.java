package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.type.OrderStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문(결제 계획서 3-1 · 3-2).
 *
 * <p>금액 4종은 서버가 {@code OrderPricingCalculator}로 계산해 넣는다 — 앱이 보낸 금액은 받지 않는다. 배송지는 스냅샷이다.
 * 사용자가 배송지를 지우거나 고쳐도 주문 상세는 그대로다.
 *
 * <p>상태 전이의 경합 차단은 {@code OrderRepository}의 조건부 UPDATE가 한다(4-1). 이 클래스는 빈 주문이 생기지 않게
 * 정적 팩토리로만 만든다(선행 수정 계획서 3-5). {@code paid_payment_id}는 문자열 컬럼이다 — 결제와 주문이 서로를 참조하는
 * 순환을 JPA 연관으로 두면 지연 로딩이 서로를 끌고 다닌다. FK는 마이그레이션(V145)에만 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_order_number", columnNames = {"order_number"}),
                @UniqueConstraint(name = "uk_orders_user_idempotency_key", columnNames = {"user_id", "idempotency_key"})
        },
        indexes = @Index(name = "idx_orders_status_expires_at", columnList = "status, expires_at")
)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    /** yyyyMMdd-NNNNNN — {@code OrderNumberGenerator}. */
    @Column(name = "order_number", nullable = false, length = 30)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    /** 정가 합. C9 「상품 금액」. */
    @Column(name = "product_total", nullable = false)
    private Integer productTotal;

    /** 정가 합 − 판매가 합. C9 「할인 금액」. */
    @Column(name = "discount_total", nullable = false)
    private Integer discountTotal;

    @Column(name = "delivery_fee_total", nullable = false)
    private Integer deliveryFeeTotal;

    /** 판매가 합 + 배송비 합 — 결제 금액. */
    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;

    // ── 배송지 스냅샷 ─────────────────────────────────────────────────────
    @Column(name = "recipient_name", length = 64)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    /** C9 요청사항 — 프리셋 4종 또는 직접 입력 50자. {@code DeliveryAddress.memo}와 같은 제약. */
    @Column(name = "delivery_memo", length = 50)
    private String deliveryMemo;

    /** 포트원 {@code orderName} — "첫 상품명 외 N건". */
    @Column(name = "order_name", length = 100)
    private String orderName;

    /** 앱이 주문 생성마다 보내는 UUID. 더블 탭이 주문을 두 번 만들지 않게 한다. UK는 (user_id, idempotency_key). */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** 생성 + 결제 대기 시간. 만료 스케줄러의 기준(4-4). */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 이 주문을 완료한 결제 — PAID 전이와 같은 UPDATE에서 채운다. confirm이 「내 결제가 아니다」를 판정하는 근거(4-5). */
    @Column(name = "paid_payment_id", length = 64)
    private String paidPaymentId;

    /** 재고 복원 시각 — 복원은 {@code WHERE stock_released_at IS NULL} 조건부 UPDATE로 1회만(4-3). */
    @Column(name = "stock_released_at")
    private LocalDateTime stockReleasedAt;

    /** 만료 전 포트원 조회 실패 횟수 — 상한에 닿으면 조회 없이 만료한다(4-4). */
    @Column(name = "expiry_check_failures", nullable = false)
    private int expiryCheckFailures;

    /** 포트원 PENDING으로 만료를 미룬 횟수 — 상한 3(4-4). */
    @Column(name = "expiry_deferrals", nullable = false)
    private int expiryDeferrals;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderProduct> orderProducts = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDeliveryGroup> deliveryGroups = new ArrayList<>();

    private Order(Users user, String orderNumber, Totals totals, AddressSnapshot address, String deliveryMemo,
                  String orderName, String idempotencyKey, LocalDateTime expiresAt) {
        this.user = user;
        this.orderNumber = orderNumber;
        this.status = OrderStatus.PAYMENT_PENDING;
        this.productTotal = totals.productTotal();
        this.discountTotal = totals.discountTotal();
        this.deliveryFeeTotal = totals.deliveryFeeTotal();
        this.totalAmount = totals.totalAmount();
        this.recipientName = address.recipientName();
        this.recipientPhone = address.recipientPhone();
        this.zipCode = address.zipCode();
        this.address = address.address();
        this.detailAddress = address.detailAddress();
        this.deliveryMemo = deliveryMemo;
        this.orderName = orderName;
        this.idempotencyKey = idempotencyKey;
        this.expiresAt = expiresAt;
    }

    /** 결제 대기 주문 — 주문 생성 트랜잭션(T2) 안에서만 불린다. 그룹·상품·결제 행은 호출자가 붙인다. */
    public static Order create(Users user, String orderNumber, Totals totals, AddressSnapshot address,
                               String deliveryMemo, String orderName, String idempotencyKey, LocalDateTime expiresAt) {
        return new Order(user, orderNumber, totals, address, deliveryMemo, orderName, idempotencyKey, expiresAt);
    }

    public void addDeliveryGroup(OrderDeliveryGroup group) {
        deliveryGroups.add(group);
    }

    public void addOrderProduct(OrderProduct orderProduct) {
        orderProducts.add(orderProduct);
    }

    public boolean isOwnedBy(Long userId) {
        return user != null && user.getId().equals(userId);
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /** 이 주문이 {@code paymentId}로 완료됐는가 — 4-5 ④(a) n=0 분기의 판정. */
    public boolean isPaidWith(String paymentId) {
        return status == OrderStatus.PAID && paymentId != null && paymentId.equals(paidPaymentId);
    }

    /** 첫 상품명 외 N건 — PG 길이 제한(100자)에 맞춰 자른다. */
    public static String orderNameOf(List<String> productNames) {
        if (productNames == null || productNames.isEmpty()) {
            return "주문";
        }
        String first = productNames.get(0);
        String name = productNames.size() == 1 ? first : first + " 외 " + (productNames.size() - 1) + "건";
        return name.length() <= 100 ? name : name.substring(0, 100);
    }

    /** C9 금액 블록 4줄과 1:1. */
    public record Totals(int productTotal, int discountTotal, int deliveryFeeTotal, int totalAmount) {
    }

    public record AddressSnapshot(String recipientName, String recipientPhone, String zipCode, String address,
                                  String detailAddress) {
    }
}
