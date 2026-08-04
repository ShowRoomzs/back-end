package showroomz.api.admin.creator.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.creator.dto.CreatorApplicationDetailResponse;
import showroomz.api.admin.creator.dto.CreatorApplicationListResponse;
import showroomz.api.admin.creator.dto.CreatorApplicationRejectRequest;
import showroomz.api.admin.creator.dto.CreatorApplicationSearchCondition;
import showroomz.api.admin.creator.docs.AdminCreatorApplicationControllerDocs;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.auth.service.CreatorApplicationService;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/admin/creator/applications")
@RequiredArgsConstructor
public class AdminCreatorApplicationController implements AdminCreatorApplicationControllerDocs {

    private final CreatorApplicationService creatorApplicationService;

    @Override
    @GetMapping
    public ResponseEntity<CreatorApplicationListResponse> getApplications(
            @ParameterObject @ModelAttribute CreatorApplicationSearchCondition condition,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(creatorApplicationService.getApplications(condition, pagingRequest));
    }

    @Override
    @GetMapping("/{applicationId}")
    public ResponseEntity<CreatorApplicationDetailResponse> getApplicationDetail(
            @PathVariable("applicationId") Long applicationId) {
        return ResponseEntity.ok(creatorApplicationService.getApplicationDetail(applicationId));
    }

    @Override
    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<Void> approveApplication(
            @PathVariable("applicationId") Long applicationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        creatorApplicationService.approve(applicationId, requireAdminUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<Void> rejectApplication(
            @PathVariable("applicationId") Long applicationId,
            @Valid @RequestBody CreatorApplicationRejectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        creatorApplicationService.reject(applicationId, request, requireAdminUserId(principal));
        return ResponseEntity.noContent().build();
    }

    private Long requireAdminUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
