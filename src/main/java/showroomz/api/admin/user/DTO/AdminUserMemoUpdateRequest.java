package showroomz.api.admin.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "유저 관리자 메모 수정 요청 DTO")
public class AdminUserMemoUpdateRequest {

    @Size(max = 500, message = "관리자 메모는 최대 500자까지 입력 가능합니다.")
    @Schema(description = "관리자 메모 내용", example = "블랙리스트 의심 유저. 지속적인 모니터링 필요.")
    private String adminMemo;
}
