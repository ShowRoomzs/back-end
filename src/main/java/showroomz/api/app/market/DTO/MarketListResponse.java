package showroomz.api.app.market.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.market.type.ShopType;

/**
 * 마켓 목록 항목 — <b>소비자 앱은 더 이상 쓰지 않는다.</b>
 *
 * <p>소비자에게 조회되는 것은 쇼룸뿐이라 샵 목록({@code GET /v1/user/shops})은
 * {@code GET /v1/user/showrooms}로 대체됐다. 이 클래스가 남아 있는 것은 기획 제외된
 * 추천 마켓 API({@code CommonMarketService})가 아직 참조하기 때문이다 — 그쪽 코드는
 * 보류 상태라 손대지 않는다.
 *
 * <p>새 화면에서 쓰지 말 것. 쇼룸 목록은 {@code ShowroomListItem}이다.
 */
@Getter
@NoArgsConstructor
public class MarketListResponse {
    private Long shopId;
    private String shopName;
    private String shopImageUrl;
    private Long mainCategoryId;
    private String mainCategoryName;
    private ShopType shopType;

    // JPQL 생성자 - s.roleType을 ShopType으로 변환
    public MarketListResponse(Long shopId, String shopName, String shopImageUrl,
                               Long mainCategoryId, String mainCategoryName, RoleType roleType) {
        this.shopId = shopId;
        this.shopName = shopName;
        this.shopImageUrl = shopImageUrl;
        this.mainCategoryId = mainCategoryId;
        this.mainCategoryName = mainCategoryName;
        this.shopType = roleType == RoleType.CREATOR ? ShopType.SHOWROOM : ShopType.MARKET;
    }
}
