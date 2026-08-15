package showroomz.api.admin.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.api.admin.notice.type.AdminNoticeStatusFilter;

@Getter
@AllArgsConstructor
@Schema(description = "상태 탭 건수 (기획 §20-3) — 전체 · 게시 · 게시 종료 3종")
public class AdminNoticeStatusCount {

    @Schema(description = "탭 코드", example = "PUBLISHED")
    private AdminNoticeStatusFilter status;

    @Schema(description = "탭 표시명", example = "게시")
    private String displayName;

    @Schema(description = "건수", example = "5")
    private long count;

    public static AdminNoticeStatusCount of(AdminNoticeStatusFilter status, long count) {
        return new AdminNoticeStatusCount(status, status.getDescription(), count);
    }
}
