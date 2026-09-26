package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게시물 숨김 사유 7종(M5 · 32 설계 1-5). 반려({@link GroupBuyPostRejectReason})와 앞 네 값은 코드가 같지만
 * 계약 불일치가 하나로 묶이고 「사실과 다른 정보」가 더해진다 — 하나로 합치면 모달마다 엉뚱한 선택지가 뜬다.
 */
@Getter
@RequiredArgsConstructor
public enum GroupBuyPostHideReason {
    AD_EFFECT_ASSERTION("표시광고법 위반 문구 — 효과 단정"),
    AD_MEDICAL_CLAIM("표시광고법 위반 문구 — 의료적 효능 표현"),
    AD_SUPERLATIVE("표시광고법 위반 문구 — 최저가·최상급 표현"),
    DISCLOSURE_DAMAGED("대가관계 표시 훼손"),
    CONTRACT_MISMATCH("계약과 다른 상품·가격 기재"),
    FALSE_INFORMATION("사실과 다른 정보"),
    ETC("기타(직접 입력)");

    private final String label;

    /** 모르는 코드는 null — 그럴듯한 라벨을 지어내지 않는다. */
    public static GroupBuyPostHideReason parse(String code) {
        try {
            return code == null ? null : valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
