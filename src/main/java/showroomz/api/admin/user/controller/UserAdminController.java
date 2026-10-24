package showroomz.api.admin.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import showroomz.api.admin.user.docs.UserAdminControllerDocs;
import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.admin.user.service.AdminUserCommandService;
import showroomz.api.admin.user.service.AdminUserMemoService;
import showroomz.api.admin.user.service.AdminUserPrivacyService;
import showroomz.api.admin.user.service.AdminUserService;
import showroomz.api.admin.user.type.AdminUserSort;
import showroomz.api.admin.user.type.AdminUserTab;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@RestController
@RequestMapping("/v1/admin/users")
@RequiredArgsConstructor
public class UserAdminController implements UserAdminControllerDocs {

    private final AdminUserService adminUserService;
    private final AdminUserCommandService commandService;
    private final AdminUserMemoService memoService;
    private final AdminUserPrivacyService privacyService;

    @Override
    @GetMapping
    public ResponseEntity<AdminUserDto.ListResponse> getUsers(
            @RequestParam(value = "tab", defaultValue = "ALL") AdminUserTab tab,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "providerType", required = false) ProviderType providerType,
            @RequestParam(value = "sort", defaultValue = "RECENT_JOINED") AdminUserSort sort,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest) {

        // 정렬은 쿼리가 직접 잡는다 — 누적 주문순은 집계값 기준이라 Pageable의 Sort로 표현되지 않는다
        Pageable pageable = pagingRequest.toPageable(Sort.unsorted());

        return ResponseEntity.ok(
                adminUserService.getUsers(tab, keyword, providerType, sort, pageable));
    }

    @Override
    @GetMapping("/reason-options")
    public ResponseEntity<AdminUserDto.ReasonOptions> reasonOptions() {
        return ResponseEntity.ok(adminUserService.reasonOptions());
    }

    @Override
    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserDto.UserDetailResponse> getUserDetail(
            @PathVariable("userId") Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminUserService.getUserDetail(userId, operator(principal)));
    }

    @Override
    @PostMapping("/{userId}/privacy-unmask")
    public ResponseEntity<AdminUserDto.UserDetailResponse> unmask(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody AdminUserDto.UnmaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(privacyService.unmask(userId, operator(principal), request.reason()));
    }

    @Override
    @PostMapping("/{userId}/privacy-lock")
    public ResponseEntity<Void> lock(
            @PathVariable("userId") Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        privacyService.lock(userId, operator(principal));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{userId}/suspend")
    public ResponseEntity<AdminUserDto.StatusResult> suspend(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody AdminUserDto.SuspendRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.suspend(userId, operator(principal), request));
    }

    @Override
    @PostMapping("/{userId}/release")
    public ResponseEntity<AdminUserDto.StatusResult> release(
            @PathVariable("userId") Long userId,
            @RequestBody(required = false) AdminUserDto.ReleaseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(commandService.release(userId, operator(principal), request));
    }

    @Override
    @GetMapping("/{userId}/histories")
    public ResponseEntity<AdminUserDto.HistoryPage> getHistories(
            @PathVariable("userId") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserService.getHistories(userId, page, size));
    }

    @Override
    @GetMapping("/{userId}/memos")
    public ResponseEntity<AdminUserDto.MemoPage> getMemos(
            @PathVariable("userId") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(memoService.list(userId, operator(principal), page, size));
    }

    @Override
    @PostMapping("/{userId}/memos")
    public ResponseEntity<AdminUserDto.Memo> createMemo(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody AdminUserDto.MemoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(memoService.create(userId, operator(principal), request.content()));
    }

    @Override
    @PatchMapping("/{userId}/memos/{memoId}")
    public ResponseEntity<AdminUserDto.Memo> updateMemo(
            @PathVariable("userId") Long userId,
            @PathVariable("memoId") Long memoId,
            @Valid @RequestBody AdminUserDto.MemoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(memoService.update(userId, memoId, operator(principal), request.content()));
    }

    @Override
    @DeleteMapping("/{userId}/memos/{memoId}")
    public ResponseEntity<Void> deleteMemo(
            @PathVariable("userId") Long userId,
            @PathVariable("memoId") Long memoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        memoService.delete(userId, memoId, operator(principal));
        return ResponseEntity.noContent().build();
    }

    private static Long operator(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return principal.getUserId();
    }
}
