package showroomz.api.seller.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.product.entity.Product;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "판매자용 상품 문의 상세 응답")
public class ProductInquiryDetailResponse {

    @Schema(description = "문의 ID", example = "1")
    private Long inquiryId;

    @Schema(description = "문의 타입", example = "PRODUCT_INQUIRY")
    private ProductInquiryType type;

    @Schema(description = "고객명. 실명이 있으면 실명을, 없으면 닉네임을 반환합니다.", example = "홍길동")
    private String customerName;

    @Schema(description = "고객 이메일", example = "user@example.com")
    private String email;

    @Schema(description = "문의 등록 일시", example = "2026-04-01T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "문의 내용", example = "이 상품 재입고 예정이 있나요?")
    private String content;

    @Schema(description = "답변 내용. 답변 전이면 null 입니다.", example = "다음 주 중 재입고 예정입니다.")
    private String answerContent;

    @Schema(description = "상품명", example = "오버핏 셔츠")
    private String productName;

    @Schema(description = "상품 번호", example = "SRZ-20260401-001")
    private String productCode;

    @Schema(description = "정가", example = "59000")
    private Integer regularPrice;

    @Schema(description = "판매가", example = "49000")
    private Integer salePrice;

    @Schema(description = "진열 여부", example = "true")
    private Boolean isDisplay;

    @Schema(description = "강제 품절 여부", example = "false")
    private Boolean isOutOfStockForced;

    @Schema(
            description = "판매 상태 코드. 판매 가능하면 ON_SALE, 미진열 또는 강제 품절이면 UNAVAILABLE 을 반환합니다.",
            example = "ON_SALE",
            allowableValues = {"ON_SALE", "UNAVAILABLE"}
    )
    private String saleStatus;

    public static ProductInquiryDetailResponse from(ProductInquiry inquiry) {
        Users user = inquiry.getUser();
        Product product = inquiry.getProduct();

        String customerName = resolveCustomerName(user);
        String saleStatus = resolveSaleStatus(product);

        return ProductInquiryDetailResponse.builder()
                .inquiryId(inquiry.getId())
                .type(inquiry.getType())
                .customerName(customerName)
                .email(user.getEmail())
                .createdAt(inquiry.getCreatedAt())
                .content(inquiry.getContent())
                .answerContent(inquiry.getAnswerContent())
                .productName(product.getName())
                .productCode(product.getProductNumber())
                .regularPrice(product.getRegularPrice())
                .salePrice(product.getSalePrice())
                .isDisplay(product.getIsDisplay())
                .isOutOfStockForced(product.getIsOutOfStockForced())
                .saleStatus(saleStatus)
                .build();
    }

    private static String resolveCustomerName(Users user) {
        String name = user.getName();
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        return user.getNickname();
    }

    private static String resolveSaleStatus(Product product) {
        if (Boolean.FALSE.equals(product.getIsDisplay())
                || Boolean.TRUE.equals(product.getIsOutOfStockForced())) {
            return "UNAVAILABLE";
        }
        return "ON_SALE";
    }
}
