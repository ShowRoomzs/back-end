package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;

import java.time.LocalDateTime;

/**
 * 클레임 요청(= 한 박스 · 35 설계서 1-4 · 앱 클레임 설계서 1-2) — 사유 · 회수 송장 · 수취 주소 · 배송비는 요청에 하나다.
 * 상태와 검수 판정은 항목({@link OrderClaim})에 있다. 한 묶음은 한 하위주문 안의 항목들이다.
 */
@Entity
@Table(name = "order_claim_collection",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_claim_collection_idempotency",
                columnNames = {"user_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderClaimCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "collection_id")
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_group_id", nullable = false)
    private OrderDeliveryGroup deliveryGroup;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ClaimType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 40)
    private ClaimReason reasonCode;

    @Column(name = "reason_detail", length = 1000)
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_bearer", nullable = false, length = 16)
    private ClaimFeeBearer feeBearer;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    // ── 회수 송장 — 소비자 입력. 브랜드 API 는 읽기만 ──

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier", length = 30)
    private DeliveryCarrier carrier;

    @Column(name = "tracking_number", length = 50)
    private String trackingNumber;

    /** 입력 시각 = 회수 중 전환. 정정으로 바뀌지 않는다. */
    @Column(name = "invoice_registered_at")
    private LocalDateTime invoiceRegisteredAt;

    /** 회수 송장 등록 기한 — 지나면 자동 취소(전이 #14). 수정 기한도 같다. */
    @Column(name = "invoice_due_at", nullable = false)
    private LocalDateTime invoiceDueAt;

    @Column(name = "last_tracking_at")
    private LocalDateTime lastTrackingAt;

    @Column(name = "last_tracking_label", length = 100)
    private String lastTrackingLabel;

    /** 추적상 브랜드 도착 — 입고 확인과 따로 저장한다(기산점 미결 §35-9 A-5). */
    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    // ── 반품 수취 주소 스냅샷 — 신청 시점의 마켓 출고지 ──

    @Column(name = "return_recipient", length = 64)
    private String returnRecipient;

    @Column(name = "return_contact", length = 20)
    private String returnContact;

    @Column(name = "return_address", length = 255)
    private String returnAddress;

    @Column(name = "return_detail_address", length = 255)
    private String returnDetailAddress;

    /** 반품 배송비 차감액 — 요청당 한 번. 고객 귀책 반품만, 아니면 0. */
    @Column(name = "return_deduction", nullable = false)
    private Integer returnDeduction;

    // ── 재발송 수취지 스냅샷 — 교환 새 상품 · 반려 상품이 가는 곳. 기본은 원 주문 배송지 ──

    @Column(name = "reship_recipient", length = 64)
    private String reshipRecipient;

    @Column(name = "reship_phone", length = 20)
    private String reshipPhone;

    @Column(name = "reship_zip_code", length = 10)
    private String reshipZipCode;

    @Column(name = "reship_address", length = 255)
    private String reshipAddress;

    @Column(name = "reship_detail_address", length = 255)
    private String reshipDetailAddress;

    @Column(name = "reship_memo", length = 255)
    private String reshipMemo;

    /** 요청의 판정이 다 끝난 순간 확정되는 환불액. */
    @Column(name = "refund_amount")
    private Integer refundAmount;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public OrderClaimCollection(Long orderId, OrderDeliveryGroup deliveryGroup, Long marketId, Long userId,
                                ClaimType type, ClaimReason reasonCode, String reasonDetail, ClaimFeeBearer feeBearer,
                                String idempotencyKey, LocalDateTime invoiceDueAt, String returnRecipient,
                                String returnContact, String returnAddress, String returnDetailAddress,
                                Integer returnDeduction, String reshipRecipient, String reshipPhone,
                                String reshipZipCode, String reshipAddress, String reshipDetailAddress,
                                String reshipMemo, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.deliveryGroup = deliveryGroup;
        this.marketId = marketId;
        this.userId = userId;
        this.type = type;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
        this.feeBearer = feeBearer;
        this.idempotencyKey = idempotencyKey;
        this.invoiceDueAt = invoiceDueAt;
        this.returnRecipient = returnRecipient;
        this.returnContact = returnContact;
        this.returnAddress = returnAddress;
        this.returnDetailAddress = returnDetailAddress;
        this.returnDeduction = returnDeduction != null ? returnDeduction : 0;
        this.reshipRecipient = reshipRecipient;
        this.reshipPhone = reshipPhone;
        this.reshipZipCode = reshipZipCode;
        this.reshipAddress = reshipAddress;
        this.reshipDetailAddress = reshipDetailAddress;
        this.reshipMemo = reshipMemo;
        this.createdAt = createdAt;
    }

    /** 교환받을 배송지 변경 — 고른 배송지의 값을 복사한다(스냅샷). 바꿀 수 있는 단계인지는 호출자가 본다. */
    public void changeReshipAddress(String recipient, String phone, String zipCode, String address,
                                    String detailAddress, String memo) {
        this.reshipRecipient = recipient;
        this.reshipPhone = phone;
        this.reshipZipCode = zipCode;
        this.reshipAddress = address;
        this.reshipDetailAddress = detailAddress;
        this.reshipMemo = memo;
    }

    public boolean isFinalized() {
        return finalizedAt != null;
    }

    /** 판정 종료 — 그 요청의 클레임이 전부 검수 판정을 받았다. 환불할 것이 없으면(전체 반려 · 교환) 환불액은 null. */
    /**
     * 검수에서 브랜드 귀책으로 인정(1009 기획 수정본 5-b 귀책 변경) — 반품 배송비 차감을 돌려주고 반려 재발송비를 브랜드가 진다.
     * 판정 종료 전에만 의미가 있다(환불액은 종료 때 이 값으로 계산한다).
     */
    public void acceptSellerFault() {
        this.feeBearer = ClaimFeeBearer.SELLER;
        this.returnDeduction = 0;
    }

    public void finalizeWith(Integer refundAmount, LocalDateTime now) {
        this.refundAmount = refundAmount;
        this.finalizedAt = now;
    }

    /** 운영자가 집행한 금액으로 환불액을 확정한다 — 예정액과 다를 수 있다. */
    public void confirmRefund(int refundedAmount) {
        this.refundAmount = refundedAmount;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean hasInvoice() {
        return carrier != null && trackingNumber != null;
    }

    /** 회수 송장 입력 — 묶음을 회수 중으로 옮기는 전이(#2)는 호출자가 같은 트랜잭션에서 한다. */
    public void registerInvoice(DeliveryCarrier carrier, String trackingNumber, LocalDateTime now) {
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.invoiceRegisteredAt = now;
    }

    /** 오입력 정정 — 입력 시각은 유지하고 추적 값만 리셋한다(새 송장의 이력이 0건부터 다시 쌓인다). */
    public void updateInvoice(DeliveryCarrier carrier, String trackingNumber) {
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.lastTrackingAt = null;
        this.lastTrackingLabel = null;
    }
}
