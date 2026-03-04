package showroomz.api.app.market.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.app.auth.entity.RoleType;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowingMarketResponse {
    private Long shopId;
    private String shopName;
    private String shopImageUrl;
    private RoleType shopType;
}
