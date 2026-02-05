package showroomz.api.app.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import showroomz.api.app.auth.DTO.ValidationErrorResponse;
import showroomz.api.app.docs.UserControllerDocs;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.user.DTO.NicknameCheckResponse;
import showroomz.api.app.user.DTO.RefundAccountRequest;
import showroomz.api.app.user.DTO.RefundAccountResponse;
import showroomz.api.app.user.DTO.UpdateUserProfileRequest;
import showroomz.api.app.user.DTO.UserProfileResponse;
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
        NicknameCheckResponse response = userService.checkNickname(nickname);
        return ResponseEntity.ok(response);
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

        // 3. 입력값 검증
        List<ValidationErrorResponse.FieldError> fieldErrors = new ArrayList<>();

        // 닉네임 검증
        if (request.getNickname() != null && !request.getNickname().isEmpty()) {
            // 현재 닉네임과 다를 경우에만 검증
            if (!request.getNickname().equals(currentUser.getNickname())) {
                // 닉네임 길이 검증
                if (!userService.isValidNicknameLength(request.getNickname())) {
                    fieldErrors.add(new ValidationErrorResponse.FieldError("nickname",
                            "닉네임은 2자 이상 10자 이하이어야 합니다."));
                } else {
                    // 길이가 유효한 경우에만 다른 검증 수행
                    NicknameCheckResponse nicknameCheck = userService.checkNickname(request.getNickname());

                    if (!nicknameCheck.getIsAvailable()) {
                        if ("DUPLICATE".equals(nicknameCheck.getCode())) {
                            // 중복 닉네임은 즉시 예외 발생 (409 Conflict)
                            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
                        } else if ("INVALID_FORMAT".equals(nicknameCheck.getCode())) {
                            fieldErrors.add(new ValidationErrorResponse.FieldError("nickname",
                                    "닉네임에 특수문자나 이모티콘을 사용할 수 없습니다."));
                        } else if ("PROFANITY".equals(nicknameCheck.getCode())) {
                            fieldErrors.add(new ValidationErrorResponse.FieldError("nickname",
                                    "부적절한 단어가 포함되어 있습니다."));
                        }
                    }
                }
            }
        }

        // 생년월일 형식 검증
        if (request.getBirthday() != null && !request.getBirthday().isEmpty()) {
            if (!request.getBirthday().matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                fieldErrors.add(new ValidationErrorResponse.FieldError("birthday",
                        "생년월일 형식이 올바르지 않습니다."));
            }
        }

        // 성별 검증
        if (request.getGender() != null && !request.getGender().isEmpty()) {
            if (!request.getGender().equals("MALE") && !request.getGender().equals("FEMALE")) {
                fieldErrors.add(new ValidationErrorResponse.FieldError("gender",
                        "성별은 MALE 또는 FEMALE만 가능합니다."));
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

