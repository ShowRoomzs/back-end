package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 오픈 반려 사유 7종(M1 · 32 설계 1-5). <b>심사 기준과 같은 축</b>이라 반려 사유별 통계가 축 단위로도 묶인다.
 *
 * <p>숨김 사유({@link GroupBuyPostHideReason})와 분리한다 — 같은 위반은 같은 코드를 쓰되 선택지 구성이 다르다.
 */
@Getter
@RequiredArgsConstructor
public enum GroupBuyPostRejectReason {
    AD_EFFECT_ASSERTION("표시광고법 위반 문구 — 효과 단정", Axis.AD_LAW),
    AD_MEDICAL_CLAIM("표시광고법 위반 문구 — 의료적 효능 표현", Axis.AD_LAW),
    AD_SUPERLATIVE("표시광고법 위반 문구 — 최저가·최상급 표현", Axis.AD_LAW),
    CONTRACT_PRODUCT_MISMATCH("계약과 다른 상품 구성", Axis.CONTRACT_MISMATCH),
    CONTRACT_PRICE_MISMATCH("계약과 다른 가격 표기", Axis.CONTRACT_MISMATCH),
    DISCLOSURE_DAMAGED("대가관계 표시 훼손", Axis.DISCLOSURE),
    ETC("기타(직접 입력)", Axis.ETC);

    private final String label;
    private final Axis axis;

    /** 모르는 코드는 null — 그럴듯한 라벨을 지어내지 않는다. */
    public static GroupBuyPostRejectReason parse(String code) {
        try {
            return code == null ? null : valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum Axis {
        AD_LAW("표시광고법"),
        CONTRACT_MISMATCH("계약 불일치"),
        DISCLOSURE("대가관계"),
        ETC("기타");

        private final String label;
    }
}
