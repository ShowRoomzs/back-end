package showroomz.api.admin.faq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.faq.entity.Faq;
import showroomz.domain.faq.type.FaqCategory;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminFaqListResponse {

    @Schema(description = "FAQ ID", example = "1")
    private Long id;

    @Schema(description = "카테고리", example = "DELIVERY")
    private FaqCategory category;

    @Schema(description = "카테고리 표시명", example = "배송")
    private String categoryDisplayName;

    @Schema(description = "질문", example = "배송은 얼마나 걸리나요?")
    private String question;

    @Schema(description = "답변", example = "평균 2~3일 소요됩니다.")
    private String answer;

    @Schema(description = "등록일")
    private LocalDateTime createdAt;

    @Schema(description = "수정일")
    private LocalDateTime modifiedAt;

    public static AdminFaqListResponse from(Faq faq) {
        return AdminFaqListResponse.builder()
                .id(faq.getId())
                .category(faq.getCategory())
                .categoryDisplayName(faq.getCategory().getDisplayName())
                .question(faq.getQuestion())
                .answer(faq.getAnswer())
                .createdAt(faq.getCreatedAt())
                .modifiedAt(faq.getModifiedAt())
                .build();
    }
}
