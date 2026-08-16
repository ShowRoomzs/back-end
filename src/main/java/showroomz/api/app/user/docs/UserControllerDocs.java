package showroomz.api.app.user.docs;

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
import showroomz.api.app.user.DTO.WithdrawalInfoResponse;

@Tag(name = "User - Profile", description = "사용자 프로필 관리 API")
public interface UserControllerDocs {

    @Operation(
            summary = "현재 로그인한 사용자 정보 조회",
            description = "프로필 카드에 표시될 현재 로그인한 사용자의 정보(닉네임, 이메일, 프로필 이미지 등)를 조회합니다.\n\n" +
                    "**참고사항**\n" +
                    "- 프로필 사진이 없는 경우 `profileImageUrl`은 `null`로 반환됩니다.\n" +
                    "- `followingCount`: 내가 팔로우한 쇼룸 수\n\n" +
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
                                                    "  \"name\": \"홍길동\",\n" +
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
                                                    "  \"name\": \"홍길동\",\n" +
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
                    responseCode = "403",
                    description = "이미 탈퇴한 회원 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "이미 탈퇴한 회원",
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
            summary = "닉네임 유효성 검사 (C0-1 가입 · C15-1 닉네임 변경)",
            description = "가입 화면과 닉네임 변경 화면이 같은 규칙·문구를 씁니다. 항상 200으로 응답하고 " +
                    "`code`로 상태를 구분합니다. `message`는 화면에 그대로 노출할 문구입니다.\n\n" +
                    "**응답 코드 (code)**\n" +
                    "- `AVAILABLE`: 사용할 수 있음 (isAvailable: true) - \"사용할 수 있는 닉네임이에요\"\n" +
                    "- `UNCHANGED`: 로그인 상태에서 현재 닉네임을 그대로 입력 (isAvailable: false) - " +
                    "오류가 아니므로 경고색 없이 표시하되 [저장]은 비활성으로 둡니다\n" +
                    "- `INVALID_LENGTH`: 2자 미만/10자 초과 (isAvailable: false)\n" +
                    "- `INVALID_FORMAT`: 한글·영문·숫자 외 문자 포함 (isAvailable: false)\n" +
                    "- `PROFANITY`: 금칙어 포함 (isAvailable: false)\n" +
                    "- `DUPLICATE`: 이미 사용 중 (isAvailable: false)\n\n" +
                    "**인증:** 선택. Authorization 헤더를 함께 보내면 현재 닉네임과 비교해 `UNCHANGED`를 구분합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "검사 결과 - Status: 200 OK (모든 상태가 200으로 내려옵니다)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NicknameCheckResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용 가능",
                                            value = "{\n" +
                                                    "  \"isAvailable\": true,\n" +
                                                    "  \"code\": \"AVAILABLE\",\n" +
                                                    "  \"message\": \"사용할 수 있는 닉네임이에요\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "현재 닉네임 그대로",
                                            value = "{\n" +
                                                    "  \"isAvailable\": false,\n" +
                                                    "  \"code\": \"UNCHANGED\",\n" +
                                                    "  \"message\": \"현재 사용 중인 닉네임이에요\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "길이 미달",
                                            value = "{\n" +
                                                    "  \"isAvailable\": false,\n" +
                                                    "  \"code\": \"INVALID_LENGTH\",\n" +
                                                    "  \"message\": \"2자 이상 입력해 주세요\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "형식 오류",
                                            value = "{\n" +
                                                    "  \"isAvailable\": false,\n" +
                                                    "  \"code\": \"INVALID_FORMAT\",\n" +
                                                    "  \"message\": \"한글·영문·숫자만 사용할 수 있어요\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "금지 단어",
                                            value = "{\n" +
                                                    "  \"isAvailable\": false,\n" +
                                                    "  \"code\": \"PROFANITY\",\n" +
                                                    "  \"message\": \"사용할 수 없는 단어가 포함되어 있어요\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "중복",
                                            value = "{\n" +
                                                    "  \"isAvailable\": false,\n" +
                                                    "  \"code\": \"DUPLICATE\",\n" +
                                                    "  \"message\": \"이미 사용 중인 닉네임이에요\"\n" +
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
                    example = "수민이네"
            )
            @RequestParam("nickname") String nickname
    );

    @Operation(
            summary = "현재 로그인한 사용자 프로필 정보 수정 (C15 · C15-1)",
            description = "설정 화면에서 바꿀 수 있는 값만 수정합니다 - **닉네임**과 **프로필 사진**.\n\n" +
                    "이름·생년월일·성별·휴대폰번호는 본인인증(PASS) 결과라 여기서 수정할 수 없고, " +
                    "`POST /v1/user/settings/account/verifications`(재인증)로만 갱신됩니다. " +
                    "광고성 정보 수신 동의는 `PATCH /v1/user/settings/notifications`로 옮겼습니다.\n\n" +
                    "닉네임이 현재 값과 같으면 오류 없이 통과하며 아무것도 바뀌지 않습니다. " +
                    "`profileImageUrl`에 빈 문자열을 보내면 기본 이미지로 되돌립니다.\n\n" +
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
                                                    "  \"name\": \"홍길동\",\n" +
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
                    responseCode = "403",
                    description = "이미 탈퇴한 회원 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "이미 탈퇴한 회원",
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
            description = "프로필 수정 요청 (모든 필드는 선택사항, 보내지 않은 필드는 변경되지 않음)",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UpdateUserProfileRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "C15-1 닉네임 변경",
                                    value = "{\n  \"nickname\": \"수민이네\"\n}"
                            ),
                            @ExampleObject(
                                    name = "C15 프로필 사진 변경",
                                    value = "{\n  \"profileImageUrl\": \"https://cdn.showroomz.com/profile/1.jpg\"\n}"
                            ),
                            @ExampleObject(
                                    name = "프로필 사진 제거",
                                    value = "{\n  \"profileImageUrl\": \"\"\n}",
                                    description = "빈 문자열이면 기본 이미지로 되돌립니다"
                            )
                    }
            )
    )
    ResponseEntity<?> updateCurrentUser(@RequestBody UpdateUserProfileRequest request);

    @Operation(
            summary = "C15-3/C15-4 회원 탈퇴 화면 진입 데이터",
            description = "탈퇴 화면을 그리는 데 필요한 값을 한 번에 반환합니다.\n\n" +
                    "- `withdrawable`: 탈퇴 가능 여부. false면 차단 상태이므로 동의 체크와 [탈퇴하기]를 계속 비활성으로 둡니다.\n" +
                    "- `ongoingOrderCount`: 진행 중인 주문 상품 수. 0보다 크면 \"진행 중인 주문이 있어 지금은 탈퇴할 수 없어요\" " +
                    "안내와 \"진행 중 주문 N건 보기\"를 노출합니다.\n" +
                    "- `followingCount` / `wishlistCount` / `cartCount`: 탈퇴 시 삭제되는 활동 기록 수. " +
                    "최종 확인 모달의 \"팔로잉 N곳과 좋아요 M개가 모두 삭제되고...\" 문구에 씁니다.\n" +
                    "- `reasons`: 1단계에 노출할 탈퇴 이유 목록. `code`를 그대로 탈퇴 요청의 `reason`으로 보냅니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WithdrawalInfoResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "진행 중 주문이 있어 차단된 경우",
                                            value = "{\n" +
                                                    "  \"withdrawable\": false,\n" +
                                                    "  \"ongoingOrderCount\": 2,\n" +
                                                    "  \"followingCount\": 4,\n" +
                                                    "  \"wishlistCount\": 12,\n" +
                                                    "  \"cartCount\": 3,\n" +
                                                    "  \"reasons\": [\n" +
                                                    "    { \"code\": \"NO_GROUP_BUY\", \"label\": \"원하는 공구가 없어요\" },\n" +
                                                    "    { \"code\": \"TOO_MANY_NOTIFICATIONS\", \"label\": \"알림이 너무 많아요\" },\n" +
                                                    "    { \"code\": \"INCONVENIENT_APP\", \"label\": \"앱이 사용하기 불편해요\" },\n" +
                                                    "    { \"code\": \"PRIVACY_CONCERN\", \"label\": \"개인정보가 걱정돼요\" },\n" +
                                                    "    { \"code\": \"REJOIN_OTHER_ACCOUNT\", \"label\": \"다른 계정으로 다시 가입할 거예요\" },\n" +
                                                    "    { \"code\": \"ETC\", \"label\": \"기타\" }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "탈퇴 가능한 경우",
                                            value = "{\n" +
                                                    "  \"withdrawable\": true,\n" +
                                                    "  \"ongoingOrderCount\": 0,\n" +
                                                    "  \"followingCount\": 4,\n" +
                                                    "  \"wishlistCount\": 12,\n" +
                                                    "  \"cartCount\": 0,\n" +
                                                    "  \"reasons\": []\n" +
                                                    "}",
                                            description = "reasons는 항상 6개가 내려오며, 예시에서만 생략했습니다"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "이미 탈퇴한 회원 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "이미 탈퇴한 회원",
                                    value = "{\n" +
                                            "  \"code\": \"USER_WITHDRAWN\",\n" +
                                            "  \"message\": \"탈퇴한 회원입니다.\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<WithdrawalInfoResponse> getWithdrawalInfo(
            @Parameter(hidden = true) UserPrincipal userPrincipal
    );

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
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "은행 코드 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"은행 코드는 3자리여야 합니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "계좌번호 오류",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"계좌번호는 숫자만 입력해주세요.\"\n" +
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