package showroomz.api.app.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import showroomz.api.app.auth.DTO.ValidationErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.user.DTO.NicknameCheckResponse;
import showroomz.api.app.user.DTO.RefundAccountRequest;
import showroomz.api.app.user.DTO.RefundAccountResponse;
import showroomz.api.app.user.DTO.UpdateUserProfileRequest;
import showroomz.api.app.user.DTO.UserProfileResponse;
import showroomz.api.app.user.DTO.WithdrawalInfoResponse;
import showroomz.api.app.user.docs.UserControllerDocs;
import showroomz.api.app.user.service.UserService;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

    private final UserService userService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser() {
        // 1. SecurityContext에서 현재 인증된 사용자 정보 가져오기
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        String username = userPrincipal.getUsername();

        // 2. 사용자 프로필 조회 (팔로잉 수 포함)
        UserProfileResponse response = userService.getProfile(username);
        
        // 3. 프로필 이미지 URL 정리
        String profileImageUrl = response.getProfileImageUrl();
        if (profileImageUrl != null && profileImageUrl.isEmpty()) {
            response.setProfileImageUrl(null);
        }

        return ResponseEntity.ok(response);
    }

    // 내부 호출용 메소드 (Swagger 문서화 불필요)
    @GetMapping
    @io.swagger.v3.oas.annotations.Hidden
    public Users getUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof UserPrincipal)) {
             throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        
        return userService.getUser(((UserPrincipal) principal).getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @GetMapping("/check-nickname")
    public ResponseEntity<NicknameCheckResponse> checkNickname(@RequestParam("nickname") String nickname) {
        // 가입(C0-1)에서는 비로그인으로도 호출한다. 로그인 상태(C15-1)면 현재 닉네임을 넘겨
        // "자기 닉네임 그대로"를 중복이 아니라 UNCHANGED로 구분한다.
        NicknameCheckResponse response = userService.checkNickname(nickname, findCurrentNickname());
        return ResponseEntity.ok(response);
    }

    /** 로그인 상태가 아니거나 회원을 찾을 수 없으면 null */
    private String findCurrentNickname() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            return null;
        }
        return userService.getUser(userPrincipal.getUsername())
                .map(Users::getNickname)
                .orElse(null);
    }

    @Override
    @PatchMapping("/me")
    public ResponseEntity<?> updateCurrentUser(@RequestBody UpdateUserProfileRequest request) {
        // 1. SecurityContext에서 현재 인증된 사용자 정보 가져오기
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal == null || !(principal instanceof UserPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }

        UserPrincipal userPrincipal = (UserPrincipal) principal;
        String username = userPrincipal.getUsername();

        // 2. 현재 사용자 정보 조회
        Users currentUser = userService.getUser(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 3. 입력값 검증 — 화면에서 바꿀 수 있는 값은 닉네임과 프로필 사진뿐이다
        List<ValidationErrorResponse.FieldError> fieldErrors = new ArrayList<>();

        if (request.getNickname() != null && !request.getNickname().isEmpty()) {
            NicknameCheckResponse nicknameCheck =
                    userService.checkNickname(request.getNickname(), currentUser.getNickname());

            // 현재 닉네임 그대로면 바꿀 것이 없으므로 통과시킨다(저장은 no-op)
            if (!nicknameCheck.getIsAvailable() && !"UNCHANGED".equals(nicknameCheck.getCode())) {
                if ("DUPLICATE".equals(nicknameCheck.getCode())) {
                    // 중복 닉네임은 즉시 예외 발생 (409 Conflict)
                    throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
                }
                fieldErrors.add(new ValidationErrorResponse.FieldError("nickname", nicknameCheck.getMessage()));
            }
        }

        // 4. 검증 오류가 있으면 ValidationErrorResponse 반환
        if (!fieldErrors.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ValidationErrorResponse(
                            ErrorCode.INVALID_INPUT_VALUE.getCode(),
                            ErrorCode.INVALID_INPUT_VALUE.getMessage(),
                            fieldErrors
                    ));
        }

        // 5. 프로필 업데이트 (모든 검증을 통과한 경우에만 수행)
        userService.updateProfile(username, request);

        // 6. 업데이트된 프로필 조회 (팔로잉 수 포함)
        UserProfileResponse response = userService.getProfile(username);
        
        // 7. 프로필 이미지 URL 정리
        String profileImageUrl = response.getProfileImageUrl();
        if (profileImageUrl != null && profileImageUrl.isEmpty()) {
            response.setProfileImageUrl(null);
        }

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/withdrawal")
    public ResponseEntity<WithdrawalInfoResponse> getWithdrawalInfo(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        if (userPrincipal == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return ResponseEntity.ok(userService.getWithdrawalInfo(userPrincipal.getUsername()));
    }

    @Override
    @GetMapping("/refund-account")
    public ResponseEntity<RefundAccountResponse> getRefundAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        if (userPrincipal == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        RefundAccountResponse response = userService.getRefundAccount(userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }

    @Override
    @PutMapping("/refund-account")
    public ResponseEntity<Void> updateRefundAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody RefundAccountRequest request
    ) {
        if (userPrincipal == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        userService.updateRefundAccount(userPrincipal.getUserId(), request);
        return ResponseEntity.ok().build();
    }
}

