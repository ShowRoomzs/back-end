package showroomz.api.app.market.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private Long mainCategoryId;
    private String mainCategoryName;
    private String csNumber;
    
    // SNS 링크 배열
    private List<SnsLinkResponse> snsLinks;
    
    // 팔로우 관련 정보
    private long followerCount; // 이 마켓을 찜한 유저 수
    private boolean isFollowed; // 현재 유저가 찜했는지 여부

    @Getter
    @AllArgsConstructor
    public static class SnsLinkResponse {
        @Schema(
                description = "SNS 종류 (INSTAGRAM, TIKTOK, X, YOUTUBE)",
                example = "INSTAGRAM",
                allowableValues = {"INSTAGRAM", "TIKTOK", "X", "YOUTUBE"}
        )
        private String snsType;
        private String snsUrl;
    }
}