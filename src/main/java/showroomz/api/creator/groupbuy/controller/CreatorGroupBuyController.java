package showroomz.api.creator.groupbuy.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.groupbuy.docs.CreatorGroupBuyControllerDocs;
import showroomz.api.creator.groupbuy.dto.CreatorExtensionRejectRequest;
import showroomz.api.creator.groupbuy.dto.CreatorFulfillmentCheckRequest;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyDetailResponse;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyListItem;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyPostRequest;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuySummaryResponse;
import showroomz.api.creator.groupbuy.dto.CreatorSuspensionRequestRequest;
import showroomz.api.creator.groupbuy.service.CreatorGroupBuyCommandService;
import showroomz.api.creator.groupbuy.service.CreatorGroupBuyQueryService;
import showroomz.domain.groupbuy.type.CreatorGroupBuySortType;
import showroomz.domain.groupbuy.type.CreatorGroupBuyTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/creator/group-buys")
@RequiredArgsConstructor
public class CreatorGroupBuyController implements CreatorGroupBuyControllerDocs {

    private final CreatorGroupBuyQueryService queryService;
    private final CreatorGroupBuyCommandService commandService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<CreatorGroupBuyListItem>> getGroupBuys(
            @RequestParam(value = "tab", required = false) CreatorGroupBuyTab tab,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) CreatorGroupBuySortType sort,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getGroupBuys(getCurrentUserEmail(), tab, keyword, sort, pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<CreatorGroupBuySummaryResponse> getSummary() {
        return ResponseEntity.ok(queryService.getSummary(getCurrentUserEmail()));
    }

    @Override
    @GetMapping("/{groupBuyId}")
    public ResponseEntity<CreatorGroupBuyDetailResponse> getGroupBuy(
            @PathVariable Long groupBuyId,
            @RequestParam(value = "tab", required = false) CreatorGroupBuyTab tab,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) CreatorGroupBuySortType sort) {
        return ResponseEntity.ok(queryService.getGroupBuy(getCurrentUserEmail(), groupBuyId, tab, keyword, sort));
    }

    @Override
    @PutMapping("/{groupBuyId}/post/draft")
    public ResponseEntity<CreatorGroupBuyDetailResponse> saveDraft(
            @PathVariable Long groupBuyId, @RequestBody CreatorGroupBuyPostRequest request) {
        return ResponseEntity.ok(commandService.saveDraft(getCurrentUserEmail(), groupBuyId, request));
    }

    @Override
    @PostMapping("/{groupBuyId}/post/submission")
    public ResponseEntity<CreatorGroupBuyDetailResponse> submitPost(
            @PathVariable Long groupBuyId, @RequestBody CreatorGroupBuyPostRequest request) {
        return ResponseEntity.ok(commandService.submitPost(getCurrentUserEmail(), groupBuyId, request));
    }

    @Override
    @PatchMapping("/{groupBuyId}/post")
    public ResponseEntity<CreatorGroupBuyDetailResponse> editPost(
            @PathVariable Long groupBuyId, @RequestBody CreatorGroupBuyPostRequest request) {
        return ResponseEntity.ok(commandService.editPost(getCurrentUserEmail(), groupBuyId, request));
    }

    @Override
    @PostMapping("/{groupBuyId}/extension/acceptance")
    public ResponseEntity<CreatorGroupBuyDetailResponse> acceptExtension(@PathVariable Long groupBuyId) {
        return ResponseEntity.ok(commandService.acceptExtension(getCurrentUserEmail(), groupBuyId));
    }

    @Override
    @PostMapping("/{groupBuyId}/extension/rejection")
    public ResponseEntity<CreatorGroupBuyDetailResponse> rejectExtension(
            @PathVariable Long groupBuyId, @Valid @RequestBody CreatorExtensionRejectRequest request) {
        return ResponseEntity.ok(commandService.rejectExtension(getCurrentUserEmail(), groupBuyId, request));
    }

    @Override
    @PostMapping("/{groupBuyId}/suspension-request")
    public ResponseEntity<CreatorGroupBuyDetailResponse> requestSuspension(
            @PathVariable Long groupBuyId, @Valid @RequestBody CreatorSuspensionRequestRequest request) {
        return ResponseEntity.ok(commandService.requestSuspension(getCurrentUserEmail(), groupBuyId, request));
    }

    @Override
    @PostMapping("/{groupBuyId}/fulfillment-check")
    public ResponseEntity<CreatorGroupBuyDetailResponse> checkFulfillment(
            @PathVariable Long groupBuyId, @Valid @RequestBody CreatorFulfillmentCheckRequest request) {
        return ResponseEntity.ok(commandService.checkFulfillment(getCurrentUserEmail(), groupBuyId, request));
    }

    private String getCurrentUserEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
