package showroomz.api.app.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.notice.entity.Notice;

import java.time.LocalDateTime;

@Getter
public class NoticeResponse {

    @Schema(description = "공지 ID", example = "1")
    private final Long id;

    @Schema(description = "제목", example = "서비스 점검 안내")
    private final String title;

    /** 중요 배지 — 목록 상단에 고정된다 (기획 §20-3) */
    @Schema(description = "중요 여부 — [중요] 배지 노출 및 목록 상단 고정", example = "true")
    private final boolean pinned;

    @Schema(description = "등록일", example = "2026-07-05T10:15:00")
    private final LocalDateTime createdDate;

    public NoticeResponse(Notice notice) {
        this.id = notice.getId();
        this.title = notice.getTitle();
        this.pinned = notice.isPinned();
        this.createdDate = notice.getCreatedAt();
    }

    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(notice);
    }
}
