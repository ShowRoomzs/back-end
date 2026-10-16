package showroomz.api.admin.creator.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import showroomz.api.admin.creator.docs.AdminCreatorControllerDocs;
import showroomz.api.admin.creator.dto.AdminCreatorDto.*;
import showroomz.api.admin.creator.service.AdminCreatorCommandService;
import showroomz.api.admin.creator.service.AdminCreatorMemoService;
import showroomz.api.admin.creator.service.AdminCreatorPrivacyService;
import showroomz.api.admin.creator.service.AdminCreatorQueryService;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.member.brand.type.SanctionReleaseReason;
import showroomz.domain.member.creator.type.CreatorPrivacyAccessReason;
import showroomz.domain.member.creator.type.CreatorSuspensionReason;
import showroomz.domain.member.creator.type.CreatorWithdrawalRejectReason;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/v1/admin/creators")
@RequiredArgsConstructor
public class AdminCreatorController implements AdminCreatorControllerDocs {

    private final AdminCreatorQueryService queries;
    private final AdminCreatorCommandService commands;
    private final AdminCreatorMemoService memos;
    private final AdminCreatorPrivacyService privacy;

    @Override
    @GetMapping("/reason-options")
    public ReasonOptions reasonOptions() {
        List<Option> suspension = Arrays.stream(CreatorSuspensionReason.values())
                .map(r -> new Option(r.name(), r.getLabel(), r.isDetailRequired(), r.isHoldsPayout())).toList();
        List<Option> release = Arrays.stream(SanctionReleaseReason.values())
                .filter(r -> r != SanctionReleaseReason.WITHDRAWN)
                .map(r -> new Option(r.name(), r.getLabel(), false, null)).toList();
        List<Option> reject = Arrays.stream(CreatorWithdrawalRejectReason.values())
                .map(r -> new Option(r.name(), r.getLabel(), r.isDetailRequired(), null)).toList();
        List<Option> unmask = Arrays.stream(CreatorPrivacyAccessReason.values())
                .map(r -> new Option(r.name(), r.getLabel(), false, null)).toList();
        return new ReasonOptions(suspension, release, reject, unmask);
    }

    @Override
    @GetMapping("/{creatorId}")
    public Detail detail(@PathVariable Long creatorId, @AuthenticationPrincipal UserPrincipal principal) {
        return queries.detail(creatorId, operator(principal));
    }

    @Override
    @GetMapping("/{creatorId}/overview")
    public Overview overview(@PathVariable Long creatorId) {
        return queries.overview(creatorId);
    }

    @Override
    @GetMapping("/{creatorId}/settlements")
    public SettlementList settlements(@PathVariable Long creatorId, @RequestParam(defaultValue = "5") int size) {
        return queries.settlements(creatorId, size);
    }

    @Override
    @GetMapping("/{creatorId}/contracts")
    public PageResponse<ContractRow> contracts(@PathVariable Long creatorId, @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return queries.contracts(creatorId, page, size);
    }

    @Override
    @GetMapping("/{creatorId}/connections")
    public ConnectionList connections(@PathVariable Long creatorId) {
        return queries.connections(creatorId);
    }

    @Override
    @GetMapping("/{creatorId}/histories")
    public HistoryPage histories(@PathVariable Long creatorId, @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return queries.histories(creatorId, page, size);
    }

    @Override
    @GetMapping("/{creatorId}/memos")
    public MemoPage memos(@PathVariable Long creatorId, @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal UserPrincipal principal) {
        return memos.list(creatorId, operator(principal), page, size);
    }

    @Override
    @PostMapping("/{creatorId}/memos")
    public Memo createMemo(@PathVariable Long creatorId, @Valid @RequestBody MemoRequest request,
                           @AuthenticationPrincipal UserPrincipal principal) {
        return memos.create(creatorId, operator(principal), request.content());
    }

    @Override
    @PatchMapping("/{creatorId}/memos/{memoId}")
    public Memo updateMemo(@PathVariable Long creatorId, @PathVariable Long memoId, @Valid @RequestBody MemoRequest request,
                           @AuthenticationPrincipal UserPrincipal principal) {
        return memos.update(creatorId, memoId, operator(principal), request.content());
    }

    @Override
    @DeleteMapping("/{creatorId}/memos/{memoId}")
    public ResponseEntity<Void> deleteMemo(@PathVariable Long creatorId, @PathVariable Long memoId,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        memos.delete(creatorId, memoId, operator(principal));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{creatorId}/privacy-unmask")
    public Detail unmask(@PathVariable Long creatorId, @Valid @RequestBody UnmaskRequest request,
                         @AuthenticationPrincipal UserPrincipal principal) {
        return privacy.unmask(creatorId, operator(principal), request.reason());
    }

    @Override
    @PostMapping("/{creatorId}/privacy-lock")
    public ResponseEntity<Void> lock(@PathVariable Long creatorId, @AuthenticationPrincipal UserPrincipal principal) {
        privacy.lock(creatorId, operator(principal));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{creatorId}/suspend")
    public Detail suspend(@PathVariable Long creatorId, @Valid @RequestBody SuspendRequest request,
                          @AuthenticationPrincipal UserPrincipal principal) {
        return commands.suspend(creatorId, operator(principal), request);
    }

    @Override
    @PostMapping("/{creatorId}/release")
    public Detail release(@PathVariable Long creatorId, @Valid @RequestBody(required = false) ReleaseRequest request,
                          @AuthenticationPrincipal UserPrincipal principal) {
        return commands.release(creatorId, operator(principal), request);
    }

    @Override
    @PostMapping("/{creatorId}/withdrawal-requests/{requestId}/approve")
    public Detail approveWithdrawal(@PathVariable Long creatorId, @PathVariable Long requestId,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return commands.approveWithdrawal(creatorId, requestId, operator(principal));
    }

    @Override
    @PostMapping("/{creatorId}/withdrawal-requests/{requestId}/reject")
    public Detail rejectWithdrawal(@PathVariable Long creatorId, @PathVariable Long requestId,
                                   @Valid @RequestBody RejectWithdrawalRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return commands.rejectWithdrawal(creatorId, requestId, operator(principal), request);
    }

    private static Long operator(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
