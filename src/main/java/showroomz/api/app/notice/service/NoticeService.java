package showroomz.api.app.notice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.notice.dto.NoticeDetailResponse;
import showroomz.api.app.notice.dto.NoticeResponse;
import showroomz.domain.notice.entity.Notice;
import showroomz.domain.notice.repository.NoticeRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public PageResponse<NoticeResponse> getNoticeList(Pageable pageable) {
        Page<Notice> noticePage = noticeRepository.findAllByIsVisibleTrue(pageable);
        Page<NoticeResponse> responsePage = noticePage.map(NoticeResponse::from);
        return PageResponse.of(responsePage);
    }

    public NoticeDetailResponse getNoticeDetail(Long noticeId) {
        Notice notice = noticeRepository.findByIdAndIsVisibleTrue(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));
        return NoticeDetailResponse.from(notice);
    }
}
