package showroomz.swaggerDocs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import showroomz.admin.DTO.AdminLoginRequest;
import showroomz.admin.DTO.AdminSignUpRequest;
import showroomz.auth.DTO.ErrorResponse;
import showroomz.auth.DTO.TokenResponse;
import showroomz.auth.DTO.ValidationErrorResponse;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Tag(name = "Admin", description = "관리자(판매자) API")
public interface AdminControllerDocs {

    @Operation(
            summary = "관리자(판매자) 회원가입",
            description = "계정, 판매자, 마켓 정보를 입력받아 관리자 계정을 생성합니다.\n\n" +
                    "**생성되는 정보:**\n" +
                    "- Users 엔티티: 관리자 계정 정보 (이메일, 비밀번호, 판매자 이름, 연락처)\n" +
                    "- Market 엔티티: 마켓 정보 (마켓명, 고객센터 번호)\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공 - Status: 201 Created",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"message\": \"관리자 회원가입이 완료되었습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "398",
                    description = "입력값 형식 오류 - Status: 400 Bad Request",
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
                                                    "      \"field\": \"email\",\n" +
                                                    "      \"reason\": \"이메일 형식이 올바르지 않습니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"password\",\n" +
                                                    "      \"reason\": \"비밀번호는 8~16자의 영문자, 숫자, 특수문자를 포함해야 합니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"sellerContact\",\n" +
                                                    "      \"reason\": \"올바른 휴대폰 번호 형식이 아닙니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"marketName\",\n" +
                                                    "      \"reason\": \"마켓명은 공백과 특수문자를 사용할 수 없으며, 한글 또는 영문 중 하나만 사용해야 합니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"csNumber\",\n" +
                                                    "      \"reason\": \"올바른 전화번호 형식이 아닙니다.\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "399",
                    description = "비밀번호 불일치 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "비밀번호 불일치 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"BAD_REQUEST\",\n" +
                                                    "  \"message\": \"비밀번호가 일치하지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "이메일 중복 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "이메일 중복 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"DUPLICATE_EMAIL\",\n" +
                                                    "  \"message\": \"이미 사용 중인 이메일입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "397",
                    description = "마켓명 중복 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "마켓명 중복 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"DUPLICATE_MARKET_NAME\",\n" +
                                                    "  \"message\": \"이미 사용 중인 마켓명입니다.\"\n" +
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
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "관리자(판매자) 회원가입 정보\n\n" +
                    "**계정 정보:**\n" +
                    "- email: 필수, 아이디로 사용되는 이메일\n" +
                    "- password: 필수, 8~16자, 영문+숫자+특수문자 조합\n" +
                    "- passwordConfirm: 필수, 비밀번호 재입력\n\n" +
                    "**판매자 정보:**\n" +
                    "- sellerName: 필수, 판매 담당자 이름\n" +
                    "- sellerContact: 필수, 판매 담당자 연락처 (010-1234-5678 형식)\n\n" +
                    "**마켓 정보:**\n" +
                    "- marketName: 필수, 마켓명 (공백/특수문자 불가, 한글 또는 영문 중 하나만 사용)\n" +
                    "- csNumber: 필수, 고객센터 전화번호 (02-1234-5678 형식)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminSignUpRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"email\": \"admin@showroomz.shop\",\n" +
                                            "  \"password\": \"Admin123!\",\n" +
                                            "  \"passwordConfirm\": \"Admin123!\",\n" +
                                            "  \"sellerName\": \"김담당\",\n" +
                                            "  \"sellerContact\": \"010-1234-5678\",\n" +
                                            "  \"marketName\": \"쇼룸즈\",\n" +
                                            "  \"csNumber\": \"02-1234-5678\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<?> registerAdmin(@RequestBody AdminSignUpRequest request);

    @Operation(
            summary = "이메일 중복 체크",
            description = "관리자 회원가입 시 사용할 이메일의 중복 여부를 확인합니다.\n\n" +
                    "**응답:**\n" +
                    "- `true`: 이메일이 이미 사용 중 (중복)\n" +
                    "- `false`: 이메일 사용 가능"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "중복 체크 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Boolean.class),
                            examples = {
                                    @ExampleObject(
                                            name = "중복인 경우",
                                            value = "true",
                                            description = "이메일이 이미 사용 중입니다."
                                    ),
                                    @ExampleObject(
                                            name = "사용 가능한 경우",
                                            value = "false",
                                            description = "이메일을 사용할 수 있습니다."
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Boolean> checkEmail(
            @Parameter(
                    description = "중복 체크할 이메일 주소",
                    required = true,
                    example = "admin@showroomz.shop"
            )
            @RequestParam String email
    );

    @Operation(
            summary = "마켓명 중복 체크",
            description = "관리자 회원가입 시 사용할 마켓명의 중복 여부를 확인합니다.\n\n" +
                    "**응답:**\n" +
                    "- `true`: 마켓명이 이미 사용 중 (중복)\n" +
                    "- `false`: 마켓명 사용 가능"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "중복 체크 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Boolean.class),
                            examples = {
                                    @ExampleObject(
                                            name = "중복인 경우",
                                            value = "true",
                                            description = "마켓명이 이미 사용 중입니다."
                                    ),
                                    @ExampleObject(
                                            name = "사용 가능한 경우",
                                            value = "false",
                                            description = "마켓명을 사용할 수 있습니다."
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Boolean> checkMarketName(
            @Parameter(
                    description = "중복 체크할 마켓명",
                    required = true,
                    example = "쇼룸즈"
            )
            @RequestParam String marketName
    );

    @Operation(
            summary = "관리자(판매자) 로그인",
            description = "이메일과 비밀번호로 관리자 계정에 로그인합니다.\n\n" +
                    "**응답:**\n" +
                    "- Access Token: 관리자 API 접근에 사용\n" +
                    "- Refresh Token: Access Token 갱신에 사용"
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
                                                    "  \"isNewMember\": false\n" +
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
                                                    "      \"field\": \"email\",\n" +
                                                    "      \"reason\": \"이메일을 입력해주세요.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"password\",\n" +
                                                    "      \"reason\": \"비밀번호를 입력해주세요.\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 실패 - 아이디 또는 비밀번호 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "로그인 실패 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"아이디 또는 비밀번호가 올바르지 않습니다.\"\n" +
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
                    schema = @Schema(implementation = AdminLoginRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"email\": \"admin@showroomz.shop\",\n" +
                                            "  \"password\": \"Admin123!\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<TokenResponse> login(@RequestBody AdminLoginRequest request);
}

