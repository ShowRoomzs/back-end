package showroomz.api.admin.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "공지 수정 요청 (기획 §20-2) — 저장은 상태를 건드리지 않는다. "
        + "게시 종료 상태에서 저장해도 재게시되지 않는다.")
public class AdminNoticeUpdateRequest {

    @NotBlank(message = "제목은 필수 입력값입니다.")
    @Schema(description = "제목 (필수)", example = "SHOWROOMZ 앱 v1.2 업데이트 안내")
    private String title;

    @NotBlank(message = "본문은 필수 입력값입니다.")
    @Schema(description = "본문 (필수, 리치 에디터 HTML). 이미지는 최대 3장", example = "<p>안녕하세요, SHOWROOMZ입니다.</p>")
    private String content;

    @Schema(description = "중요 표시 여부 — 수정 시 해제도 가능하다 (미입력 시 false)", example = "true")
    private Boolean pinned;

    public boolean isPinned() {
        return Boolean.TRUE.equals(pinned);
    }
}
