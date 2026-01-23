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
@Schema(description = "공통 마켓 상세 응답")
public class MarketDetailResponse {

    @Schema(description = "마켓 ID", example = "1")
    private Long marketId;

    @Schema(description = "마켓명", example = "쇼룸즈")
    private String marketName;

    @Schema(description = "마켓 대표 이미지 URL", example = "https://example.com/image.jpg")
    private String marketImageUrl;

    @Schema(description = "마켓 한줄 소개", example = "트렌디한 라이프스타일을 제안하는 마켓입니다.")
    private String marketDescription;

    @Schema(description = "마켓 URL", example = "https://www.showroomz.co.kr/shop/showroomz")
    private String marketUrl;

    @Schema(description = "대표 카테고리", example = "패션/의류")
    private String mainCategory;

    @Schema(description = "고객센터 번호", example = "1588-0000")
    private String csNumber;

    @Schema(description = "SNS 링크 목록 (최대 3개)")
    private List<SnsLinkResponse> snsLinks;

    // 팔로우 관련 정보
    @Schema(description = "이 마켓을 찜한 유저 수", example = "150")
    private long followerCount;

    @Schema(description = "현재 유저가 찜했는지 여부 (비로그인 시 false)", example = "true")
    private boolean isFollowed;

    @Getter
    @AllArgsConstructor
    @Schema(description = "SNS 링크 정보")
    public static class SnsLinkResponse {

        @Schema(description = "SNS 타입", example = "INSTAGRAM")
        private String snsType;

        @Schema(description = "SNS URL", example = "https://instagram.com/example")
        private String snsUrl;
    }
}


