package showroomz.api.admin.notice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.notice.dto.AdminNoticeDetailResponse;
import showroomz.api.admin.notice.dto.AdminNoticeListResponse;
import showroomz.api.admin.notice.dto.AdminNoticeRegisterRequest;
import showroomz.domain.notice.entity.Notice;
import showroomz.domain.notice.repository.NoticeRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminNoticeService {

    private final NoticeRepository noticeRepository;

    @Transactional
    public Long registerNotice(AdminNoticeRegisterRequest request) {
        boolean isVisible = request.getIsVisible() == null ? true : request.getIsVisible();

        Notice notice = Notice.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .isVisible(isVisible)
                .build();

        return noticeRepository.save(notice).getId();
    }

    public PageResponse<AdminNoticeListResponse> getNotices(String keyword, PagingRequest pagingRequest) {
        Pageable pageable = pagingRequest.toPageable();
        Page<Notice> noticePage;

        if (keyword == null || keyword.trim().isEmpty()) {
            noticePage = noticeRepository.findAll(pageable);
        } else {
            noticePage = noticeRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(keyword, keyword, pageable);
        }

        Page<AdminNoticeListResponse> dtoPage = noticePage.map(AdminNoticeListResponse::from);
        return new PageResponse<>(dtoPage);
    }

    public AdminNoticeDetailResponse getNotice(Long noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("해당 공지사항을 찾을 수 없습니다."));

        return AdminNoticeDetailResponse.from(notice);
    }

    @Transactional
    public void deleteNotice(Long noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("해당 공지사항을 찾을 수 없습니다."));

        noticeRepository.delete(notice);
    }
}
