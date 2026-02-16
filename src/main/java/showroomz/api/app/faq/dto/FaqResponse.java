package showroomz.api.app.faq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.faq.entity.Faq;

@Getter
@Builder
public class FaqResponse {

    @Schema(description = "FAQ ID")
    private Long id;

    @Schema(description = "카테고리 코드 (enum 이름: DELIVERY, ORDER_PAYMENT 등)")
    private String category;

    @Schema(description = "카테고리 한글명", example = "배송")
    private String categoryName;

    @Schema(description = "질문")
    private String question;

    @Schema(description = "답변")
    private String answer;

    public static FaqResponse from(Faq faq) {
        return FaqResponse.builder()
                .id(faq.getId())
                .category(faq.getCategory().name())
                .categoryName(faq.getCategory().getDisplayName())
                .question(faq.getQuestion())
                .answer(faq.getAnswer())
                .build();
    }
}

