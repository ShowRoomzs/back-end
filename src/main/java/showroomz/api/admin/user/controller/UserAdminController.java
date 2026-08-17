package showroomz.api.admin.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import showroomz.api.admin.user.docs.UserAdminControllerDocs;
import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.admin.user.dto.AdminUserMemoUpdateRequest;
import showroomz.api.admin.user.service.AdminUserService;
import showroomz.api.admin.user.type.AdminUserSort;
import showroomz.api.admin.user.type.AdminUserTab;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/admin/users")
@RequiredArgsConstructor
public class UserAdminController implements UserAdminControllerDocs {

    private final AdminUserService adminUserService;

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
    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserDto.UserDetailResponse> getUserDetail(
            @PathVariable("userId") Long userId) {
        AdminUserDto.UserDetailResponse response = adminUserService.getUserDetail(userId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/{userId}/memo")
    public ResponseEntity<Void> updateAdminMemo(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody AdminUserMemoUpdateRequest request) {

        adminUserService.updateAdminMemo(userId, request);

        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{userId}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable("userId") Long userId,
            @RequestBody AdminUserDto.UserStatusUpdateRequest request) {

        adminUserService.updateUserStatus(userId, request);

        return ResponseEntity.noContent().build();
    }
}
