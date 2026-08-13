package showroomz.api.admin.notice.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.admin.notice.docs.AdminNoticeControllerDocs;
import showroomz.api.admin.notice.dto.AdminNoticeDetailResponse;
import showroomz.api.admin.notice.dto.AdminNoticeListRequest;
import showroomz.api.admin.notice.dto.AdminNoticePageResponse;
import showroomz.api.admin.notice.dto.AdminNoticeRegisterRequest;
import showroomz.api.admin.notice.dto.AdminNoticeUpdateRequest;
import showroomz.api.admin.notice.service.AdminNoticeService;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.net.URI;
import java.util.Objects;

@RestController
@RequestMapping("/v1/admin/notices")
@RequiredArgsConstructor
@Hidden
public class AdminNoticeController implements AdminNoticeControllerDocs {

    private final AdminNoticeService adminNoticeService;

    @Override
    @PostMapping
    public ResponseEntity<Void> registerNotice(
            @Valid @RequestBody AdminNoticeRegisterRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long noticeId = adminNoticeService.registerNotice(request, requireOperatorId(principal));
        URI location = Objects.requireNonNull(URI.create("/v1/admin/notices/" + noticeId));
        return ResponseEntity.created(location).build();
    }

    @Override
    @GetMapping
    public ResponseEntity<AdminNoticePageResponse> getNotices(
            @ModelAttribute AdminNoticeListRequest request,
            @ModelAttribute PagingRequest pagingRequest) {
        AdminNoticePageResponse response = adminNoticeService.getNotices(request, pagingRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{noticeId}")
    public ResponseEntity<AdminNoticeDetailResponse> getNotice(@PathVariable("noticeId") Long noticeId) {
        AdminNoticeDetailResponse response = adminNoticeService.getNotice(noticeId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PutMapping("/{noticeId}")
    public ResponseEntity<Void> updateNotice(
            @PathVariable("noticeId") Long noticeId,
            @Valid @RequestBody AdminNoticeUpdateRequest request) {
        adminNoticeService.updateNotice(noticeId, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{noticeId}/end")
    public ResponseEntity<Void> endNotice(@PathVariable("noticeId") Long noticeId) {
        adminNoticeService.endNotice(noticeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{noticeId}/publish")
    public ResponseEntity<Void> publishNotice(@PathVariable("noticeId") Long noticeId) {
        adminNoticeService.publishNotice(noticeId);
        return ResponseEntity.noContent().build();
    }

    private Long requireOperatorId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
