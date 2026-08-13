package showroomz.domain.notice.type;

import lombok.Getter;

/**
 * 공지 상태 (기획 §20-1) — 2종 고정
 * - 게시(PUBLISHED): 소비자 앱 공지사항에 노출 중
 * - 게시 종료(ENDED): 노출 중단 — 삭제하지 않고 목록에는 남는다
 *
 * 공지는 삭제하지 않는다 — "그때 무엇을 알렸는가"가 기록으로 남아야 한다.
 * 임시저장(초안) 상태도 두지 않는다 — 공지는 쓰면 바로 올리는 성격이다.
 */
@Getter
public enum NoticeStatus {

    PUBLISHED("게시"),
    ENDED("게시 종료");

    private final String displayName;

    NoticeStatus(String displayName) {
        this.displayName = displayName;
    }
}
