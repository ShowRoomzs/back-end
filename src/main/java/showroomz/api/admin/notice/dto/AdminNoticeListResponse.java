package showroomz.api.admin.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.notice.entity.Notice;
import showroomz.domain.notice.type.NoticeStatus;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "공지 목록 행 (기획 §20-3) — 컬럼 6종: 번호 · 제목 · 등록일 · 수정일 · 상태 · 관리")
public class AdminNoticeListResponse {

    @Schema(description = "공지 ID", example = "6")
    private Long noticeId;

    /** 등록 순 채번 — 삭제가 없어 번호가 비지 않는다 (기획 §20-3) */
    @Schema(description = "번호 (등록 순 채번, 공지 ID와 동일)", example = "6")
    private Long number;

    @Schema(description = "제목", example = "SHOWROOMZ 앱 v1.2 업데이트 안내")
    private String title;

    @Schema(description = "중요 여부 — 제목 앞 배지, 목록 상단 고정", example = "true")
    private boolean pinned;

    @Schema(description = "상태", example = "PUBLISHED")
    private NoticeStatus status;

    @Schema(description = "상태 표시명", example = "게시")
    private String statusName;

    @Schema(description = "등록일 (분까지 표기)", example = "2026-07-10T09:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정일 (분까지 표기)", example = "2026-07-10T09:30:00")
    private LocalDateTime modifiedAt;

    public static AdminNoticeListResponse from(Notice notice) {
        return AdminNoticeListResponse.builder()
                .noticeId(notice.getId())
                .number(notice.getId())
                .title(notice.getTitle())
                .pinned(notice.isPinned())
                .status(notice.getStatus())
                .statusName(notice.getStatus().getDisplayName())
                .createdAt(notice.getCreatedAt())
                .modifiedAt(notice.getModifiedAt())
                .build();
    }
}
