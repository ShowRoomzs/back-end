package showroomz.api.app.notice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.docs.NoticeControllerDocs;
import showroomz.api.app.notice.dto.NoticeDetailResponse;
import showroomz.api.app.notice.dto.NoticeResponse;
import showroomz.api.app.notice.service.NoticeService;
import showroomz.global.dto.PageResponse;

@RestController
@RequestMapping("/v1/user/notices")
@RequiredArgsConstructor
public class NoticeController implements NoticeControllerDocs {

    private final NoticeService noticeService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<NoticeResponse>> getNoticeList(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(noticeService.getNoticeList(pageable));
    }

    @Override
    @GetMapping("/{noticeId}")
    public ResponseEntity<NoticeDetailResponse> getNoticeDetail(@PathVariable Long noticeId) {
        return ResponseEntity.ok(noticeService.getNoticeDetail(noticeId));
    }
}
