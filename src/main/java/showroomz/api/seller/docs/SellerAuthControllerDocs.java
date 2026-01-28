package showroomz.api.seller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.RefreshTokenRequest;
import showroomz.api.app.auth.DTO.TokenResponse;
import showroomz.api.app.auth.DTO.ValidationErrorResponse;
import showroomz.api.seller.auth.DTO.SellerDto;
import showroomz.api.seller.auth.DTO.SellerLoginRequest;
import showroomz.api.seller.auth.DTO.SellerSignUpRequest;
import showroomz.api.seller.auth.DTO.CreatorSignUpRequest;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Tag(name = "Seller - Auth", description = "Seller Auth API")
public interface SellerAuthControllerDocs {

    @Operation(
            summary = "관리자(판매자) 회원가입 요청",
            description = "계정, 판매자, 마켓 정보를 입력받아 관리자(판매자) 계정을 생성합니다.\n\n" +
                    "**주의사항:**\n" +
                    "- 회원가입 직후에는 **승인 대기(PENDING)** 상태가 되며 로그인이 불가능합니다.\n" +
                    "- 슈퍼 관리자의 승인 완료 후 로그인이 가능해집니다.\n" +
                    "- 따라서 회원가입 성공 시 토큰 대신 안내 메시지를 반환합니다.\n\n" +
                    "**반려된 계정 재가입:**\n" +
                    "- 이전에 반려(REJECTED)된 계정의 경우, 동일한 이메일로 재가입 시 기존 정보를 업데이트하고 상태를 PENDING으로 변경합니다.\n" +
                    "- 반려 사유는 초기화되며, 새로운 정보로 재심사를 받을 수 있습니다.\n" +
                    "- 재가입 성공 시 \"재가입 신청이 완료되었습니다. 관리자 승인 후 로그인이 가능합니다.\" 메시지를 반환합니다.\n\n" +
                    "**승인 상태:**\n" +
                    "- **PENDING**: 승인 대기 상태 (로그인 불가)\n" +
                    "- **APPROVED**: 승인 완료 상태 (로그인 가능)\n" +
                    "- **REJECTED**: 승인 반려 상태 (로그인 불가, 재가입 가능)\n\n" +
                    "**권한:** 없음 (회원가입은 인증 불필요)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 신청 성공 - Status: 201 Created",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = {
                                    @ExampleObject(
                                            name = "신규 가입 성공 예시",
                                            value = "{\n" +
                                                    "  \"message\": \"회원가입 신청이 완료되었습니다. 관리자 승인 후 로그인이 가능합니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "재가입 성공 예시",
                                            value = "{\n" +
                                                    "  \"message\": \"재가입 신청이 완료되었습니다. 관리자 승인 후 로그인이 가능합니다.\"\n" +
                                                    "}",
                                            description = "반려된 계정의 재가입 시 반환되는 메시지입니다."
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
                    description = "이메일 중복 - Status: 400 Bad Request\n\n" +
                            "**참고:** 반려(REJECTED)된 계정은 동일한 이메일로 재가입이 가능합니다. " +
                            "승인 대기(PENDING) 또는 승인 완료(APPROVED) 상태의 계정만 중복 에러가 발생합니다.",
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
                    schema = @Schema(implementation = SellerSignUpRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"email\": \"seller@example.com\",\n" +
                                            "  \"password\": \"Seller123!\",\n" +
                                            "  \"passwordConfirm\": \"Seller123!\",\n" +
                                            "  \"sellerName\": \"김담당\",\n" +
                                            "  \"sellerContact\": \"010-1234-5678\",\n" +
                                            "  \"marketName\": \"쇼룸즈\",\n" +
                                            "  \"csNumber\": \"02-1234-5678\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<?> registerAdmin(@RequestBody SellerSignUpRequest request);

    @Operation(
            summary = "크리에이터(쇼룸) 회원가입 요청",
            description = "크리에이터(쇼룸) 전용 회원가입 API입니다.\n\n" +
                    "**특징:**\n" +
                    "- 활동명(activityName)을 함께 저장합니다.\n" +
                    "- 마켓 타입은 SHOWROOM으로 저장됩니다.\n" +
                    "- SNS 플랫폼 정보와 URL을 함께 등록합니다.\n" +
                    "  - `snsType`: SNS 타입 (INSTAGRAM, TIKTOK, X, YOUTUBE)\n\n" +
                    "**승인 플로우:**\n" +
                    "- 기본적으로 판매자와 동일하게 승인 대기(PENDING) 상태로 생성되며, 관리자 승인 후 로그인 가능합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "쇼룸 회원가입 신청 성공 - Status: 201 Created",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = {
                                    @ExampleObject(
                                            name = "신규 쇼룸 가입 성공 예시",
                                            value = "{\n" +
                                                    "  \"message\": \"쇼룸 개설 신청이 완료되었습니다. 관리자 승인 후 로그인이 가능합니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "크리에이터(쇼룸) 회원가입 정보\n\n" +
                    "**SNS 정보:**\n" +
                    "- snsType: SNS 타입 (INSTAGRAM, TIKTOK, X, YOUTUBE)\n" +
                    "- snsUrl: SNS 프로필/채널 URL",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CreatorSignUpRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"email\": \"creator@example.com\",\n" +
                                            "  \"password\": \"Creator123!\",\n" +
                                            "  \"passwordConfirm\": \"Creator123!\",\n" +
                                            "  \"sellerName\": \"김창작\",\n" +
                                            "  \"sellerContact\": \"010-1234-5678\",\n" +
                                            "  \"marketName\": \"myshowroom\",\n" +
                                            "  \"activityName\": \"뷰티크리에이터\",\n" +
                                            "  \"snsType\": \"INSTAGRAM\",\n" +
                                            "  \"snsUrl\": \"https://instagram.com/my_id\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<?> registerCreator(@RequestBody CreatorSignUpRequest request);

    @Operation(
            summary = "이메일 중복 체크",
            description = "관리자 회원가입 시 사용할 이메일의 중복 여부를 확인합니다.\n\n" +
                    "**응답:**\n" +
                    "- `isAvailable`: true면 사용 가능, false면 중복\n" +
                    "- `code`: 응답 코드 (AVAILABLE: 사용 가능, DUPLICATE: 중복)\n" +
                    "- `message`: 결과 메시지"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "중복 체크 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SellerDto.CheckEmailResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용 가능한 경우",
                                            value = "{\n" +
                                                    "  \"isAvailable\": true,\n" +
                                                    "  \"code\": \"AVAILABLE\",\n" +
                                                    "  \"message\": \"사용 가능한 이메일입니다.\"\n" +
                                                    "}",
                                            description = "이메일을 사용할 수 있습니다."
                                    ),
                                    @ExampleObject(
                                            name = "중복인 경우",
                                            value = "{\n" +
                                                    "  \"isAvailable\": false,\n" +
                                                    "  \"code\": \"DUPLICATE\",\n" +
                                                    "  \"message\": \"이미 사용 중인 이메일입니다.\"\n" +
                                                    "}",
                                            description = "이메일이 이미 사용 중입니다."
                                    )
                            }
                    )
            )
    })
    ResponseEntity<SellerDto.CheckEmailResponse> checkEmail(
            @Parameter(
                    description = "중복 체크할 이메일 주소",
                    required = true,
                    example = "seller@showroomz.shop"
            )
            @RequestParam String email
    );

    @Operation(
            summary = "판매자 로그인",
            description = "이메일과 비밀번호로 판매자 계정에 로그인합니다.\n\n" +
                    "**제약사항:**\n" +
                    "- 승인 완료(APPROVED)된 계정만 로그인할 수 있습니다.\n" +
                    "- 승인 대기(PENDING) 또는 반려(REJECTED)된 계정은 403 Forbidden 에러가 발생합니다.\n" +
                    "- 반려(REJECTED)된 계정의 경우, 반려 사유가 있으면 응답의 `message` 필드에 반려 사유가 포함됩니다."
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
                                                    "  \"role\": \"SELLER\"\n" +
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
                                            name = "비밀번호 오류",
                                            value = "{\"code\": \"UNAUTHORIZED\", \"message\": \"아이디 또는 비밀번호가 올바르지 않습니다.\"}"
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
                                    ),
                                    @ExampleObject(
                                            name = "승인 반려됨 (반려 사유 없음)",
                                            value = "{\n" +
                                                    "  \"code\": \"ACCOUNT_REJECTED\",\n" +
                                                    "  \"message\": \"가입 승인이 반려된 계정입니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "승인 반려됨 (반려 사유 포함)",
                                            value = "{\n" +
                                                    "  \"code\": \"ACCOUNT_REJECTED_WITH_REASON\",\n" +
                                                    "  \"message\": \"가입 승인이 반려되었습니다. 반려 사유: 서류 미비로 인한 가입 승인 반려\"\n" +
                                                    "}",
                                            description = "반려 사유가 있는 경우, message 필드에 반려 사유가 포함됩니다."
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "판매자 로그인 정보\n" +
                    "- email: 필수, 판매자 이메일\n" +
                    "- password: 필수, 비밀번호",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SellerLoginRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "일반 판매자 로그인",
                                    value = "{\n" +
                                            "  \"email\": \"seller@example.com\",\n" +
                                            "  \"password\": \"Seller123!\"\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "슈퍼 관리자 로그인",
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
                    "**권한:** : 없음\n" +
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
                                                    "  \"refreshTokenExpiresIn\": 1209600,\n" +
                                                    "  \"role\": \"SELLER\"\n" +
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
                    responseCode = "404",
                    description = "관리자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "관리자 없음",
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
    ResponseEntity<TokenResponse> refreshToken(@RequestBody RefreshTokenRequest request);

    @Operation(
            summary = "로그아웃",
            description = "관리자를 로그아웃 처리합니다. Authorization 헤더에 Bearer {access_token}이 필요하며, Body에 refreshToken을 전달해야 합니다.\n\n" +
                    "**권한:** SELLER\n" +
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
            @RequestBody RefreshTokenRequest refreshTokenRequest
    );

    @Operation(
            summary = "판매자 회원 탈퇴",
            description = "인증된 판매자의 회원 탈퇴를 처리합니다. 판매자 계정과 관련된 모든 리프레시 토큰, 마켓 정보가 삭제됩니다.\n\n" +
                    "**권한:** SELLER\n" +
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
                                                    "  \"message\": \"판매자 회원 탈퇴가 완료되었습니다.\"\n" +
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
                    description = "관리자를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "관리자 없음 예시",
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

