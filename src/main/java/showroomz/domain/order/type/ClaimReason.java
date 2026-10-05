package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 반품·교환 사유 5종(35 설계서 1-5) — 소비자 앱과 파트너센터가 같은 라벨을 받는다.
 * 취소 요청의 {@code CancelRequestReason}과 값이 겹치지만 합치지 않는다 — 취소에는 부담 주체가 없다.
 *
 * <p>{@code consumerSelectable} — 앱 시안의 선택지는 4종이다(「사이즈가 맞지 않음」 없음). 5종 통일은 확정 대기다
 * (앱 클레임 설계서 Q1).
 */
@Getter
@RequiredArgsConstructor
public enum ClaimReason {
    CHANGE_OF_MIND("단순 변심", ClaimFeeBearer.CONSUMER, false, true),
    ORDER_MISTAKE("주문 실수", ClaimFeeBearer.CONSUMER, false, true),
    SIZE_MISMATCH("사이즈가 맞지 않음", ClaimFeeBearer.CONSUMER, false, false),
    DAMAGED_OR_DEFECTIVE("배송 상품 파손 및 불량", ClaimFeeBearer.SELLER, true, true),
    WRONG_OR_LATE_DELIVERY("오배송 및 배송 지연", ClaimFeeBearer.SELLER, true, true);

    private final String label;
    private final ClaimFeeBearer feeBearer;
    /** 브랜드 부담 사유는 상세 내용이 필수다. */
    private final boolean detailRequired;
    private final boolean consumerSelectable;
}
