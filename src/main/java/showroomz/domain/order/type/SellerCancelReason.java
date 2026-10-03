package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 직권 취소 사유(E5) — ETC 는 직접 입력이 필수다. */
@Getter
@RequiredArgsConstructor
public enum SellerCancelReason {

    SOLD_OUT("품절"),
    DEFECT("상품 하자"),
    UNDELIVERABLE_AREA("배송 불가 지역"),
    ETC("기타");

    private final String label;
}
