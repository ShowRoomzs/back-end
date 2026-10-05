package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 검수 거절 사유 5종(35 설계서 1-6) — 상세 설명은 사유와 무관하게 전부 필수다. */
@Getter
@RequiredArgsConstructor
public enum ClaimRejectReason {
    USED("개봉·사용 흔적"),
    PACKAGE_DAMAGED("포장 훼손"),
    PRODUCT_MISMATCH("상품 불일치"),
    PERIOD_EXPIRED("기간 경과"),
    ETC("기타");

    private final String label;
}
