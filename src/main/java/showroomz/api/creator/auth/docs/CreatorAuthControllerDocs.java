package showroomz.api.creator.auth.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.SocialLoginRequest;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.creator.auth.DTO.CreatorCompleteRegistrationRequest;
import showroomz.api.creator.auth.DTO.ShowroomNameCheckResponse;

@Tag(name = "Creator - Auth", description = "크리에이터 인증/추가정보 API")
public interface CreatorAuthControllerDocs {

    @Operation(
            summary = "크리에이터 소셜 로그인",
            description = "카카오, 네이버, 구글, 애플 소셜 로그인으로 크리에이터 계정을 인증합니다.\n\n" +
                    "**유저 로그인과의 차이:**\n" +
                    "- 승인된 크리에이터(`role=CREATOR`)만 로그인 가능\n" +
                    "- 신청이 반려된 경우 로그인 불가, 반려 사유를 응답\n" +
                    "- 승인 대기(PENDING)인 경우 로그인 불가\n\n" +
                    "**추가 정보 미입력 (`isNewMember=true`):**\n" +
                    "- 셀러와 동일하게 `registerToken`만 반환 (access/refresh 미발급, 5분 유효)\n" +
                    "- 이후 `POST /v1/creator/auth/complete-registration`으로 추가 정보 입력"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TokenResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "추가 정보 입력 완료",
                                            value = "{\n" +
                                                    "  \"tokenType\": \"Bearer\",\n" +
                                                    "  \"accessToken\": \"eyJhbGciOiJIUzI1NiJ9...\",\n" +
                                                    "  \"refreshToken\": \"dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...\",\n" +
                                                    "  \"accessTokenExpiresIn\": 3600,\n" +
                                                    "  \"refreshTokenExpiresIn\": 1209600,\n" +
                                                    "  \"isNewMember\": false,\n" +
                                                    "  \"role\": \"CREATOR\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "추가 정보 미입력",
                                            value = "{\n" +
                                                    "  \"isNewMember\": true,\n" +
                                                    "  \"registerToken\": \"eyJhbGciOiJIUzI1Ni...\",\n" +
                                                    "  \"role\": \"CREATOR\"\n" +
                                                    "}",
                                            description = "관리자 승인 직후 첫 로그인 시 registerToken(5분 유효)이 반환됩니다."
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "승인 대기 / 반려",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "승인 대기",
                                            value = "{\"code\": \"ACCOUNT_NOT_APPROVED\", \"message\": \"관리자 승인 대기 중인 계정입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "반려 (사유 포함)",
                                            value = "{\"code\": \"ACCOUNT_REJECTED_WITH_REASON\", \"message\": \"팔로워 수 기준 미달 - 제출하신 채널의 팔로워 수가 기준에 미달합니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "반려 (사유 없음)",
                                            value = "{\"code\": \"ACCOUNT_REJECTED\", \"message\": \"가입 승인이 반려된 계정입니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "크리에이터 아님 / 입력값 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "크리에이터 아님",
                                            value = "{\"code\": \"ACCOUNT_ROLE_MISMATCH\", \"message\": \"해당 계정의 유형이 올바르지 않습니다.\"}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "소셜 로그인 요청",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SocialLoginRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "카카오 로그인",
                                    value = "{\n" +
                                            "  \"providerType\": \"KAKAO\",\n" +
                                            "  \"token\": \"kakao-access-token\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<TokenResponse> socialLogin(
            HttpServletRequest request,
            @Valid @RequestBody SocialLoginRequest socialLoginRequest);

    @Operation(
            summary = "쇼룸명 중복 확인",
            description = "`complete-registration`에서 사용할 쇼룸명의 형식·중복 여부를 확인합니다.\n\n" +
                    "**규칙:**\n" +
                    "- 공백/특수문자 불가\n" +
                    "- 한글(+숫자) 또는 영문(+숫자)만 사용 (혼용 불가)\n\n" +
                    "**응답:**\n" +
                    "- `isAvailable`: true면 사용 가능\n" +
                    "- `code`: `AVAILABLE` / `DUPLICATE` / `INVALID_FORMAT`\n" +
                    "- `message`: 결과 메시지"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "중복 체크 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ShowroomNameCheckResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용 가능",
                                            value = "{\n" +
                                                    "  \"isAvailable\": true,\n" +
                                                    "  \"code\": \"AVAILABLE\",\n" +
                                                    "  \"message\": \"사용 가능한 쇼룸명입니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "중복",
                                            value = "{\n" +
                                                    "  \"isAvailable\": false,\n" +
                                                    "  \"code\": \"DUPLICATE\",\n" +
                                                    "  \"message\": \"이미 사용 중인 쇼룸명입니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "형식 오류",
                                            value = "{\n" +
                                                    "  \"isAvailable\": false,\n" +
                                                    "  \"code\": \"INVALID_FORMAT\",\n" +
                                                    "  \"message\": \"쇼룸명은 공백과 특수문자를 사용할 수 없으며, 한글 또는 영문 중 하나만 사용해야 합니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<ShowroomNameCheckResponse> checkShowroomName(
            @Parameter(
                    description = "검사할 쇼룸명",
                    required = true,
                    example = "myshowroom"
            )
            @RequestParam("showroomName") String showroomName
    );

