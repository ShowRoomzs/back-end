package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 클레임 진행 단계(35 설계서 1-2) — 화면 9종 + 화면 밖 1종. 배지 톤은 전부 중립이다(단계는 진행 순서일 뿐).
 *
 * <p>{@link #PAYMENT_PENDING}은 고객 귀책 교환이 재발송 배송비 결제를 기다리는 임시 상태다 — 브랜드 화면 어디에도
 * 나오지 않고, 결제가 확정되면 REQUESTED(또는 COLLECTING)가 된다(앱 클레임 설계서 1-1).
 */
@Getter
@RequiredArgsConstructor
public enum ClaimStatus {
    PAYMENT_PENDING("결제 대기"),
    REQUESTED("회수 대기"),
    COLLECTING("회수 중"),
    ARRIVED("입고 확인 전"),
    RECEIVED("검수 대기"),
    REFUND_PENDING("환불 대기"),
    RESHIP_READY("재발송 대기"),
    RESHIPPING("재발송 중"),
    REJECT_HOLD("거절 보류"),
    COMPLETED("완료");

    private final String label;
}
