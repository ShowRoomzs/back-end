package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.type.PaymentStatus;
import showroomz.domain.order.vo.ShippingAddressSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 주문 — 결제 단위(소비자 결제 한 번 = 주문 1건).
 * 배송·정산은 공구별 하위주문(SubOrder), 취소·반품은 주문 항목(OrderProduct) 단위로 분리한다
 * (결제=한 번 / 배송·정산=공구별 / 취소·반품=항목별 — 거래 플로우 §10).
 * 배송지는 주문 시점 스냅샷으로 저장해 주소록 변경·삭제와 무관하게 고정.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Embedded
    private ShippingAddressSnapshot shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    /** PG 거래 ID */
    @Column(name = "pg_transaction_id", length = 255)
    private String pgTransactionId;

    /** 총 결제금액 — 공구가(스냅샷) × 수량 합계 */
    @Column(name = "total_amount")
    private Integer totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubOrder> subOrders = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderProduct> orderProducts = new ArrayList<>();

    @Builder
    public Order(Users user, ShippingAddressSnapshot shippingAddress, String paymentMethod, Integer totalAmount) {
        this.user = user;
        this.shippingAddress = shippingAddress;
        this.paymentMethod = paymentMethod;
        this.totalAmount = totalAmount;
        this.paymentStatus = PaymentStatus.PENDING;
    }

    /** PG 승인 성공 → 결제완료. 재고 차감은 이 시점(서비스에서 처리) */
    public void completePayment(String pgTransactionId) {
        this.pgTransactionId = pgTransactionId;
        this.paymentStatus = PaymentStatus.PAID;
    }

    /** PG 승인 실패 → 결제실패 */
    public void failPayment() {
        this.paymentStatus = PaymentStatus.FAILED;
    }

    /** 전체 취소·환불 처리됨 */
    public void cancel() {
        this.paymentStatus = PaymentStatus.CANCELED;
    }

    public boolean isPaid() {
        return paymentStatus == PaymentStatus.PAID;
    }
}
