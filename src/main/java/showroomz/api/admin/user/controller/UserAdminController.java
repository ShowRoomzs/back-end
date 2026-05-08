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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import showroomz.api.admin.user.docs.UserAdminControllerDocs;
import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.admin.user.dto.AdminUserMemoUpdateRequest;
import showroomz.api.admin.user.service.AdminUserService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/admin/users")
@RequiredArgsConstructor
public class UserAdminController implements UserAdminControllerDocs {

    private final AdminUserService adminUserService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<AdminUserDto.UserResponse>> getUsers(
            @ParameterObject @ModelAttribute PagingRequest pagingRequest,
            @ParameterObject @ModelAttribute AdminUserDto.SearchCondition searchCondition) {

        // 기본 정렬: 가입일 최신순
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = pagingRequest.toPageable(sort);

        PageResponse<AdminUserDto.UserResponse> response =
                adminUserService.getUsers(searchCondition, pageable);

        return ResponseEntity.ok(response);
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
