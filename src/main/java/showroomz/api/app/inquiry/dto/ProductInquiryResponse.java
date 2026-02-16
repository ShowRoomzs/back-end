package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryType;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "상품 문의 응답 (목록/상세 공용)")
public class ProductInquiryResponse {

    @Schema(description = "상품 문의 ID")
    private Long inquiryId;

    @Schema(description = "상품 ID")
    private Long productId;

    @Schema(description = "쇼룸(마켓) 이름")
    private String shopName;

    @Schema(description = "상품명")
    private String productName;

    @Schema(description = "상품 대표 이미지 URL")
    private String productImageUrl;

    @Schema(description = "문의 타입 코드 (PRODUCT_INQUIRY, SIZE_INQUIRY, STOCK_INQUIRY)")
    private ProductInquiryType type;

    @Schema(description = "문의 타입 한글명", example = "사이즈 문의")
    private String typeName;

    @Schema(description = "문의 내용")
    private String content;

    @Schema(description = "답변 상태")
    private InquiryStatus status;

    @Schema(description = "답변 내용")
    private String answerContent;

    @Schema(description = "문의 등록 일시")
    private LocalDateTime createdAt;

    @Schema(description = "답변 일시")
    private LocalDateTime answeredAt;

    // 이미지 로직을 제거하고, 이미지는 항상 파라미터로 받도록 통일
    public static ProductInquiryResponse of(ProductInquiry inquiry, String imageUrl) {
        return ProductInquiryResponse.builder()
                .inquiryId(inquiry.getId())
                .productId(inquiry.getProduct().getProductId())
                .shopName(inquiry.getProduct().getMarket().getMarketName())
                .productName(inquiry.getProduct().getName())
                .productImageUrl(imageUrl) // Service에서 계산된 URL 주입
                .type(inquiry.getType())
                .typeName(inquiry.getType().getDescription())
                .content(inquiry.getContent())
                .status(inquiry.getStatus())
                .answerContent(inquiry.getAnswerContent())
                .createdAt(inquiry.getCreatedAt())
                .answeredAt(inquiry.getAnsweredAt())
                .build();
    }
}

