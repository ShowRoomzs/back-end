package showroomz.api.creator.basicinfo.docs;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.basicinfo.dto.CreatorBasicInfoDto;

@Tag(name = "Creator - Basic Info", description = "쇼룸 스튜디오 기본정보 관리 API")
@SecurityRequirement(name = "Authorization")
public interface CreatorBasicInfoControllerDocs {

    // ------------------------------------------------------------------ 계정 탭

    @Operation(summary = "계정 탭 조회",
            description = """
                    본인 인증 정보(마스킹 · 셀프 수정 불가) · 업무 이메일 · 로그인 수단 · 탈퇴 블록(S1 ~ S8).

                    **본인 인증 정보** — 실명 · 휴대폰은 가운데 마스킹, 생년월일은 저장 형식 그대로. 바꾸려면 운영팀 문의(FE 문구).

                    **로그인 수단** — 스튜디오는 소셜 로그인만 있다(비밀번호 없음). `provider` 는 가입한 소셜 수단(`KAKAO` · `NAVER` · `GOOGLE` · `APPLE` 등).

                    **탈퇴 `state`** — 아래 순서로 처음 맞는 것 하나:
                    1. `PENDING`(S7) — 승인 대기 신청이 있다. `pendingRequest` 에 신청 id · 일시 · 사유 · 처리 기한
                    2. `REJECTED`(S8) — 가장 최근 신청이 반려됐고 안내를 아직 [확인]하지 않았다. `rejection` 에 사유 · 안내
                    3. `BLOCKED`(S4) — 정지 중 신청 불가(설정 · 아래). 조건과 무관
                    4. `AVAILABLE`(S5) — 조건 3종 모두 충족
                    5. `BLOCKED`(S4) — 조건 미충족

                    **정지 중 신청(`suspensionBlockedReason`)** — 설정 `member.sanction.allow-withdrawal-while-suspended`(기본 true)에 따른다.
                    허용이면 정지와 무관하게 조건만 본다(항상 null). 끄면 정지 중에는 신청 409 `CREATOR_SUSPENDED` 와 같은 문장이 실린다 —
                    `PENDING` 이면 null(접수된 신청은 유지), `REJECTED` 면 안내와 함께 실려 재신청이 막혔음을 알린다.

                    **조건 행(`conditions`)** — 상태와 무관하게 항상 3행(`UNFINISHED_GROUP_BUYS` · `UNFINISHED_CONTRACTS` · `UNSETTLED`)을 내린다.
                    어드민 승인 직전 재검증과 같은 판정식이다. `breakdown` 은 0 건 키를 내리지 않는다. 조건 행의 링크(공구 · 계약 · 정산 관리)는 FE 라우팅이다.

                    | 조건 | `breakdown.key` |
                    | --- | --- |
                    | `UNFINISHED_GROUP_BUYS` 끝나지 않은 공구 | `PREPARING` · `READY` · `IN_PROGRESS` · `SUSPENSION_SCHEDULED` · `AWAITING_ORDER_CLOSURE`(종료 후 주문 종결 대기) |
                    | `UNFINISHED_CONTRACTS` 끝나지 않은 계약 | `SIGNING` · `CONCLUSION_PENDING` · `CONCLUDED`(끝나지 않은 공구에 연결된 체결 계약) |
                    | `UNSETTLED` 미정산 | `REVIEWING` · `ADJUSTING` · `PAYOUT_SCHEDULED` · `PAYOUT_FAILED` |

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreatorBasicInfoDto.AccountResponse.class),
                            examples = {
                                    @ExampleObject(name = "S4 조건 미충족(BLOCKED)", value = """
                                            {
                                              "identity": { "realName": "소*연", "birthday": "2003-07-19", "phoneNumber": "010-****-6416" },
                                              "businessEmail": "soyeon@example.com",
                                              "login": { "provider": "KAKAO", "providerLabel": "카카오" },
                                              "withdrawal": {
                                                "state": "BLOCKED", "suspensionBlockedReason": null, "satisfiedCount": 0, "totalCount": 3,
                                                "conditions": [
                                                  { "code": "UNFINISHED_GROUP_BUYS", "label": "끝나지 않은 공구", "count": 2, "satisfied": false,
                                                    "resolution": "주문이 모두 종결되어야 합니다",
                                                    "breakdown": [ { "key": "IN_PROGRESS", "label": "진행중", "count": 1 },
                                                                   { "key": "AWAITING_ORDER_CLOSURE", "label": "종료 후 주문 종결 대기", "count": 1 } ] },
                                                  { "code": "UNFINISHED_CONTRACTS", "label": "끝나지 않은 계약", "count": 1, "satisfied": false,
                                                    "resolution": "연결된 공구가 끝나야 합니다",
                                                    "breakdown": [ { "key": "CONCLUDED", "label": "계약 성립", "count": 1 } ] },
                                                  { "code": "UNSETTLED", "label": "미정산", "count": 1, "satisfied": false,
                                                    "resolution": "모두 지급 완료되어야 합니다",
                                                    "breakdown": [ { "key": "ADJUSTING", "label": "조정 협의", "count": 1 } ] }
                                                ],
                                                "approvalDueBusinessDays": 5, "pendingRequest": null, "rejection": null
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "S5 신청 가능(AVAILABLE)", value = """
                                            {
                                              "identity": { "realName": "소*연", "birthday": "2003-07-19", "phoneNumber": "010-****-6416" },
                                              "businessEmail": "soyeon@example.com",
                                              "login": { "provider": "KAKAO", "providerLabel": "카카오" },
                                              "withdrawal": {
                                                "state": "AVAILABLE", "suspensionBlockedReason": null, "satisfiedCount": 3, "totalCount": 3,
                                                "conditions": [
                                                  { "code": "UNFINISHED_GROUP_BUYS", "label": "끝나지 않은 공구", "count": 0, "satisfied": true,
                                                    "resolution": "주문이 모두 종결되어야 합니다", "breakdown": [] },
                                                  { "code": "UNFINISHED_CONTRACTS", "label": "끝나지 않은 계약", "count": 0, "satisfied": true,
                                                    "resolution": "연결된 공구가 끝나야 합니다", "breakdown": [] },
                                                  { "code": "UNSETTLED", "label": "미정산", "count": 0, "satisfied": true,
                                                    "resolution": "모두 지급 완료되어야 합니다", "breakdown": [] }
                                                ],
                                                "approvalDueBusinessDays": 5, "pendingRequest": null, "rejection": null
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "정지 중 신청 불가(BLOCKED · 설정 false)", value = """
                                            {
                                              "identity": { "realName": "소*연", "birthday": "2003-07-19", "phoneNumber": "010-****-6416" },
                                              "businessEmail": "soyeon@example.com",
                                              "login": { "provider": "KAKAO", "providerLabel": "카카오" },
                                              "withdrawal": {
                                                "state": "BLOCKED", "suspensionBlockedReason": "계정 이용 정지 중에는 탈퇴를 신청할 수 없습니다.",
                                                "satisfiedCount": 3, "totalCount": 3,
                                                "conditions": [ "… 3행 — S5 와 같음(조건은 충족)" ],
                                                "approvalDueBusinessDays": 5, "pendingRequest": null, "rejection": null
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "S7 승인 대기(PENDING)", value = """
                                            {
                                              "identity": { "realName": "소*연", "birthday": "2003-07-19", "phoneNumber": "010-****-6416" },
                                              "businessEmail": "soyeon@example.com",
                                              "login": { "provider": "KAKAO", "providerLabel": "카카오" },
                                              "withdrawal": {
                                                "state": "PENDING", "suspensionBlockedReason": null, "satisfiedCount": 3, "totalCount": 3,
                                                "conditions": [ "… 3행 — S5 와 같음" ],
                                                "approvalDueBusinessDays": 5,
                                                "pendingRequest": { "requestId": 41, "requestedAt": "2026-10-14T10:20:00",
                                                                    "reasonText": "활동 중단", "dueDate": "2026-10-21" },
                                                "rejection": null
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "S8 반려 안내(REJECTED)", value = """
                                            {
                                              "identity": { "realName": "소*연", "birthday": "2003-07-19", "phoneNumber": "010-****-6416" },
                                              "businessEmail": "soyeon@example.com",
                                              "login": { "provider": "KAKAO", "providerLabel": "카카오" },
                                              "withdrawal": {
                                                "state": "REJECTED", "suspensionBlockedReason": null, "satisfiedCount": 2, "totalCount": 3,
                                                "conditions": [ "… 3행 — 현재 판정 그대로" ],
                                                "approvalDueBusinessDays": 5,
                                                "pendingRequest": null,
                                                "rejection": { "requestId": 41, "rejectedAt": "2026-10-16T15:02:00",
                                                               "reasonCode": "SETTLEMENT_IN_PROGRESS", "reasonLabel": "미정산 남음",
                                                               "note": "9월 정산 지급이 끝난 뒤 다시 신청해 주세요." }
                                              }
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "404", description = "인플루언서 아님 — CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_NOT_FOUND", "message": "존재하지 않는 크리에이터입니다." }
                                    """)))
    })
    ResponseEntity<CreatorBasicInfoDto.AccountResponse> getAccount(@Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "업무 이메일 저장",
            description = """
                    승인 · 계약 · 정산 알림을 받을 주소를 바꾼다. 이 탭에서 셀프 수정하는 유일한 값이다.

                    - **즉시 저장** — 인증 메일 없음 · 다른 계정과 중복 검사 없음 · 처리 이력 없음.
                    - 앞뒤 공백은 지우고 검증한다. 같은 값이면 저장 없이 200.
                    - 형식 — `@` 앞뒤가 있고 **도메인에 점이 있어야** 한다(`soyeon@example` 은 400). 512자 이내.
                    - 응답은 계정 탭 조회와 같다 — FE 는 그대로 다시 그린다.

                    **권한:** CREATOR
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CreatorBasicInfoDto.UpdateBusinessEmailRequest.class),
                    examples = @ExampleObject(value = """
                            { "businessEmail": "soyeon.work@example.com" }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공 — 계정 탭 조회와 같은 응답",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreatorBasicInfoDto.AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "미입력 · 형식 오류 · 512자 초과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "형식 오류", value = """
                                            { "code": "INVALID_INPUT", "message": "이메일 형식이 올바르지 않습니다." }
                                            """),
                                    @ExampleObject(name = "미입력", value = """
                                            { "code": "INVALID_INPUT", "message": "업무 이메일을 입력해 주세요." }
                                            """)
                            })),
            @ApiResponse(responseCode = "409", description = "탈퇴한 인플루언서 — CREATOR_WITHDRAWN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_WITHDRAWN", "message": "탈퇴한 인플루언서입니다. 조치할 수 없습니다." }
                                    """)))
    })
    ResponseEntity<CreatorBasicInfoDto.AccountResponse> updateBusinessEmail(
            @Parameter(hidden = true) UserPrincipal principal,
            @Valid @RequestBody CreatorBasicInfoDto.UpdateBusinessEmailRequest request);

