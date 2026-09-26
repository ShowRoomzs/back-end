package showroomz.domain.product.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductGroupBuyStatus {
    PREPARING("준비중"),
    READY("준비완료"),
    IN_PROGRESS("진행중"),
    NOT_CONNECTED("연결없음");

    private final String description;

    /**
     * 공구에 연결된 상품인지 (C7).
     *
     * <p>상품 상세는 공구 게시물의 상품 카드에서만 진입한다 — 공구에 붙지 않은 상품(NOT_CONNECTED)은
     * 게시되지 않는다. 준비중·준비완료도 연결로 본다 — 그 둘은 "공구가 아직 안 열렸다"이지
     * "공구가 없다"가 아니다. 공구가 끝나 연결이 풀리면 이 값이 다시 NOT_CONNECTED로 돌아가고,
     * 그 순간부터 상세도 함께 막힌다 — 진열 여부는 별개 조건으로 서비스 계층에서 더 확인한다.
     */
    public boolean isConnected() {
        return this != NOT_CONNECTED;
    }

    /**
     * 지금 살 수 있는지 — <b>진행중만</b>이다. {@link #isConnected()}는 상세 진입용 의미라 준비중·준비완료도 참이다.
     *
     * <p>공구 모듈이 이 값을 동기화하기 전에는 모든 상품이 NOT_CONNECTED라 차이가 드러나지 않았다.
     * 구매 판정에 {@code isConnected()}를 쓰면 시작 전 공구 상품이 결제된다(공구 설계서 1-11 · P3 선행 조건).
     */
    public boolean isPurchasable() {
        return this == IN_PROGRESS;
    }
}
