package showroomz.api.admin.notice.type;

import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.domain.notice.type.NoticeStatus;

/** 목록 상태 탭 (기획 §20-3) — 기본 진입 탭은 전체다. */
@Getter
@AllArgsConstructor
public enum AdminNoticeStatusFilter {

    ALL("전체", null),
    PUBLISHED("게시", NoticeStatus.PUBLISHED),
    ENDED("게시 종료", NoticeStatus.ENDED);

    private final String description;
    private final NoticeStatus status;
}
