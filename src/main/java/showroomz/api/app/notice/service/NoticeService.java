package showroomz.api.app.notice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.notice.dto.NoticeDetailResponse;
import showroomz.api.app.notice.dto.NoticeResponse;
import showroomz.domain.notice.entity.Notice;
import showroomz.domain.notice.repository.NoticeRepository;
import showroomz.domain.notice.type.NoticeStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    /** 중요 고정 상단 + 등록일 최신순 — 어드민 목록과 같은 정렬이다 (기획 §20-3) */
    private static final Sort NOTICE_SORT = Sort.by(
            Sort.Order.desc("pinned"),
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final NoticeRepository noticeRepository;

    public PageResponse<NoticeResponse> getNoticeList(PagingRequest pagingRequest) {
        Page<Notice> noticePage = noticeRepository.findAllByStatus(
                NoticeStatus.PUBLISHED, pagingRequest.toPageable(NOTICE_SORT));
        return PageResponse.of(noticePage.map(NoticeResponse::from));
    }

    /** 게시 종료된 공지는 앱에서 접근할 수 없다 — 404로 응답한다 (기획 §20-6 4번) */
    public NoticeDetailResponse getNoticeDetail(Long noticeId) {
        Notice notice = noticeRepository.findByIdAndStatus(noticeId, NoticeStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));
        return NoticeDetailResponse.from(notice);
    }
}
