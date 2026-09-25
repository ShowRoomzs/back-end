package showroomz.api.seller.groupbuy.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.groupbuy.docs.SellerGroupBuyControllerDocs;
import showroomz.api.seller.groupbuy.dto.GroupBuyAppealAttachmentPresignRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyAppealAttachmentPresignResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuyAppealSubmitRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyDetailResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuyEarlyCloseRequestRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyExtensionRequestRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyFulfillmentCheckRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyIssueOpenRequest;
import showroomz.api.seller.groupbuy.dto.GroupBuyIssueOpenResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuyListItem;
import showroomz.api.seller.groupbuy.dto.GroupBuySummaryResponse;
import showroomz.api.seller.groupbuy.dto.GroupBuySuspensionRequestRequest;
import showroomz.api.seller.groupbuy.service.SellerGroupBuyCommandService;
import showroomz.api.seller.groupbuy.service.SellerGroupBuyQueryService;
import showroomz.domain.groupbuy.type.GroupBuySortType;
import showroomz.domain.groupbuy.type.GroupBuyTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/seller/group-buys")
@RequiredArgsConstructor
public class SellerGroupBuyController implements SellerGroupBuyControllerDocs {

    private final SellerGroupBuyQueryService queryService;
    private final SellerGroupBuyCommandService commandService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<GroupBuyListItem>> getGroupBuys(
            @RequestParam(value = "tab", required = false) GroupBuyTab tab,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) GroupBuySortType sort,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getGroupBuys(getCurrentSellerEmail(), tab, keyword, sort, pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<GroupBuySummaryResponse> getSummary() {
        return ResponseEntity.ok(queryService.getSummary(getCurrentSellerEmail()));
    }

    @Override
    @GetMapping("/{groupBuyId}")
    public ResponseEntity<GroupBuyDetailResponse> getGroupBuy(@PathVariable Long groupBuyId) {
        return ResponseEntity.ok(queryService.getGroupBuy(getCurrentSellerEmail(), groupBuyId));
    }

    @Override
    @PostMapping("/{groupBuyId}/stock-confirmation")
    public ResponseEntity<GroupBuyDetailResponse> confirmStock(@PathVariable Long groupBuyId) {
        return ResponseEntity.ok(commandService.confirmStock(getCurrentSellerEmail(), groupBuyId));
    }

    @Override
    @PostMapping("/{groupBuyId}/extension-request")
    public ResponseEntity<GroupBuyDetailResponse> requestExtension(
            @PathVariable Long groupBuyId, @Valid @RequestBody GroupBuyExtensionRequestRequest request) {
        return ResponseEntity.ok(commandService.requestExtension(getCurrentSellerEmail(), groupBuyId, request));
    }

    @Override
    @PostMapping("/{groupBuyId}/early-close-request")
    public ResponseEntity<GroupBuyDetailResponse> requestEarlyClose(
            @PathVariable Long groupBuyId, @Valid @RequestBody GroupBuyEarlyCloseRequestRequest request) {
        return ResponseEntity.ok(commandService.requestEarlyClose(getCurrentSellerEmail(), groupBuyId, request));
    }

    @Override
    @PostMapping("/{groupBuyId}/suspension-request")
    public ResponseEntity<GroupBuyDetailResponse> requestSuspension(
            @PathVariable Long groupBuyId, @Valid @RequestBody GroupBuySuspensionRequestRequest request) {
        return ResponseEntity.ok(commandService.requestSuspension(getCurrentSellerEmail(), groupBuyId, request));
    }

    @Override
    @PostMapping("/{groupBuyId}/appeal/attachments")
    public ResponseEntity<GroupBuyAppealAttachmentPresignResponse> presignAppealAttachment(
            @PathVariable Long groupBuyId, @Valid @RequestBody GroupBuyAppealAttachmentPresignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commandService.presignAppealAttachment(getCurrentSellerEmail(), groupBuyId, request));
    }

    @Override
    @PostMapping("/{groupBuyId}/appeal")
    public ResponseEntity<GroupBuyDetailResponse> submitAppeal(
            @PathVariable Long groupBuyId, @Valid @RequestBody GroupBuyAppealSubmitRequest request) {
        return ResponseEntity.ok(commandService.submitAppeal(getCurrentSellerEmail(), groupBuyId, request));
    }

    @Override
    @PostMapping("/{groupBuyId}/issues")
    public ResponseEntity<GroupBuyIssueOpenResponse> openIssue(
            @PathVariable Long groupBuyId, @Valid @RequestBody GroupBuyIssueOpenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commandService.openIssue(getCurrentSellerEmail(), groupBuyId, request));
    }

    @Override
    @PostMapping("/{groupBuyId}/fulfillment-check")
    public ResponseEntity<GroupBuyDetailResponse> checkFulfillment(
            @PathVariable Long groupBuyId, @Valid @RequestBody GroupBuyFulfillmentCheckRequest request) {
        return ResponseEntity.ok(commandService.checkFulfillment(getCurrentSellerEmail(), groupBuyId, request));
    }

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
