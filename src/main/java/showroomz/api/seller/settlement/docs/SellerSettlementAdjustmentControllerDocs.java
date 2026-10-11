package showroomz.api.seller.settlement.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.common.settlement.dto.SettlementAdjustmentDto;

import static showroomz.api.seller.settlement.docs.SellerSettlementDocsExamples.*;

@Tag(name = "Seller - Settlement Adjustment", description = "파트너센터 정산 조정 협의 API (§13)")
public interface SellerSettlementAdjustmentControllerDocs {

    String TURN_RULE = """
            **차례 · 권한(서버 판정 · 뷰어 기준)**

            | `turn` | 라벨 | 뜻 |
            |---|---|---|
            | `MY_TURN` | 내 응답 필요 | 상대의 최신 제안이 응답 대기 — 동의 · 반대 · 다른 금액 제안 가능 |
            | `THEIR_TURN` | 상대 응답 대기 | 내가 낸 최신 제안이 응답 대기 — 버튼 없음 |
            | `OPEN_FLOOR` | 협의 중 | 최신 제안에 반대가 나온 뒤 — 양측 모두 다른 금액 제안 가능 · 반대한 쪽은 그 제안에 동의도 가능(§46 A-9 잠정) |
            | `CLOSED` | 종결 | 합의 · 기한 만료 |

            버튼은 `permissions.canAccept · canReject · canCounter` 로 그립니다 — 내가 보낸 제안에는 처음부터 버튼이 없습니다.
            `canSend = false` 면 스레드 입력창 잠금(종결 — 메시지 전송은 409 `SETTLEMENT_ADJUSTMENT_CLOSED`).
            """;

