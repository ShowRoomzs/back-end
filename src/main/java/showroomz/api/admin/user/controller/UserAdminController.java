package showroomz.api.admin.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.user.DTO.AdminUserDto;
import showroomz.api.admin.user.service.AdminUserService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - User", description = "관리자 일반 유저 관리 API")
@RestController
@RequestMapping("/v1/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final AdminUserService adminUserService;

    @Operation(
            summary = "일반 유저 목록 조회",
            description = "가입 채널, 가입일, 활동 상태별 필터링 및 페이징 조회\n\n" +
                    "**필터 기능:**\n" +
                    "- providerType: 가입 채널 (GOOGLE, FACEBOOK, NAVER, KAKAO, APPLE, LOCAL)\n" +
                    "- status: 활동 상태 (NORMAL, DORMANT, WITHDRAWN)\n" +
                    "- startDate / endDate: 가입일 기준 조회 기간 (YYYY-MM-DD)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**페이징 파라미터:**\n" +
                    "- page: 페이지 번호 (1부터 시작, 기본값: 1)\n" +
                    "- size: 페이지당 항목 수 (기본값: 20)"
    )
    @GetMapping
    public ResponseEntity<PageResponse<AdminUserDto.UserResponse>> getUsers(
            @ModelAttribute PagingRequest pagingRequest,
            @ModelAttribute AdminUserDto.SearchCondition searchCondition) {

        // 기본 정렬: 가입일 최신순
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = pagingRequest.toPageable(sort);

        PageResponse<AdminUserDto.UserResponse> response = 
                adminUserService.getUsers(searchCondition, pageable);

        return ResponseEntity.ok(response);
    }
}
