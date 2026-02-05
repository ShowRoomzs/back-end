package showroomz.api.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.user.DTO.AdminUserDto;
import showroomz.api.admin.user.repository.UserSpecification;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.Users;
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
}