    @Operation(
            summary = "크리에이터 추가 정보 입력 (승인 후 최초)",
            description = "관리자 승인 후 크리에이터 소셜 로그인에서 받은 `registerToken`으로 추가 정보를 등록합니다.\n\n" +
                    "**요청 헤더:** `Authorization: Bearer {registerToken}`\n\n" +
                    "**필수 필드:**\n" +
                    "- `showroomName`: 쇼룸명 (중복 불가, 사전 확인: `GET /v1/creator/auth/check-showroom-name`)\n" +
                    "- `businessType`: `INDIVIDUAL`(개인/비사업자) 또는 `BUSINESS`(개인사업자/법인)\n" +
                    "- `bankName`: 은행명\n" +
                    "- `accountNumber`: 계좌번호 (하이픈 없이 숫자만)\n" +
                    "- `bankBookImageUrl`: 통장 사본 URL\n\n" +
                    "**사업자(`BUSINESS`) 선택 시 추가 필수:**\n" +
                    "- `businessRegistrationNumber`: 사업자등록번호 (예: 123-45-67890)\n" +
                    "- `businessLicenseImageUrl`: 사업자등록증 URL\n\n" +
                    "**완료 후:** `isNewMember`가 `false`로 변경되며 access/refresh 토큰이 발급됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "추가 정보 등록 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TokenResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공",
                                            value = "{\n" +
                                                    "  \"tokenType\": \"Bearer\",\n" +
                                                    "  \"accessToken\": \"eyJhbGciOiJIUzI1NiJ9...\",\n" +
                                                    "  \"refreshToken\": \"dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...\",\n" +
                                                    "  \"accessTokenExpiresIn\": 3600,\n" +
                                                    "  \"refreshTokenExpiresIn\": 1209600,\n" +
                                                    "  \"isNewMember\": false,\n" +
                                                    "  \"role\": \"CREATOR\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 / 이미 등록 완료 / 쇼룸명 중복",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "쇼룸명 중복",
                                            value = "{\"code\": \"DUPLICATE_SHOWROOM_NAME\", \"message\": \"이미 사용 중인 쇼룸명입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "이미 등록 완료",
                                            value = "{\"code\": \"ALREADY_REGISTERED\", \"message\": \"이미 회원가입이 완료된 사용자입니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "registerToken 누락 또는 만료",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "토큰 만료",
                                            value = "{\"code\": \"UNAUTHORIZED\", \"message\": \"회원가입 유효 시간이 만료되었습니다. 다시 로그인해주세요.\"}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "크리에이터 추가 정보",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CreatorCompleteRegistrationRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "개인(비사업자)",
                                    value = "{\n" +
                                            "  \"showroomName\": \"myshowroom\",\n" +
                                            "  \"businessType\": \"INDIVIDUAL\",\n" +
                                            "  \"bankName\": \"국민은행\",\n" +
                                            "  \"accountNumber\": \"12345678901234\",\n" +
                                            "  \"bankBookImageUrl\": \"https://s3.../bankbook.jpg\"\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "개인사업자/법인",
                                    value = "{\n" +
                                            "  \"showroomName\": \"myshowroom\",\n" +
                                            "  \"businessType\": \"BUSINESS\",\n" +
                                            "  \"businessRegistrationNumber\": \"123-45-67890\",\n" +
                                            "  \"businessLicenseImageUrl\": \"https://s3.../license.jpg\",\n" +
                                            "  \"bankName\": \"국민은행\",\n" +
                                            "  \"accountNumber\": \"12345678901234\",\n" +
                                            "  \"bankBookImageUrl\": \"https://s3.../bankbook.jpg\"\n" +
                                            "}"
                            )
                    }
            )
    )
    @SecurityRequirement(name = "Authorization")
    ResponseEntity<TokenResponse> completeRegistration(
            HttpServletRequest request,
            @Valid @RequestBody CreatorCompleteRegistrationRequest registrationRequest);
}
