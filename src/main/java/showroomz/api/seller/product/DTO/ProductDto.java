package showroomz.api.seller.product.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

import org.springframework.data.domain.Page;
import showroomz.api.common.product.dto.ProductProcessingHistoryDto;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGender;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.domain.product.type.ProductHideReasonType;
import showroomz.global.dto.PageResponse;

public class ProductDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상품 등록 요청")
    public static class CreateProductRequest {

        @NotNull(message = "카테고리 ID는 필수 입력값입니다.")
        @Schema(description = "카테고리 ID", example = "1")
        private Long categoryId;

        @NotBlank(message = "상품명은 필수 입력값입니다.")
        @Size(max = 255, message = "상품명은 최대 255자까지 입력 가능합니다.")
        @Schema(description = "상품명", example = "프리미엄 린넨 셔츠")
        private String name;

        @Schema(description = "판매자 상품 코드", example = "PROD-001")
        private String sellerProductCode;

        @NotNull(message = "판매가는 필수 입력값입니다.")
        @Min(value = 0, message = "판매가는 0 이상이어야 합니다.")
        @Schema(description = "판매가 (정가)", example = "59000")
        private Integer regularPrice;

        @Schema(description = "대표 이미지 URL", example = "https://example.com/image.jpg")
        private String representativeImageUrl;

        @Schema(description = "커버 이미지 URL 목록 (최대 4개)", example = "[\"https://example.com/image1.jpg\", \"https://example.com/image2.jpg\"]")
        @Size(max = 4, message = "커버 이미지는 최대 4개까지 등록 가능합니다.")
        private List<String> coverImageUrls;

        @Schema(description = "에디터 상세 설명 (HTML)", example = "<p>상품 상세 설명</p>")
        private String description;

        @Schema(description = "인스타그램 Embed 태그", example = "<blockquote>...</blockquote>")
        private String instagramEmbedTag;

        @Valid
        @Schema(description = "상품정보제공고시")
        private ProductNoticeRequest productNotice;

        @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.")
        @Schema(
                description = "재고 수량. 옵션(optionGroups/variants) 없이 단일 재고만 등록할 때 사용. " +
                        "variants를 보내지 않으면 이 값으로 단일 옵션 없는 상품이 생성됩니다. (미입력 시 0)",
                example = "999"
        )
        private Integer stock;

        @Valid
        @Schema(description = "옵션 그룹 목록. 옵션 없는 상품은 생략하거나 빈 배열")
        private List<OptionGroupRequest> optionGroups;

        @Valid
        @Schema(description = "옵션 조합(Variant) 목록. 옵션 없는 상품은 생략하고 stock만 입력")
        private List<VariantRequest> variants;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상품정보제공고시")
    public static class ProductNoticeRequest {
        @Schema(description = "용량·중량", example = "제품 상세 참고")
        private String capacityWeight;

        @Schema(description = "제품 주요 사양", example = "제품 상세 참고")
        private String mainSpecs;

        @Schema(description = "사용기한·개봉 후 사용기간", example = "제품 상세 참고")
        private String expirationPeriod;

        @Schema(description = "사용방법", example = "제품 상세 참고")
        private String usageMethod;

        @Schema(description = "화장품제조업자·책임판매업자", example = "제품 상세 참고")
        private String manufacturerSeller;

        @Schema(description = "제조국", example = "제품 상세 참고")
        private String origin;

        @Schema(description = "전성분", example = "제품 상세 참고")
        private String ingredients;

        @Schema(description = "기능성 화장품 식품의약품안전처 심사필 여부", example = "제품 상세 참고")
        private String functionalCosmeticApproval;

        @Schema(description = "사용 시 주의사항", example = "제품 상세 참고")
        private String precautions;

        @Schema(description = "품질보증기준", example = "제품 상세 참고")
        private String qualityAssurance;

        @Schema(description = "소비자상담 전화번호", example = "제품 상세 참고")
        private String customerServicePhone;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "옵션 그룹")
    public static class OptionGroupRequest {
        @NotBlank(message = "옵션 그룹명은 필수 입력값입니다.")
        @Schema(description = "옵션 그룹명", example = "사이즈")
        private String name;

        @NotEmpty(message = "옵션 목록은 필수 입력값입니다.")
        @Valid
        @Schema(description = "옵션 목록")
        private List<OptionRequest> options;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "옵션 요청")
    public static class OptionRequest {
        @NotBlank(message = "옵션명은 필수 입력값입니다.")
        @Schema(description = "옵션명", example = "S")
        private String name;

        @Schema(description = "옵션 가격 (추가 가격)", example = "0")
        @Builder.Default
        private Integer price = 0;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "옵션 조합 (Variant)")
    public static class VariantRequest {
        @NotEmpty(message = "옵션명 목록은 필수 입력값입니다.")
        @Schema(description = "조합된 옵션명", example = "[\"S\", \"Black\"]")
        private List<String> optionNames;

        @NotNull(message = "판매가는 필수 입력값입니다.")
        @Min(value = 0, message = "판매가는 0 이상이어야 합니다.")
        @Schema(description = "판매가 (옵션가 포함)", example = "50000")
        private Integer regularPrice;

        @NotNull(message = "재고 수량은 필수 입력값입니다.")
        @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.")
        @Schema(description = "재고 수량", example = "100")
        private Integer stock;

        @Schema(description = "대표 옵션 여부", example = "true")
        private Boolean isRepresentative = false;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 등록 응답")
    public static class CreateProductResponse {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "상품 번호", example = "SRZ-20251228-001")
        private String productNumber;

        @Schema(description = "응답 메시지", example = "상품이 성공적으로 등록되었습니다.")
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상품 수정 요청")
    public static class UpdateProductRequest {

        @Schema(description = "카테고리 ID", example = "1")
        private Long categoryId;

        @Size(max = 255, message = "상품명은 최대 255자까지 입력 가능합니다.")
        @Schema(description = "상품명", example = "프리미엄 린넨 셔츠")
        private String name;

        @Schema(description = "판매자 상품 코드", example = "PROD-001")
        private String sellerProductCode;

        @Schema(description = "진열 상태", example = "DISPLAY",
                allowableValues = {"DISPLAY", "HIDDEN", "PENDING_REVIEW", "HIDE_REQUEST"})
        private ProductDisplayStatus displayStatus;

        @Min(value = 0, message = "판매가는 0 이상이어야 합니다.")
        @Schema(description = "판매가 (정가)", example = "59000")
        private Integer regularPrice;

        @Schema(description = "성별", example = "UNISEX", allowableValues = {"MALE", "FEMALE", "UNISEX"})
        private ProductGender gender;

        @Schema(description = "대표 이미지 URL", example = "https://example.com/image.jpg")
        private String representativeImageUrl;

        @Schema(description = "커버 이미지 URL 목록 (최대 4개)", example = "[\"https://example.com/image1.jpg\", \"https://example.com/image2.jpg\"]")
        @Size(max = 4, message = "커버 이미지는 최대 4개까지 등록 가능합니다.")
        private List<String> coverImageUrls;

        @Schema(description = "에디터 상세 설명 (HTML)", example = "<p>상품 상세 설명</p>")
        private String description;

        @Valid
        @Schema(description = "상품정보제공고시")
        private ProductNoticeRequest productNotice;

        @Valid
        @Schema(description = "옵션 그룹 목록")
        private List<OptionGroupRequest> optionGroups;

        @Valid
        @Schema(description = "옵션 목록 (조합된 결과)")
        private List<VariantRequest> variants;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 수정 응답")
    public static class UpdateProductResponse {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "상품 번호", example = "SRZ-20251228-001")
        private String productNumber;

        @Schema(description = "응답 메시지", example = "상품이 성공적으로 수정되었습니다.")
        private String message;
    }

    @Getter
    @Schema(description = "상품 목록 조회 응답 (글로벌 PageResponse + 진열 상태별 건수)")
    public static class ProductListResponse extends PageResponse<ProductListItem> {

        @Schema(description = "진열 상태별 상품 건수 (검색어·공구상태 반영, 진열상태 필터 미반영)")
        private final DisplayStatusCounts displayStatusCounts;

        public ProductListResponse(
                List<ProductListItem> content,
                Page<?> page,
                DisplayStatusCounts displayStatusCounts) {
            super(content, page);
            this.displayStatusCounts = displayStatusCounts;
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
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 목록 항목")
    public static class ProductListItem {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "상품 번호", example = "SRZ-20251228-001")
        private String productNumber;

        @Schema(description = "판매자 상품 코드", example = "PROD-ABC-001")
        private String sellerProductCode;

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

        @Schema(description = "공구 상태 (더미). PREPARING: 준비중, READY: 준비완료, IN_PROGRESS: 진행중, NOT_CONNECTED: 연결없음",
                example = "PREPARING",
                allowableValues = {"PREPARING", "READY", "IN_PROGRESS", "NOT_CONNECTED"})
        private ProductGroupBuyStatus groupBuyStatus;

        @Schema(description = "재고 수량 (옵션 재고 합계)", example = "100")
        private Integer stock;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "가격 정보")
    public static class PriceInfo {
        @Schema(description = "판매가", example = "59000")
        private Integer regularPrice;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 상세 조회 응답 (Product 엔티티의 모든 컬럼)")
    public static class ProductDetailResponse {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "상품 번호", example = "SRZ-20251228-001")
        private String productNumber;

        @Schema(description = "마켓 ID", example = "1")
        private Long marketId;

        @Schema(description = "마켓명", example = "프리미엄 쇼핑몰")
        private String marketName;

        @Schema(description = "카테고리 ID", example = "1")
        private Long categoryId;

        @Schema(description = "카테고리명", example = "의류")
        private String categoryName;

        @Schema(description = "상품명", example = "프리미엄 린넨 셔츠")
        private String name;

        @Schema(description = "판매자 상품 코드", example = "PROD-ABC-001")
        private String sellerProductCode;

        @Schema(description = "대표 이미지 URL (타이틀/썸네일 이미지)", example = "https://example.com/image.jpg")
        private String representativeImageUrl;

        @Schema(description = "커버 이미지 URL 목록", example = "[\"https://example.com/image1.jpg\", \"https://example.com/image2.jpg\"]")
        private List<String> coverImageUrls;

        @Schema(description = "판매가 (정가)", example = "59000")
        private Integer regularPrice;

        @Schema(description = "성별", example = "UNISEX", allowableValues = {"MALE", "FEMALE", "UNISEX"})
        private ProductGender gender;

        @Schema(description = "진열 상태", example = "DISPLAY",
                allowableValues = {"DISPLAY", "HIDDEN", "PENDING_REVIEW", "HIDE_REQUEST"})
        private ProductDisplayStatus displayStatus;

        @Schema(description = "공구 상태 (더미). PREPARING: 준비중, READY: 준비완료, IN_PROGRESS: 진행중, NOT_CONNECTED: 연결없음",
                example = "PREPARING",
                allowableValues = {"PREPARING", "READY", "IN_PROGRESS", "NOT_CONNECTED"})
        private ProductGroupBuyStatus groupBuyStatus;

        @Schema(description = "미진열 사유 타입", example = "PRODUCT_NOTICE_ERROR",
                allowableValues = {"PRODUCT_NOTICE_ERROR", "AD_DISPLAY_VIOLATION", "BRAND_REQUEST", "OTHER"})
        private ProductHideReasonType hideReasonType;

        @Schema(description = "미진열 상세 사유", example = "성분 표기 누락")
        private String hideDetail;

        @Schema(description = "최근 미진열 정보 (displayStatus=HIDDEN일 때만, 가장 최근 HIDDEN 이력 기준)")
        private ProductProcessingHistoryDto.LatestHideInfo latestHideInfo;

        @Schema(description = "추천 상품 여부", example = "false")
        private Boolean isRecommended;

        @Schema(description = "상품정보제공고시 (JSON 문자열)", example = "{\"origin\":\"한국\",\"ingredients\":\"제품 상세 참고\"}")
        private String productNotice;

        @Schema(description = "상품 상세 설명 (HTML)", example = "<p>상품 상세 설명</p>")
        private String description;

        @Schema(description = "등록일", example = "2025-12-28T14:30:00Z")
        private String createdAt;

        @Schema(description = "옵션 그룹 목록")
        private List<OptionGroupInfo> optionGroups;

        @Schema(description = "옵션 조합 (Variant) 목록")
        private List<VariantInfo> variants;

        @Schema(description = "처리 이력 (브랜드/어드민 공유, 최신순). "
                + "historyType: PRODUCT_CREATED(상품 등록), PRODUCT_INFO_UPDATED(브랜드가 상품 정보 수정), "
                + "STOCK_UPDATED(재고 수량 수정), HIDDEN(미진열 처리), REDISPLAYED(다시 진열), "
                + "HIDE_REQUESTED(미진열 요청), PENDING_REVIEW(재검토 대기)")
        private List<ProductProcessingHistoryDto.HistoryItem> processingHistory;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 이미지 정보")
    public static class ProductImageInfo {
        @Schema(description = "이미지 ID", example = "1")
        private Long imageId;

        @Schema(description = "이미지 URL", example = "https://example.com/image.jpg")
        private String url;

        @Schema(description = "순서", example = "0")
        private Integer order;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "옵션 그룹 정보")
    public static class OptionGroupInfo {
        @Schema(description = "옵션 그룹 ID", example = "1")
        private Long optionGroupId;

        @Schema(description = "옵션 그룹명", example = "사이즈")
        private String name;

        @Schema(description = "옵션 목록")
        private List<OptionInfo> options;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "옵션 정보")
    public static class OptionInfo {
        @Schema(description = "옵션 ID", example = "1")
        private Long optionId;

        @Schema(description = "옵션명", example = "S")
        private String name;

        @Schema(description = "옵션 가격 (추가 가격)", example = "0")
        private Integer price;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "옵션 조합 (Variant) 정보")
    public static class VariantInfo {
        @Schema(description = "Variant ID", example = "1")
        private Long variantId;

        @Schema(description = "옵션 조합명", example = "S, Black")
        private String name;

        @Schema(description = "판매가 (옵션가 포함)", example = "50000")
        private Integer regularPrice;

        @Schema(description = "재고 수량", example = "100")
        private Integer stock;

        @Schema(description = "대표 옵션 여부", example = "true")
        private Boolean isRepresentative;

        @Schema(description = "옵션 ID 목록", example = "[1, 2]")
        private List<Long> optionIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "페이지네이션 정보")
    public static class PaginationInfo {
        @Schema(description = "현재 페이지", example = "1")
        private Integer currentPage;

        @Schema(description = "전체 페이지 수", example = "10")
        private Integer totalPages;

        @Schema(description = "전체 결과 수", example = "485")
        private Long totalResults;

        @Schema(description = "페이지당 개수", example = "50")
        private Integer size;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 삭제 응답")
    public static class DeleteProductResponse {
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "응답 메시지", example = "상품이 성공적으로 삭제되었습니다.")
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "일괄 삭제 요청")
    public static class BatchDeleteRequest {
        @NotNull(message = "상품 ID 목록은 필수 입력값입니다.")
        @Size(min = 1, message = "최소 1개 이상의 상품 ID가 필요합니다.")
        @Schema(description = "삭제할 상품 ID 목록", example = "[1, 2, 3]")
        private List<Long> productIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "일괄 삭제 응답")
    public static class BatchDeleteResponse {
        @Schema(description = "삭제된 상품 ID 목록", example = "[1, 2, 3]")
        private List<Long> productIds;

        @Schema(description = "삭제된 상품 개수", example = "3")
        private Integer count;

        @Schema(description = "응답 메시지", example = "3개의 상품이 성공적으로 삭제되었습니다.")
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "일괄 처리 요청 (품절 처리, 미진열 처리)")
    public static class BatchUpdateRequest {
        @NotNull(message = "상품 ID 목록은 필수 입력값입니다.")
        @Size(min = 1, message = "최소 1개 이상의 상품 ID가 필요합니다.")
        @Schema(description = "처리할 상품 ID 목록", example = "[1, 2, 3]")
        private List<Long> productIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "일괄 품절 상태 변경 요청")
    public static class BatchStockStatusRequest {
        @NotNull(message = "상품 ID 목록은 필수 입력값입니다.")
        @Size(min = 1, message = "최소 1개 이상의 상품 ID가 필요합니다.")
        @Schema(description = "처리할 상품 ID 목록", example = "[1, 2, 3]")
        private List<Long> productIds;

        @NotNull(message = "품절 상태는 필수 입력값입니다.")
        @Schema(description = "품절 상태 (true: 품절 처리, false: 품절 해제)", example = "true")
        private Boolean isOutOfStocked;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "일괄 진열 상태 변경 요청")
    public static class BatchDisplayStatusRequest {
        @NotNull(message = "상품 ID 목록은 필수 입력값입니다.")
        @Size(min = 1, message = "최소 1개 이상의 상품 ID가 필요합니다.")
        @Schema(description = "처리할 상품 ID 목록", example = "[1, 2, 3]")
        private List<Long> productIds;

        @NotNull(message = "진열 상태는 필수 입력값입니다.")
        @Schema(description = "진열 상태 (DISPLAY: 진열, HIDDEN: 미진열, PENDING_REVIEW: 재검토 대기, HIDE_REQUEST: 미진열 요청)",
                example = "HIDDEN",
                allowableValues = {"DISPLAY", "HIDDEN", "PENDING_REVIEW", "HIDE_REQUEST"})
        private ProductDisplayStatus displayStatus;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "일괄 처리 응답")
    public static class BatchUpdateResponse {
        @Schema(description = "처리된 상품 ID 목록", example = "[1, 2, 3]")
        private List<Long> productIds;

        @Schema(description = "처리된 상품 개수", example = "3")
        private Integer count;

        @Schema(description = "응답 메시지", example = "3개의 상품이 성공적으로 품절 처리되었습니다.")
        private String message;
    }
}

