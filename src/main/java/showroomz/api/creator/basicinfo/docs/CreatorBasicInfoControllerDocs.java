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

@Tag(name = "Creator - BasicInfo", description = "쇼룸 스튜디오 기본정보 관리(§50) — 계정 · 정산 정보")
@SecurityRequirement(name = "Authorization")
public interface CreatorBasicInfoControllerDocs {

    // ------------------------------------------------------------------ 계정 탭

    @Operation(summary = "계정 탭 조회",
            description = """
                    본인 인증 정보(마스킹 · 셀프 수정 불가) · 업무 이메일 · 로그인 수단 · 탈퇴 블록(S1 ~ S8).

                    **탈퇴 `state`** — `PENDING`(S7 · 승인 대기) → `REJECTED`(S8 · 반려 안내를 아직 [확인]하지 않음) →
                    `AVAILABLE`(S5 · 조건 모두 충족) / `BLOCKED`(S4) 순서로 정한다. 조건 행(`conditions`)은 상태와 무관하게 항상 내린다 —
                    `breakdown` 은 0 건 키를 내리지 않는다. 조건 행의 링크(공구 · 계약 · 정산 관리)는 FE 라우팅이다.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CreatorBasicInfoDto.AccountResponse.class),
                            examples = @ExampleObject(name = "S1 조건 미충족", value = """
                                    {
                                      "identity": { "realName": "소*연", "birthday": "2003-07-19", "phoneNumber": "010-****-6416" },
                                      "businessEmail": "soyeon@example.com",
                                      "login": { "provider": "KAKAO", "providerLabel": "카카오" },
                                      "withdrawal": {
                                        "state": "BLOCKED", "satisfiedCount": 0, "totalCount": 3,
                                        "conditions": [
                                          { "code": "UNFINISHED_GROUP_BUYS", "label": "끝나지 않은 공구", "count": 2, "satisfied": false,
                                            "resolution": "주문이 모두 종결되어야 합니다",
                                            "breakdown": [ { "key": "IN_PROGRESS", "label": "진행중", "count": 1 },
                                                           { "key": "AWAITING_ORDER_CLOSURE", "label": "종료 후 주문 종결 대기", "count": 1 } ] },
                                          { "code": "UNFINISHED_CONTRACTS", "label": "끝나지 않은 계약", "count": 1, "satisfied": false,
                                            "resolution": "연결된 공구가 끝나야 합니다",
                                            "breakdown": [ { "key": "CONCLUDED", "label": "체결완료", "count": 1 } ] },
                                          { "code": "UNSETTLED", "label": "미정산", "count": 1, "satisfied": false,
                                            "resolution": "모두 지급 완료되어야 합니다",
                                            "breakdown": [ { "key": "ADJUSTING", "label": "조정 협의", "count": 1 } ] }
                                        ],
                                        "approvalDueBusinessDays": 5, "pendingRequest": null, "rejection": null
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "인플루언서 아님 — CREATOR_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorBasicInfoDto.AccountResponse> getAccount(@Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "업무 이메일 저장",
            description = """
                    즉시 저장한다(인증 메일 없음). 앞뒤 공백은 지운다. 같은 값이면 저장 없이 200. 응답은 계정 탭 조회와 같다.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "미입력 · 형식 오류(`soyeon@example` — 도메인에 점이 없다)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "INVALID_INPUT", "message": "이메일 형식이 올바르지 않습니다." }
                                    """))),
            @ApiResponse(responseCode = "409", description = "탈퇴한 인플루언서 — CREATOR_WITHDRAWN")
    })
    ResponseEntity<CreatorBasicInfoDto.AccountResponse> updateBusinessEmail(
            @Parameter(hidden = true) UserPrincipal principal,
            @Valid @RequestBody CreatorBasicInfoDto.UpdateBusinessEmailRequest request);

    @Operation(summary = "탈퇴 승인 요청",
            description = """
                    조건(공구 · 계약 · 정산)을 다시 판정하고 신청 1건을 만든다. 운영자가 영업일 5일 이내에 승인 · 반려한다(무응답 자동 승인 없음).
                    대기 중에는 새 연결 수락 · 계약 서명 요청이 막힌다. 정지 중에도 신청할 수 있다. 반려 안내를 닫지 않은 채 다시 신청하면
                    그 반려는 확인한 것으로 본다.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신청 완료"),
            @ApiResponse(responseCode = "400", description = "사유 공백 CREATOR_WITHDRAWAL_REASON_REQUIRED · 500자 초과"),
            @ApiResponse(responseCode = "409", description = "열린 신청 있음 CREATOR_WITHDRAWAL_ALREADY_PENDING · "
                    + "조건 미충족 CREATOR_WITHDRAWAL_CONDITION_UNMET(「끝나지 않은 공구 1건 · …」) · 탈퇴 회원 CREATOR_WITHDRAWN")
    })
    ResponseEntity<CreatorBasicInfoDto.WithdrawalRequestResponse> requestWithdrawal(
            @Parameter(hidden = true) UserPrincipal principal,
            @RequestBody CreatorBasicInfoDto.WithdrawalRequestBody request);

    @Operation(summary = "탈퇴 승인 요청 취소",
            description = "운영자 처리 전까지 직접 취소한다. 대기 중 제약(새 연결 수락 · 계약 서명 요청)도 같은 순간 풀린다.\n\n**권한:** CREATOR")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "취소 완료"),
            @ApiResponse(responseCode = "404", description = "없음 · 남의 신청 — CREATOR_WITHDRAWAL_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "이미 처리됨 — CREATOR_WITHDRAWAL_NOT_PENDING")
    })
    ResponseEntity<Void> cancelWithdrawal(@Parameter(hidden = true) UserPrincipal principal,
                                          @PathVariable Long requestId);

    @Operation(summary = "탈퇴 반려 안내 확인",
            description = "S8 반려 안내를 닫는다 — 표시 플래그만 바뀌고 사유 · 이력은 운영자 쪽에 남는다. 두 번 불러도 204.\n\n**권한:** CREATOR")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "확인 완료"),
            @ApiResponse(responseCode = "404", description = "없음 · 남의 신청 — CREATOR_WITHDRAWAL_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "반려가 아님 — CREATOR_WITHDRAWAL_NOT_REJECTED")
    })
    ResponseEntity<Void> acknowledgeWithdrawalRejection(@Parameter(hidden = true) UserPrincipal principal,
                                                        @PathVariable Long requestId);

    // ------------------------------------------------------------------ 정산 정보 탭

    @Operation(summary = "정산 정보 탭 조회",
            description = """
                    사업자 구분 · 주민등록번호(비사업자 · 마스킹) · 사업자 정보와 세금계산서 공급받는자(사업자) · 정산 계좌(뒤 6자리) ·
                    계좌 확인 상태 · 사업자 구분 변경 배너(T4 ~ T10). **금액은 없다** — 정산 관리가 단일 소스다.

                    - `account.holderName` 은 본인 인증 실명(마스킹)이다 — 예금주를 입력받지 않는다.
                    - `account.verification.status` — `VERIFYING · FAILED · CHANGE_REQUIRED` 면 인플루언서 몫 지급이 보류된다.
                    - `actions.businessTypeChange` 는 모든 상태에서 값이 있다(같은 자리 버튼) — 열린 요청이 있을 때만 `CANCEL`.
                    - 「정산은 이렇게 진행됩니다」 카드는 FE 정적 문구다.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CreatorBasicInfoDto.SettlementInfoResponse.class),
                            examples = @ExampleObject(name = "T1 비사업자", value = """
                                    {
                                      "businessType": { "value": "INDIVIDUAL", "label": "개인 (비사업자)", "confirmedAt": null },
                                      "residentNumber": { "masked": "900101-*******", "registeredAt": "2026-07-02T10:11:00" },
                                      "business": null,
                                      "taxInvoiceRecipient": null,
                                      "account": {
                                        "bankName": "국민은행", "accountNumber": "******456789", "holderName": "소*연", "holderRule": "REAL_NAME",
                                        "bankbook": { "url": "https://…", "fileName": null, "fileSize": null, "submittedAt": null },
                                        "verification": { "status": "VERIFIED", "label": "실명 확인 완료", "tone": "SUCCESS",
                                                          "verifiedAt": "2026-07-02T10:15:00", "failReason": null }
                                      },
                                      "businessTypeChange": null,
                                      "actions": { "businessTypeChange": "REQUEST_TO_BUSINESS", "accountChange": true }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "인플루언서 아님 — CREATOR_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorBasicInfoDto.SettlementInfoResponse> getSettlement(@Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "계좌 변경 영향 — 확인 모달(T6)",
            description = """
                    「확인이 끝날 때까지 지급 예정 N건(금액)의 입금이 보류됩니다」의 재료. 인플루언서 몫 지급 행 중 확정됐고 지시 전(예정 · 보류)인 것의
                    건수 · 합계와, 이미 지시가 나가 **이전 계좌로** 지급되는 건수(`inFlightPayoutCount`). 확정 전 정산은 세지 않는다.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CreatorBasicInfoDto.AccountChangeImpact.class),
                            examples = @ExampleObject(value = """
                                    { "pendingPayoutCount": 1, "pendingPayoutAmount": 99505, "inFlightPayoutCount": 0 }
                                    """)))
    })
    ResponseEntity<CreatorBasicInfoDto.AccountChangeImpact> getAccountChangeImpact(@Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "정산 계좌 변경 · 실명 확인",
            description = """
                    은행 · 계좌번호 · 통장 사본(재제출 필수)을 저장하고 **실명 확인**을 시작한다 — 예금주는 받지 않는다(본인 인증 실명).
                    확인은 PG 셀러 등록 · 수정의 계좌 검증이며 커밋 직후 비동기로 반영된다. 응답 시점의 `account.verification.status` 는
                    `VERIFYING` 이고, FE 는 정산 정보를 다시 조회해 결과(`VERIFIED` · `FAILED`)를 그린다.

                    - 확인이 끝날 때까지 인플루언서 몫 **지급 예정 건은 보류**된다(보류 사유 「정산 계좌 확인 중」) — 끝나면 자동으로 풀린다.
                    - 이미 지시가 나간 지급은 이전 계좌로 끝난다. 진행 중 지급이 있어도 변경을 막지 않는다.
                    - 통장 사본은 `POST /v1/creator/images?type=CREATOR_DOCUMENT`(jpg · png · pdf · 10MB)로 먼저 올린다.

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 · 확인 시작 — 응답은 정산 정보 탭 조회와 같다"),
            @ApiResponse(responseCode = "400", description = "같은 계좌 CREATOR_ACCOUNT_UNCHANGED · 계좌번호 형식 · 통장 사본 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_ACCOUNT_UNCHANGED", "message": "현재 등록된 계좌와 같습니다." }
                                    """))),
            @ApiResponse(responseCode = "404", description = "없는 은행 코드 — BANK_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "탈퇴한 인플루언서 — CREATOR_WITHDRAWN")
    })
    ResponseEntity<CreatorBasicInfoDto.SettlementInfoResponse> changeSettlementAccount(
            @Parameter(hidden = true) UserPrincipal principal,
            @Valid @RequestBody CreatorBasicInfoDto.ChangeSettlementAccountRequest request);

    // ------------------------------------------------------------------ 사업자 구분 변경(T4 ~ T10 · §16-2a)

    @Operation(summary = "사업자 구분 변경 요청",
            description = """
                    T7(개인사업자로 — 사업자등록번호 + 사업자등록증) · T8(비사업자로 — 주민등록번호 + 폐업사실증명원). 어드민 변경 요청 큐에
                    들어가고 **운영자 확인 후 반영**된다. 확인 대기 중에도 정산은 기존 구분으로 계속되고, 승인되면 **다음 정산부터** 새 구분이다.

                    - `targetType` 은 현재 구분의 반대만 — 같으면 409 `CHANGE_REQUEST_BUSINESS_TYPE_MISMATCH`.
                    - 열린 요청은 1건 — 409 `CHANGE_REQUEST_ALREADY_PENDING`. 반려되면 바로 다시 요청할 수 있다.
                    - 증빙은 `POST /v1/creator/images?type=CREATOR_DOCUMENT`(jpg · png · pdf · 10MB)로 먼저 올린다.
                    - 주민등록번호 원문은 암호화해 요청 행에만 두고 처리(승인 · 반려 · 취소) 즉시 지운다. 응답 · 로그에 원문이 없다.
                    - 변경 사유 입력은 받지 않는다(시안에 입력란 없음).

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "요청 접수 — 정산 정보 탭 배너가 「확인 대기」(T4 · T9)"),
            @ApiResponse(responseCode = "400", description = "구분 미선택 · 사업자등록번호 형식 · 주민등록번호 형식 · 증빙 누락(CHANGE_REQUEST_EVIDENCE_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "INVALID_INPUT", "message": "올바른 사업자등록번호 형식으로 입력해 주세요. (예: 000-00-00000)" }
                                    """))),
            @ApiResponse(responseCode = "409", description = "방향 불일치 CHANGE_REQUEST_BUSINESS_TYPE_MISMATCH · 열린 요청 CHANGE_REQUEST_ALREADY_PENDING · "
                    + "탈퇴 CREATOR_WITHDRAWN"),
            @ApiResponse(responseCode = "413", description = "증빙 10MB 초과 — FILE_SIZE_EXCEEDED")
    })
    ResponseEntity<CreatorBasicInfoDto.BusinessTypeChangeCreated> requestBusinessTypeChange(
            @Parameter(hidden = true) UserPrincipal principal,
            @RequestBody CreatorBasicInfoDto.BusinessTypeChangeRequest request);

    @Operation(summary = "사업자 구분 변경 요청 취소",
            description = "확인 대기 중(T4 · T9) 직접 취소한다 — 정산에는 아무 영향이 없다.\n\n**권한:** CREATOR")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "취소 완료"),
            @ApiResponse(responseCode = "400", description = "이미 처리됨 — CHANGE_REQUEST_NOT_PENDING"),
            @ApiResponse(responseCode = "403", description = "남의 요청 — CHANGE_REQUEST_ACCESS_DENIED"),
            @ApiResponse(responseCode = "404", description = "없음 — CHANGE_REQUEST_NOT_FOUND")
    })
    ResponseEntity<Void> cancelBusinessTypeChange(@Parameter(hidden = true) UserPrincipal principal,
                                                  @PathVariable Long requestId);

    @Operation(summary = "사업자 구분 변경 결과 배너 닫기",
            description = "승인(T5 · T10) · 반려(T4a · T9a) 배너를 닫는다. 두 번 불러도 204.\n\n**권한:** CREATOR")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "확인 완료"),
            @ApiResponse(responseCode = "400", description = "처리 전 요청"),
            @ApiResponse(responseCode = "403", description = "남의 요청 — CHANGE_REQUEST_ACCESS_DENIED")
    })
    ResponseEntity<Void> acknowledgeBusinessTypeChange(@Parameter(hidden = true) UserPrincipal principal,
                                                       @PathVariable Long requestId);
}
