package showroomz.api.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.admin.user.dto.AdminUserMemoUpdateRequest;
import showroomz.api.admin.user.repository.UserSpecification;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.history.entity.UserStatusHistory;
import showroomz.domain.history.repository.UserStatusHistoryRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserStatusHistoryRepository userStatusHistoryRepository;

    public PageResponse<AdminUserDto.UserResponse> getUsers(
            AdminUserDto.SearchCondition condition, Pageable pageable) {

        // 검색 조건 생성
        Specification<Users> spec = UserSpecification.search(condition);

        // 페이징 조회
        Page<Users> usersPage = userRepository.findAll(spec, pageable);

        // DTO 변환
        List<AdminUserDto.UserResponse> content = usersPage.getContent().stream()
                .map(AdminUserDto.UserResponse::from)
                .collect(Collectors.toList());

        // PageResponse 생성
        return new PageResponse<>(content, usersPage);
    }

    /**
     * 유저 상세 정보 조회
     */
    public AdminUserDto.UserDetailResponse getUserDetail(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return AdminUserDto.UserDetailResponse.from(user);
    }

    @Transactional
    public void updateAdminMemo(Long userId, AdminUserMemoUpdateRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.updateAdminMemo(request.getAdminMemo());
    }

    /**
     * 유저 상태 변경 (정지 및 활성화)
     */
    @Transactional // 쓰기 작업을 위해 클래스 레벨의 readOnly = true 설정을 덮어씀
    public void updateUserStatus(Long userId, AdminUserDto.UserStatusUpdateRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserStatus newStatus = request.getStatus();

        
        // 이전 상태와 새 상태가 동일하다면 변경 불필요
        if (newStatus == user.getStatus()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미 해당 상태로 설정되어 있습니다.");
        }


        // 요구사항: 변경 가능한 상태를 정지 상태(SUSPENDED)와 활성 상태(NORMAL)로만 제한
        if (newStatus != UserStatus.NORMAL && newStatus != UserStatus.SUSPENDED) {
            throw new IllegalArgumentException("유저 상태는 NORMAL(활성) 또는 SUSPENDED(정지)로만 변경할 수 있습니다.");
        }

        user.updateStatus(newStatus);

        // 유저 상태 변경 히스토리 저장
        userStatusHistoryRepository.save(UserStatusHistory.builder()
        .user(user)
        .previousStatus(newStatus)
        .newStatus(newStatus)
        .reason("")
        .build());
    }
}
