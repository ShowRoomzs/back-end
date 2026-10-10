package showroomz.api.admin.thread.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.thread.docs.AdminThreadControllerDocs;
import showroomz.api.admin.thread.dto.AdminThreadDto.ChannelInfo;
import showroomz.api.admin.thread.dto.AdminThreadDto.ChannelListItem;
import showroomz.api.admin.thread.dto.AdminThreadDto.MessageItem;
import showroomz.api.admin.thread.dto.AdminThreadDto.MessageList;
import showroomz.api.admin.thread.dto.AdminThreadDto.ResendNoticeResponse;
import showroomz.api.admin.thread.dto.AdminThreadDto.SendRequest;
import showroomz.api.admin.thread.dto.AdminThreadDto.Summary;
import showroomz.api.admin.thread.service.AdminThreadCommandService;
import showroomz.api.admin.thread.service.AdminThreadQueryService;
import showroomz.api.admin.thread.type.AdminChannelTab;
import showroomz.api.admin.thread.type.AdminIssueState;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.common.attachment.dto.AttachmentDownloadRequest;
import showroomz.api.common.attachment.dto.AttachmentDownloadResponse;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.dto.CompleteAttachmentRequest;
import showroomz.api.common.attachment.dto.PresignRequest;
import showroomz.api.common.attachment.dto.PresignResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminThreadController implements AdminThreadControllerDocs {

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 30;

    private final AdminThreadQueryService queries;
    private final AdminThreadCommandService commands;

    @Override
    @GetMapping("/v1/admin/connections/threads")
    public PageResponse<ChannelListItem> list(@RequestParam(required = false) AdminChannelTab tab,
                                              @RequestParam(required = false) AdminIssueState state,
                                              @RequestParam(required = false) String keyword,
                                              @ModelAttribute PagingRequest paging) {
        // 필수 파라미터 누락은 전역 처리기가 받지 않아 500이 된다 — 여기서 400으로 돌린다.
        if (tab == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return queries.list(tab, state, keyword, paging);
    }

    @Override
    @GetMapping("/v1/admin/connections/summary")
    public Summary summary() {
        return queries.summary();
    }

    @Override
    @GetMapping("/v1/admin/threads/{threadId}/info")
    public ChannelInfo info(@PathVariable Long threadId) {
        return queries.info(threadId);
    }

    @Override
    @GetMapping("/v1/admin/threads/{threadId}/messages")
    public MessageList messages(@PathVariable Long threadId,
                                @RequestParam(required = false) Long cursor,
                                @RequestParam(required = false) Integer size) {
        int pageSize = (size == null || size <= 0) ? DEFAULT_MESSAGE_PAGE_SIZE : size;
        return queries.messages(threadId, cursor, pageSize);
    }

    @Override
    @PostMapping("/v1/admin/threads/{threadId}/messages")
    public ResponseEntity<MessageItem> send(@PathVariable Long threadId,
                                            @Valid @RequestBody SendRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        AdminThreadCommandService.SendOutcome outcome = commands.send(threadId, operator(principal), request);
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(outcome.item());
    }

    @Override
    @PostMapping("/v1/admin/threads/{threadId}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long threadId) {
        commands.markRead(threadId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/v1/admin/threads/{threadId}/attachments/presign")
    public ResponseEntity<PresignResponse> presign(@PathVariable Long threadId,
                                                   @Valid @RequestBody PresignRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commands.presign(threadId, operator(principal), request));
    }

    @Override
    @PatchMapping("/v1/admin/attachments/{attachmentId}/complete")
    public AttachmentSummary completeUpload(@PathVariable Long attachmentId,
                                            @Valid @RequestBody(required = false) CompleteAttachmentRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        CompleteAttachmentRequest body = request == null ? new CompleteAttachmentRequest() : request;
        return commands.completeUpload(attachmentId, operator(principal), body);
    }

    @Override
    @PostMapping("/v1/admin/attachments/download")
    public List<AttachmentDownloadResponse> download(@Valid @RequestBody AttachmentDownloadRequest request,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return commands.download(request.getAttachmentIds(), operator(principal));
    }

    @Override
    @PostMapping("/v1/admin/threads/{threadId}/cards/{messageId}/resend-notice")
    public ResendNoticeResponse sendResendNotice(@PathVariable Long threadId, @PathVariable Long messageId,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return commands.sendResendNotice(threadId, messageId, operator(principal));
    }

    private Long operator(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
