package showroomz.api.admin.product.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.domain.product.type.ProductHideReasonType;
import showroomz.global.dto.PageResponse;

import java.util.List;

public class AdminProductDto {

    @Getter
    @Schema(description = "관리자 상품 목록 조회 응답 (글로벌 PageResponse + 진열/공구 상태별 건수)")
    public static class ProductListResponse extends PageResponse<ProductListItem> {

        @Schema(description = "진열 상태별 상품 건수 (검색어·공구상태 반영, 진열상태 필터 미반영)")
        private final DisplayStatusCounts displayStatusCounts;

        @Schema(description = "공구 상태별 상품 건수 (검색어·진열상태 반영, 공구상태 필터 미반영)")
        private final GroupBuyStatusCounts groupBuyStatusCounts;

        public ProductListResponse(
                List<ProductListItem> content,
                Page<?> page,
                DisplayStatusCounts displayStatusCounts,
                GroupBuyStatusCounts groupBuyStatusCounts) {
            super(content, page);
            this.displayStatusCounts = displayStatusCounts;
            this.groupBuyStatusCounts = groupBuyStatusCounts;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "진열 상태별 상품 건수")
    public static class DisplayStatusCounts {
        @Schema(description = "전체 건수", example = "195")
        private long all;

        @Schema(description = "진열 건수", example = "120")
        private long display;

        @Schema(description = "미진열 건수", example = "40")
        private long hidden;

        @Schema(description = "재검토 대기 건수", example = "20")
        private long pendingReview;

        @Schema(description = "미진열 요청 건수", example = "15")
        private long hideRequest;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "공구 상태별 상품 건수")
    public static class GroupBuyStatusCounts {
        @Schema(description = "전체 건수", example = "195")
        private long all;

        @Schema(description = "준비중 건수", example = "0")
        private long preparing;

        @Schema(description = "준비완료 건수", example = "0")
        private long ready;

        @Schema(description = "진행중 건수", example = "0")
        private long inProgress;

        @Schema(description = "연결없음 건수", example = "195")
        private long notConnected;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "관리자 상품 목록 항목")
    public static class ProductListItem {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "상품 번호", example = "SRZ-20251228-001")
        private String productNumber;

        @Schema(description = "판매자 상품 코드", example = "PROD-ABC-001")
        private String sellerProductCode;

        @Schema(description = "마켓(브랜드)명", example = "프리미엄 쇼핑몰")
        private String marketName;

        @Schema(description = "썸네일 URL", example = "https://example.com/thumbnail.jpg")
        private String thumbnailUrl;

        @Schema(description = "상품명", example = "프리미엄 린넨 셔츠")
        private String name;

        @Schema(description = "판매가", example = "59000")
        private Integer regularPrice;

        @Schema(description = "등록일", example = "2025-12-28T14:30:00Z")
        private String createdAt;

        @Schema(description = "수정일", example = "2026-01-05T10:00:00Z")
        private String modifiedAt;

        @Schema(description = "진열 상태 (DISPLAY: 진열, HIDDEN: 미진열, PENDING_REVIEW: 재검토 대기, HIDE_REQUEST: 미진열 요청)",
                example = "DISPLAY",
                allowableValues = {"DISPLAY", "HIDDEN", "PENDING_REVIEW", "HIDE_REQUEST"})
        private ProductDisplayStatus displayStatus;

        @Schema(description = "공구 상태. PREPARING: 준비중, READY: 준비완료, IN_PROGRESS: 진행중, NOT_CONNECTED: 연결없음",
                example = "NOT_CONNECTED",
                allowableValues = {"PREPARING", "READY", "IN_PROGRESS", "NOT_CONNECTED"})
        private ProductGroupBuyStatus groupBuyStatus;

        @Schema(description = "재고 수량 (옵션 재고 합계)", example = "100")
        private Integer stock;
    }


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
