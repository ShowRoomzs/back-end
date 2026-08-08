package showroomz.api.seller.thread.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.thread.docs.SellerThreadControllerDocs;
import showroomz.api.seller.thread.dto.MessageItem;
import showroomz.api.seller.thread.dto.MessageListResponse;
import showroomz.api.seller.thread.dto.SendMessageRequest;
import showroomz.api.seller.thread.dto.ThreadListItem;
import showroomz.api.seller.thread.dto.ThreadSummaryResponse;
import showroomz.api.seller.thread.service.SellerThreadService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequiredArgsConstructor
@Hidden
public class SellerThreadController implements SellerThreadControllerDocs {

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 30;

    private final SellerThreadService sellerThreadService;

    @Override
    @GetMapping("/v1/seller/connections/threads")
    public ResponseEntity<PageResponse<ThreadListItem>> getThreads(@ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(sellerThreadService.getThreads(getCurrentSellerEmail(), pagingRequest));
    }

    @Override
    @GetMapping("/v1/seller/connections/summary")
    public ResponseEntity<ThreadSummaryResponse> getSummary() {
        return ResponseEntity.ok(sellerThreadService.getSummary(getCurrentSellerEmail()));
    }

    @Override
    @GetMapping("/v1/seller/threads/{threadId}/messages")
    public ResponseEntity<MessageListResponse> getMessages(
            @PathVariable("threadId") Long threadId,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        int pageSize = (size == null || size <= 0) ? DEFAULT_MESSAGE_PAGE_SIZE : size;
        return ResponseEntity.ok(sellerThreadService.getMessages(getCurrentSellerEmail(), threadId, cursor, pageSize));
    }

    @Override
    @PostMapping("/v1/seller/threads/{threadId}/messages")
    public ResponseEntity<MessageItem> sendMessage(
            @PathVariable("threadId") Long threadId,
            @Valid @RequestBody SendMessageRequest request) {
        SellerThreadService.SendMessageOutcome outcome =
                sellerThreadService.sendMessage(getCurrentSellerEmail(), threadId, request);
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(outcome.item());
    }

    @Override
    @PostMapping("/v1/seller/threads/{threadId}/read")
    public ResponseEntity<Void> markRead(@PathVariable("threadId") Long threadId) {
        sellerThreadService.markRead(getCurrentSellerEmail(), threadId);
        return ResponseEntity.noContent().build();
    }

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }
}
