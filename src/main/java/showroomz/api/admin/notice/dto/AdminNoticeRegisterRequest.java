package showroomz.api.admin.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "공지 등록 요청 (기획 §20-4) — 등록은 곧 게시다. 임시저장·예약 게시는 두지 않는다.")
public class AdminNoticeRegisterRequest {

    @NotBlank(message = "제목은 필수 입력값입니다.")
    @Schema(description = "제목 (필수)", example = "SHOWROOMZ 앱 v1.2 업데이트 안내")
    private String title;

    @NotBlank(message = "본문은 필수 입력값입니다.")
    @Schema(description = "본문 (필수, 리치 에디터 HTML). 이미지는 최대 3장", example = "<p>안녕하세요, SHOWROOMZ입니다.</p>")
    private String content;

    @Schema(description = "중요 표시 여부 — 목록 상단 고정 노출 (미입력 시 false)", example = "true")
    private Boolean pinned;

    /** 중요는 상태가 아니라 분류라, 미입력이면 일반 공지로 본다 (기획 §20-1) */
    public boolean isPinned() {
        return Boolean.TRUE.equals(pinned);
    }
}
