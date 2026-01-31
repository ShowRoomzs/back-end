package showroomz.api.app.market.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.market.type.ShopType;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowingMarketResponse {
    private Long shopId;
    private String shopName;
    private String shopImageUrl;
    private ShopType shopType;
}
