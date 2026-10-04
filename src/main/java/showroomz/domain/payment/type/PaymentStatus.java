package showroomz.domain.payment.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * 결제 시도 상태(결제 계획서 4-2). 한 주문에 살아 있는({@link #READY}) 결제는 항상 하나다.
 *
 * <pre>
 * READY ──▶ PAID · FAILED · EXPIRED · SUPERSEDED · CANCELLED
 * SUPERSEDED · FAILED ──▶ PAID          (뒤늦게 결제됐는데 주문이 아직 PAYMENT_PENDING — 주문의 결제로 받아들인다)
 * PAID ──▶ CANCEL_REQUESTED ──▶ CANCELLED · PAID(거절 복귀) · CANCEL_FAILED · CANCELLED_MISMATCH
 * SUPERSEDED · EXPIRED · CANCELLED 에서 포트원 PAID 발견 ──▶ CANCEL_REQUESTED ──▶ CANCELLED_MISMATCH
 * </pre>
 */
@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    READY("결제 대기"),
    PAID("결제 완료"),
    FAILED("결제 실패"),
    EXPIRED("만료"),
    /** 재시도로 새 paymentId가 발급됨 — 이 시도는 더 이상 주문의 결제가 아니다. */
    SUPERSEDED("대체됨"),
    CANCELLED("취소"),
    /** 취소 선점 표시 — 포트원 취소 API는 이 전이를 통과한 트랜잭션이 커밋된 뒤에만 부른다(4-7). */
    CANCEL_REQUESTED("취소 요청"),
    /** 금액 불일치·만료 후 결제·다른 시도가 이미 완료 → 시스템 자동 취소. */
    CANCELLED_MISMATCH("불일치 자동 취소"),
    /** 시스템이 돌려주지 못한 돈 — 운영자가 PG 콘솔에서 처리한다(종결). */
    CANCEL_FAILED("취소 실패");

    private final String label;

    /** confirm이 포트원을 다시 읽어도 결과가 바뀌지 않는 종결 상태(4-5 ②). PAID·FAILED는 트리거에 따라 다르다. */
    public static final Set<PaymentStatus> CLOSED = Set.of(CANCELLED, CANCELLED_MISMATCH, CANCEL_FAILED);

    /** 포트원 PAID를 「주문의 결제」로 받아들일 수 있는 상태(4-2 · 4-5 ④a). */
    public static final Set<PaymentStatus> PAYABLE = Set.of(READY, SUPERSEDED, FAILED);

    /** 불일치 자동 취소를 선점할 수 있는 상태(4-5 ④b). */
    public static final Set<PaymentStatus> AUTO_CANCEL_CLAIMABLE = Set.of(READY, PAID, SUPERSEDED, EXPIRED, CANCELLED, FAILED);

    public boolean isClosed() {
        return CLOSED.contains(this);
    }
}
