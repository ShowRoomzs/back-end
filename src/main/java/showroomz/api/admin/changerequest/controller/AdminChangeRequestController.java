package showroomz.api.admin.changerequest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.changerequest.docs.AdminChangeRequestControllerDocs;
import showroomz.api.admin.changerequest.dto.AdminChangeRequestDto;
import showroomz.api.admin.changerequest.service.AdminChangeRequestService;
import showroomz.api.admin.changerequest.type.AdminChangeRequestStatusFilter;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/change-requests")
@RequiredArgsConstructor
public class AdminChangeRequestController implements AdminChangeRequestControllerDocs {

    private final AdminChangeRequestService adminChangeRequestService;

    @Override
    @GetMapping
    public ResponseEntity<AdminChangeRequestDto.ListResponse> getList(
            @RequestParam(value = "status", defaultValue = "PENDING") AdminChangeRequestStatusFilter status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest) {
        Pageable pageable = pagingRequest.toPageable(Sort.unsorted());
        return ResponseEntity.ok(adminChangeRequestService.getList(status, keyword, pageable));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<AdminChangeRequestDto.SummaryResponse> getSummary() {
        return ResponseEntity.ok(adminChangeRequestService.getSummary());
    }

    @Override
    @GetMapping("/reject-reasons")
    public ResponseEntity<List<AdminChangeRequestDto.RejectReasonOption>> getRejectReasons(
            @RequestParam("type") ChangeRequestType type) {
        return ResponseEntity.ok(adminChangeRequestService.getRejectReasons(type));
    }

    @Override
    @GetMapping("/{requestId}")
    public ResponseEntity<AdminChangeRequestDto.DetailResponse> getDetail(
            @PathVariable("requestId") Long requestId,
            @RequestParam(value = "status", defaultValue = "PENDING") AdminChangeRequestStatusFilter status) {
        return ResponseEntity.ok(adminChangeRequestService.getDetail(requestId, status));
    }

    @Override
    @PostMapping("/{requestId}/approve")
    public ResponseEntity<AdminChangeRequestDto.ProcessResponse> approve(
            @PathVariable("requestId") Long requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminChangeRequestService.approve(requestId, requireOperatorId(principal)));
    }

    @Override
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<AdminChangeRequestDto.ProcessResponse> reject(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody AdminChangeRequestDto.RejectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminChangeRequestService.reject(requestId, requireOperatorId(principal), request));
    }

    private Long requireOperatorId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
