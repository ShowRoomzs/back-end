package showroomz.api.admin.product.inspection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.product.type.ProductGender;
import showroomz.domain.product.type.ProductInspectionStatus;
import showroomz.domain.product.type.ProductRejectReasonType;

import java.util.List;

public class AdminProductInspectionDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "관리자 검수 목록 항목")
    public static class ListItem {
        private Long productId;
        private String productNumber;
        private String name;
        private String thumbnailUrl;
        private Long marketId;
        private String marketName;
        private ProductInspectionStatus inspectionStatus;
        private String createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "마켓 요약 (관리자 검수 상세)")
    public static class MarketSummary {
        private Long marketId;
        private String marketName;
        private String csNumber;
        private String sellerName;
        private String sellerPhone;
        private String sellerEmail;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "검수 이력 타임라인 항목")
    public static class HistoryItem {
        private Long historyId;
        private ProductInspectionStatus previousStatus;
        private ProductInspectionStatus newStatus;
        private ProductRejectReasonType rejectReasonType;
        private String rejectDetail;
        private String createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "검수 상세 (상품 + 마켓 + 이력)")
    public static class InspectionDetailResponse {
        private ProductDetail product;
        private MarketSummary market;
        private List<HistoryItem> inspectionHistory;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 상세(검수용)")
    public static class ProductDetail {
        private Long productId;
        private String productNumber;
        private Long marketId;
        private String marketName;
        private Long categoryId;
        private String categoryName;
        private String name;
        private String sellerProductCode;
        private String representativeImageUrl;
        private List<String> coverImageUrls;
        private Integer regularPrice;
        private Integer salePrice;
        private ProductGender gender;
        private Integer purchasePrice;
        private Boolean isDisplay;
        private Boolean isOutOfStockForced;
        private Boolean isRecommended;
        private String productNotice;
        private String description;
        private String tags;
        private String deliveryType;
        private Integer deliveryFee;
        private Integer deliveryFreeThreshold;
        private Integer deliveryEstimatedDays;
        private String createdAt;
        private ProductInspectionStatus inspectionStatus;
        private String adminMemo;
        private ProductRejectReasonType rejectReasonType;
        private String rejectDetail;
        private List<showroomz.api.seller.product.DTO.ProductDto.OptionGroupInfo> optionGroups;
        private List<showroomz.api.seller.product.DTO.ProductDto.VariantInfo> variants;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "단건 검수 상태 변경")
    public static class UpdateStatusRequest {
        @NotNull
        @Schema(description = "변경할 검수 상태", example = "APPROVED")
        private ProductInspectionStatus inspectionStatus;

        @Size(max = 500)
        @Schema(description = "관리자 메모 (최대 500자)", example = "이미지 보완 요청")
        private String adminMemo;

        @Schema(description = "반려 사유 타입 (REJECTED일 때 필수)", example = "POLICY_VIOLATION")
        private ProductRejectReasonType rejectReasonType;

        @Size(max = 500)
        @Schema(description = "반려 상세 (rejectReasonType이 OTHER일 때 필수)", example = "정책 위반")
        private String rejectDetail;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "일괄 검수 상태 변경")
    public static class BulkUpdateStatusRequest {
        @NotEmpty
        @Schema(description = "상품 ID 목록", example = "[1,2,3]")
        private List<Long> productIds;

        @NotNull
        @Schema(description = "변경할 검수 상태", example = "APPROVED")
        private ProductInspectionStatus inspectionStatus;

        @Size(max = 500)
        @Schema(description = "관리자 메모 (동일 값으로 일괄 적용)")
        private String adminMemo;

        @Schema(description = "반려 사유 타입 (REJECTED일 때 필수)", example = "INFO_MISMATCH")
        private ProductRejectReasonType rejectReasonType;

        @Size(max = 500)
        @Schema(description = "반려 상세 (rejectReasonType이 OTHER일 때 필수)")
        private String rejectDetail;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "일괄 검수 처리 응답")
    public static class BulkUpdateStatusResponse {
        private List<Long> productIds;
        private int count;
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "단건 검수 상태 변경 응답")
    public static class UpdateStatusResponse {
        private Long productId;
        private ProductInspectionStatus inspectionStatus;
        private String message;
    }
}
