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

    public Page<AdminNoticeListResponse> getNotices(Pageable pageable) {
        return noticeRepository.findAll(pageable)
                .map(AdminNoticeListResponse::from);
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
