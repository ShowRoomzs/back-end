package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryType;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "상품 문의 응답 (목록/상세 공용)")
public class ProductInquiryResponse {

    @Schema(description = "상품 문의 ID")
    private Long id;

    @Schema(description = "상품 ID")
    private Long productId;

    @Schema(description = "쇼룸(마켓) 이름")
    private String shopName;

    @Schema(description = "상품명")
    private String productName;

    @Schema(description = "상품 대표 이미지 URL")
    private String productImageUrl;

    @Schema(description = "문의 유형 코드 (OPTION, INGREDIENT_USAGE, RESTOCK, DELIVERY, ETC)")
    private ProductInquiryType type;

    @Schema(description = "문의 유형 한글명", example = "성분·사용법")
    private String typeName;

    @Schema(description = "문의 내용")
    private String content;

    @Schema(description = "비밀글 여부 — 목록에서는 잠금 표시로 자리만 남깁니다")
    private boolean secret;

    @Schema(description = "첨부 사진 URL (최대 3장)")
    private List<String> imageUrls;

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
                .id(inquiry.getId())
                .productId(inquiry.getProduct().getProductId())
                .shopName(inquiry.getProduct().getMarket().getMarketName())
                .productName(inquiry.getProduct().getName())
                .productImageUrl(imageUrl) // Service에서 계산된 URL 주입
                .type(inquiry.getType())
                .typeName(inquiry.getType().getDescription())
                .content(inquiry.getContent())
                .secret(inquiry.isSecret())
                .imageUrls(inquiry.getImageUrls())
                .status(inquiry.getStatus())
                .answerContent(inquiry.getAnswerContent())
                .createdAt(inquiry.getCreatedAt())
                .answeredAt(inquiry.getAnsweredAt())
                .build();
    }
}
