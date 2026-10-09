package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 반품·교환 사유(35 설계서 1-5) — 소비자 앱과 파트너센터가 같은 라벨을 받는다.
 * 취소 요청의 {@code CancelRequestReason}과 값이 겹치지만 합치지 않는다 — 취소에는 부담 주체가 없다.
 *
 * <p>{@code consumerSelectable} — 앱 시안의 선택지에 「사이즈가 맞지 않음」은 없다. 파트너 목록 필터에는 남는다.
 *
 * <p>{@link #OTHER} — 「기타 (직접 입력)」(1009 기획 · 결정 10). <b>반품에서만</b> 고를 수 있고, 기본은 소비자 귀책이라
 * 배송비가 차감되며 상세 내용이 필수다. 검수에서 브랜드 귀책으로 인정되면 차감액이 돌아온다(검수 반려의 귀책 변경).
 */
@Getter
@RequiredArgsConstructor
public enum ClaimReason {
    CHANGE_OF_MIND("단순 변심", ClaimFeeBearer.CONSUMER, false, true, false),
    ORDER_MISTAKE("주문 실수", ClaimFeeBearer.CONSUMER, false, true, false),
    SIZE_MISMATCH("사이즈가 맞지 않음", ClaimFeeBearer.CONSUMER, false, false, false),
    DAMAGED_OR_DEFECTIVE("배송 상품 파손 및 불량", ClaimFeeBearer.SELLER, true, true, false),
    WRONG_OR_LATE_DELIVERY("오배송 및 배송 지연", ClaimFeeBearer.SELLER, true, true, false),
    OTHER("기타", ClaimFeeBearer.CONSUMER, true, true, true);

    private final String label;
    private final ClaimFeeBearer feeBearer;
    /** 브랜드 부담 사유와 「기타」는 상세 내용이 필수다. */
    private final boolean detailRequired;
    private final boolean consumerSelectable;
    /** 반품 화면에만 나오는 사유 — 교환에는 「기타」가 없다(시안 C10-3 1b). */
    private final boolean returnOnly;

    /** 이 유형의 요청에서 소비자가 고를 수 있는가. */
    public boolean isSelectableFor(ClaimType type) {
        return consumerSelectable && (!returnOnly || type == ClaimType.RETURN);
    }
}
