package showroomz.api.admin.notice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.admin.notice.docs.AdminNoticeControllerDocs;
import showroomz.api.admin.notice.dto.AdminNoticeDetailResponse;
import showroomz.api.admin.notice.dto.AdminNoticeListResponse;
import showroomz.api.admin.notice.dto.AdminNoticeRegisterRequest;
import showroomz.api.admin.notice.service.AdminNoticeService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.net.URI;

@RestController
@RequestMapping("/v1/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController implements AdminNoticeControllerDocs {

    private final AdminNoticeService adminNoticeService;

    @Override
    @PostMapping
    public ResponseEntity<Void> registerNotice(@Valid @RequestBody AdminNoticeRegisterRequest request) {
        Long noticeId = adminNoticeService.registerNotice(request);
        return ResponseEntity.created(URI.create("/v1/common/notices/" + noticeId)).build();
    }

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AdminNoticeListResponse>> getNotices(
            @RequestParam(name = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest) {
        PageResponse<AdminNoticeListResponse> response = adminNoticeService.getNotices(keyword, pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{noticeId}")
    public ResponseEntity<AdminNoticeDetailResponse> getNotice(@PathVariable("noticeId") Long noticeId) {
        AdminNoticeDetailResponse response = adminNoticeService.getNotice(noticeId);
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/{noticeId}")
    public ResponseEntity<Void> deleteNotice(@PathVariable("noticeId") Long noticeId) {
        adminNoticeService.deleteNotice(noticeId);
        return ResponseEntity.noContent().build();
    }
}