    @Operation(summary = "조정 금액 미리보기 (13 D2 · C1)",
            description = """
                    요청 모달의 「실지급 · 브랜드 수취액 자동 계산」입니다. 숫자는 전부 정산 포트의 계산값(전 항목 절사)이고,
                    FE 는 산식을 들지 않고 입력을 디바운스로 이 API 에 보냅니다.

                    **권한:** SELLER — 남의 정산은 404

                    | 상황 | 응답 |
                    |---|---|
                    | `rewardAmount` 생략(모달 열기) | `input` · `preview` · `inRange` = null — 「0원 ~ `maxRewardAmount`원 안에서 입력」을 그릴 값과 원래 금액 계산(`original`)만 |
                    | 범위 안 입력 | `input` · `preview`(그 금액으로 확정했다면) · `inRange = true` |
                    | 범위 밖 입력 | `preview = null` · `inRange = false` — 에러가 아니다 |
                    | 요청 불가 | `canRequest = false` → 버튼 비활성(에러 문구 없이) · `cannotRequestReason` = `REVIEW_CLOSED`(확인 기간 밖) · `ALREADY_ADJUSTING`(협의 있음) |

                    `maxRewardAmount` — 「조정 후 브랜드 수취액 ≥ 0」을 식으로 옮긴 상한(리워드 부가세 포함 · **10원 미만 절사**).
                    사업자 인플루언서면 `withholdingAmount = 0` · `creatorNetAmount` = 리워드 + 부가세.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "미리보기",
                    content = @Content(schema = @Schema(implementation = SettlementAdjustmentDto.PreviewResponse.class), examples = {
                            @ExampleObject(name = "모달 열기", summary = "rewardAmount 생략 — 상한 · 원래 금액 계산만", value = PREVIEW_OPEN),
                            @ExampleObject(name = "입력", summary = "rewardAmount=270000 — 브랜드 수취액 1,604,200 · 인플루언서 실지급 297,000", value = PREVIEW_INPUT),
                            @ExampleObject(name = "범위 밖", summary = "rewardAmount=1800000 — inRange false · preview null", value = PREVIEW_OUT_OF_RANGE),
                            @ExampleObject(name = "요청 불가", summary = "확인 기간 경과 — canRequest false · REVIEW_CLOSED", value = PREVIEW_CLOSED)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없음 · 다른 브랜드의 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND)))
    })
    ResponseEntity<SettlementAdjustmentDto.PreviewResponse> preview(
            @Parameter(description = "정산 id", example = "29", required = true) Long settlementId,
            @Parameter(description = "입력 리워드(공급가) — 생략하면 모달을 여는 값만", example = "270000") Long rewardAmount);

    @Operation(summary = "정산 조정 요청 (13 D2 [정산 조정 요청])",
            description = """
                    정산 확인 기간에 리워드 조정을 요청합니다 — **3자 이슈 스레드가 자동으로 열리고** 정산은 「조정 협의」(전 수취자 보류)가 됩니다.
                    금액은 아직 바뀌지 않습니다. 응답의 `threadId` 로 연결·소통 화면(14)으로 보냅니다.

                    **권한:** SELLER

                    | 검사(순서대로) | 실패 |
                    |---|---|
                    | 내 정산인가 | 404 `SETTLEMENT_NOT_FOUND` |
                    | 협의가 이미 있는가(정산당 1회) | 409 `SETTLEMENT_ADJUSTMENT_ALREADY_EXISTS` |
                    | 확인 기간 안인가(자동 확정이 먼저 돌았으면 밖) | 409 `SETTLEMENT_ADJUSTMENT_WINDOW_CLOSED` |
                    | `reason` 공백 아님 · 1,000자 이하 | 400 `SETTLEMENT_ADJUSTMENT_REASON_REQUIRED` · `INVALID_INPUT` |
                    | 0 ≤ `rewardAmount` ≤ 상한 ∧ 원래 금액과 다름(같으면 협의가 아니다) | 400 `SETTLEMENT_ADJUSTMENT_AMOUNT_OUT_OF_RANGE`(메시지에 상한) |
                    | 인플루언서와 연결이 살아 있는가 | 503 `GROUP_BUY_THREAD_UNAVAILABLE` |

                    근거 첨부 칸은 없습니다 — 스레드 말풍선에 첨부합니다. 합의 기한 = 개설 + 10영업일 23:59:59 · 연장 없음.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "요청 접수 · 스레드 개설",
                    content = @Content(schema = @Schema(implementation = SettlementAdjustmentDto.RequestResponse.class),
                            examples = @ExampleObject(summary = "10.05 요청 → 합의 기한 10.20 23:59:59(10.09 한글날 제외 10영업일)", value = REQUEST_CREATED))),
            @ApiResponse(responseCode = "400", description = "사유 누락 · 1,000자 초과 · 금액 범위 밖 · 금액 누락 · 음수",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "사유 누락", value = ERR_ADJUSTMENT_REASON_REQUIRED),
                            @ExampleObject(name = "사유 1,000자 초과", value = ERR_REASON_TOO_LONG),
                            @ExampleObject(name = "금액 범위 밖 · 원래 금액과 같음", value = ERR_ADJUSTMENT_AMOUNT_OUT_OF_RANGE),
                            @ExampleObject(name = "금액 누락", value = ERR_NOT_NULL),
                            @ExampleObject(name = "음수", value = ERR_MIN_ZERO)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN))),
            @ApiResponse(responseCode = "404", description = "없음 · 다른 브랜드의 정산 (`SETTLEMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_SETTLEMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "확인 기간 밖 (`SETTLEMENT_ADJUSTMENT_WINDOW_CLOSED`) · 이미 협의 있음 (`SETTLEMENT_ADJUSTMENT_ALREADY_EXISTS`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "확인 기간 밖", value = ERR_ADJUSTMENT_WINDOW_CLOSED),
                            @ExampleObject(name = "이미 협의 있음", value = ERR_ADJUSTMENT_ALREADY_EXISTS)
                    })),
            @ApiResponse(responseCode = "503", description = "인플루언서와 연결이 끊겨 스레드를 열 수 없음 (`GROUP_BUY_THREAD_UNAVAILABLE`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_GROUP_BUY_THREAD_UNAVAILABLE)))
    })
    ResponseEntity<SettlementAdjustmentDto.RequestResponse> request(
            @Parameter(description = "정산 id", example = "29", required = true) Long settlementId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "요청 리워드(공급가) · 사유",
                    content = @Content(schema = @Schema(implementation = SettlementAdjustmentDto.RequestBody.class),
                            examples = @ExampleObject(summary = "원래 294,000 → 270,000 감액 요청", value = REQ_REQUEST)))
            SettlementAdjustmentDto.RequestBody body);

    @Operation(summary = "이슈 스레드 고정 카드 · 제안 목록 (14 `.iss-pin`)",
            description = """
                    스레드 화면이 들고 있는 `threadId` 로 협의를 읽습니다 — 고정 카드(공구 · 보류 금액 · 요청 금액 · 합의 기한 D-N) · 제안 전량(seq 오름차순) · 내 권한.

                    **권한:** SELLER — 스레드 당사자가 아니면 403 `THREAD_ACCESS_DENIED` · 조정 스레드가 아니면 404 `SETTLEMENT_ADJUSTMENT_NOT_FOUND`

                    - `latestProposal` — 최신 제안(고정 카드의 「요청 금액」). `deltaAmount` = 제안 − 원래 리워드(증액 + · 감액 −)
                    - `counterpart` — 상대 당사자(인플루언서)
                    - `remainingBusinessDays` — D-N · 종결 뒤 null
                    - `proposals[].cardMessageId` — 그 제안의 스레드 카드 메시지

                    """ + TURN_RULE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "고정 카드 · 제안 목록 · 권한",
                    content = @Content(schema = @Schema(implementation = SettlementAdjustmentDto.AdjustmentView.class),
                            examples = @ExampleObject(summary = "인플루언서 282,000 제안 → 내 응답 필요(동의 · 반대 · 다른 금액)", value = VIEW_MY_TURN))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "스레드 당사자 아님 (`THREAD_ACCESS_DENIED`) · 판매자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "스레드 당사자 아님", value = ERR_THREAD_ACCESS_DENIED),
                            @ExampleObject(name = "권한 없음", value = ERR_FORBIDDEN)
                    })),
            @ApiResponse(responseCode = "404", description = "없는 스레드 (`THREAD_NOT_FOUND`) · 조정 스레드 아님 (`SETTLEMENT_ADJUSTMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "없는 스레드", value = ERR_THREAD_NOT_FOUND),
                            @ExampleObject(name = "조정 스레드 아님", value = ERR_ADJUSTMENT_NOT_FOUND)
                    }))
    })
    ResponseEntity<SettlementAdjustmentDto.AdjustmentView> byThread(
            @Parameter(description = "이슈 스레드 id — 요청 응답의 threadId", example = "812", required = true) Long threadId);

    @Operation(summary = "다른 금액 제안 (14 C1)",
            description = """
                    최신 제안을 「다른 금액 제안으로 응답됨」(`COUNTERED`)으로 닫고 새 제안을 냅니다. 합의 기한은 그대로입니다. `reason` 은 선택.
                    응답은 갱신된 고정 카드 · 제안 목록이고, 차례는 상대에게 넘어갑니다(`THEIR_TURN`).

                    **권한:** SELLER

                    | 검사(순서대로) | 실패 |
                    |---|---|
                    | 협의가 있는가 · 내가 당사자인가 | 404 `SETTLEMENT_ADJUSTMENT_NOT_FOUND` · 403 `SETTLEMENT_ADJUSTMENT_ACCESS_DENIED` |
                    | 종결 전 · 합의 기한 전 | 409 `SETTLEMENT_ADJUSTMENT_CLOSED` · `SETTLEMENT_ADJUSTMENT_DEADLINE_PASSED` |
                    | `permissions.canCounter` | 409 `SETTLEMENT_ADJUSTMENT_NOT_YOUR_TURN` |
                    | 0 ≤ 금액 ≤ 상한 | 400 `SETTLEMENT_ADJUSTMENT_AMOUNT_OUT_OF_RANGE` |
                    | 최신 제안과 다른 금액(같으면 동의를 눌러야 한다) | 400 `SETTLEMENT_ADJUSTMENT_AMOUNT_UNCHANGED` |
                    | 그 사이 상대가 응답하지 않았는가 | 409 `SETTLEMENT_ADJUSTMENT_STATE_CHANGED` |

                    """ + TURN_RULE)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "제안 등록 — 갱신된 고정 카드 · 제안 목록",
                    content = @Content(schema = @Schema(implementation = SettlementAdjustmentDto.AdjustmentView.class),
                            examples = @ExampleObject(summary = "276,000 제안 — 직전 제안 COUNTERED · 상대 응답 대기", value = VIEW_COUNTERED))),
            @ApiResponse(responseCode = "400", description = "금액 범위 밖 · 최신 제안과 같은 금액 · 금액 누락 · 음수",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "범위 밖", value = ERR_ADJUSTMENT_AMOUNT_OUT_OF_RANGE),
                            @ExampleObject(name = "같은 금액", value = ERR_ADJUSTMENT_AMOUNT_UNCHANGED),
                            @ExampleObject(name = "금액 누락", value = ERR_NOT_NULL),
                            @ExampleObject(name = "음수", value = ERR_MIN_ZERO)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "협의 당사자 아님 (`SETTLEMENT_ADJUSTMENT_ACCESS_DENIED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ADJUSTMENT_ACCESS_DENIED))),
            @ApiResponse(responseCode = "404", description = "없는 협의 (`SETTLEMENT_ADJUSTMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ADJUSTMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "내 차례 아님 · 종결 · 기한 경과 · 그 사이 상태 변경",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "내 차례 아님", value = ERR_ADJUSTMENT_NOT_YOUR_TURN),
                            @ExampleObject(name = "종결", value = ERR_ADJUSTMENT_CLOSED),
                            @ExampleObject(name = "기한 경과", value = ERR_ADJUSTMENT_DEADLINE_PASSED),
                            @ExampleObject(name = "상태 변경", value = ERR_ADJUSTMENT_STATE_CHANGED)
                    }))
    })
    ResponseEntity<SettlementAdjustmentDto.AdjustmentView> counter(
            @Parameter(description = "협의 id — 고정 카드의 adjustmentId", example = "4", required = true) Long adjustmentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "제안 리워드(공급가) · 사유(선택)",
                    content = @Content(schema = @Schema(implementation = SettlementAdjustmentDto.CounterBody.class),
                            examples = @ExampleObject(value = REQ_COUNTER)))
            SettlementAdjustmentDto.CounterBody body);

    @Operation(summary = "동의 (14 C2)",
            description = """
                    최신 제안에 동의합니다 — **금액 변경 + 보류 해제 + 종결**이 한 번에 일어납니다(운영자 승인 단계 없음 · 되돌릴 수 없다).
                    정산은 즉시 「지급 예정」(합의 확정 · 확정일 = 동의일 · 지급 예정일 = +3영업일)이 되고, 스레드는 잠깁니다(`canSend = false`).

                    **권한:** SELLER

                    - `proposalId` 는 **최신 제안**이어야 합니다 — 화면이 오래돼 옛 제안 id 를 보내면 409 `SETTLEMENT_ADJUSTMENT_STATE_CHANGED`(새로 고친다)
                    - 내가 낸 제안 · `canAccept = false` 면 409 `SETTLEMENT_ADJUSTMENT_NOT_YOUR_TURN`
                    - 그 밖의 검사(404 · 403 · 종결 · 기한)는 다른 금액 제안과 같습니다

                    """ + TURN_RULE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "합의 · 종결 — 갱신된 고정 카드",
                    content = @Content(schema = @Schema(implementation = SettlementAdjustmentDto.AdjustmentView.class),
                            examples = @ExampleObject(summary = "282,000 합의 — AGREED · CLOSED · 버튼 · 입력창 잠금", value = VIEW_ACCEPTED))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "협의 당사자 아님 (`SETTLEMENT_ADJUSTMENT_ACCESS_DENIED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ADJUSTMENT_ACCESS_DENIED))),
            @ApiResponse(responseCode = "404", description = "없는 협의 (`SETTLEMENT_ADJUSTMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ADJUSTMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "옛 제안 · 내 차례 아님 · 종결 · 기한 경과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "옛 제안 · 상태 변경", value = ERR_ADJUSTMENT_STATE_CHANGED),
                            @ExampleObject(name = "내 차례 아님", value = ERR_ADJUSTMENT_NOT_YOUR_TURN),
                            @ExampleObject(name = "종결", value = ERR_ADJUSTMENT_CLOSED),
                            @ExampleObject(name = "기한 경과", value = ERR_ADJUSTMENT_DEADLINE_PASSED)
                    }))
    })
    ResponseEntity<SettlementAdjustmentDto.AdjustmentView> accept(
            @Parameter(description = "협의 id", example = "4", required = true) Long adjustmentId,
            @Parameter(description = "최신 제안 id — latestProposal.proposalId", example = "9", required = true) Long proposalId);

    @Operation(summary = "반대 (14 C3)",
            description = """
                    응답 대기 중인 최신 제안에 반대합니다 — 사유 없음. 협의는 계속되고(`turn = OPEN_FLOOR`) 양측 모두 다른 금액을 낼 수 있으며,
                    반대한 쪽은 마음을 바꿔 그 제안에 동의할 수도 있습니다. 합의 기한까지 합의가 없으면 원래 금액으로 확정됩니다.

                    **권한:** SELLER

                    - 반대는 **응답 대기(`PENDING`) 제안에만** — 이미 반대한 제안을 다시 반대할 수 없습니다(`canReject = false`)
                    - `proposalId` 가 최신이 아니면 409 `SETTLEMENT_ADJUSTMENT_STATE_CHANGED` · 그 밖의 검사는 동의와 같습니다

                    """ + TURN_RULE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반대 기록 — 갱신된 고정 카드",
                    content = @Content(schema = @Schema(implementation = SettlementAdjustmentDto.AdjustmentView.class),
                            examples = @ExampleObject(summary = "282,000 반대 — REJECTED · OPEN_FLOOR · 동의 · 다른 금액 가능", value = VIEW_REJECTED))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "협의 당사자 아님 (`SETTLEMENT_ADJUSTMENT_ACCESS_DENIED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ADJUSTMENT_ACCESS_DENIED))),
            @ApiResponse(responseCode = "404", description = "없는 협의 (`SETTLEMENT_ADJUSTMENT_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ADJUSTMENT_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "옛 제안 · 내 차례 아님 · 종결 · 기한 경과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "옛 제안 · 상태 변경", value = ERR_ADJUSTMENT_STATE_CHANGED),
                            @ExampleObject(name = "내 차례 아님", value = ERR_ADJUSTMENT_NOT_YOUR_TURN),
                            @ExampleObject(name = "종결", value = ERR_ADJUSTMENT_CLOSED),
                            @ExampleObject(name = "기한 경과", value = ERR_ADJUSTMENT_DEADLINE_PASSED)
                    }))
    })
    ResponseEntity<SettlementAdjustmentDto.AdjustmentView> reject(
            @Parameter(description = "협의 id", example = "4", required = true) Long adjustmentId,
            @Parameter(description = "최신 제안 id — latestProposal.proposalId", example = "9", required = true) Long proposalId);
}
