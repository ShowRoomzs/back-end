package showroomz.api.admin.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.notice.entity.Notice;
import showroomz.domain.notice.type.NoticeStatus;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "공지 상세 (기획 §20-4) — 수정 페이지 좌측 폼 + 우측 게시 설정")
public class AdminNoticeDetailResponse {

    @Schema(description = "공지 ID", example = "6")
    private Long noticeId;

    @Schema(description = "제목", example = "SHOWROOMZ 앱 v1.2 업데이트 안내")
    private String title;

    @Schema(description = "본문 (리치 에디터 HTML)", example = "<p>안녕하세요, SHOWROOMZ입니다.</p>")
    private String content;

    @Schema(description = "상태", example = "PUBLISHED")
    private NoticeStatus status;

    @Schema(description = "상태 표시명", example = "게시")
    private String statusName;

    @Schema(description = "중요 여부", example = "true")
    private boolean pinned;

    @Schema(description = "등록일시", example = "2026-07-10T09:40:00")
    private LocalDateTime createdAt;

    @Schema(description = "최종 수정 일시", example = "2026-07-10T09:40:00")
    private LocalDateTime modifiedAt;

    @Schema(description = "게시 종료 일시 — 게시 종료 상태에서만 값이 있다", example = "2026-07-08T09:00:00")
    private LocalDateTime endedAt;

    @Schema(description = "작성자명 (운영자)", example = "김운영")
    private String authorName;

    public static AdminNoticeDetailResponse of(Notice notice, String authorName) {
        return AdminNoticeDetailResponse.builder()
                .noticeId(notice.getId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .status(notice.getStatus())
                .statusName(notice.getStatus().getDisplayName())
                .pinned(notice.isPinned())
                .createdAt(notice.getCreatedAt())
                .modifiedAt(notice.getModifiedAt())
                .endedAt(notice.getEndedAt())
                .authorName(authorName)
                .build();
    }
}
