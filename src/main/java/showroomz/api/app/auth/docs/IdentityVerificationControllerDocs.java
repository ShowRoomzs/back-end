package showroomz.api.app.auth.docs;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.IdentityVerificationCompleteRequest;
import showroomz.api.app.auth.DTO.IdentityVerificationCompleteResponse;
import showroomz.api.app.auth.DTO.IdentityVerificationStartRequest;
import showroomz.api.app.auth.DTO.IdentityVerificationStartResponse;

@Tag(name = "User - Identity Verification", description = "본인인증(포트원 V2 · KCP 휴대폰 본인확인) — C0-2 회원가입 · C15-2 재인증")
public interface IdentityVerificationControllerDocs {

    @Operation(
            summary = "본인인증 시작 — id · storeId · channelKey 발급",
            description = "포트원 SDK 창을 띄우기 전에 부릅니다. 응답의 세 값을 **그대로** RN SDK "
                    + "`<IdentityVerification request={{ storeId, identityVerificationId, channelKey }} onComplete onError />` 에 넘깁니다.\n\n"
                    + "- `redirectUrl` · `customer` · `bypass` 는 넣지 않습니다(SDK 가 `redirectUrl` 을 `portone://blank` 로 덮어씁니다).\n"
                    + "- 앱에 포트원 키를 고정하지 않습니다 — 테스트 → 운영 채널 전환은 서버 설정만 바뀝니다.\n"
                    + "- 1분 안에 같은 용도로 다시 부르면 **같은 id** 가 내려옵니다(연타 멱등).\n"
                    + "- `expiresAt`(시작 + 10분) 을 넘기면 완료를 부르지 말고 다시 시작합니다.\n\n"
                    + "**권한:** `SIGNUP` — 소셜 로그인이 돌려준 registerToken(GUEST) / `REVERIFY` — accessToken(USER)\n"
                    + "**요청 헤더:** Authorization: Bearer {registerToken | accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "발급 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = IdentityVerificationStartResponse.class),
                            examples = @ExampleObject(name = "성공", value = "{\n"
                                    + "  \"identityVerificationId\": \"idv01HXYZ3K8M2Q7R9T1V4W6Y8Z\",\n"
                                    + "  \"storeId\": \"store-4ff4af41-85e3-4559-8eb8-0d08a2c6ceec\",\n"
                                    + "  \"channelKey\": \"channel-key-9987cb87-6458-4888-b94e-68d9a2da896d\",\n"
                                    + "  \"expiresAt\": \"2026-10-24T10:10:00\"\n"
                                    + "}"))),
            @ApiResponse(responseCode = "400", description = "`SIGNUP` 인데 이미 가입된 사용자 — `ALREADY_REGISTERED`",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "토큰 없음 · 만료 — `UNAUTHORIZED`",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "`REVERIFY` 인데 탈퇴한 회원 — `USER_WITHDRAWN`",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "`REVERIFY` 인데 가입 미완료(GUEST) — `USER_NOT_FOUND`",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<IdentityVerificationStartResponse> start(HttpServletRequest request,
                                                            @RequestBody IdentityVerificationStartRequest body);

    @Operation(
            summary = "본인인증 완료 — 서버가 포트원을 조회해 판정",
            description = "SDK `onComplete` 가 오면 **성공 · 실패 가리지 말고** 부릅니다(`onError` 뒤에도 부르면 409 로 떨어져 같은 화면으로 갑니다). "
                    + "서버가 `GET /identity-verifications/{id}` 로 포트원에 직접 물어 `VERIFIED` 인지 · 누구인지 판정합니다. "
                    + "앱이 보낸 값은 기록만 하고 판정에 쓰지 않습니다.\n\n"
                    + "**판정 순서:** 포트원 조회 → 만 14세 → DI 누락 → DI 중복(다른 활성 회원) → VERIFIED 보관(10분)\n\n"
                    + "**이후:** `SIGNUP` 이면 `POST /v1/user/auth/social/signup`, `REVERIFY` 면 `POST /v1/user/settings/account/verifications` 가 "
                    + "이 결과를 회원정보에 반영합니다. 두 API 는 완료가 선행되지 않으면 400 `IDENTITY_VERIFICATION_REQUIRED` 입니다.\n\n"
                    + "**화면 분기(C0):** 200 → 1c 회원가입 / 403 `UNDER_MIN_AGE` → 1d 14세 차단 / 409 · 422 · 502 → 1e 「다시 인증하기」(= 시작 API 부터)\n\n"
                    + "**권한:** 시작과 같은 토큰\n"
                    + "**요청 본문(선택 · 기록용):** `identityVerificationTxId` · `code` · `pgCode` — SDK 응답 그대로"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "인증 완료 (이미 VERIFIED 인 행을 다시 불러도 같은 200)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = IdentityVerificationCompleteResponse.class),
                            examples = @ExampleObject(name = "성공", value = "{\n"
                                    + "  \"status\": \"VERIFIED\",\n"
                                    + "  \"purpose\": \"SIGNUP\",\n"
                                    + "  \"maskedName\": \"홍길*\",\n"
                                    + "  \"maskedPhone\": \"010-****-0000\",\n"
                                    + "  \"verifiedAt\": \"2026-10-24T10:03:12\"\n"
                                    + "}"))),
            @ApiResponse(responseCode = "403", description = "만 14세 미만 — `UNDER_MIN_AGE`. 인증 정보는 저장하지 않고 즉시 폐기합니다(C0 1d)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "14세 미만", value = "{\n  \"code\": \"UNDER_MIN_AGE\",\n  \"message\": \"만 14세 미만은 가입할 수 없습니다.\"\n}"))),
            @ApiResponse(responseCode = "404", description = "내 인증 요청이 아니거나 없음 — `IDENTITY_VERIFICATION_NOT_FOUND`",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "아직 완료 안 됨 `IDENTITY_VERIFICATION_NOT_COMPLETED`(창만 닫힘 — 다시 부를 수 있음) · "
                    + "유효 시간 지남 `IDENTITY_VERIFICATION_EXPIRED` · 이미 사용됨 `IDENTITY_VERIFICATION_CONSUMED` · "
                    + "같은 명의로 이미 가입된 활성 회원 있음 `IDENTITY_ALREADY_REGISTERED`",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "창만 닫힘", value = "{\n  \"code\": \"IDENTITY_VERIFICATION_NOT_COMPLETED\",\n  \"message\": \"본인인증이 완료되지 않았습니다. 다시 시도해 주세요.\"\n}"),
                                    @ExampleObject(name = "중복 가입", value = "{\n  \"code\": \"IDENTITY_ALREADY_REGISTERED\",\n  \"message\": \"이미 가입된 본인인증 정보입니다.\"\n}")
                            })),
            @ApiResponse(responseCode = "422", description = "인증사가 실패를 돌려줌(제한 시간 초과 · 통신사 불일치 등) — `IDENTITY_VERIFICATION_FAILED`. 다시 시작합니다",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "인증사 통신 실패(결과 모름) — `IDENTITY_GATEWAY_ERROR`. 행은 남아 있으니 같은 id 로 다시 부를 수 있습니다",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<IdentityVerificationCompleteResponse> complete(HttpServletRequest request,
                                                                  @Parameter(description = "시작 API 가 발급한 identityVerificationId") @PathVariable String identityVerificationId,
                                                                  @RequestBody(required = false) IdentityVerificationCompleteRequest body);
}
