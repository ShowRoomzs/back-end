package showroomz.api.admin.notice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
}
