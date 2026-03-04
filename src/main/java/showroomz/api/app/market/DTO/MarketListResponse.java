package showroomz.api.app.market.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.market.type.ShopType;

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
