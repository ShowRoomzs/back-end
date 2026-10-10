package showroomz.api.creator.settlement.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.common.settlement.dto.SettlementAdjustmentDto;

@Tag(name = "Creator - Settlement Adjustment", description = "쇼룸 스튜디오 정산 조정 협의 API (12 D1 · 11 이슈 스레드 · 44 이슈 스레드 설계서 3절)")
public interface CreatorSettlementAdjustmentControllerDocs {

    String TURN_RULE = """
            **차례 · 권한(서버 판정)** — `turn`: `MY_TURN`(내 응답 필요) · `THEIR_TURN`(상대 응답 대기) · `OPEN_FLOOR`(반대 뒤 — 양측 모두 다른 금액 제안 가능 ·
            반대한 쪽은 그 제안에 동의도 가능 · §46 A-9 잠정) · `CLOSED`(종결). `permissions.canAccept · canReject · canCounter` 로 버튼을 그린다 —
            내가 보낸 제안에는 처음부터 버튼이 없다. `canSend` 가 false 면 입력창 잠금(종결 스레드 · 409 SETTLEMENT_ADJUSTMENT_CLOSED).
            """;

    @Operation(summary = "조정 금액 미리보기 (12 D1 · C1)",
            description = """
                    요청 모달의 「실지급 · 브랜드 수취액 자동 계산」 — 숫자는 전부 정산 포트의 계산값(전 항목 절사)이다. FE 는 산식을 들지 않고
                    입력을 디바운스로 이 API 에 보낸다.

                    - `rewardAmount` 생략 시 `input` · `preview` 는 null — 모달을 열 때 「0원 ~ `maxRewardAmount`원 안에서 입력」을 그릴 값만 내린다
                    - `maxRewardAmount` — 「조정 후 브랜드 수취액 ≥ 0」 · 리워드 부가세 포함 · 10원 미만 절사
                    - `canRequest = false` 면 버튼 비활성(에러 문구 없이) · 사유 `cannotRequestReason`(`REVIEW_CLOSED` · `ALREADY_ADJUSTING`)
                    - 범위 밖 입력이면 `inRange = false` · `preview = null`

                    **권한:** CREATOR — 남의 정산은 404
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SettlementAdjustmentDto.PreviewResponse> preview(@Parameter(description = "정산 id") Long settlementId,
                                                                   @Parameter(description = "입력 리워드(공급가)") Long rewardAmount);

    @Operation(summary = "정산 조정 요청 (12 D1 [정산 조정 요청])",
            description = """
                    정산 확인 기간에 리워드 조정을 요청한다 — 3자 이슈 스레드가 자동으로 열리고 정산은 「조정 협의」(전액 보류)가 된다.
                    **금액은 바뀌지 않는다** — 합의(동의)로만 바뀐다. 차액 선지급 없음. 응답의 `threadId` 로 연결·소통 화면으로 보낸다.

                    - `rewardAmount` — 0 ≤ 금액 ≤ 상한 ∧ 원래 금액과 달라야 한다(같으면 협의가 아니다)
                    - `reason` — 필수 · 1,000자. 근거 첨부 칸은 없다(스레드 말풍선 첨부로)
                    - 합의 기한 = 개설 + 10영업일 23:59:59 · 연장 없음

                    **권한:** CREATOR
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "요청 접수 · 스레드 개설"),
            @ApiResponse(responseCode = "400", description = "SETTLEMENT_ADJUSTMENT_REASON_REQUIRED · SETTLEMENT_ADJUSTMENT_AMOUNT_OUT_OF_RANGE(메시지에 상한) · INVALID_INPUT(1,000자 초과)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "SETTLEMENT_ADJUSTMENT_WINDOW_CLOSED(확인 기간 밖 · 자동 확정 선행) · SETTLEMENT_ADJUSTMENT_ALREADY_EXISTS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "GROUP_BUY_THREAD_UNAVAILABLE — 브랜드와 연결이 끊겨 스레드를 열 수 없다",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SettlementAdjustmentDto.RequestResponse> request(@Parameter(description = "정산 id") Long settlementId,
                                                                   SettlementAdjustmentDto.RequestBody body);

    @Operation(summary = "이슈 스레드 고정 카드 · 제안 목록 (11 `.iss-pin`)",
            description = """
                    스레드 화면이 들고 있는 `threadId` 로 협의를 읽는다 — 고정 카드(공구 · 보류 금액 · 요청 금액 · 합의 기한 D-N) · 제안 전량 · 내 권한.
                    `latestProposal.deltaAmount` = 제안 − 원래 리워드(증액 + · 감액 −). 종결 뒤 `remainingBusinessDays = null`.

                    """ + TURN_RULE + """

                    **권한:** CREATOR — 스레드 당사자가 아니면 403 THREAD_ACCESS_DENIED · 조정 스레드가 아니면 404
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "THREAD_ACCESS_DENIED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "THREAD_NOT_FOUND · SETTLEMENT_ADJUSTMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SettlementAdjustmentDto.AdjustmentView> byThread(@Parameter(description = "스레드 id") Long threadId);

    @Operation(summary = "다른 금액 제안 (11 C1)",
            description = """
                    최신 제안을 「다른 금액 제안으로 응답됨」으로 닫고 새 제안을 낸다. 합의 기한은 그대로다. `reason` 은 선택.
                    같은 금액이면 400 `SETTLEMENT_ADJUSTMENT_AMOUNT_UNCHANGED` — 동의를 눌러야 한다. 응답은 갱신된 고정 카드 · 제안 목록.

                    """ + TURN_RULE)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "제안 등록"),
            @ApiResponse(responseCode = "400", description = "SETTLEMENT_ADJUSTMENT_AMOUNT_OUT_OF_RANGE · SETTLEMENT_ADJUSTMENT_AMOUNT_UNCHANGED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SETTLEMENT_ADJUSTMENT_ACCESS_DENIED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "SETTLEMENT_ADJUSTMENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "SETTLEMENT_ADJUSTMENT_NOT_YOUR_TURN · SETTLEMENT_ADJUSTMENT_CLOSED · SETTLEMENT_ADJUSTMENT_DEADLINE_PASSED · SETTLEMENT_ADJUSTMENT_STATE_CHANGED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SettlementAdjustmentDto.AdjustmentView> counter(@Parameter(description = "협의 id") Long adjustmentId,
                                                                  SettlementAdjustmentDto.CounterBody body);

    @Operation(summary = "동의 (11 C2)",
            description = """
                    최신 제안에 동의한다 — **금액 변경 + 보류 해제 + 종결**이 한 번에 일어난다(운영자 승인 단계 없음 · 되돌릴 수 없다).
                    정산은 즉시 「지급 예정」(확정일 = 동의일 · 지급 예정일 = +3영업일). 옛 제안 id 면 409 STATE_CHANGED(화면을 새로 고친다).

                    """ + TURN_RULE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "합의 · 종결"),
            @ApiResponse(responseCode = "409", description = "SETTLEMENT_ADJUSTMENT_NOT_YOUR_TURN · SETTLEMENT_ADJUSTMENT_STATE_CHANGED · SETTLEMENT_ADJUSTMENT_CLOSED · SETTLEMENT_ADJUSTMENT_DEADLINE_PASSED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SettlementAdjustmentDto.AdjustmentView> accept(@Parameter(description = "협의 id") Long adjustmentId,
                                                                 @Parameter(description = "최신 제안 id") Long proposalId);

    @Operation(summary = "반대 (11 C3)",
            description = """
                    최신 제안(응답 대기)에 반대한다 — 사유 없음 · 협의는 계속된다(`turn = OPEN_FLOOR`). 합의 기한까지 합의가 없으면 원래 금액으로 확정된다.

                    """ + TURN_RULE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반대 기록"),
            @ApiResponse(responseCode = "409", description = "SETTLEMENT_ADJUSTMENT_NOT_YOUR_TURN · SETTLEMENT_ADJUSTMENT_STATE_CHANGED · SETTLEMENT_ADJUSTMENT_CLOSED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SettlementAdjustmentDto.AdjustmentView> reject(@Parameter(description = "협의 id") Long adjustmentId,
                                                                 @Parameter(description = "최신 제안 id") Long proposalId);
}
