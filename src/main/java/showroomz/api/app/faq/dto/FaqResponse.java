package showroomz.api.app.faq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.inquiry.type.InquiryType;

@Getter
@Builder
public class FaqResponse {

    @Schema(description = "FAQ ID")
    private Long id;

    @Schema(description = "질문 타입")
    private InquiryType type;

    @Schema(description = "카테고리")
    private String category;

    @Schema(description = "질문")
    private String question;

    @Schema(description = "답변")
    private String answer;

    public static FaqResponse from(Faq faq) {
        return FaqResponse.builder()
                .id(faq.getId())
                .type(faq.getType())
                .category(faq.getCategory())
                .question(faq.getQuestion())
                .answer(faq.getAnswer())
                .build();
    }
}

