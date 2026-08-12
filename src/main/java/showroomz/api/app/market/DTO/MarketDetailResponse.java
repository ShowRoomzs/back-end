package showroomz.api.app.market.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.market.type.ShopType;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDetailResponse {
    private Long shopId;
    private String shopName;
    private String shopImageUrl;
    private String shopDescription;
    private String shopUrl;
    private ShopType shopType;
    private Long mainCategoryId;
    private String mainCategoryName;

    // SNS 링크 배열
    private List<SnsLinkResponse> snsLinks;

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
