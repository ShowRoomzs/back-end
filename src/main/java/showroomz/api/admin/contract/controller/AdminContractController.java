package showroomz.api.admin.contract.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.contract.docs.AdminContractControllerDocs;
import showroomz.api.admin.contract.dto.AdminContractDto.*;
import showroomz.api.admin.contract.service.*;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.contract.type.*;
import showroomz.global.dto.*;
import showroomz.global.error.exception.*;

@RestController
@RequestMapping("/v1/admin/contracts")
@RequiredArgsConstructor
public class AdminContractController implements AdminContractControllerDocs {
    private final AdminContractQueryService queries;
    private final AdminContractCommandService commands;
    private final AdminContractDocumentService documents;
    private final ContractDraftService drafts;

    @GetMapping
    public PageResponse<ListItem> list(@RequestParam(defaultValue = "ALL") AdminContractTab tab,
            @RequestParam(required = false) AdminContractQueue queue, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "REVIEW_REQUESTED_ASC") AdminContractSort sort, @ModelAttribute PagingRequest paging) {
        if (paging.getSize() != 20 && paging.getSize() != 50) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        return queries.list(tab, queue, keyword, sort, paging.toPageable(Sort.unsorted()));
    }
    @GetMapping("/summary")
    public Summary summary() { return queries.summary(); }
    @GetMapping("/{id}")
    public Detail detail(@PathVariable Long id) { return queries.detail(id); }
    @PostMapping("/{id}/review/approve")
    public ProcessResponse approve(@PathVariable Long id, @Valid @RequestBody ApproveRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return commands.approve(id, operator(principal), request);
    }
    @PostMapping("/{id}/review/reject")
    public ProcessResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return commands.reject(id, operator(principal), request);
    }
    @PutMapping("/{id}/signatures")
    public ProcessResponse signatures(@PathVariable Long id, @Valid @RequestBody SignatureRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return commands.updateSignatures(id, operator(principal), request);
    }
    @PostMapping("/{id}/conclude")
    public ProcessResponse conclude(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) { return commands.conclude(id, operator(principal)); }
    @PostMapping("/{id}/expire")
    public ProcessResponse expire(@PathVariable Long id, @Valid @RequestBody ExpireRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return commands.expire(id, operator(principal), request);
    }
    @PostMapping("/{id}/cancel")
    public ProcessResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return commands.cancel(id, operator(principal), request);
    }
    @PostMapping("/{id}/resend/handle")
    public ProcessResponse resend(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) { return commands.handleResend(id, operator(principal)); }
    @PostMapping("/{id}/documents/presign")
    public PresignResponse presign(@PathVariable Long id, @Valid @RequestBody PresignRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return documents.presign(id, operator(principal), request);
    }
    @PostMapping("/{id}/documents")
    public DownloadResponse register(@PathVariable Long id, @Valid @RequestBody RegisterDocumentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return documents.register(id, operator(principal), request);
    }
    @DeleteMapping("/{id}/documents/{type}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @PathVariable ContractDocumentType type, @AuthenticationPrincipal UserPrincipal principal) {
        documents.delete(id, operator(principal), type);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}/documents/{type}")
    public DownloadResponse download(@PathVariable Long id, @PathVariable ContractDocumentType type) { return documents.download(id, type); }
    @GetMapping("/{id}/document-draft")
    public DownloadResponse draft(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) { return drafts.download(id, operator(principal)); }
    private Long operator(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        return principal.getUserId();
    }
}
