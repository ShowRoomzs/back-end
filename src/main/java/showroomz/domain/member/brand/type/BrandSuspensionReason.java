package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.member.common.type.SuspensionReasonType;

/**
 * 브랜드 정지 사유 6종(§51-2 · M1). <b>지급 정지 여부는 사유가 정한다</b> — 운영자가 켜고 끄는 스위치가 아니다(§51-1). 돈과 무관한 사유로
 * 이미 판매 · 배송이 끝난 대금까지 묶으면 과도한 제재가 된다.
 */
@Getter
@RequiredArgsConstructor
public enum BrandSuspensionReason implements SuspensionReasonType {
    SETTLEMENT_INFO_MISMATCH("정산 정보 불일치", true),
    BUSINESS_INFO_FALSIFIED("사업자 정보 허위 기재", true),
    PRODUCT_INFO_FALSIFIED("상품 정보 허위·과장", false),
    REPEATED_SHIPPING_DELAY("배송 지연 반복", false),
    CONSUMER_RESPONSE_NEGLECT("소비자 응대 불이행", false),
    OTHER("기타(직접 입력)", false);

    private final String label;
    /** 브랜드 몫 지급 정지 — 계좌 · 사업자 확인이 필요한 사유만. */
    private final boolean holdsPayout;

    public boolean isDetailRequired() {
        return this == OTHER;
    }
}
