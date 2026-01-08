package showroomz.api.app.market.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDetailResponse {
    private Long marketId;
    private String marketName;
    private String marketImageUrl;
    private String marketDescription;
    private String marketUrl;
    private String mainCategory;
    private String csNumber;
    
    // SNS 링크
    private String snsLink1;
    private String snsLink2;
    private String snsLink3;
    
    // 팔로우 관련 정보
    private long followerCount; // 이 마켓을 찜한 유저 수
    private boolean isFollowed; // 현재 유저가 찜했는지 여부
}

