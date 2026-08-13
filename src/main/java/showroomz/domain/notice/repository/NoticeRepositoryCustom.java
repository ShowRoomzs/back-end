package showroomz.domain.notice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.notice.entity.Notice;
import showroomz.domain.notice.type.NoticeStatus;

import java.time.LocalDateTime;
import java.util.Map;

public interface NoticeRepositoryCustom {

    /** 어드민 목록 — 중요 고정 상단 + 등록일 최신순 (기획 §20-3) */
    Page<Notice> findAdminNoticeList(NoticeStatus status, String keyword, Pageable pageable);

    /** 상태 탭 건수 (검색어 적용 기준) */
    Map<NoticeStatus, Long> countByStatusGroup(String keyword);

    /** 툴바의 "중요 N건" — 현재 탭·검색어 기준 */
    long countPinned(NoticeStatus status, String keyword);

    /**
     * 상태 전이 전용 (기획 §20-5).
     * 등록일·수정일을 갱신하지 않아야 하므로 더티 체킹(@LastModifiedDate) 대신 벌크 업데이트를 쓴다 —
     * 갱신하면 오래된 공지가 목록 최상단으로 튀어 올라간다.
     */
    void changeStatus(Long noticeId, NoticeStatus status, LocalDateTime endedAt);
}
