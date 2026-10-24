package showroomz.api.admin.brand.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.brand.docs.AdminBrandControllerDocs;
import showroomz.api.admin.brand.dto.AdminBrandDto;
import showroomz.api.admin.brand.service.AdminBrandCommandService;
import showroomz.api.admin.brand.service.AdminBrandMemoService;
import showroomz.api.admin.brand.service.AdminBrandQueryService;
import showroomz.api.admin.common.AdminMemberListSort;
import showroomz.api.admin.common.AdminMemberListTab;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/admin/brands")
@RequiredArgsConstructor
public class AdminBrandController implements AdminBrandControllerDocs {

    private final AdminBrandQueryService queries;
    private final AdminBrandCommandService commands;
    private final AdminBrandMemoService memos;

    @Override
    @GetMapping
    public AdminBrandDto.ListResponse list(@RequestParam(required = false) AdminMemberListTab tab,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) AdminMemberListSort sort,
                                           @ModelAttribute PagingRequest paging) {
        return queries.list(tab, keyword, sort, paging);
    }

    @Override
    @GetMapping("/reason-options")
    public AdminBrandDto.ReasonOptions reasonOptions() {
        return queries.reasonOptions();
    }

    @Override
    @GetMapping("/{marketId}")
    public AdminBrandDto.Detail detail(@PathVariable Long marketId, @AuthenticationPrincipal UserPrincipal principal) {
        return queries.detail(marketId, operator(principal));
    }

    @Override
    @PostMapping("/{marketId}/suspend")
    public AdminBrandDto.Rail suspend(@PathVariable Long marketId,
                                      @Valid @RequestBody AdminBrandDto.SuspendRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return commands.suspend(marketId, request, operator(principal));
    }

    @Override
    @PostMapping("/{marketId}/release")
    public AdminBrandDto.Rail release(@PathVariable Long marketId,
                                      @Valid @RequestBody(required = false) AdminBrandDto.ReleaseRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return commands.release(marketId, request, operator(principal));
    }

    @Override
    @PostMapping("/{marketId}/withdrawal-requests/{requestId}/approve")
    public AdminBrandDto.Rail approveWithdrawal(@PathVariable Long marketId, @PathVariable Long requestId,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return commands.approveWithdrawal(marketId, requestId, operator(principal));
    }

    @Override
    @PostMapping("/{marketId}/withdrawal-requests/{requestId}/reject")
    public AdminBrandDto.Rail rejectWithdrawal(@PathVariable Long marketId, @PathVariable Long requestId,
                                               @Valid @RequestBody AdminBrandDto.WithdrawalRejectRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return commands.rejectWithdrawal(marketId, requestId, request, operator(principal));
    }

    @Override
    @GetMapping("/{marketId}/histories")
    public AdminBrandDto.HistoryPage histories(@PathVariable Long marketId, @ModelAttribute PagingRequest paging) {
        return queries.historyPage(marketId, paging);
    }

    @Override
    @GetMapping("/{marketId}/memos")
    public AdminBrandDto.MemoPage memos(@PathVariable Long marketId, @ModelAttribute PagingRequest paging,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return queries.memoPage(marketId, operator(principal), paging);
    }

    @Override
    @PostMapping("/{marketId}/memos")
    public AdminBrandDto.Memo createMemo(@PathVariable Long marketId, @Valid @RequestBody AdminBrandDto.MemoRequest request,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return memos.create(marketId, request.content(), operator(principal));
    }

    @Override
    @PatchMapping("/{marketId}/memos/{memoId}")
    public AdminBrandDto.Memo updateMemo(@PathVariable Long marketId, @PathVariable Long memoId,
                                         @Valid @RequestBody AdminBrandDto.MemoRequest request,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return memos.update(marketId, memoId, request.content(), operator(principal));
    }

    @Override
    @DeleteMapping("/{marketId}/memos/{memoId}")
    public ResponseEntity<Void> deleteMemo(@PathVariable Long marketId, @PathVariable Long memoId,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        memos.delete(marketId, memoId, operator(principal));
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{marketId}/connected-creators")
    public AdminBrandDto.ConnectedCreatorPage connectedCreators(@PathVariable Long marketId,
                                                                @ModelAttribute PagingRequest paging) {
        return queries.connectedCreatorPage(marketId, paging);
    }

    private static Long operator(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
