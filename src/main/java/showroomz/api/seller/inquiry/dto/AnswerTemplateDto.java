package showroomz.api.seller.inquiry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.api.seller.inquiry.type.MarketInquiryFilterType;
import showroomz.domain.inquiry.entity.AnswerTemplate;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "답변 템플릿 항목")
public class AnswerTemplateDto {

    @Schema(description = "템플릿 ID", example = "1")
    private Long templateId;

    @Schema(description = "템플릿 제목", example = "재입고 안내 템플릿")
    private String title;

    @Schema(description = "카테고리 코드", example = "STOCK")
    private MarketInquiryFilterType category;

    @Schema(description = "카테고리명", example = "재고/재입고 문의")
    private String categoryName;

    @Schema(description = "답변 내용", example = "안녕하세요, 해당 상품은 다음 주 중 재입고 예정입니다.")
    private String content;

    @Schema(description = "작성일시", example = "2026-04-01T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정일시", example = "2026-04-01T12:00:00")
    private LocalDateTime modifiedAt;

    @JsonProperty("isActive")
    @Schema(description = "활성 여부", example = "true")
    private boolean isActive;

    public static AnswerTemplateDto from(AnswerTemplate template) {
        return AnswerTemplateDto.builder()
                .templateId(template.getId())
                .title(template.getTitle())
                .category(template.getCategory())
                .categoryName(template.getCategory().getDescription())
                .content(template.getContent())
                .createdAt(template.getCreatedAt())
                .modifiedAt(template.getModifiedAt())
                .isActive(template.isActive())
                .build();
    }
}
