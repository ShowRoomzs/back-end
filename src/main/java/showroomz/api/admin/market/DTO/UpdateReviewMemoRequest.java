package showroomz.api.admin.market.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateReviewMemoRequest {

    @Schema(description = "관리자용 검토 메모 (최대 500자)", example = "서류 확인 완료, 마켓 URL 보완 필요", maxLength = 500)
    @Size(max = 500, message = "검토 메모는 500자 이내로 입력해주세요.")
    private String reviewMemo;
}
