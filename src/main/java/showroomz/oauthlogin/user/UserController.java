package showroomz.oauthlogin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import showroomz.oauthlogin.auth.DTO.ErrorResponse;
import showroomz.oauthlogin.auth.DTO.ValidationErrorResponse;
import showroomz.oauthlogin.user.DTO.NicknameCheckResponse;
import showroomz.oauthlogin.user.DTO.UpdateUserProfileRequest;
import showroomz.oauthlogin.user.DTO.UserProfileResponse;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 정보 API")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(
            summary = "현재 로그인한 사용자 정보 조회",
            description = "프로필 카드에 표시될 현재 로그인한 사용자의 정보(닉네임, 이메일, 프로필 이미지 등)를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 없음, 만료, 위조)"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    public ResponseEntity<?> getCurrentUser() {
        try {
            // 1. SecurityContext에서 현재 인증된 사용자 정보 가져오기
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            
            if (principal == null || !(principal instanceof User)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "인증 정보가 유효하지 않습니다. 다시 로그인해주세요."));
            }

            User springUser = (User) principal;
            String username = springUser.getUsername();

            // 2. 사용자 정보 조회
            Users user = userService.getUser(username)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "존재하지 않는 회원입니다."
                    ));

            // 3. UserProfileResponse로 변환
            String profileImageUrl = user.getProfileImageUrl();
            if (profileImageUrl != null && profileImageUrl.isEmpty()) {
                profileImageUrl = null;
            }
            
            UserProfileResponse response = new UserProfileResponse(
                    user.getUserId(), // id
                    user.getEmail(),
                    user.getNickname(),
                    profileImageUrl,
                    user.getBirthday(),
                    user.getGender(),
                    user.getProviderType(),
                    user.getRoleType(),
                    user.getCreatedAt(),
                    user.getModifiedAt(),
                    user.isMarketingAgree()
            );

            return ResponseEntity.ok(response);

        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("USER_NOT_FOUND", e.getReason()));
            }
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("UNAUTHORIZED", "인증 정보가 유효하지 않습니다. 다시 로그인해주세요."));
        }
    }

    @GetMapping
    @io.swagger.v3.oas.annotations.Hidden
    public Users getUser() {
        User principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Users user = userService.getUser(principal.getUsername())
        		.orElseThrow(() -> new RuntimeException("User not found"));

        return user;
    }

    @GetMapping("/check-nickname")
    @Operation(
            summary = "닉네임 중복 확인",
            description = "사용하려는 닉네임의 사용 가능 여부를 확인합니다."
    )
    public ResponseEntity<NicknameCheckResponse> checkNickname(
            @Parameter(name = "nickname", description = "확인할 닉네임", required = true)
            @RequestParam("nickname") String nickname) {
        NicknameCheckResponse response = userService.checkNickname(nickname);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    @Operation(
            summary = "현재 로그인한 사용자 프로필 정보 수정",
            description = "현재 로그인한 사용자의 프로필 정보(닉네임, 프로필 이미지 등)를 수정합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 형식 오류"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "닉네임 중복")
    })
    public ResponseEntity<?> updateCurrentUser(@RequestBody UpdateUserProfileRequest request) {
        try {
            // 1. SecurityContext에서 현재 인증된 사용자 정보 가져오기
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            
            if (principal == null || !(principal instanceof User)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "인증 정보가 유효하지 않습니다. 다시 로그인해주세요."));
            }

            User springUser = (User) principal;
            String username = springUser.getUsername();

            // 2. 현재 사용자 정보 조회
            Users currentUser = userService.getUser(username)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "존재하지 않는 회원입니다."
                    ));

            // 3. 입력값 검증
            List<ValidationErrorResponse.FieldError> fieldErrors = new ArrayList<>();

            // 닉네임 검증
            if (request.getNickname() != null && !request.getNickname().isEmpty()) {
                // 현재 닉네임과 동일한지 확인
                if (!request.getNickname().equals(currentUser.getNickname())) {
                    // 닉네임 길이 검증 (먼저 체크)
                    if (!userService.isValidNicknameLength(request.getNickname())) {
                        fieldErrors.add(new ValidationErrorResponse.FieldError("nickname", 
                                "닉네임은 2자 이상 10자 이하이어야 합니다."));
                    } else {
                        // 길이가 유효한 경우에만 다른 검증 수행
                        NicknameCheckResponse nicknameCheck = userService.checkNickname(request.getNickname());
                        
                        if (!nicknameCheck.getIsAvailable()) {
                            if ("DUPLICATE".equals(nicknameCheck.getCode())) {
                                return ResponseEntity.status(HttpStatus.CONFLICT)
                                        .body(new ErrorResponse("DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다."));
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

            // 4. 검증 오류가 있으면 400 에러 반환
            if (!fieldErrors.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ValidationErrorResponse("INVALID_INPUT", "입력값이 올바르지 않습니다.", fieldErrors));
            }

            // 5. 프로필 업데이트
            Users updatedUser = userService.updateProfile(username, request);

            // 6. UserProfileResponse로 변환
            String profileImageUrl = updatedUser.getProfileImageUrl();
            if (profileImageUrl != null && profileImageUrl.isEmpty()) {
                profileImageUrl = null;
            }
            
            UserProfileResponse response = new UserProfileResponse(
                    updatedUser.getUserId(), // id
                    updatedUser.getEmail(),
                    updatedUser.getNickname(),
                    profileImageUrl,
                    updatedUser.getBirthday(),
                    updatedUser.getGender(),
                    updatedUser.getProviderType(),
                    updatedUser.getRoleType(),
                    updatedUser.getCreatedAt(),
                    updatedUser.getModifiedAt(),
                    updatedUser.isMarketingAgree()
            );

            return ResponseEntity.ok(response);

        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("USER_NOT_FOUND", e.getReason()));
            }
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
        }
    }
}