    @Operation(summary = "탈퇴 승인 요청",
            description = """
                    S6 모달의 [탈퇴 승인 요청]. 인플루언서는 즉시 탈퇴가 없다 — 신청 → 운영자 승인(§51-5).

                    - 조건(공구 · 계약 · 정산)을 **서버가 다시 판정**한다. 하나라도 남았으면 409 `CREATOR_WITHDRAWAL_CONDITION_UNMET` —
                      응답 최상위 `unmet` 에 미충족 조건별 건수, `message` 에 건수 문장이 실린다.
                    - 사유는 **필수 · 1 ~ 500자**(앞뒤 공백 제거 후). 선택지가 정의되지 않아 FE 가 고른 라벨 문자열을 그대로 보낸다.
                    - 운영자가 **영업일 5일 이내**에 승인 · 반려한다(`dueDate` — 무응답 자동 승인 없음).
                    - 대기 중에는 새 연결 수락 · 계약 서명 요청이 막힌다(409 `CREATOR_WITHDRAWAL_PENDING` · 브랜드 쪽 `CREATOR_COUNTERPARTY_WITHDRAWAL_PENDING`).
                      진행 중 공구 운영 · 정산은 그대로다.
                    - **정지 중 신청은 설정에 따른다**(`member.sanction.allow-withdrawal-while-suspended` · 기본 true = 허용 — 정지는 새 일을
                      막고 탈퇴는 정리하는 일이다). 끄면 정지 중 409 `CREATOR_SUSPENDED`(계정 탭 `suspensionBlockedReason` 과 같은 문장).
                    - 반려 안내(S8)를 닫지 않은 채 다시 신청하면 그 반려는 확인한 것으로 본다.

                    **권한:** CREATOR
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CreatorBasicInfoDto.WithdrawalRequestBody.class),
                    examples = @ExampleObject(value = """
                            { "reason": "활동 중단" }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신청 완료 — 계정 탭이 S7(PENDING)로 바뀐다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreatorBasicInfoDto.WithdrawalRequestResponse.class),
                            examples = @ExampleObject(value = """
                                    { "requestId": 41, "requestedAt": "2026-10-14T10:20:00", "dueDate": "2026-10-21" }
                                    """))),
            @ApiResponse(responseCode = "400", description = "사유 공백 · 500자 초과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "사유 공백", value = """
                                            { "code": "CREATOR_WITHDRAWAL_REASON_REQUIRED", "message": "탈퇴 사유를 입력해 주세요." }
                                            """),
                                    @ExampleObject(name = "500자 초과", value = """
                                            { "code": "INVALID_INPUT", "message": "탈퇴 사유는 500자 이내로 입력해 주세요." }
                                            """)
                            })),
            @ApiResponse(responseCode = "404", description = "인플루언서 아님 — CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "조건 미충족 · 열린 신청 있음 · 탈퇴 회원 · 정지 중(설정 false)",
                    content = @Content(mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "정지 중(설정 false)", value = """
                                            { "code": "CREATOR_SUSPENDED", "message": "계정 이용 정지 중에는 탈퇴를 신청할 수 없습니다." }
                                            """),
                                    @ExampleObject(name = "조건 미충족", value = """
                                            {
                                              "code": "CREATOR_WITHDRAWAL_CONDITION_UNMET",
                                              "message": "탈퇴 조건이 충족되지 않았습니다 — 끝나지 않은 공구 1건 · 끝나지 않은 계약 0건 · 미정산 2건",
                                              "unmet": { "UNFINISHED_GROUP_BUYS": 1, "UNSETTLED": 2 }
                                            }
                                            """),
                                    @ExampleObject(name = "열린 신청 있음", value = """
                                            { "code": "CREATOR_WITHDRAWAL_ALREADY_PENDING", "message": "처리 중인 탈퇴 신청이 있습니다." }
                                            """),
                                    @ExampleObject(name = "탈퇴 회원", value = """
                                            { "code": "CREATOR_WITHDRAWN", "message": "탈퇴한 인플루언서입니다. 조치할 수 없습니다." }
                                            """)
                            }))
    })
    ResponseEntity<CreatorBasicInfoDto.WithdrawalRequestResponse> requestWithdrawal(
            @Parameter(hidden = true) UserPrincipal principal,
            @RequestBody CreatorBasicInfoDto.WithdrawalRequestBody request);

    @Operation(summary = "탈퇴 승인 요청 취소",
            description = """
                    S7 의 [요청 취소]. 운영자 처리 전까지 직접 취소한다. 대기 중 제약(새 연결 수락 · 계약 서명 요청)도 같은 순간 풀린다.
                    처리 이력에 「탈퇴 신청 취소」가 남는다.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "취소 완료 — 계정 탭이 S4 · S5 로 돌아간다"),
            @ApiResponse(responseCode = "404", description = "없음 · 남의 신청 — CREATOR_WITHDRAWAL_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_WITHDRAWAL_NOT_FOUND", "message": "존재하지 않는 탈퇴 신청입니다." }
                                    """))),
            @ApiResponse(responseCode = "409", description = "이미 승인 · 반려 · 취소됨 — CREATOR_WITHDRAWAL_NOT_PENDING",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_WITHDRAWAL_NOT_PENDING", "message": "이미 처리된 탈퇴 신청입니다." }
                                    """)))
    })
    ResponseEntity<Void> cancelWithdrawal(@Parameter(hidden = true) UserPrincipal principal,
                                          @Parameter(description = "탈퇴 신청 ID — 계정 탭 `withdrawal.pendingRequest.requestId`",
                                                  required = true, example = "41")
                                          @PathVariable Long requestId);

    @Operation(summary = "탈퇴 반려 안내 확인",
            description = """
                    S8 반려 안내의 [확인]. 표시 플래그만 바뀌고 사유 · 이력은 운영자 쪽에 그대로 남는다.
                    확인 뒤 계정 탭은 현재 조건에 따라 S4(BLOCKED) · S5(AVAILABLE)로 돌아간다. **멱등** — 두 번 불러도 204(첫 확인 시각 유지).

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "확인 완료"),
            @ApiResponse(responseCode = "404", description = "없음 · 남의 신청 — CREATOR_WITHDRAWAL_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "반려된 신청이 아님(대기 · 승인 · 취소) — CREATOR_WITHDRAWAL_NOT_REJECTED",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_WITHDRAWAL_NOT_REJECTED", "message": "반려된 탈퇴 신청만 확인 처리할 수 있습니다." }
                                    """)))
    })
    ResponseEntity<Void> acknowledgeWithdrawalRejection(@Parameter(hidden = true) UserPrincipal principal,
                                                        @Parameter(description = "탈퇴 신청 ID — 계정 탭 `withdrawal.rejection.requestId`",
                                                                required = true, example = "41")
                                                        @PathVariable Long requestId);

    // ------------------------------------------------------------------ 정산 정보 탭

    @Operation(summary = "정산 정보 탭 조회",
            description = """
                    사업자 구분 · 주민등록번호(비사업자 · 마스킹) · 사업자 정보와 세금계산서 공급받는자(사업자) · 정산 계좌(뒤 6자리) ·
                    계좌 확인 상태 · 사업자 구분 변경 배너(T1 ~ T10). **금액은 없다** — 정산 관리가 단일 소스다.

                    **구분별 블록**

                    | 블록 | 비사업자(`INDIVIDUAL`) | 개인사업자(`BUSINESS`) |
                    | --- | --- | --- |
                    | `residentNumber` | `900101-*******` · 등록 시각(이전 등록분은 null) | null(값이 남아 있어도 카드 없음) |
                    | `business` | null | 등록번호 · 사업자등록증 |
                    | `taxInvoiceRecipient` | null | 공급받는자(SHOWROOMZ) — [발행 정보 복사] 재료 |
                    | `account.holderRule` | `REAL_NAME` | `REAL_NAME_OR_COMPANY` |

                    **정산 계좌**
                    - `account.holderName` 은 본인 인증 실명(마스킹)이다 — 예금주를 입력받지 않는다.
                    - `account.verification.status` — `UNVERIFIED`(확인 전) · `VERIFYING`(운영자 확인 중) · `VERIFIED`(확인 완료) ·
                      `FAILED`(확인 실패 · `failReason`) · `CHANGE_REQUIRED`(변경 필요). `VERIFYING · FAILED · CHANGE_REQUIRED` 면 인플루언서 몫 지급이 보류된다.
                    - 제출 서류(`bankbook` · `business.license`)의 파일명 · 크기 · 제출일은 온보딩분이면 null — 「제출 완료」만 그린다.

                    **사업자 구분 변경 배너(`businessTypeChange`)** — 배너가 보이는 최근 요청 1건. 없으면 null.

                    | `status` | `TO_BUSINESS` | `TO_INDIVIDUAL` |
                    | --- | --- | --- |
                    | `PENDING` | T4 확인 대기 | T9 확인 대기 |
                    | `REJECTED` | T4a 반려(사유 · 상세) | T9a 반려 |
                    | `APPROVED` | T5 전환 완료 | T10 전환 완료 |

                    처리 결과 배너는 [닫기](`POST …/{requestId}/acknowledge`)까지 보인다. 취소한 요청은 배너가 없다.

                    **버튼(`actions`)** — `businessTypeChange` 는 모든 상태에서 값이 있다(같은 자리 버튼): 열린 요청이 있으면 `CANCEL`,
                    없으면 현재 구분의 반대 `REQUEST_TO_BUSINESS` · `REQUEST_TO_INDIVIDUAL`. 「정산은 이렇게 진행됩니다」 카드는 FE 정적 문구다.

                    사업자 구분 변경과 계좌 변경은 **동시에 요청할 수 없다**(열린 요청은 인플루언서당 1건). 버튼은 숨기지 않고 비활성 사유를 싣는다.

                    | 확인 중인 요청 | `businessTypeChange` | `businessTypeChangeBlockedReason` | `accountChange` | `accountChangeBlockedReason` |
                    | --- | --- | --- | --- | --- |
                    | 없음 | `REQUEST_TO_*` | null | true | null |
                    | 사업자 구분 변경 | `CANCEL` | null | **false** | 「사업자 구분 확인이 끝난 뒤 요청할 수 있어요」 |
                    | 계좌 변경 | `REQUEST_TO_*`(비활성) | 「정산 계좌 확인이 끝난 뒤 요청할 수 있어요」 | true | null |

                    사유가 있는 버튼을 눌러도 서버는 409 `CHANGE_REQUEST_ALREADY_PENDING` 을 낸다.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreatorBasicInfoDto.SettlementInfoResponse.class),
                            examples = {
                                    @ExampleObject(name = "T1 비사업자", value = """
                                            {
                                              "businessType": { "value": "INDIVIDUAL", "label": "개인 (비사업자)", "confirmedAt": null },
                                              "residentNumber": { "masked": "900101-*******", "registeredAt": "2026-07-02T10:11:00" },
                                              "business": null,
                                              "taxInvoiceRecipient": null,
                                              "account": {
                                                "bankName": "국민은행", "accountNumber": "******456789", "holderName": "소*연", "holderRule": "REAL_NAME",
                                                "bankbook": { "url": "https://cdn…/creator-document/bankbook.jpg", "fileName": null, "fileSize": null, "submittedAt": null },
                                                "verification": { "status": "VERIFIED", "label": "확인 완료", "tone": "SUCCESS",
                                                                  "verifiedAt": "2026-07-02T10:15:00", "failReason": null }
                                              },
                                              "businessTypeChange": null,
                                              "actions": { "businessTypeChange": "REQUEST_TO_BUSINESS", "businessTypeChangeBlockedReason": null,
                                                           "accountChange": true, "accountChangeBlockedReason": null }
                                            }
                                            """),
                                    @ExampleObject(name = "개인사업자 · 계좌 확인 중", value = """
                                            {
                                              "businessType": { "value": "BUSINESS", "label": "개인사업자", "confirmedAt": "2026-09-03T14:00:00" },
                                              "residentNumber": null,
                                              "business": {
                                                "registrationNumber": "123-45-67890",
                                                "license": { "url": "https://cdn…/creator-document/license.pdf", "fileName": "사업자등록증_뷰티소연.pdf",
                                                             "fileSize": 1258291, "submittedAt": "2026-09-01T09:30:00" }
                                              },
                                              "taxInvoiceRecipient": { "businessName": "(주)쇼룸즈", "registrationNumber": "000-00-00000",
                                                                       "representative": "홍길동", "address": "서울특별시 …", "taxEmail": "tax@showroomz.example" },
                                              "account": {
                                                "bankName": "신한은행", "accountNumber": "******234567", "holderName": "소*연", "holderRule": "REAL_NAME_OR_COMPANY",
                                                "bankbook": { "url": "https://cdn…/creator-document/9f2c.jpg", "fileName": "통장사본_신한은행.jpg",
                                                              "fileSize": 482113, "submittedAt": "2026-10-15T11:00:00" },
                                                "verification": { "status": "VERIFYING", "label": "운영자 확인 중", "tone": "INFO",
                                                                  "verifiedAt": null, "failReason": null }
                                              },
                                              "businessTypeChange": null,
                                              "actions": { "businessTypeChange": "REQUEST_TO_INDIVIDUAL", "businessTypeChangeBlockedReason": null,
                                                           "accountChange": true, "accountChangeBlockedReason": null }
                                            }
                                            """),
                                    @ExampleObject(name = "T4 사업자 전환 확인 대기", value = """
                                            {
                                              "businessType": { "value": "INDIVIDUAL", "label": "개인 (비사업자)", "confirmedAt": null },
                                              "residentNumber": { "masked": "900101-*******", "registeredAt": "2026-07-02T10:11:00" },
                                              "business": null,
                                              "taxInvoiceRecipient": null,
                                              "account": { "…": "T1 과 같음" },
                                              "businessTypeChange": {
                                                "requestId": 31, "requestCode": "CHG-2026-0031", "type": "TO_BUSINESS", "status": "PENDING",
                                                "requestedAt": "2026-10-16T09:12:00", "processedAt": null,
                                                "businessRegistrationNumber": "123-45-67890", "maskedResidentNumber": null,
                                                "evidence": { "url": "https://cdn…/creator-document/license.pdf", "fileName": "사업자등록증_뷰티소연.pdf",
                                                              "fileSize": 1258291, "submittedAt": "2026-10-16T09:12:00" },
                                                "rejectReason": null, "rejectReasonDetail": null,
                                                "accountChangeRequiredOnApproval": false
                                              },
                                              "actions": { "businessTypeChange": "CANCEL", "businessTypeChangeBlockedReason": null,
                                                           "accountChange": false,
                                                           "accountChangeBlockedReason": "사업자 구분 확인이 끝난 뒤 요청할 수 있어요" }
                                            }
                                            """),
                                    @ExampleObject(name = "T9a 비사업자 전환 반려", value = """
                                            {
                                              "businessType": { "value": "BUSINESS", "label": "개인사업자", "confirmedAt": "2026-09-03T14:00:00" },
                                              "residentNumber": null,
                                              "business": { "…": "개인사업자 예시와 같음" },
                                              "taxInvoiceRecipient": { "…": "개인사업자 예시와 같음" },
                                              "account": { "…": "개인사업자 예시와 같음" },
                                              "businessTypeChange": {
                                                "requestId": 35, "requestCode": "CHG-2026-0035", "type": "TO_INDIVIDUAL", "status": "REJECTED",
                                                "requestedAt": "2026-10-10T10:00:00", "processedAt": "2026-10-11T16:40:00",
                                                "businessRegistrationNumber": null, "maskedResidentNumber": "900101-*******",
                                                "evidence": { "url": "https://cdn…/creator-document/closure.pdf", "fileName": "폐업사실증명원.pdf",
                                                              "fileSize": 302144, "submittedAt": "2026-10-10T10:00:00" },
                                                "rejectReason": { "code": "CLOSURE_NOT_CONFIRMED", "label": "폐업 사실이 확인되지 않음 (비사업자 전환)" },
                                                "rejectReasonDetail": null,
                                                "accountChangeRequiredOnApproval": false
                                              },
                                              "actions": { "businessTypeChange": "REQUEST_TO_INDIVIDUAL", "businessTypeChangeBlockedReason": null,
                                                           "accountChange": true, "accountChangeBlockedReason": null }
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "404", description = "인플루언서 아님 — CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_NOT_FOUND", "message": "존재하지 않는 크리에이터입니다." }
                                    """)))
    })
    ResponseEntity<CreatorBasicInfoDto.SettlementInfoResponse> getSettlement(@Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "계좌 변경 영향 — 확인 모달(T6)",
            description = """
                    「확인이 끝날 때까지 지급 예정 N건(금액)의 입금이 보류됩니다」의 재료. 모달을 열 때 부른다.

                    - `pendingPayoutCount` · `pendingPayoutAmount` — 인플루언서 몫 지급 행 중 **확정됐고 지시 전**(예정 · 보류)인 것. 운영자 확인이 끝날 때까지 기다린다.
                    - `inFlightPayoutCount` — 이미 지급 지시가 나가 **이전 계좌로** 지급되는 건수. 변경을 막지는 않는다.
                    - 확정 전 정산(검토 · 조정 협의)은 세지 않는다 — 확정 시점의 계좌로 나간다.
                    - 0 건이면 FE 는 보류 문장을 빼고 그린다.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreatorBasicInfoDto.AccountChangeImpact.class),
                            examples = {
                                    @ExampleObject(name = "지급 예정 1건", value = """
                                            { "pendingPayoutCount": 1, "pendingPayoutAmount": 99505, "inFlightPayoutCount": 0 }
                                            """),
                                    @ExampleObject(name = "영향 없음", value = """
                                            { "pendingPayoutCount": 0, "pendingPayoutAmount": 0, "inFlightPayoutCount": 0 }
                                            """)
                            })),
            @ApiResponse(responseCode = "404", description = "인플루언서 아님 — CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorBasicInfoDto.AccountChangeImpact> getAccountChangeImpact(@Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "정산 계좌 변경 요청 (1021 기-22)",
            description = """
                    은행 · 계좌번호 · 통장 사본으로 **계좌 변경 요청**을 낸다 — 바로 바뀌지 않고 운영자가 통장 사본과 예금주(= 본인 인증 실명)를 확인한 뒤
                    반영한다. 예금주는 받지 않는다.

                    **흐름**
                    1. 통장 사본을 `POST /v1/creator/images?type=CREATOR_DOCUMENT`(jpg · jpeg · png · pdf · 10MB)로 먼저 올린다.
                    2. 확인 모달(T6)의 재료는 `GET …/settlement-account/change-impact`.
                    3. 이 API 로 요청 — 응답의 `pendingAccountChange` 에 요청 번호 · 은행 · 마스킹 계좌 · 보류 지급이 실린다. `account` 는 **기존 계좌 그대로**다.
                    4. 운영자 승인 → 새 계좌로 바뀌고 PG 계좌 실존 확인(`verification.status = VERIFYING` → `VERIFIED` · `FAILED`)이 이어진다.
                       반려 → 기존 계좌 그대로 · 알림. 확인 중 취소는 `DELETE …/settlement-account/change-request`.

                    **지급 영향**
                    - 확인이 끝날 때까지 인플루언서 몫 **지급 요청 전 예정 지급은 보류**된다(「정산 계좌 확인 중」). 이미 PG 에 나간 지급은 기존 계좌로 끝난다.
                    - 브랜드 몫 · 플랫폼 몫에는 영향이 없다.

                    **검증** — 은행 코드 3자리(`GET /v1/common/banks`) · 계좌번호 하이픈 없이 숫자 10 ~ 16자리 · 통장 사본 URL 필수 ·
                    지금 계좌와 은행 · 번호가 모두 같으면 400 `CREATOR_ACCOUNT_UNCHANGED` · 열린 변경 요청(사업자 구분 변경 포함)이 있으면 409
                    `CHANGE_REQUEST_ALREADY_PENDING`.

                    **권한:** CREATOR
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CreatorBasicInfoDto.ChangeSettlementAccountRequest.class),
                    examples = @ExampleObject(value = """
                            {
                              "bankCode": "088",
                              "accountNumber": "110123456789",
                              "bankbookImageUrl": "https://cdn…/creator-document/9f2c.jpg",
                              "bankbookFileName": "통장사본_신한은행.jpg"
                            }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 접수 — 응답은 정산 정보 탭 조회와 같다(`pendingAccountChange` 채움 · `account` 는 기존 계좌)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreatorBasicInfoDto.SettlementInfoResponse.class))),
            @ApiResponse(responseCode = "400", description = "같은 계좌 · 은행 미선택 · 계좌번호 형식 · 통장 사본 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "같은 계좌", value = """
                                            { "code": "CREATOR_ACCOUNT_UNCHANGED", "message": "현재 등록된 계좌와 같습니다." }
                                            """),
                                    @ExampleObject(name = "계좌번호 형식", value = """
                                            { "code": "INVALID_INPUT", "message": "계좌번호는 10~16자리 숫자로 입력해 주세요." }
                                            """),
                                    @ExampleObject(name = "통장 사본 누락", value = """
                                            { "code": "INVALID_INPUT", "message": "통장 사본을 첨부해 주세요." }
                                            """)
                            })),
            @ApiResponse(responseCode = "404", description = "없는 은행 코드 — BANK_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "BANK_NOT_FOUND", "message": "존재하지 않는 은행 코드입니다." }
                                    """))),
            @ApiResponse(responseCode = "409", description = "탈퇴한 인플루언서 — CREATOR_WITHDRAWN · 열린 변경 요청 — CHANGE_REQUEST_ALREADY_PENDING",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorBasicInfoDto.SettlementInfoResponse> changeSettlementAccount(
            @Parameter(hidden = true) UserPrincipal principal,
            @Valid @RequestBody CreatorBasicInfoDto.ChangeSettlementAccountRequest request);

    @Operation(summary = "정산 계좌 변경 요청 취소 (1021 기-22)",
            description = """
                    확인 중인 계좌 변경 요청을 거둔다 — 기존 계좌 그대로 · 보류했던 지급은 원래 예정일(지났으면 오늘)로 돌아간다.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 — 정산 정보 탭 조회와 같다(`pendingAccountChange = null`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreatorBasicInfoDto.SettlementInfoResponse.class))),
            @ApiResponse(responseCode = "400", description = "확인 중인 계좌 변경 요청이 없음 — CHANGE_REQUEST_NOT_PENDING",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorBasicInfoDto.SettlementInfoResponse> cancelSettlementAccountChange(
            @Parameter(hidden = true) UserPrincipal principal);

    // ------------------------------------------------------------------ 사업자 구분 변경(T4 ~ T10 · §16-2a)

    @Operation(summary = "사업자 구분 변경 요청",
            description = """
                    T7(개인사업자로) · T8(비사업자로). 어드민 변경 요청 큐(`/v1/admin/change-requests`)에 들어가고 **운영자 확인 후 반영**된다.
                    확인 대기 중에도 정산은 기존 구분으로 계속되고, 승인되면 **다음 정산부터** 새 구분이다(정산 생성 시점 스냅샷).

                    **방향별 필수 값**

                    | `targetType` | 필수 | 증빙 |
                    | --- | --- | --- |
                    | `BUSINESS`(T7) | `businessRegistrationNumber` — 하이픈 포함 `000-00-00000` | 사업자등록증 |
                    | `INDIVIDUAL`(T8) | `residentRegistrationNumber` — 13자리(하이픈 허용) | 폐업사실증명원 |

                    - `targetType` 은 현재 구분의 반대만 — 같으면 409 `CHANGE_REQUEST_BUSINESS_TYPE_MISMATCH`(두 탭에서 동시에 누른 경우 등).
                    - 열린 요청은 1건 — 409 `CHANGE_REQUEST_ALREADY_PENDING`. 반려 · 취소되면 바로 다시 요청할 수 있다.
                    - 증빙은 `POST /v1/creator/images?type=CREATOR_DOCUMENT`(jpg · jpeg · png · pdf · 10MB)로 먼저 올리고 URL · 파일명 · 크기를 싣는다.
                    - 주민등록번호 원문은 암호화해 요청 행에만 두고 처리(승인 · 반려 · 취소) 즉시 지운다. 응답 · 로그에 원문이 없다(배너는 `900101-*******`).
                    - 변경 사유 입력은 받지 않는다(시안에 입력란 없음). 상호 · 대표자도 받지 않는다 — 운영자가 등록증에서 읽는다.
                    - 요청 코드 `CHG-{연도}-{4자리}` 는 브랜드 변경 요청과 같은 순번을 쓴다.

                    **권한:** CREATOR
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CreatorBasicInfoDto.BusinessTypeChangeRequest.class),
                    examples = {
                            @ExampleObject(name = "T7 개인사업자로", value = """
                                    {
                                      "targetType": "BUSINESS",
                                      "businessRegistrationNumber": "123-45-67890",
                                      "evidenceFileUrl": "https://cdn…/creator-document/license.pdf",
                                      "evidenceFileName": "사업자등록증_뷰티소연.pdf",
                                      "evidenceFileSize": 1258291
                                    }
                                    """),
                            @ExampleObject(name = "T8 비사업자로", value = """
                                    {
                                      "targetType": "INDIVIDUAL",
                                      "residentRegistrationNumber": "900101-2345678",
                                      "evidenceFileUrl": "https://cdn…/creator-document/closure.pdf",
                                      "evidenceFileName": "폐업사실증명원.pdf",
                                      "evidenceFileSize": 302144
                                    }
                                    """)
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "요청 접수 — 정산 정보 탭 배너가 「확인 대기」(T4 · T9) · 버튼이 `CANCEL`",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreatorBasicInfoDto.BusinessTypeChangeCreated.class),
                            examples = @ExampleObject(value = """
                                    { "requestId": 31, "requestCode": "CHG-2026-0031", "type": "TO_BUSINESS", "status": "PENDING",
                                      "requestedAt": "2026-10-16T09:12:00" }
                                    """))),
            @ApiResponse(responseCode = "400", description = "구분 미선택 · 사업자등록번호 형식 · 주민등록번호 형식 · 증빙 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "구분 미선택", value = """
                                            { "code": "INVALID_INPUT", "message": "바꿀 사업자 구분을 선택해 주세요." }
                                            """),
                                    @ExampleObject(name = "사업자등록번호 형식", value = """
                                            { "code": "INVALID_INPUT", "message": "올바른 사업자등록번호 형식으로 입력해 주세요. (예: 000-00-00000)" }
                                            """),
                                    @ExampleObject(name = "주민등록번호 형식", value = """
                                            { "code": "INVALID_INPUT", "message": "주민등록번호를 정확히 입력해 주세요." }
                                            """),
                                    @ExampleObject(name = "증빙 누락", value = """
                                            { "code": "CHANGE_REQUEST_EVIDENCE_REQUIRED", "message": "증빙 서류를 첨부해주세요." }
                                            """)
                            })),
            @ApiResponse(responseCode = "409", description = "방향 불일치 · 열린 요청 있음 · 탈퇴 회원",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "방향 불일치", value = """
                                            { "code": "CHANGE_REQUEST_BUSINESS_TYPE_MISMATCH",
                                              "message": "현재 사업자 구분에서는 요청할 수 없습니다. 새로고침 후 다시 시도해 주세요." }
                                            """),
                                    @ExampleObject(name = "열린 요청 있음", value = """
                                            { "code": "CHANGE_REQUEST_ALREADY_PENDING", "message": "이미 검토 중인 변경 요청이 있습니다." }
                                            """),
                                    @ExampleObject(name = "탈퇴 회원", value = """
                                            { "code": "CREATOR_WITHDRAWN", "message": "탈퇴한 인플루언서입니다. 조치할 수 없습니다." }
                                            """)
                            })),
            @ApiResponse(responseCode = "413", description = "증빙 10MB 초과 — FILE_SIZE_EXCEEDED",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "FILE_SIZE_EXCEEDED", "message": "파일 크기가 10MB를 초과했습니다. 10MB 이하의 파일을 선택해 주세요." }
                                    """)))
    })
    ResponseEntity<CreatorBasicInfoDto.BusinessTypeChangeCreated> requestBusinessTypeChange(
            @Parameter(hidden = true) UserPrincipal principal,
            @RequestBody CreatorBasicInfoDto.BusinessTypeChangeRequest request);

    @Operation(summary = "사업자 구분 변경 요청 취소",
            description = """
                    확인 대기 중(T4 · T9) 배너의 [요청 취소]. 정산에는 아무 영향이 없다. 주민등록번호 원문(T8)은 취소 즉시 지운다.
                    취소한 요청은 배너가 없고, 어드민 목록에는 `status=ALL` 탭에서만 `CANCELED` 로 보인다.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "취소 완료 — 버튼이 다시 `REQUEST_TO_*`"),
            @ApiResponse(responseCode = "400", description = "이미 승인 · 반려 · 취소됨 — CHANGE_REQUEST_NOT_PENDING",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CHANGE_REQUEST_NOT_PENDING", "message": "검토 대기 상태인 요청만 처리할 수 있습니다." }
                                    """))),
            @ApiResponse(responseCode = "403", description = "남의 요청 · 브랜드 요청 — CHANGE_REQUEST_ACCESS_DENIED",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CHANGE_REQUEST_ACCESS_DENIED", "message": "해당 변경 요청에 대한 권한이 없습니다." }
                                    """))),
            @ApiResponse(responseCode = "404", description = "없음 — CHANGE_REQUEST_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CHANGE_REQUEST_NOT_FOUND", "message": "존재하지 않는 변경 요청입니다." }
                                    """)))
    })
    ResponseEntity<Void> cancelBusinessTypeChange(@Parameter(hidden = true) UserPrincipal principal,
                                                  @Parameter(description = "변경 요청 ID — 정산 정보 탭 `businessTypeChange.requestId`",
                                                          required = true, example = "31")
                                                  @PathVariable Long requestId);

    @Operation(summary = "사업자 구분 변경 결과 배너 닫기",
            description = """
                    승인(T5 · T10) · 반려(T4a · T9a) 배너의 [닫기]. 이후 정산 정보 탭의 `businessTypeChange` 가 null 이 된다.
                    **멱등** — 두 번 불러도 204. 처리 전(대기) 요청은 닫을 수 없다(400).

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "확인 완료"),
            @ApiResponse(responseCode = "400", description = "처리 전 요청 — INVALID_INPUT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "INVALID_INPUT", "message": "처리가 끝난 요청만 확인할 수 있습니다." }
                                    """))),
            @ApiResponse(responseCode = "403", description = "남의 요청 · 브랜드 요청 — CHANGE_REQUEST_ACCESS_DENIED",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없음 — CHANGE_REQUEST_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> acknowledgeBusinessTypeChange(@Parameter(hidden = true) UserPrincipal principal,
                                                       @Parameter(description = "변경 요청 ID — 정산 정보 탭 `businessTypeChange.requestId`",
                                                               required = true, example = "31")
                                                       @PathVariable Long requestId);
}
