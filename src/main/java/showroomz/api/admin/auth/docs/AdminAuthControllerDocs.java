package showroomz.api.admin.auth.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.RefreshTokenRequest;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.seller.auth.DTO.SellerLoginRequest;

import java.util.Map;

@Tag(name = "Admin - Auth", description = "관리자 인증 API")
public interface AdminAuthControllerDocs {

    @Operation(
            summary = "관리자 로그인",
            description = "이메일과 비밀번호로 관리자 계정에 로그인합니다.\n\n" +
                    "**제약사항:**\n" +
                    "- 관리자(RoleType.ADMIN) 계정만 로그인할 수 있습니다.\n" +
                    "- 판매자(RoleType.SELLER) 계정은 이 엔드포인트로 로그인할 수 없습니다. 판매자 로그인은 `/v1/seller/auth/login`을 사용하세요.\n\n" +
                    "**권한:** 없음 (로그인은 인증 불필요)\n" +
                    "**요청 헤더:** 없음"
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
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"tokenType\": \"Bearer\",\n" +
                                                    "  \"accessToken\": \"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ...\",\n" +
                                                    "  \"refreshToken\": \"dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...\",\n" +
                                                    "  \"accessTokenExpiresIn\": 3600,\n" +
                                                    "  \"refreshTokenExpiresIn\": 1209600,\n" +
                                                    "  \"isNewMember\": false,\n" +
                                                    "  \"role\": \"ADMIN\"\n" +
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
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "입력값 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 실패 - 아이디 또는 비밀번호 오류, 또는 판매자 계정",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "비밀번호 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"아이디 또는 비밀번호가 올바르지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "로그인 실패 - 계정 미승인",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "승인 대기 중",
                                            value = "{\n" +
                                                    "  \"code\": \"ACCOUNT_NOT_APPROVED\",\n" +
                                                    "  \"message\": \"관리자 승인 대기 중인 계정입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "관리자 로그인 정보\n" +
                    "- email: 필수, 관리자 이메일\n" +
                    "- password: 필수, 비밀번호",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SellerLoginRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"email\": \"super\",\n" +
                                            "  \"password\": \"super\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<TokenResponse> login(@RequestBody SellerLoginRequest request);

    @Operation(
            summary = "Access Token 재발급",
            description = "Refresh Token을 사용하여 새로운 Access Token을 발급받습니다. Refresh Token이 만료 3일 이내인 경우 새로운 Refresh Token도 함께 발급됩니다.\n\n" +
                    "**권한:** 없음\n" +
                    "**요청 헤더:** 없음"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 재발급 성공",
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
                                                    "  \"role\": \"ADMIN\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "리프레시 토큰 만료 또는 유효하지 않음",
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
                                    ),
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
                    responseCode = "404",
                    description = "관리자를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "관리자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
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
    ResponseEntity<TokenResponse> refreshToken(@RequestBody RefreshTokenRequest request);

    @Operation(
            summary = "관리자 로그아웃",
            description = "관리자를 로그아웃 처리합니다. Authorization 헤더에 Bearer {access_token}이 필요하며, Body에 refreshToken을 전달해야 합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "로그아웃 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시",
                                            value = "{\n" +
                                                    "  \"message\": \"관리자 로그아웃이 완료되었습니다.\"\n" +
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
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다.\"\n" +
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
            @RequestBody RefreshTokenRequest refreshTokenRequest
    );
}
