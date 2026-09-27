package showroomz.domain.groupbuy.service;

import showroomz.domain.groupbuy.type.GroupBuyProductState;
import showroomz.domain.groupbuy.type.GroupBuySaleState;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 소비자 앱의 공구 블록 한 벌(공구 게시물 설계 5-2) — C1·C3·C4·C5가 같은 값을 쓰고, 화면별 차이(목록에서 마감이면 상품
 * 행을 싣지 않는 것 등)는 응답을 조립하는 쪽이 정한다.
 *
 * @param dDay         KST 날짜 차이 — 마감 당일 0. {@link GroupBuySaleState#CLOSED}면 null
 * @param adDisclosure 대가관계 표시 문구 — {@link GroupBuyDisclosure#text}. 저장하지 않고 조립한다(31 설계 2-8)
 * @param products     계약 상품 전부 · {@code sort_order} 순
 */
public record GroupBuyPostCard(
        Long groupBuyId,
        String title,
        GroupBuySaleState saleState,
        Integer dDay,
        LocalDateTime endAt,
        String adDisclosure,
        List<Product> products
) {

    /** 하트 잠금 — 마감이면 해제만. {@code PUBLISHED}인 공구 게시물은 노출중 또는 마감 3일 이내뿐이라 {@code canLike}와 답이 같다(6-3). */
    public boolean likeLocked() {
        return saleState.isClosed();
    }

    /**
     * @param name            계약 시점 상품명 — 상품명이 바뀌어도 게시물은 계약대로
     * @param detailAvailable C7로 갈 수 있는지(4-6). 마감이면 false(미결 ④ 기본값)
     */
    public record Product(
            Long productId,
            String name,
            String thumbnailUrl,
            Integer regularPrice,
            Integer groupBuyPrice,
            int discountRate,
            GroupBuyProductState state,
            boolean detailAvailable
    ) {
    }
}
