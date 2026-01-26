package showroomz.api.admin.product.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class AdminProductDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상품 추천 상태 변경 요청")
    public static class UpdateRecommendationRequest {
        @NotNull(message = "추천 여부는 필수 입력값입니다.")
        @Schema(description = "추천 여부", example = "true")
        private Boolean isRecommended;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 추천 상태 변경 응답")
    public static class UpdateRecommendationResponse {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "상품 번호", example = "SRZ-20251228-001")
        private String productNumber;

        @Schema(description = "추천 여부", example = "true")
        private Boolean isRecommended;

        @Schema(description = "응답 메시지", example = "상품 추천 상태가 성공적으로 변경되었습니다.")
        private String message;
    }
}
