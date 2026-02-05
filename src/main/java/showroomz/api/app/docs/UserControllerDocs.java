package showroomz.api.app.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.auth.DTO.ValidationErrorResponse;
import showroomz.api.app.user.DTO.NicknameCheckResponse;
import showroomz.api.app.user.DTO.RefundAccountRequest;
import showroomz.api.app.user.DTO.RefundAccountResponse;
import showroomz.api.app.user.DTO.UpdateUserProfileRequest;
import showroomz.api.app.user.DTO.UserProfileResponse;

@Tag(name = "User - Profile", description = "사용자 프로필 관리 API")
public interface UserControllerDocs {

    @Operation(
            summary = "현재 로그인한 사용자 정보 조회",
            description = "프로필 카드에 표시될 현재 로그인한 사용자의 정보(닉네임, 이메일, 프로필 이미지 등)를 조회합니다.\n\n" +
                    "**참고사항**\n" +
                    "- 프로필 사진이 없는 경우 `profileImageUrl`은 `null`로 반환됩니다.\n" +
                    "- `followingCount`: 내가 팔로우하는 유저(또는 마켓) 수\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserProfileResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시 (프로필 사진 있음)",
                                            value = "{\n" +
                                                    "  \"id\": 1,\n" +
                                                    "  \"email\": \"string\",\n" +
                                                    "  \"nickname\": \"string\",\n" +
                                                    "  \"profileImageUrl\": \"https://k.kakaocdn.net/img_640x640.jpg\",\n" +
                                                    "  \"phoneNumber\": \"010-1234-5678\",\n" +
                                                    "  \"birthday\": \"YYYY-MM-DD\",\n" +
                                                    "  \"gender\": \"MALE\",\n" +
                                                    "  \"providerType\": \"GOOGLE\",\n" +
                                                    "  \"roleType\": \"USER\",\n" +
                                                    "  \"createdAt\": \"2025-10-31T10:00:00Z\",\n" +
                                                    "  \"modifiedAt\": \"2025-10-31T10:00:00Z\",\n" +
                                                    "  \"marketingAgree\": true,\n" +
                                                    "  \"followingCount\": 0\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "성공 시 (프로필 사진 없음)",
                                            value = "{\n" +
                                                    "  \"id\": 1,\n" +
                                                    "  \"email\": \"string\",\n" +
                                                    "  \"nickname\": \"string\",\n" +
                                                    "  \"profileImageUrl\": null,\n" +
                                                    "  \"phoneNumber\": \"010-1234-5678\",\n" +
                                                    "  \"birthday\": \"YYYY-MM-DD\",\n" +
                                                    "  \"gender\": \"MALE\",\n" +
                                                    "  \"providerType\": \"GOOGLE\",\n" +
                                                    "  \"roleType\": \"USER\",\n" +
                                                    "  \"createdAt\": \"2025-10-31T10:00:00Z\",\n" +
                                                    "  \"modifiedAt\": \"2025-10-31T10:00:00Z\",\n" +
                                                    "  \"marketingAgree\": true,\n" +
                                                    "  \"followingCount\": 0\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
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
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
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
    ResponseEntity<UserProfileResponse> getCurrentUser();

    @Operation(
            summary = "닉네임 유효성 검사",
            description = "닉네임 유효성 검사를 수행합니다.\n\n" +
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
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NicknameCheckResponse.class),
                            examples = {
                                    @ExampleObject(
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
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NicknameCheckResponse.class),
                            examples = {
                                    @ExampleObject(
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
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NicknameCheckResponse.class),
                            examples = {
                                    @ExampleObject(
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
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NicknameCheckResponse.class),
                            examples = {
                                    @ExampleObject(
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
    ResponseEntity<NicknameCheckResponse> checkNickname(
            @Parameter(
                    name = "nickname",
                    description = "검사할 닉네임 (필수)",
                    required = true,
                    example = "abc123"
            )
            @RequestParam("nickname") String nickname
    );

    @Operation(
            summary = "현재 로그인한 사용자 프로필 정보 수정",
            description = "현재 로그인한 사용자의 프로필 정보(닉네임, 프로필 이미지 등)를 수정합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserProfileResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시",
                                            value = "{\n" +
                                                    "  \"id\": 1,\n" +
                                                    "  \"email\": \"string\",\n" +
                                                    "  \"nickname\": \"string\",\n" +
                                                    "  \"profileImageUrl\": \"https://k.kakaocdn.net/dn/.../img_640x640.jpg\",\n" +
                                                    "  \"phoneNumber\": \"010-1234-5678\",\n" +
                                                    "  \"birthday\": \"YYYY-MM-DD\",\n" +
                                                    "  \"gender\": \"MALE\",\n" +
                                                    "  \"providerType\": \"GOOGLE\",\n" +
                                                    "  \"roleType\": \"USER\",\n" +
                                                    "  \"createdAt\": \"2025-10-31T10:00:00Z\",\n" +
                                                    "  \"modifiedAt\": \"2025-10-31T10:00:00Z\",\n" +
                                                    "  \"marketingAgree\": true,\n" +
                                                    "  \"followingCount\": 0\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 형식 오류 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = {
                                    @ExampleObject(
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
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"gender\",\n" +
                                                    "      \"reason\": \"성별은 MALE 또는 FEMALE만 가능합니다.\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
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
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "닉네임 중복 - Status: 409 Conflict",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
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
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UpdateUserProfileRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"nickname\": \"string\",\n" +
                                            "  \"phoneNumber\": \"010-1234-5678\",\n" +
                                            "  \"birthday\": \"YYYY-MM-DD\",\n" +
                                            "  \"gender\": \"MALE\",\n" +
                                            "  \"profileImageUrl\": \"https://...\",\n" +
                                            "  \"marketingAgree\": true\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<?> updateCurrentUser(@RequestBody UpdateUserProfileRequest request);

    @Operation(
            summary = "내 환불 계좌 조회",
            description = "등록된 환불 계좌 정보를 조회합니다. 등록된 정보가 없으면 null을 반환합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 - Status: 200 OK (등록된 계좌가 있으면 본문에 데이터, 없으면 null)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefundAccountResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "등록된 환불 계좌가 있는 경우",
                                            value = "{\n" +
                                                    "  \"bankCode\": \"004\",\n" +
                                                    "  \"bankName\": \"KB국민은행\",\n" +
                                                    "  \"accountNumber\": \"123456789012\",\n" +
                                                    "  \"accountHolder\": \"홍길동\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "등록된 환불 계좌가 없는 경우",
                                            value = "null"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
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
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
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
    ResponseEntity<RefundAccountResponse> getRefundAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal
    );

    @Operation(
            summary = "환불 계좌 등록/수정",
            description = "로그인한 사용자의 환불 계좌 정보를 등록하거나 수정합니다.\n\n" +
                    "**설명**\n" +
                    "- 환불이 발생할 경우 이 계좌로 환불금이 입금됩니다.\n" +
                    "- 기존 환불 계좌가 있는 경우 새 정보로 덮어씌워집니다.\n" +
                    "- `bankCode`는 은행 목록 조회 API(`common/banks`)에서 제공하는 3자리 표준 코드를 사용합니다. (예: KB국민은행 004, 카카오뱅크 090)\n" +
                    "- `accountNumber`는 하이픈 없이 숫자만 입력해야 합니다.\n" +
                    "- `accountHolder`(예금주명)는 선택 입력입니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "등록/수정 성공 - Status: 200 OK (응답 본문 없음)"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 형식 오류 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유효성 검증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\",\n" +
                                                    "  \"errors\": [\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"bankCode\",\n" +
                                                    "      \"reason\": \"은행 코드는 3자리여야 합니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"accountNumber\",\n" +
                                                    "      \"reason\": \"계좌번호는 숫자만 입력해주세요.\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
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
                    responseCode = "403",
                    description = "탈퇴한 회원 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "탈퇴 회원",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_WITHDRAWN\",\n" +
                                                    "  \"message\": \"탈퇴한 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자 또는 은행 코드를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "은행 코드 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"BANK_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 은행 코드입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "환불 계좌 정보 (bankCode, accountNumber 필수 / accountHolder 선택)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = RefundAccountRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"bankCode\": \"004\",\n" +
                                            "  \"accountNumber\": \"123456789012\",\n" +
                                            "  \"accountHolder\": \"홍길동\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateRefundAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody RefundAccountRequest request
    );
}