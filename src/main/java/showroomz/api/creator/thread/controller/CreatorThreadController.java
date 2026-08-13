package showroomz.api.creator.thread.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.common.attachment.dto.AttachmentDownloadResponse;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.dto.CompleteAttachmentRequest;
import showroomz.api.common.attachment.dto.PresignRequest;
import showroomz.api.common.attachment.dto.PresignResponse;
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
public class CreatorThreadController implements CreatorThreadControllerDocs {

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 30;

    private final CreatorThreadService creatorThreadService;

    @Override
    @GetMapping("/v1/creator/connections/threads")
    public ResponseEntity<PageResponse<ThreadListItem>> getThreads(
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(creatorThreadService.getThreads(getCurrentUserEmail(), keyword, pagingRequest));
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

    @Override
    @PostMapping("/v1/creator/threads/{threadId}/attachments/presign")
    public ResponseEntity<PresignResponse> createPresignedUpload(
            @PathVariable("threadId") Long threadId,
            @Valid @RequestBody PresignRequest request) {
        PresignResponse response = creatorThreadService.createPresignedUpload(getCurrentUserEmail(), threadId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @PatchMapping("/v1/creator/attachments/{attachmentId}/complete")
    public ResponseEntity<AttachmentSummary> completeUpload(
            @PathVariable("attachmentId") Long attachmentId,
            @Valid @RequestBody(required = false) CompleteAttachmentRequest request) {
        CompleteAttachmentRequest body = request == null ? new CompleteAttachmentRequest() : request;
        AttachmentSummary response = creatorThreadService.completeUpload(getCurrentUserEmail(), attachmentId, body);
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/v1/creator/attachments/{attachmentId}/download")
    public ResponseEntity<AttachmentDownloadResponse> getDownloadUrl(
            @PathVariable("attachmentId") Long attachmentId) {
        return ResponseEntity.ok(creatorThreadService.getDownloadUrl(getCurrentUserEmail(), attachmentId));
    }

    private String getCurrentUserEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ((UserPrincipal) principal).getUsername();
    }
}
