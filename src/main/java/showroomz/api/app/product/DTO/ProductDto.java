package showroomz.api.app.product.DTO;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Builder
    @Schema(description = "상품 검색 요청")
    public static class ProductSearchRequest {
        @Schema(description = "검색어", example = "린넨")
        private String q;

        @Schema(description = "카테고리 ID", example = "1")
        private Long categoryId;

        @Schema(description = "마켓 ID", example = "5")
        private Long marketId;

        @Schema(description = "필터 목록 (JSON)")
        private List<FilterRequest> filters;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "필터 요청")
    public static class FilterRequest {
        @Schema(description = "필터 키", example = "color")
        private String key;

        @Schema(description = "필터 값 목록", example = "[\"black\", \"white\"]")
        private List<String> values;

        @Schema(description = "최소값 (숫자형 필터)")
        private Integer minValue;

        @Schema(description = "최대값 (숫자형 필터)")
        private Integer maxValue;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 목록 항목")
    public static class ProductItem {
        @Schema(description = "상품 ID", example = "1024")
        private Long id;

        @Schema(description = "상품 번호", example = "SRZ-20251228-001")
        private String productNumber;

        @Schema(description = "상품명", example = "프리미엄 린넨 셔츠")
        private String name;

        @Schema(description = "판매자 상품 코드", example = "PROD-001")
        private String sellerProductCode;

        @Schema(description = "대표 이미지 URL", example = "https://example.com/image.jpg")
        private String representativeImageUrl;

        @Schema(description = "썸네일 URL", example = "https://example.com/image.jpg")
        private String thumbnailUrl;

        @Schema(description = "카테고리 ID", example = "1")
        private Long categoryId;

        @Schema(description = "카테고리명", example = "의류")
        private String categoryName;

        @Schema(description = "마켓 ID", example = "5")
        private Long marketId;

        @Schema(description = "마켓명", example = "M 브라이튼")
        private String marketName;

        @Schema(description = "가격 정보")
        private PriceInfo price;

        @Schema(description = "할인율 (%)", example = "70")
        private Integer discountRate;

        @Schema(description = "성별", example = "UNISEX", allowableValues = {"MALE", "FEMALE", "UNISEX"})
        private String gender;

        @Schema(description = "진열 여부", example = "true")
        private Boolean isDisplay;

        @Schema(description = "추천 상품 여부", example = "false")
        private Boolean isRecommended;

        @Schema(description = "상품정보제공고시 (JSON)")
        private String productNotice;

        @Schema(description = "상품 상세 설명", example = "<p>상품 상세 설명</p>")
        private String description;

        @Schema(description = "등록일", example = "2025-12-28T14:30:00Z")
        private String createdAt;

        @Schema(description = "재고 상태")
        private StockStatus status;

        @Schema(description = "좋아요 수", example = "1200")
        private Long likeCount;

        @Schema(description = "찜 수", example = "300")
        private Long wishCount;

        @Schema(description = "리뷰 수", example = "850")
        private Long reviewCount;

        @Schema(description = "찜 여부", example = "false")
        private Boolean isWished;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "가격 정보")
    public static class PriceInfo {
        @Schema(description = "정가", example = "113000")
        private Integer regularPrice;

        @Schema(description = "할인율 (%)", example = "70")
        private Integer discountRate;

        @Schema(description = "할인 판매가", example = "33900")
        private Integer salePrice;

        @Schema(description = "최대 혜택가", example = "31000")
        private Integer maxBenefitPrice;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "재고 상태")
    public static class StockStatus {
        @Schema(description = "재고 기반 품절 여부", example = "false")
        private Boolean isOutOfStock;

        @Schema(description = "관리자 강제 품절 여부", example = "false")
        private Boolean isOutOfStockForced;
    }

    /**
     * C7 상품 상세 응답 — 화면에 그려지는 값만 담는다.
     *
     * <p>상세 화면은 갤러리 · 브랜드 줄 · 가격 · 배송 블록 · 세 개의 탭(상세정보 / 문의 / 판매자 정보)
     * · 옵션 시트로 이뤄진다. 문의 탭은 별도 API(<code>/v1/common/products/{productId}/inquiries</code>)가
     * 담당하므로 이 응답에 없다. 찜(♥)은 게시물 단위라 상품 상세에는 두지 않는다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "상품 상세 조회 응답")
    public static class ProductDetailResponse {
        @Schema(description = "상품 ID", example = "1024")
        private Long id;

        @Schema(description = "상품명", example = "시카 리페어 앰플 30ml 리필 2개 세트")
        private String name;

        @Schema(description = "갤러리 대표 이미지 URL (첫 장)", example = "https://example.com/image.jpg")
        private String representativeImageUrl;

        @Schema(description = "갤러리 나머지 이미지 URL 목록 (2번째 장부터)")
        private List<String> coverImageUrls;

        @Schema(description = "브랜드(쇼룸) ID", example = "5")
        private Long marketId;

        @Schema(description = "브랜드명 — 갤러리 아래 브랜드 줄", example = "라보에이치")
        private String marketName;

        @Schema(description = "브랜드 사이트 링크 — 없으면 null이며, 화면은 [브랜드 사이트] 버튼을 숨긴다",
                example = "https://labo-h.example.com")
        private String brandSiteUrl;

        @Schema(description = "정가 (취소선으로 표시)", example = "38000")
        private Integer regularPrice;

        @Schema(description = "할인율 (%) — 서버가 계산해 내려준다. 할인이 없으면 0", example = "34")
        private Integer discountRate;

        @Schema(description = "공구 판매가", example = "24900")
        private Integer salePrice;

        @Schema(
                description = "공구 상태 — 공구에 연결된 상품만 조회되므로 NOT_CONNECTED는 내려오지 않습니다.",
                example = "IN_PROGRESS",
                allowableValues = {"PREPARING", "READY", "IN_PROGRESS"}
        )
        private String groupBuyStatus;

        @Schema(description = "상품 전체 판매 상태 — 하단 CTA를 [구매하기]와 판매 종료 상태로 가르는 값")
        private StockStatus status;

        @Schema(description = "배송 · 교환 · 반품 정보")
        private DeliveryInfo delivery;

        @Schema(description = "상품 상세 설명 (상세정보 탭 본문 HTML)")
        private String description;

        @Schema(description = "상품정보제공고시 (JSON 객체) — 상세정보 탭의 규격 표와 판매자 정보 탭의 고시 표에 함께 쓰인다")
        private JsonNode productNotice;

        @Schema(description = "옵션 그룹 목록 (옵션 시트의 드롭다운)")
        private List<OptionGroupInfo> optionGroups;

        @Schema(description = "옵션 조합(Variant) 목록 (옵션 시트의 항목)")
        private List<VariantInfo> variants;

        @Schema(description = "판매자 정보 (판매자 정보 탭 · 전자상거래법 표시 항목)")
        private SellerInfo sellerInfo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "배송 · 교환 · 반품 정보")
    public static class DeliveryInfo {
        @Schema(description = "발송까지 걸리는 영업일 수 — 화면의 \"N일 이내 출발 예정\"", example = "2")
        private Integer shippingLeadDays;

        @Schema(description = "기본 배송비", example = "3000")
        private Integer deliveryFee;

        @Schema(description = "무료배송 기준 금액 — null이면 무료배송 기준이 없다", example = "30000")
        private Integer freeShippingThreshold;

        @Schema(description = "도서산간 추가 배송비", example = "5000")
        private Integer remoteAreaSurcharge;

        @Schema(description = "반품 배송비", example = "3000")
        private Integer returnFee;

        @Schema(description = "교환 배송비", example = "6000")
        private Integer exchangeFee;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "판매자 정보 (전자상거래법 표시 항목)")
    public static class SellerInfo {
        @Schema(description = "상호명", example = "주식회사 라보에이치")
        private String companyName;

        @Schema(description = "대표자", example = "홍길동")
        private String representativeName;

        @Schema(description = "사업자등록번호", example = "000-00-00000")
        private String businessRegistrationNumber;

        @Schema(description = "통신판매업 신고번호", example = "제0000-서울강남-00000호")
        private String mailOrderRegNumber;

        @Schema(description = "사업장 소재지 (기본 주소 + 상세 주소)", example = "서울특별시 강남구 ○○로 00 4층")
        private String businessAddress;

        @Schema(description = "고객센터 번호", example = "000-0000-0000")
        private String csNumber;

        @Schema(description = "이메일", example = "brand@example.com")
        private String email;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "옵션별 재고 다중 조회 응답 (페이징 없음)")
    public static class VariantStockListResponse {
        @Schema(description = "옵션별 재고/가격 목록")
        private List<ProductVariantStockResponse> variants;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "옵션별 재고/가격 조회 응답")
    public static class ProductVariantStockResponse {
        @Schema(description = "상품 ID", example = "1024")
        private Long productId;

        @Schema(description = "옵션(Variant) ID", example = "1")
        private Long variantId;

        @Schema(description = "재고 수량", example = "10")
        private Integer stock;

        @Schema(description = "재고 기반 품절 여부", example = "false")
        private Boolean isOutOfStock;

        @Schema(description = "관리자 강제 품절 여부", example = "false")
        private Boolean isOutOfStockForced;

        @Schema(description = "가격 정보")
        private PriceInfo price;
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

        @Schema(description = "정가", example = "59000")
        private Integer regularPrice;

        @Schema(description = "할인 판매가", example = "49000")
        private Integer salePrice;

        @Schema(description = "재고 수량", example = "10")
        private Integer stock;

        @Schema(description = "품절 여부 — 재고 0이거나 강제 품절이면 true. 옵션 시트에서 취소선·회색으로 표시된다",
                example = "false")
        private Boolean isOutOfStock;

        @Schema(description = "대표 옵션 여부", example = "true")
        private Boolean isRepresentative;

        @Schema(description = "옵션 ID 목록", example = "[1, 2]")
        private List<Long> optionIds;
    }
}
