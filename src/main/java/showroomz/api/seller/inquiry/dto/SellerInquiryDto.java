package showroomz.api.seller.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.support.ProductInquiryStatusLabel;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryType;

import java.time.LocalDateTime;

/** 목록 행 (§23-2 컬럼 7종) — 상태는 맨 뒷열, 관리 열은 없고 행 전체 클릭으로 상세에 들어간다. */
@Getter
@Builder
@Schema(description = "파트너센터 문의 목록 행")
public class SellerInquiryDto {

    @Schema(description = "문의 ID", example = "71")
    private Long inquiryId;

    @Schema(description = "문의 유형 코드", example = "INGREDIENT_USAGE")
    private ProductInquiryType type;

    @Schema(description = "문의 유형 명 — 점 없는 중립 배지(분류)", example = "성분·사용법")
    private String typeName;

    @Schema(description = "질문 — 목록은 한 줄 말줄임으로 표시한다")
    private String content;

    @Schema(description = "상품명", example = "수분진정 세럼 30ml")
    private String productName;

    @Schema(description = "비밀글 여부", example = "false")
    private boolean secret;

    @Schema(description = "공개여부 명 — 점 없는 중립 배지(분류)", example = "공개")
    private String visibilityName;

    @Schema(description = "등록일 — 날짜 + 시:분까지 표기한다", example = "2026-08-11T09:12:00")
    private LocalDateTime createdAt;

    @Schema(description = "답변일 — 미답변이면 null", example = "2026-08-11T14:20:00")
    private LocalDateTime answeredAt;

    @Schema(description = "답변 축", example = "WAITING")
    private InquiryStatus status;

    @Schema(description = "노출 축", example = "NORMAL")
    private InquiryExposureStatus exposureStatus;

    @Schema(description = "두 축을 합친 표시 상태", example = "답변대기")
    private String statusLabel;

    public static SellerInquiryDto from(ProductInquiry inquiry) {
        return SellerInquiryDto.builder()
                .inquiryId(inquiry.getId())
                .type(inquiry.getType())
                .typeName(inquiry.getType().getDescription())
                .content(inquiry.getContent())
                .productName(inquiry.getProduct().getName())
                .secret(inquiry.isSecret())
                .visibilityName(inquiry.isSecret() ? "비밀글" : "공개")
                .createdAt(inquiry.getCreatedAt())
                .answeredAt(inquiry.getAnsweredAt())
                .status(inquiry.getStatus())
                .exposureStatus(inquiry.getExposureStatus())
                .statusLabel(ProductInquiryStatusLabel.of(inquiry.getStatus(), inquiry.getExposureStatus()))
                .build();
    }
}
