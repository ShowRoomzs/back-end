package showroomz.api.creator.thread.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.thread.docs.CreatorThreadControllerDocs;
import showroomz.api.creator.thread.dto.MessageItem;
import showroomz.api.creator.thread.dto.MessageListResponse;
import showroomz.api.creator.thread.dto.SendMessageRequest;
import showroomz.api.creator.thread.dto.ThreadListItem;
import showroomz.api.creator.thread.dto.ThreadSummaryResponse;
import showroomz.api.creator.thread.service.CreatorThreadService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequiredArgsConstructor
@Hidden
public class CreatorThreadController implements CreatorThreadControllerDocs {

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 30;

    private final CreatorThreadService creatorThreadService;

    @Override
    @GetMapping("/v1/creator/connections/threads")
    public ResponseEntity<PageResponse<ThreadListItem>> getThreads(@ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(creatorThreadService.getThreads(getCurrentUserEmail(), pagingRequest));
    }

    @Override
    @GetMapping("/v1/creator/connections/summary")
    public ResponseEntity<ThreadSummaryResponse> getSummary() {
        return ResponseEntity.ok(creatorThreadService.getSummary(getCurrentUserEmail()));
    }

    @Override
    @GetMapping("/v1/creator/threads/{threadId}/messages")
    public ResponseEntity<MessageListResponse> getMessages(
            @PathVariable("threadId") Long threadId,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        int pageSize = (size == null || size <= 0) ? DEFAULT_MESSAGE_PAGE_SIZE : size;
        return ResponseEntity.ok(creatorThreadService.getMessages(getCurrentUserEmail(), threadId, cursor, pageSize));
    }

    @Override
    @PostMapping("/v1/creator/threads/{threadId}/messages")
    public ResponseEntity<MessageItem> sendMessage(
            @PathVariable("threadId") Long threadId,
            @Valid @RequestBody SendMessageRequest request) {
        CreatorThreadService.SendMessageOutcome outcome =
                creatorThreadService.sendMessage(getCurrentUserEmail(), threadId, request);
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(outcome.item());
    }

    @Override
    @PostMapping("/v1/creator/threads/{threadId}/read")
    public ResponseEntity<Void> markRead(@PathVariable("threadId") Long threadId) {
        creatorThreadService.markRead(getCurrentUserEmail(), threadId);
        return ResponseEntity.noContent().build();
    }

    private String getCurrentUserEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }
}
