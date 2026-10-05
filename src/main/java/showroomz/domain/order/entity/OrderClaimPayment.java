package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;
import showroomz.domain.order.type.ClaimPaymentStatus;
import showroomz.domain.payment.type.CardIssuer;
import showroomz.domain.payment.type.EasyPayProvider;
import showroomz.domain.payment.type.PaymentMethod;

import java.time.LocalDateTime;

/**
 * 클레임 재발송 배송비 결제 시도(앱 클레임 설계서 1-5) — 주문 결제({@code payment})와 테이블을 섞지 않는다.
 * {@code paymentId}는 포트원 paymentId 와 같은 문자열이고({@code clm-{chargeId}-{attempt}}), 접두로 주문 결제와 구분한다.
 *
 * <p>상태 전이는 전부 리포지토리의 조건부 UPDATE 다. 청구·요청에 FK 를 걸지 않는다 — 결제 대기 요청은 30분 뒤 지워지는데,
 * 그 뒤에 도착한 결제를 자동 취소하려면 이 행이 남아 있어야 한다.
 * {@link Persistable}을 구현하는 이유는 {@code Payment}와 같다 — 문자열 PK 를 미리 채운 새 행을 INSERT 한 방으로 저장한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_claim_payment",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_claim_payment_attempt",
                columnNames = {"charge_id", "attempt"}))
public class OrderClaimPayment implements Persistable<String> {

    public static final String ID_PREFIX = "clm-";

    @Id
    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "charge_id", nullable = false)
    private Long chargeId;

    @Column(name = "collection_id", nullable = false)
    private Long collectionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ClaimPaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_issuer", length = 20)
    private CardIssuer cardIssuer;

    @Enumerated(EnumType.STRING)
    @Column(name = "easy_pay_provider", length = 20)
    private EasyPayProvider easyPayProvider;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "channel_key", length = 100)
    private String channelKey;

    @Column(name = "pg_tx_id", length = 100)
    private String pgTxId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "fail_code", length = 100)
    private String failCode;

    @Column(name = "cancel_requested_at")
    private LocalDateTime cancelRequestedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Transient
    private boolean isNew = true;

    private OrderClaimPayment(Long chargeId, Long collectionId, Long userId, int attempt, PaymentMethod method,
                              CardIssuer cardIssuer, EasyPayProvider easyPayProvider, int amount, String channelKey,
                              LocalDateTime now) {
        this.paymentId = paymentIdOf(chargeId, attempt);
        this.chargeId = chargeId;
        this.collectionId = collectionId;
        this.userId = userId;
        this.attempt = attempt;
        this.status = ClaimPaymentStatus.READY;
        this.method = method;
        this.cardIssuer = cardIssuer;
        this.easyPayProvider = easyPayProvider;
        this.amount = amount;
        this.channelKey = channelKey;
        this.createdAt = now;
    }

    public static OrderClaimPayment ready(Long chargeId, Long collectionId, Long userId, int attempt,
                                          PaymentMethod method, CardIssuer cardIssuer,
                                          EasyPayProvider easyPayProvider, int amount, String channelKey,
                                          LocalDateTime now) {
        return new OrderClaimPayment(chargeId, collectionId, userId, attempt, method, cardIssuer, easyPayProvider,
                amount, channelKey, now);
    }

    public static String paymentIdOf(Long chargeId, int attempt) {
        return ID_PREFIX + chargeId + "-" + attempt;
    }

    /** 포트원 paymentId 가 클레임 결제의 것인가 — 웹훅이 이것으로 갈래를 정한다. */
    public static boolean isClaimPaymentId(String paymentId) {
        return paymentId != null && paymentId.startsWith(ID_PREFIX);
    }

    /** 사용자가 고른 수단의 화면 라벨 — 「신한카드」·「카카오페이」. */
    public String methodLabel() {
        if (method == PaymentMethod.CARD) {
            return cardIssuer != null ? cardIssuer.getLabel() + "카드" : "카드";
        }
        return easyPayProvider != null ? easyPayProvider.getLabel() : "간편결제";
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
}
