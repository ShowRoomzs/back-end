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
            description = "프로필 카드에 표시될 현재 로그인한 사용자의 정보(닉네임, 이메일, 프로필 이미지 등)를 조회합니다.\n\n" +
                    "**참고사항**\n" +
                    "- 프로필 사진이 없는 경우 `profileImageUrl`은 `null`로 반환됩니다.\n\n" 
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 - Status: 200 OK",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UserProfileResponse.class),
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "성공 시 (프로필 사진 있음)",
                                            value = "{\n" +
                                                    "  \"id\": 1,\n" +
                                                    "  \"email\": \"string\",\n" +
                                                    "  \"nickname\": \"string\",\n" +
                                                    "  \"profileImageUrl\": \"https://k.kakaocdn.net/img_640x640.jpg\",\n" +
                                                    "  \"birthday\": \"YYYY-MM-DD\",\n" +
                                                    "  \"gender\": \"MALE\",\n" +
                                                    "  \"providerType\": \"GOOGLE\",\n" +
                                                    "  \"roleType\": \"USER\",\n" +
                                                    "  \"createdAt\": \"2025-10-31T10:00:00\",\n" +
                                                    "  \"modifiedAt\": \"2025-10-31T10:00:00\",\n" +
                                                    "  \"marketingAgree\": true\n" +
                                                    "}"
                                    ),
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "성공 시 (프로필 사진 없음)",
                                            value = "{\n" +
                                                    "  \"id\": 1,\n" +
                                                    "  \"email\": \"string\",\n" +
                                                    "  \"nickname\": \"string\",\n" +
                                                    "  \"profileImageUrl\": null,\n" +
                                                    "  \"birthday\": \"YYYY-MM-DD\",\n" +
                                                    "  \"gender\": \"MALE\",\n" +
                                                    "  \"providerType\": \"GOOGLE\",\n" +
                                                    "  \"roleType\": \"USER\",\n" +
                                                    "  \"createdAt\": \"2025-10-31T10:00:00\",\n" +
                                                    "  \"modifiedAt\": \"2025-10-31T10:00:00\",\n" +
                                                    "  \"marketingAgree\": true\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "헤더에 토큰이 없거나, 만료되었거나, 위조된 경우 - Status: 401 Unauthorized",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
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
            summary = "닉네임 유효성 검사",
            description = "닉네임 유효성 검사를 수행합니다.\n\n" +
                    "**호출 도메인**\n" +
                    "- 개발: https://localhost:8080\n" +
                    "- 배포: https://api.showroomz.shop\n\n" +
                    "**응답 코드 (code)**\n" +
                    "- `AVAILABLE`: 사용 가능한 닉네임 (isAvailable: true)\n" +
                    "- `INVALID_FORMAT`: 형식 오류 - 이모티콘, 특수문자 등 (isAvailable: false)\n" +
                    "- `PROFANITY`: 금칙어(욕설) 포함 (isAvailable: false)\n" +
                    "- `DUPLICATE`: 이미 존재하는 닉네임 (isAvailable: false)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "사용 가능한 경우 - Status: 200 OK",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = NicknameCheckResponse.class),
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "사용 가능한 경우",
                                            value = "{\n" +
                                                    "  \"isAvailable\": true,\n" +
                                                    "  \"code\": \"AVAILABLE\",\n" +
                                                    "  \"message\": \"사용 가능한 닉네임입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "201",
                    description = "이미 사용 중인 경우 (중복) - Status: 200 OK",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = NicknameCheckResponse.class),
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "이미 사용 중인 경우 (중복)",
                                            value = "{\n" +
                                                    "  \"isAvailable\": false,\n" +
                                                    "  \"code\": \"DUPLICATE\",\n" +
                                                    "  \"message\": \"이미 사용 중인 닉네임입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "202",
                    description = "욕설이 포함된 경우 (금칙어) - Status: 200 OK",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = NicknameCheckResponse.class),
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "욕설이 포함된 경우 (금칙어)",
                                            value = "{\n" +
                                                    "  \"isAvailable\": false,\n" +
                                                    "  \"code\": \"PROFANITY\",\n" +
                                                    "  \"message\": \"부적절한 단어가 포함되어 있습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "203",
                    description = "이모티콘/특수문자 포함 (형식) - Status: 200 OK",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = NicknameCheckResponse.class),
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "이모티콘/특수문자 포함 (형식)",
                                            value = "{\n" +
                                                    "  \"isAvailable\": false,\n" +
                                                    "  \"code\": \"INVALID_FORMAT\",\n" +
                                                    "  \"message\": \"닉네임에 특수문자나 이모티콘을 사용할 수 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    public ResponseEntity<NicknameCheckResponse> checkNickname(
            @Parameter(
                    name = "nickname",
                    description = "검사할 닉네임 (필수)",
                    required = true,
                    example = "abc123"
            )
            @RequestParam("nickname") String nickname) {
        NicknameCheckResponse response = userService.checkNickname(nickname);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    @Operation(
            summary = "현재 로그인한 사용자 프로필 정보 수정",
            description = "현재 로그인한 사용자의 프로필 정보(닉네임, 프로필 이미지 등)를 수정합니다.\n\n" +
                    "**호출 도메인**\n" +
                    "- 개발: https://localhost:8080\n" +
                    "- 배포: https://api.showroomz.shop"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공 - Status: 200 OK",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UserProfileResponse.class),
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "성공 시",
                                            value = "{\n" +
                                                    "  \"id\": 1,\n" +
                                                    "  \"email\": \"string\",\n" +
                                                    "  \"nickname\": \"string\",\n" +
                                                    "  \"profileImageUrl\": \"https://k.kakaocdn.net/dn/.../img_640x640.jpg\",\n" +
                                                    "  \"birthday\": \"YYYY-MM-DD\",\n" +
                                                    "  \"gender\": \"MALE\",\n" +
                                                    "  \"providerType\": \"GOOGLE\",\n" +
                                                    "  \"roleType\": \"USER\",\n" +
                                                    "  \"createdAt\": \"2025-10-31T10:00:00\",\n" +
                                                    "  \"modifiedAt\": \"2025-10-31T10:00:00\",\n" +
                                                    "  \"marketingAgree\": true\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 형식 오류 - Status: 400 Bad Request",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ValidationErrorResponse.class),
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "입력값 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\",\n" +
                                                    "  \"errors\": [\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"nickname\",\n" +
                                                    "      \"reason\": \"닉네임은 2자 이상 10자 이하이어야 합니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"nickname\",\n" +
                                                    "      \"reason\": \"닉네임에 특수문자나 이모티콘을 사용할 수 없습니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"nickname\",\n" +
                                                    "      \"reason\": \"부적절한 단어가 포함되어 있습니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"birthday\",\n" +
                                                    "      \"reason\": \"생년월일 형식이 올바르지 않습니다.\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "닉네임 중복 - Status: 409 Conflict",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "닉네임 중복",
                                            value = "{\n" +
                                                    "  \"code\": \"DUPLICATE_NICKNAME\",\n" +
                                                    "  \"message\": \"이미 사용 중인 닉네임입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "프로필 수정 요청 (모든 필드는 선택사항)",
            required = true,
            content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = "application/json",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UpdateUserProfileRequest.class),
                    examples = {
                            @io.swagger.v3.oas.annotations.media.ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"nickname\": \"string\",\n" +
                                            "  \"birthday\": \"YYYY-MM-DD\",\n" +
                                            "  \"gender\": \"MALE\",\n" +
                                            "  \"profileImageUrl\": \"https://...\",\n" +
                                            "  \"marketingAgree\": true\n" +
                                            "}"
                            )
                    }
            )
    )
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
