package showroomz.api.seller.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "답변 템플릿 삭제 요청")
public class AnswerTemplateDeleteRequest {

    @NotEmpty(message = "삭제할 템플릿 ID를 하나 이상 입력해주세요.")
    @Schema(description = "삭제할 템플릿 ID 목록", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1, 2, 3]")
    private List<Long> templateIds;
}
