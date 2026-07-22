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
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.auth.DTO.CreatorCompleteRegistrationRequest;

@Tag(name = "Creator - Auth", description = "크리에이터 인증/추가정보 API")
@SecurityRequirement(name = "Authorization")
public interface CreatorAuthControllerDocs {

    @Operation(
            summary = "크리에이터 추가 정보 입력 (승인 후 최초)",
            description = "관리자 승인 후 소셜 로그인에서 받은 accessToken으로 추가 정보를 등록합니다.\n\n" +
                    "**요청 헤더:** `Authorization: Bearer {accessToken}`\n\n" +
                    "**필수 필드:**\n" +
                    "- `showroomName`: 쇼룸명 (중복 불가)\n" +
                    "- `businessType`: `INDIVIDUAL`(개인/비사업자) 또는 `BUSINESS`(개인사업자/법인)\n" +
                    "- `bankName`: 은행명\n" +
                    "- `accountNumber`: 계좌번호 (하이픈 없이 숫자만)\n" +
                    "- `bankBookImageUrl`: 통장 사본 URL\n\n" +
                    "**사업자(`BUSINESS`) 선택 시 추가 필수:**\n" +
                    "- `businessRegistrationNumber`: 사업자등록번호 (예: 123-45-67890)\n" +
                    "- `businessLicenseImageUrl`: 사업자등록증 URL\n\n" +
                    "**완료 후:** `isNewMember`가 `false`로 변경되며 access/refresh 토큰이 재발급됩니다.\n\n" +
                    "**권한:** CREATOR"
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
                    description = "인증 필요",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\"code\": \"UNAUTHORIZED\", \"message\": \"인증 정보가 유효하지 않습니다.\"}"
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
    ResponseEntity<TokenResponse> completeRegistration(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreatorCompleteRegistrationRequest registrationRequest);
}
