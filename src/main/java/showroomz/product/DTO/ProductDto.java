package showroomz.product.DTO;

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

        @NotBlank(message = "카테고리명은 필수 입력값입니다.")
        @JsonProperty("category_name")
        @Schema(description = "카테고리명", example = "옷")
        private String categoryName;

        @NotBlank(message = "상품명은 필수 입력값입니다.")
        @Size(max = 255, message = "상품명은 최대 255자까지 입력 가능합니다.")
        @Schema(description = "상품명", example = "프리미엄 린넨 셔츠")
        private String name;

        @JsonProperty("seller_product_code")
        @Schema(description = "판매자 상품 코드", example = "PROD-001")
        private String sellerProductCode;

        @JsonProperty("is_display")
        @Schema(description = "진열 상태", example = "true")
        private Boolean isDisplay = true;

        @JsonProperty("is_out_of_stock_forced")
        @Schema(description = "강제 품절 처리 여부", example = "false")
        private Boolean isOutOfStockForced = false;

        @JsonProperty("purchase_price")
        @Min(value = 0, message = "매입가는 0 이상이어야 합니다.")
        @Schema(description = "매입가", example = "30000")
        private Integer purchasePrice;

        @JsonProperty("regular_price")
        @NotNull(message = "판매가(할인 전)는 필수 입력값입니다.")
        @Min(value = 0, message = "판매가는 0 이상이어야 합니다.")
        @Schema(description = "판매가 (할인 전)", example = "59000")
        private Integer regularPrice;

        @JsonProperty("sale_price")
        @NotNull(message = "할인 판매가는 필수 입력값입니다.")
        @Min(value = 0, message = "할인 판매가는 0 이상이어야 합니다.")
        @Schema(description = "할인 판매가 (최종가)", example = "49000")
        private Integer salePrice;

        @JsonProperty("is_discount")
        @Schema(description = "할인 설정 여부", example = "true")
        private Boolean isDiscount = false;

        @JsonProperty("representative_image_url")
        @Schema(description = "대표 이미지 URL", example = "https://example.com/image.jpg")
        private String representativeImageUrl;

        @JsonProperty("cover_image_urls")
        @Schema(description = "커버 이미지 URL 목록 (최대 4개)", example = "[\"https://example.com/image1.jpg\", \"https://example.com/image2.jpg\"]")
        @Size(max = 4, message = "커버 이미지는 최대 4개까지 등록 가능합니다.")
        private List<String> coverImageUrls;

        @Schema(description = "에디터 상세 설명 (HTML)", example = "<p>상품 상세 설명</p>")
        private String description;

        @Schema(description = "인스타그램 Embed 태그", example = "<blockquote>...</blockquote>")
        private String instagramEmbedTag;

        @JsonProperty("tags")
        @Schema(description = "태그 목록", example = "[\"신상\", \"할인\", \"인기\"]")
        private List<String> tags;

        @JsonProperty("delivery_type")
        @Schema(description = "배송 유형", example = "STANDARD")
        private String deliveryType;

        @Min(value = 0, message = "배송비는 0 이상이어야 합니다.")
        @JsonProperty("delivery_fee")
        @Schema(description = "배송비", example = "3000")
        private Integer deliveryFee;

        @Min(value = 0, message = "무료 배송 최소 금액은 0 이상이어야 합니다.")
        @JsonProperty("delivery_free_threshold")
        @Schema(description = "무료 배송 최소 금액", example = "50000")
        private Integer deliveryFreeThreshold;

        @Min(value = 1, message = "배송 예상 일수는 1 이상이어야 합니다.")
        @JsonProperty("delivery_estimated_days")
        @Schema(description = "배송 예상 일수", example = "3")
        private Integer deliveryEstimatedDays;

        @JsonProperty("product_notice")
        @Valid
        @Schema(description = "상품정보제공고시")
        private ProductNoticeRequest productNotice;

        @JsonProperty("option_groups")
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

        @JsonProperty("washing_method")
        @Schema(description = "세탁 방법", example = "제품 상세 참고")
        private String washingMethod;

        @JsonProperty("manufacture_date")
        @Schema(description = "제조연월", example = "제품 상세 참고")
        private String manufactureDate;

        @JsonProperty("as_info")
        @Schema(description = "A/S 정보", example = "제품 상세 참고")
        private String asInfo;

        @JsonProperty("quality_assurance")
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
        @JsonProperty("option_names")
        @NotEmpty(message = "옵션명 목록은 필수 입력값입니다.")
        @Schema(description = "조합된 옵션명", example = "[\"S\", \"Black\"]")
        private List<String> optionNames;

        @JsonProperty("sale_price")
        @NotNull(message = "판매가는 필수 입력값입니다.")
        @Min(value = 0, message = "판매가는 0 이상이어야 합니다.")
        @Schema(description = "옵션가 포함 최종가", example = "50000")
        private Integer salePrice;

        @NotNull(message = "재고 수량은 필수 입력값입니다.")
        @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.")
        @Schema(description = "재고 수량", example = "100")
        private Integer stock;

        @JsonProperty("is_display")
        @Schema(description = "진열 여부", example = "true")
        private Boolean isDisplay = true;

        @JsonProperty("is_representative")
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
}

