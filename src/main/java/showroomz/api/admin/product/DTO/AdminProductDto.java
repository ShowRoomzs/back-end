package showroomz.api.admin.product.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductHideReasonType;

import java.util.List;

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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "관리자 상품 진열 상태 변경 요청")
    public static class UpdateDisplayStatusRequest {
        @NotNull(message = "진열 상태는 필수 입력값입니다.")
        @Schema(description = "변경할 진열 상태 (HIDDEN: 미진열 처리, DISPLAY: 다시 진열)",
                example = "HIDDEN",
                allowableValues = {"HIDDEN", "DISPLAY"})
        private ProductDisplayStatus displayStatus;

        @Schema(description = "미진열 사유 (displayStatus=HIDDEN 시 필수)",
                example = "PRODUCT_NOTICE_ERROR",
                allowableValues = {"PRODUCT_NOTICE_ERROR", "AD_DISPLAY_VIOLATION", "BRAND_REQUEST", "OTHER"})
        private ProductHideReasonType hideReasonType;

        @Schema(description = "미진열 상세 사유 (선택)", example = "성분 표기 누락")
        private String hideDetail;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "관리자 상품 진열 상태 변경 응답")
    public static class UpdateDisplayStatusResponse {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "상품 번호", example = "SRZ-20251228-001")
        private String productNumber;

        @Schema(description = "변경된 진열 상태", example = "HIDDEN")
        private ProductDisplayStatus displayStatus;

        @Schema(description = "미진열 사유 타입", example = "PRODUCT_NOTICE_ERROR")
        private ProductHideReasonType hideReasonType;

        @Schema(description = "미진열 상세 사유", example = "성분 표기 누락")
        private String hideDetail;

        @Schema(description = "응답 메시지", example = "상품이 미진열 처리되었습니다.")
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "관리자 상품 진열 상태 일괄 변경 요청")
    public static class BulkUpdateDisplayStatusRequest {
        @NotNull(message = "상품 ID 목록은 필수입니다.")
        @Schema(description = "상품 ID 목록", example = "[1, 2, 3]")
        private List<Long> productIds;

        @NotNull(message = "진열 상태는 필수 입력값입니다.")
        @Schema(description = "변경할 진열 상태 (HIDDEN: 미진열 처리, DISPLAY: 다시 진열)",
                example = "HIDDEN",
                allowableValues = {"HIDDEN", "DISPLAY"})
        private ProductDisplayStatus displayStatus;

        @Schema(description = "미진열 사유 (displayStatus=HIDDEN 시 필수)",
                example = "PRODUCT_NOTICE_ERROR",
                allowableValues = {"PRODUCT_NOTICE_ERROR", "AD_DISPLAY_VIOLATION", "BRAND_REQUEST", "OTHER"})
        private ProductHideReasonType hideReasonType;

        @Schema(description = "미진열 상세 사유 (선택)", example = "성분 표기 누락")
        private String hideDetail;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "관리자 상품 진열 상태 일괄 변경 응답")
    public static class BulkUpdateDisplayStatusResponse {
        @Schema(description = "처리된 상품 ID 목록")
        private List<Long> productIds;

        @Schema(description = "처리 건수", example = "3")
        private Integer count;

        @Schema(description = "변경된 진열 상태", example = "HIDDEN")
        private ProductDisplayStatus displayStatus;

        @Schema(description = "응답 메시지", example = "3개 상품이 미진열 처리되었습니다.")
        private String message;
    }
}
