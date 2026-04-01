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

    @Schema(description = "문의 타입")
    private ProductInquiryType type;

    @Schema(description = "고객명")
    private String customerName;

    @Schema(description = "고객 이메일")
    private String email;

    @Schema(description = "문의 등록 일시")
    private LocalDateTime createdAt;

    @Schema(description = "문의 내용")
    private String content;

    @Schema(description = "답변 내용")
    private String answerContent;

    @Schema(description = "상품명")
    private String productName;

    @Schema(description = "상품 번호")
    private String productCode;

    @Schema(description = "정가")
    private Integer regularPrice;

    @Schema(description = "판매가")
    private Integer salePrice;

    @Schema(description = "진열 여부")
    private Boolean isDisplay;

    @Schema(description = "강제 품절 여부")
    private Boolean isOutOfStockForced;

    @Schema(description = "판매 상태", example = "판매중")
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
        if (Boolean.FALSE.equals(product.getIsDisplay())) {
            return "미진열";
        }
        if (Boolean.TRUE.equals(product.getIsOutOfStockForced())) {
            return "품절";
        }
        return "판매중";
    }
}
