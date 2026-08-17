package showroomz.api.app.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.notice.entity.Notice;

import java.time.LocalDateTime;

@Getter
public class NoticeDetailResponse {

    @Schema(description = "공지 ID", example = "1")
    private final Long id;

    @Schema(description = "제목", example = "서비스 점검 안내")
    private final String title;

    @Schema(description = "본문 (어드민에서 작성한 리치 에디터 HTML이 그대로 실린다)",
            example = "<p>2026년 7월 20일 02:00~04:00 점검 예정입니다.</p>")
    private final String content;

    @Schema(description = "중요 여부", example = "true")
    private final boolean pinned;

    @Schema(description = "등록일", example = "2026-07-05T10:15:00Z")
    private final LocalDateTime createdDate;

    public NoticeDetailResponse(Notice notice) {
        this.id = notice.getId();
        this.title = notice.getTitle();
        this.content = notice.getContent();
        this.pinned = notice.isPinned();
        this.createdDate = notice.getCreatedAt();
    }

    public static NoticeDetailResponse from(Notice notice) {
        return new NoticeDetailResponse(notice);
    }
}
