package showroomz.domain.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.order.entity.Order;
import showroomz.domain.payment.type.CardIssuer;
import showroomz.domain.payment.type.EasyPayProvider;
import showroomz.domain.payment.type.MismatchReason;
import showroomz.domain.payment.type.PaymentMethod;
import showroomz.domain.payment.type.PaymentStatus;

import java.time.LocalDateTime;

/**
 * 결제 시도 한 건(결제 계획서 3-2 {@code payment}). PK는 포트원 {@code paymentId}와 같은 문자열이라 웹훅·조회에서 조인 없이
 * 바로 찾는다 — {@code {order_number}-{attempt}}.
 *
 * <p>상태 전이는 전부 {@code PaymentRepository}의 조건부 UPDATE다(4-7). 이 클래스는 생성과 읽기만 한다.
 * {@link Persistable}을 구현하는 이유 — 문자열 PK를 미리 채우면 Spring Data가 새 행인지 알 수 없어 {@code merge}(SELECT 후
 * INSERT)로 저장한다. 새 행임을 직접 알려 INSERT 한 방으로 끝낸다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_order_attempt", columnNames = {"order_id", "attempt"}),
        indexes = {
                @Index(name = "idx_payment_status_next_cancel_retry", columnList = "status, next_cancel_retry_at"),
                @Index(name = "idx_payment_order_status", columnList = "order_id, status")
        }
)
public class Payment extends BaseTimeEntity implements Persistable<String> {

    public static final String CURRENCY_KRW = "KRW";

    @Id
    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    /** 포트원 사전 등록 성공 시각. NULL이면 재요청이 사전 등록을 다시 시도한다(5-3). */
    @Column(name = "pre_registered_at")
    private LocalDateTime preRegisteredAt;

    /** 취소 선점 시각 — CANCEL_REQUESTED 전이와 함께. */
    @Column(name = "cancel_requested_at")
    private LocalDateTime cancelRequestedAt;

    /** 취소 수렴 재시도 횟수 — 상한 도달 시 CANCEL_FAILED(4-4 둘째 단계). */
    @Column(name = "cancel_attempts", nullable = false)
    private int cancelAttempts;

    @Column(name = "next_cancel_retry_at")
    private LocalDateTime nextCancelRetryAt;

    /** 자동 취소 사유. NULL이면 사용자·운영자 취소. */
    @Enumerated(EnumType.STRING)
    @Column(name = "mismatch_reason", length = 40)
    private MismatchReason mismatchReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_issuer", length = 20)
    private CardIssuer cardIssuer;

    @Enumerated(EnumType.STRING)
    @Column(name = "easy_pay_provider", length = 20)
    private EasyPayProvider easyPayProvider;

    /** 주문 {@code total_amount} 복사 — 결제 시점 금액 증거. */
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /**
     * 부분 취소 누적액(1009 기획 수정본 2-3) — 환불 큐 집행이 올린다. 상태는 PAID 그대로이고, 누적이 결제액에 닿으면
     * CANCELLED 로 닫는다(주문 전체 취소의 연쇄 없이). 전액 취소 경로(소비자 취소 · 자동 취소)는 이 값을 쓰지 않는다.
     */
    @Column(name = "cancelled_amount", nullable = false)
    private int cancelledAmount;

    /** 지금 PG 에서 취소할 수 있는 잔액. */
    public int cancellableAmount() {
        return amount - cancelledAmount;
    }

    /** {@code CHAR(3)} — 마이그레이션(V145)과 같은 타입이어야 {@code ddl-auto: validate}가 통과한다. */
    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "channel_key", length = 100)
    private String channelKey;

    @Column(name = "pg_provider", length = 50)
    private String pgProvider;

    @Column(name = "pg_tx_id", length = 100)
    private String pgTxId;

    /** 포트원이 실제로 처리한 결제수단 원문 — 사용자가 고른 것과 다를 수 있다(간편결제 안의 카드 등). */
    @Column(name = "pg_method_json", columnDefinition = "TEXT")
    private String pgMethodJson;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "fail_code", length = 100)
    private String failCode;

    @Column(name = "fail_message", length = 500)
    private String failMessage;

    /** 마지막 {@code GET /payments/{id}} 원문 — 분쟁 시 근거. */
    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Transient
    private boolean isNew = true;

    private Payment(String paymentId, Order order, int attempt, PaymentMethod method, CardIssuer cardIssuer,
                    EasyPayProvider easyPayProvider, int amount, String channelKey) {
        this.paymentId = paymentId;
        this.order = order;
        this.attempt = attempt;
        this.status = PaymentStatus.READY;
        this.method = method;
        this.cardIssuer = cardIssuer;
        this.easyPayProvider = easyPayProvider;
        this.amount = amount;
        this.currency = CURRENCY_KRW;
        this.channelKey = channelKey;
    }

    /** 살아 있는 결제 — 주문 생성(T2)·재시도(T13) 안에서만 만든다. 이전 READY는 호출자가 먼저 SUPERSEDED로 내린다. */
    public static Payment ready(Order order, int attempt, PaymentMethod method, CardIssuer cardIssuer,
                                EasyPayProvider easyPayProvider, int amount, String channelKey) {
        return new Payment(paymentIdOf(order.getOrderNumber(), attempt), order, attempt, method, cardIssuer,
                easyPayProvider, amount, channelKey);
    }

    public static String paymentIdOf(String orderNumber, int attempt) {
        return orderNumber + "-" + attempt;
    }

    @Override
    public String getId() {
        return paymentId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public Long getOrderId() {
        return order == null ? null : order.getId();
    }

    /** 사용자가 고른 수단의 화면 라벨 — 「신한카드」·「카카오페이」. */
    public String methodLabel() {
        if (method == PaymentMethod.CARD) {
            return cardIssuer != null ? cardIssuer.getLabel() + "카드" : "카드";
        }
        return easyPayProvider != null ? easyPayProvider.getLabel() : "간편결제";
    }
}
