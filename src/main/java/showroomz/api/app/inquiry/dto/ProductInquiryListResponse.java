package showroomz.api.app.inquiry.dto;

import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.product.entity.ProductImage;

import java.time.LocalDateTime;
import java.util.Comparator;

@Getter
@Builder
public class ProductInquiryListResponse {

    private Long inquiryId;
    private Long productId;
    private String shopName;
    private String productName;
    private String productImageUrl;
    private String content;
    private boolean secret;
    private InquiryStatus status;
    private String answerContent;
    private LocalDateTime createdAt;
    private LocalDateTime answeredAt;

    public static ProductInquiryListResponse from(ProductInquiry inquiry) {
        String imageUrl = inquiry.getProduct().getThumbnailUrl();
        if (imageUrl == null && inquiry.getProduct().getProductImages() != null) {
            imageUrl = inquiry.getProduct().getProductImages().stream()
                    .min(Comparator.comparing(ProductImage::getOrder))
                    .map(ProductImage::getUrl)
                    .orElse(null);
        }

        return ProductInquiryListResponse.builder()
                .inquiryId(inquiry.getId())
                .productId(inquiry.getProduct().getProductId())
                .shopName(inquiry.getProduct().getMarket().getMarketName())
                .productName(inquiry.getProduct().getName())
                .productImageUrl(imageUrl)
                .content(inquiry.getContent())
                .secret(inquiry.isSecret())
                .status(inquiry.getStatus())
                .answerContent(inquiry.getAnswerContent())
                .createdAt(inquiry.getCreatedAt())
                .answeredAt(inquiry.getAnsweredAt())
                .build();
    }
}
