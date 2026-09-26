package showroomz.domain.groupbuy.type;

/**
 * 공구 게시물 상품 행 하나의 상태(공구 게시물 설계 5-2). 앱은 {@link #ON_SALE}이 아닌 행을 흑백 + 라벨로 그린다.
 *
 * <p>일부 품절이어도 게시물 배지는 D-day 그대로이고, 품절 행만 이 값이 {@link #SOLD_OUT}이다.
 */
public enum GroupBuyProductState {

    ON_SALE,
    SOLD_OUT,
    /** 공구 마감 — 게시물의 {@link GroupBuySaleState#CLOSED}면 품절 여부와 무관하게 전부 이 값이다. */
    CLOSED
}
