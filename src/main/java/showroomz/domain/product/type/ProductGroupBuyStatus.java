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
     * <p>상품 상세는 공구 게시물의 상품 카드에서만 진입한다 — 공구에 붙지 않은 상품은
     * 진열 상태와 무관하게 소비자 화면에 게시되지 않는다. 준비중·준비완료도 연결로 본다.
     * 그 둘은 "공구가 아직 안 열렸다"이지 "공구가 없다"가 아니고, 마감된 공구도 상세는 열려
     * 마감 배지를 보여줘야 하기 때문이다.
     */
    public boolean isConnected() {
        return this != NOT_CONNECTED;
    }
}
