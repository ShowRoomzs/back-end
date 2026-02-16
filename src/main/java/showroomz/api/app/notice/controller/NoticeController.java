package showroomz.api.app.notice.controller;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.app.notice.docs.NoticeControllerDocs;
import showroomz.api.app.notice.dto.NoticeDetailResponse;
import showroomz.api.app.notice.dto.NoticeResponse;
import showroomz.api.app.notice.service.NoticeService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/common/notices")
@RequiredArgsConstructor
public class NoticeController implements NoticeControllerDocs {

    private final NoticeService noticeService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<NoticeResponse>> getNoticeList(
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(noticeService.getNoticeList(pagingRequest.toPageable()));
    }

    @Override
    @GetMapping("/{noticeId}")
    public ResponseEntity<NoticeDetailResponse> getNoticeDetail(@PathVariable("noticeId") Long noticeId) {
        return ResponseEntity.ok(noticeService.getNoticeDetail(noticeId));
    }
}
