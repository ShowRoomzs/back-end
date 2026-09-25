package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 브랜드의 공구 중단 요청 사유(C2·C4).
 *
 * <p>재고 소진이 <b>없는 것이 맞다</b> — §29-2 「재고 소진은 중단이 아니라 조기 마감이다」.
 * 요청 유형별로 enum을 분리해 {@code SUSPEND + STOCK_OUT} 조합을 컴파일 단계에서 막는다(설계서 1-6).
 */
@Getter
@RequiredArgsConstructor
public enum SuspensionReasonCode {
    QUALITY_ISSUE("상품 품질 이슈"),
    PRICE_TERMS_ERROR("가격·조건 오기"),
    NEGOTIATION_BROKEN("인플루언서와 협의 결렬"),
    ETC("기타");

    private final String label;

    public boolean requiresMemo() {
        return this == ETC;
    }
}
