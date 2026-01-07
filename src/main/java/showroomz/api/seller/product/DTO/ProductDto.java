package showroomz.api.seller.product.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

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

        @Schema(description = "진열 상태", example = "true")
        private Boolean isDisplay = true;

        @Schema(description = "강제 품절 처리 여부", example = "false")
        private Boolean isOutOfStockForced = false;

        @Min(value = 0, message = "매입가는 0 이상이어야 합니다.")
        @Schema(description = "매입가", example = "30000")
        private Integer purchasePrice;

        @NotNull(message = "판매가(할인 전)는 필수 입력값입니다.")
        @Min(value = 0, message = "판매가는 0 이상이어야 합니다.")
        @Schema(description = "판매가 (할인 전)", example = "59000")
        private Integer regularPrice;

        @NotNull(message = "할인 판매가는 필수 입력값입니다.")
        @Min(value = 0, message = "할인 판매가는 0 이상이어야 합니다.")
        @Schema(description = "할인 판매가 (최종가)", example = "49000")
        private Integer salePrice;

        @Schema(description = "할인 설정 여부", example = "true")
        private Boolean isDiscount = false;

        @Schema(description = "대표 이미지 URL", example = "https://example.com/image.jpg")
        private String representativeImageUrl;

        @Schema(description = "커버 이미지 URL 목록 (최대 4개)", example = "[\"https://example.com/image1.jpg\", \"https://example.com/image2.jpg\"]")
        @Size(max = 4, message = "커버 이미지는 최대 4개까지 등록 가능합니다.")
        private List<String> coverImageUrls;

        @Schema(description = "에디터 상세 설명 (HTML)", example = "<p>상품 상세 설명</p>")
        private String description;

        @Schema(description = "인스타그램 Embed 태그", example = "<blockquote>...</blockquote>")
        private String instagramEmbedTag;

        @Schema(description = "태그 목록", example = "[\"신상\", \"할인\", \"인기\"]")
        private List<String> tags;

        @Schema(description = "배송 유형", example = "STANDARD")
        private String deliveryType;

        @Min(value = 0, message = "배송비는 0 이상이어야 합니다.")
        @Schema(description = "배송비", example = "3000")
        private Integer deliveryFee;

        @Min(value = 0, message = "무료 배송 최소 금액은 0 이상이어야 합니다.")
        @Schema(description = "무료 배송 최소 금액", example = "50000")
        private Integer deliveryFreeThreshold;

        @Min(value = 1, message = "배송 예상 일수는 1 이상이어야 합니다.")
        @Schema(description = "배송 예상 일수", example = "3")
        private Integer deliveryEstimatedDays;

        @Valid
        @Schema(description = "상품정보제공고시")
        private ProductNoticeRequest productNotice;

        @Valid
        @Schema(description = "옵션 그룹 목록")
        private List<OptionGroupRequest> optionGroups;

        @Valid
        @NotNull(message = "옵션 목록은 필수 입력값입니다.")
        @Schema(description = "옵션 목록 (조합된 결과)")
        private List<VariantRequest> variants;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상품정보제공고시")
    public static class ProductNoticeRequest {
        @Schema(description = "제조국", example = "제품 상세 참고")
        private String origin;

        @Schema(description = "소재", example = "제품 상세 참고")
        private String material;

        @Schema(description = "색상", example = "제품 상세 참고")
        private String color;

        @Schema(description = "치수", example = "제품 상세 참고")
        private String size;

        @Schema(description = "제조자", example = "제품 상세 참고")
        private String manufacturer;

        @Schema(description = "세탁 방법", example = "제품 상세 참고")
        private String washingMethod;

        @Schema(description = "제조연월", example = "제품 상세 참고")
        private String manufactureDate;

        @Schema(description = "A/S 정보", example = "제품 상세 참고")
        private String asInfo;

        @Schema(description = "품질 보증 기준", example = "제품 상세 참고")
        private String qualityAssurance;
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
        @Schema(description = "옵션 목록", example = "[\"S\", \"M\", \"L\"]")
        private List<String> options;
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
        @Schema(description = "옵션가 포함 최종가", example = "50000")
        private Integer salePrice;

        @NotNull(message = "재고 수량은 필수 입력값입니다.")
        @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.")
        @Schema(description = "재고 수량", example = "100")
        private Integer stock;

        @Schema(description = "진열 여부", example = "true")
        private Boolean isDisplay = true;

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
    @Builder
    @Schema(description = "상품 목록 조회 응답")
    public static class ProductListResponse {
        @Schema(description = "상품 목록")
        private List<ProductListItem> products;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 목록 항목")
    public static class ProductListItem {
        @JsonProperty("product_id")
        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @JsonProperty("product_number")
        @Schema(description = "상품 번호", example = "SRZ-20251228-001")
        private String productNumber;

        @JsonProperty("seller_product_code")
        @Schema(description = "판매자 상품 코드", example = "PROD-ABC-001")
        private String sellerProductCode;

        @JsonProperty("thumbnail_url")
        @Schema(description = "썸네일 URL", example = "https://example.com/thumbnail.jpg")
        private String thumbnailUrl;

        @Schema(description = "상품명", example = "프리미엄 린넨 셔츠")
        private String name;

        @Schema(description = "가격 정보")
        private PriceInfo price;

        @JsonProperty("created_at")
        @Schema(description = "등록일", example = "2025-12-28T14:30:00Z")
        private String createdAt;

        @JsonProperty("display_status")
        @Schema(description = "진열 상태", example = "DISPLAY")
        private String displayStatus;

        @JsonProperty("stock_status")
        @Schema(description = "품절 상태", example = "IN_STOCK")
        private String stockStatus;

        @JsonProperty("is_out_of_stock_forced")
        @Schema(description = "강제 품절 처리 여부", example = "false")
        private Boolean isOutOfStockForced;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "가격 정보")
    public static class PriceInfo {
        @JsonProperty("purchase_price")
        @Schema(description = "매입가", example = "25000")
        private Integer purchasePrice;

        @JsonProperty("regular_price")
        @Schema(description = "판매가", example = "59000")
        private Integer regularPrice;

        @JsonProperty("sale_price")
        @Schema(description = "할인 판매가", example = "49000")
        private Integer salePrice;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "페이지네이션 정보")
    public static class PaginationInfo {
        @JsonProperty("current_page")
        @Schema(description = "현재 페이지", example = "1")
        private Integer currentPage;

        @JsonProperty("total_pages")
        @Schema(description = "전체 페이지 수", example = "10")
        private Integer totalPages;

        @JsonProperty("total_results")
        @Schema(description = "전체 결과 수", example = "485")
        private Long totalResults;

        @Schema(description = "페이지당 개수", example = "50")
        private Integer limit;
    }
}

