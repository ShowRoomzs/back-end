package showroomz.api.seller.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "답변 템플릿 등록 응답")
public class AnswerTemplateRegisterResponse {

    @Schema(description = "생성된 답변 템플릿 ID", example = "1")
    private Long templateId;
}
