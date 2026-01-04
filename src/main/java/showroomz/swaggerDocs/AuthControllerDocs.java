package showroomz.swaggerDocs;      

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import showroomz.auth.DTO.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@Tag(name = "Auth", description = "Auth API")
public interface AuthControllerDocs {

    @Operation(
            summary = "소셜 로그인",
            description = "카카오, 네이버, 구글, 애플 소셜 로그인을 처리합니다. 신규 회원인 경우 registerToken을 반환하고, 기존 회원인 경우 accessToken과 refreshToken을 반환합니다.\n\n" +
                    "**registerToken 유효기간:** 5분 (회원가입 완료에 사용)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공 (기존 회원) - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TokenResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시",
                                            value = "{\n" +
                                                    "  \"tokenType\": \"Bearer\",\n" +
                                                    "  \"accessToken\": \"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ...\",\n" +
                                                    "  \"refreshToken\": \"dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...\",\n" +
                                                    "  \"accessTokenExpiresIn\": 3600,\n" +
                                                    "  \"refreshTokenExpiresIn\": 1209600,\n" +
                                                    "  \"isNewMember\": false\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "199",
                    description = "로그인 성공 (신규 회원) - Status: 200 OK\nregisterToken 유효기간: 5분",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TokenResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "신규 회원일 시",
                                            value = "{\n" +
                                                    "  \"isNewMember\": true,\n" +
                                                    "  \"registerToken\": \"eyJhbGciOiJIUzI1Ni...\"\n" +
                                                    "}",
                                            description = "registerToken은 5분간 유효하며, 회원가입 완료에 사용됩니다."
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "399",
                    description = "필수 파라미터 누락",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "토큰 누락",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"token은 필수 입력값입니다.\"\n" +
                                                    "}",
                                            description = "token 필드가 누락된 경우"
                                    ),
                                    @ExampleObject(
                                            name = "providerType 누락",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"providerType은 필수 입력값입니다.\"\n" +
                                                    "}",
                                            description = "providerType 필드가 누락된 경우"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "지원하지 않는 소셜 공급자",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "잘못된 공급자",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_SOCIAL_PROVIDER\",\n" +
                                                    "  \"message\": \"지원하지 않는 소셜 공급자입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "소셜 토큰 만료 또는 유효하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유효하지 않은 토큰",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"유효하지 않은 액세스 토큰입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이메일 중복 - 이미 다른 소셜 계정에서 사용 중인 이메일",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "이메일 중복",
                                            value = "{\n" +
                                                    "  \"code\": \"DUPLICATE_EMAIL\",\n" +
                                                    "  \"message\": \"이미 다른 계정에서 사용 중인 이메일입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "서버 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INTERNAL_SERVER_ERROR\",\n" +
                                                    "  \"message\": \"서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "소셜 로그인 요청 정보\n" +
                    "- providerType: 필수, 소셜 공급자 타입 (KAKAO, NAVER, GOOGLE, APPLE)\n" +
                    "- token: 필수, 애플은 idToken, 카카오/네이버/구글은 accessToken\n" +
                    "- name: 선택, 애플 로그인에서만 사용 (첫 로그인 시 이름)\n" +
                    "- fcmToken: 선택, (푸시 알림 전송용 FCM 토큰)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SocialLoginRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "네이버 로그인 예시",
                                    value = "{\n" +
                                            "  \"providerType\": \"NAVER\",\n" +
                                            "  \"token\": \"eyJhbGciOiJIUzI1NiJ9...\"\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "카카오 로그인 예시",
                                    value = "{\n" +
                                            "  \"providerType\": \"KAKAO\",\n" +
                                            "  \"token\": \"eyJhbGciOiJIUzI1NiJ9...\"\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "구글 로그인 예시",
                                    value = "{\n" +
                                            "  \"providerType\": \"GOOGLE\",\n" +
                                            "  \"token\": \"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...\"\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "애플 로그인 예시 (첫 로그인)",
                                    value = "{\n" +
                                            "  \"providerType\": \"APPLE\",\n" +
                                            "  \"token\": \"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...\",\n" +
                                            "  \"name\": \"홍길동\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<?> socialLogin(@RequestBody SocialLoginRequest socialLoginRequest);

    @Operation(
            summary = "회원가입 완료",
            description = "소셜 로그인 후 회원가입 정보를 입력하여 회원가입을 완료합니다. Register Token이 필요합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TokenResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"tokenType\": \"Bearer\",\n" +
                                                    "  \"accessToken\": \"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJrYWthb18xMjM0NTY3ODkwIiwicm9sZSI6IlJPTEVfVVNFUiIsImV4cCI6MTc3MjAwMDAwMH0.example\",\n" +
                                                    "  \"refreshToken\": \"dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4gZXhhbXBsZQ\",\n" +
                                                    "  \"accessTokenExpiresIn\": 3600,\n" +
                                                    "  \"refreshTokenExpiresIn\": 1209600\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 형식 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "입력값 오류 예시",
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
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"serviceAgree\",\n" +
                                                    "      \"reason\": \"서비스 이용약관에 동의해야 합니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"privacyAgree\",\n" +
                                                    "      \"reason\": \"개인정보 수집 및 이용에 동의해야 합니다.\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Register Token 만료",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "토큰 만료 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"회원가입 유효 시간이 만료되었습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "399",
                    description = "이미 회원가입 완료 (Status: 400 Bad Request)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "이미 회원가입 완료 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"ALREADY_REGISTERED\",\n" +
                                                    "  \"message\": \"이미 회원가입이 완료된 사용자입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "닉네임 중복",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "닉네임 중복 예시",
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
            description = "회원가입 정보",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = RegisterRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"nickname\": \"홍길동\",\n" +
                                            "  \"gender\": \"MALE\",\n" +
                                            "  \"birthday\": \"1990-01-15\",\n" +
                                            "  \"serviceAgree\": true,\n" +
                                            "  \"privacyAgree\": true,\n" +
                                            "  \"marketingAgree\": true\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<?> register(
            @Parameter(
                    description = "Authorization 헤더에 Bearer {registerToken} 형식으로 전달",
                    required = true,
                    hidden = true
            )
            HttpServletRequest request,
            @RequestBody RegisterRequest registerRequest
    );

    @Operation(
            summary = "Access Token 재발급",
            description = "Refresh Token을 사용하여 새로운 Access Token을 발급받습니다. Refresh Token이 만료 3일 이내인 경우 새로운 Refresh Token도 함께 발급됩니다.\n\n" +
                    "**권한:** 없음\n" +
                    "**요청 헤더:** 없음"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 재발급 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TokenResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시",
                                            value = "{\n" +
                                                    "  \"tokenType\": \"Bearer\",\n" +
                                                    "  \"accessToken\": \"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ...\",\n" +
                                                    "  \"refreshToken\": \"dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...\",\n" +
                                                    "  \"accessTokenExpiresIn\": 3600,\n" +
                                                    "  \"refreshTokenExpiresIn\": 1209600\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "필수 파라미터 누락",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "토큰 누락",
                                            value = "{\n" +
                                                    "  \"code\": \"BAD_REQUEST\",\n" +
                                                    "  \"message\": \"refreshToken은 필수 입력값입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "리프레시 토큰 만료 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "토큰 만료",
                                            value = "{\n" +
                                                    "  \"code\": \"REFRESH_TOKEN_EXPIRED\",\n" +
                                                    "  \"message\": \"리프레시 토큰이 만료되었습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "402",
                    description = "유효하지 않은 토큰 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유효하지 않은 토큰",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_TOKEN\",\n" +
                                                    "  \"message\": \"유효하지 않은 토큰입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "서버 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INTERNAL_SERVER_ERROR\",\n" +
                                                    "  \"message\": \"서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Refresh Token 재발급 요청",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = RefreshTokenRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"refreshToken\": \"string\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest refreshRequest);

    @Operation(
            summary = "로그아웃",
            description = "사용자를 로그아웃 처리합니다. Authorization 헤더에 Bearer {access_token}이 필요하며, Body에 refreshToken을 전달해야 합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "로그아웃 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시",
                                            value = "{\n" +
                                                    "  \"message\": \"로그아웃이 완료되었습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Body에 Refresh Token이 없는 경우 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Refresh Token 누락",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"Refresh Token이 필요합니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "헤더에 Access Token이 없거나 만료된 경우 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "서버 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INTERNAL_SERVER_ERROR\",\n" +
                                                    "  \"message\": \"서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "로그아웃 요청 (Refresh Token 필요)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = RefreshTokenRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"refreshToken\": \"string\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<?> logout(
            @Parameter(
                    description = "Authorization 헤더에 Bearer {access_token} 형식으로 전달",
                    required = true,
                    hidden = true
            )
            HttpServletRequest request,
            @RequestBody RefreshTokenRequest refreshRequest
    );

    @Operation(
            summary = "회원 탈퇴",
            description = "인증된 사용자의 회원 탈퇴를 처리합니다. 사용자 계정과 관련된 모든 리프레시 토큰이 삭제됩니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "회원 탈퇴 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"message\": \"회원 탈퇴가 완료되었습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자 없음 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"NOT_FOUND\",\n" +
                                                    "  \"message\": \"사용자를 찾을 수 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "서버 오류 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"INTERNAL_SERVER_ERROR\",\n" +
                                                    "  \"message\": \"서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<?> withdraw(
            @Parameter(
                    description = "Authorization 헤더에 Bearer {accessToken} 형식으로 전달 (Access Token만 필요)",
                    required = true,
                    hidden = true
            )
            HttpServletRequest request
    );
}