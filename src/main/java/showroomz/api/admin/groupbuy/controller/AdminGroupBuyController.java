package showroomz.api.admin.groupbuy.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.groupbuy.docs.AdminGroupBuyControllerDocs;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDetailResponse;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDto;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyListItem;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuySummaryResponse;
import showroomz.api.admin.groupbuy.service.AdminGroupBuyCommandService;
import showroomz.api.admin.groupbuy.service.AdminGroupBuyQueryService;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.groupbuy.type.AdminGroupBuySortType;
import showroomz.domain.groupbuy.type.AdminGroupBuyTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/group-buys")
@RequiredArgsConstructor
public class AdminGroupBuyController implements AdminGroupBuyControllerDocs {

    private final AdminGroupBuyQueryService queryService;
    private final AdminGroupBuyCommandService commandService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AdminGroupBuyListItem>> getGroupBuys(
            @RequestParam(value = "tab", required = false) AdminGroupBuyTab tab,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) AdminGroupBuySortType sort,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getGroupBuys(tab, keyword, sort, pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<AdminGroupBuySummaryResponse> getSummary() {
        return ResponseEntity.ok(queryService.getSummary());
    }

    @Override
    @GetMapping("/{groupBuyId}")
    public ResponseEntity<AdminGroupBuyDetailResponse> getGroupBuy(
            @PathVariable Long groupBuyId,
            @RequestParam(value = "tab", required = false) AdminGroupBuyTab tab,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) AdminGroupBuySortType sort) {
        return ResponseEntity.ok(queryService.getGroupBuy(groupBuyId, tab, keyword, sort));
    }

    @Override
    @GetMapping("/{groupBuyId}/post/revisions")
    public ResponseEntity<List<AdminGroupBuyDto.PostRevisionItem>> getPostRevisions(@PathVariable Long groupBuyId) {
        return ResponseEntity.ok(queryService.getPostRevisions(groupBuyId));
    }

    @Override
    @GetMapping("/{groupBuyId}/admin-suspension/notice-options")
    public ResponseEntity<AdminGroupBuyDto.NoticeOptionsResponse> getNoticeOptions(@PathVariable Long groupBuyId) {
        return ResponseEntity.ok(queryService.getNoticeOptions(groupBuyId));
    }

    @Override
    @GetMapping("/{groupBuyId}/admin-suspension/attachments/{attachmentId}")
    public ResponseEntity<AdminGroupBuyDto.AttachmentUrlResponse> getAppealAttachmentUrl(
            @PathVariable Long groupBuyId, @PathVariable Long attachmentId) {
        return ResponseEntity.ok(queryService.getAppealAttachmentUrl(groupBuyId, attachmentId));
    }

    @Override
    @PostMapping("/{groupBuyId}/open-review/approve")
    public ResponseEntity<AdminGroupBuyDto.ActionResponse> approveOpen(
            @PathVariable Long groupBuyId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.approveOpen(groupBuyId, requireOperatorId(principal)));
    }

    @Override
    @PostMapping("/{groupBuyId}/open-review/reject")
    public ResponseEntity<AdminGroupBuyDto.ActionResponse> rejectOpen(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.OpenRejectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.rejectOpen(groupBuyId, requireOperatorId(principal), request));
    }

    @Override
    @PostMapping("/{groupBuyId}/post/hide")
    public ResponseEntity<AdminGroupBuyDto.ActionResponse> hidePost(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.PostHideRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.hidePost(groupBuyId, requireOperatorId(principal), request));
    }

    @Override
    @PostMapping("/{groupBuyId}/post/unhide")
    public ResponseEntity<AdminGroupBuyDto.ActionResponse> unhidePost(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.PostUnhideRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.unhidePost(groupBuyId, requireOperatorId(principal), request));
    }

    @Override
    @PostMapping("/{groupBuyId}/admin-suspension/notice")
    public ResponseEntity<AdminGroupBuyDto.ActionResponse> noticeSuspension(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.SuspensionNoticeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.noticeSuspension(groupBuyId, requireOperatorId(principal), request));
    }

    @Override
    @PostMapping("/{groupBuyId}/admin-suspension/execute")
    public ResponseEntity<AdminGroupBuyDto.ActionResponse> executeSuspension(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.SuspensionExecuteRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.executeSuspension(groupBuyId, requireOperatorId(principal), request));
    }

    @Override
    @PostMapping("/{groupBuyId}/admin-suspension/withdraw")
    public ResponseEntity<AdminGroupBuyDto.ActionResponse> withdrawSuspension(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.SuspensionWithdrawRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.withdrawSuspension(groupBuyId, requireOperatorId(principal), request));
    }

    @Override
    @PostMapping("/{groupBuyId}/admin-suspension/emergency")
    public ResponseEntity<AdminGroupBuyDto.ActionResponse> emergencySuspend(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.EmergencySuspensionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.emergencySuspend(groupBuyId, requireOperatorId(principal), request));
    }

    @Override
    @PostMapping("/{groupBuyId}/change-requests/{requestId}/approve")
    public ResponseEntity<AdminGroupBuyDto.ActionResponse> approveRequest(
            @PathVariable Long groupBuyId, @PathVariable Long requestId,
            @Valid @RequestBody AdminGroupBuyDto.ChangeRequestDecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.approveRequest(groupBuyId, requestId, requireOperatorId(principal), request));
    }

    @Override
    @PostMapping("/{groupBuyId}/change-requests/{requestId}/reject")
    public ResponseEntity<AdminGroupBuyDto.ActionResponse> rejectRequest(
            @PathVariable Long groupBuyId, @PathVariable Long requestId,
            @Valid @RequestBody AdminGroupBuyDto.ChangeRequestDecisionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.rejectRequest(groupBuyId, requestId, requireOperatorId(principal), request));
    }

    @Override
    @PostMapping("/{groupBuyId}/issues")
    public ResponseEntity<AdminGroupBuyDto.IssueOpenResponse> openIssue(
            @PathVariable Long groupBuyId, @Valid @RequestBody AdminGroupBuyDto.IssueOpenRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.openIssue(groupBuyId, requireOperatorId(principal), request));
    }

    @Override
    @PostMapping("/{groupBuyId}/settlement/confirm")
    public ResponseEntity<Void> confirmSettlement(
            @PathVariable Long groupBuyId, @AuthenticationPrincipal UserPrincipal principal) {
        commandService.confirmSettlement(groupBuyId, requireOperatorId(principal));
        return ResponseEntity.noContent().build();
    }

    private Long requireOperatorId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
