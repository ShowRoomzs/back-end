package showroomz.api.common.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductHideReasonType;
import showroomz.domain.product.type.ProductProcessingHistoryType;

import java.util.List;

public class ProductProcessingHistoryDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "미진열 사유 (사유 + 상세사유 묶음)")
    public static class HideReason {
        @Schema(description = "미진열 사유 타입", example = "PRODUCT_NOTICE_ERROR",
                allowableValues = {"PRODUCT_NOTICE_ERROR", "AD_DISPLAY_VIOLATION", "BRAND_REQUEST", "OTHER"})
        private ProductHideReasonType reasonType;

        @Schema(description = "미진열 사유 설명", example = "상품 정보 제공 고시 오류")
        private String reasonDescription;

        @Schema(description = "미진열 상세 사유 (선택)", example = "성분 표기 누락")
        private String detail;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 처리 이력 항목")
    public static class HistoryItem {
        @Schema(description = "이력 ID", example = "1")
        private Long historyId;

        @Schema(description = "이력 유형", example = "HIDDEN",
                allowableValues = {
                        "PRODUCT_CREATED", "PRODUCT_INFO_UPDATED", "STOCK_UPDATED",
                        "HIDDEN", "REDISPLAYED", "HIDE_REQUESTED", "PENDING_REVIEW"
                })
        private ProductProcessingHistoryType historyType;

        @Schema(description = "이력 표시 제목", example = "미진열 처리")
        private String title;

        @Schema(description = "변경 전 진열 상태")
        private ProductDisplayStatus previousDisplayStatus;

        @Schema(description = "변경 후 진열 상태")
        private ProductDisplayStatus newDisplayStatus;

        @Schema(description = "미진열 사유 (미진열 처리 시에만, 사유+상세 묶음)")
        private HideReason hideReason;

        @Schema(description = "재고 수량 (재고 수정 이력 시)", example = "120")
        private Integer stockQuantity;

        @Schema(description = "처리자 표시명 (어드민 처리 시 '이름 운영자')", example = "김운영 운영자")
        private String processorName;

        @Schema(description = "처리 시각 (ISO-8601)", example = "2026-06-20T11:20:00Z")
        private String createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 처리 이력 목록 응답")
    public static class HistoryListResponse {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "처리 이력 (최신순)")
        private List<HistoryItem> processingHistory;
    }
}
