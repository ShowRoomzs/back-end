package showroomz.api.seller.claim.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.claim.dto.SellerClaimBatchResponse;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.api.seller.claim.dto.SellerClaimReceiveRequest;
import showroomz.api.seller.claim.dto.SellerClaimRejectRequest;
import showroomz.api.seller.claim.dto.SellerClaimReshipDto;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.time.LocalDate;
import java.util.Set;

@Tag(name = "Seller - Claim", description = "파트너센터 반품·교환 관리 API (§35)")
public interface SellerClaimControllerDocs {

    String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // ── 조회 ────────────────────────────────────────────────────────────────

    @Operation(
            summary = "반품·교환 목록",
            description = """
                    탭별 반품·교환 목록. 행은 **클레임(주문 항목 단위)** 이고, 한 박스로 같이 온 클레임은
                    `collection.size > 1` 로 묶어 그린다(「`collection.leadClaimNumber` 외 N건」). 같은 묶음은 인접하도록
                    정렬된다(탭 기본 정렬 → 묶음 id → 클레임 id).

                    **권한:** SELLER (내 브랜드 클레임만)

                    **탭(`tab`)** — 생략 시 `COLLECT_WAIT`. 탭이 조건과 기본 정렬을 소유한다(정렬 파라미터는 없다).

                    | 탭 | 포함 단계 | 기본 정렬 |
                    |---|---|---|
                    | `ALL` 전체 | 결제 대기를 뺀 전부 | 신청 최신순 |
                    | `COLLECT_WAIT` 회수 대기 | `REQUESTED` | 신청 오래된순 |
                    | `COLLECTING` 회수 중 | `COLLECTING` | 단계 진입 오래된순 |
                    | `INSPECTION` 입고·검수 | `ARRIVED` · `RECEIVED` | 도착 오래된순(도착 시각이 없으면 입고 확인 시각) — 입고 확인을 눌러도 순서가 밀리지 않는다 |
                    | `RESHIP` 재발송 대기 | `RESHIP_READY` | 단계 진입 오래된순 |
                    | `REJECT_HOLD` 거절 보류 | `REJECT_HOLD` | 거절 오래된순 |
                    | `DONE` 완료 | `REFUND_PENDING` · `RESHIPPING` · `COMPLETED` | 종결 최신순 |

                    **필터**
                    - `types` — 유형 복수 선택(`types=RETURN&types=EXCHANGE`). 생략 시 전체
                    - `reason` — 사유 1개: `CHANGE_OF_MIND` 단순 변심 · `ORDER_MISTAKE` 주문 실수 · `SIZE_MISMATCH` 사이즈가 맞지 않음
                      (이상 고객 부담) · `DAMAGED_OR_DEFECTIVE` 배송 상품 파손 및 불량 · `WRONG_OR_LATE_DELIVERY` 오배송 및 배송 지연
                      (이상 브랜드 부담)
                    - `from` · `to` — **신청일시** 기준 · `yyyy-MM-dd` · 양끝 포함. `to` 생략 시 오늘, `from` 생략 시 `to` − 30일.
                      **최대 1년** — 초과는 400 · 시작일이 종료일보다 늦으면 400
                    - `keyword` — 한 입력으로 둘 다 받는다. `CLM-3021` · `clm3021` 처럼 접수번호 모양이면 접수번호 조회,
                      아니면 **주문번호 정확히 일치**(`20261003-000123`)
                    - `page`(1부터 · 1 미만은 첫 페이지) · `size`(기본 20 · **1~100** — 밖이면 400)

                    **행의 값**
                    - 라벨 · 버튼 가능 여부(`actions`) · 기한 초과(`overdue`) · 보관 기한(`storage`)은 서버가 계산해 내린다 — FE 가 매핑하지 않는다
                    - `statusTone` 은 전부 `NEUTRAL` 이다 — 단계는 진행 순서일 뿐 경고가 아니다. 경고는 `overdue` 로만 표시한다
                    - `overdue` — ① 회수 대기 방치(신청 + 2영업일 경과 · 소비자 미발송) ② 검수 기한 경과(입고 확인 + 2영업일 · 브랜드 귀책).
                      `elapsedDays`(「경과」 열 · 단계 진입부터의 달력일)와 섞지 않는다
                    - `shipLabel` 은 재발송 대기 · 재발송 중 · 거절 보류 단계에서 **실제로 보낼(보관 중인) 물건**이다 —
                      교환 재발송은 새 옵션, 거절은 원래 옵션. 그 밖의 단계는 `null`
                    - `reshipReason` — `EXCHANGE` 교환 재발송 · `REJECT_RETURN` 반려 반송. 재발송 단계가 아니면 `null`
                    - `reshipFee` — 「재발송비」 열(결정 14). 반려 건은 반려 재발송 배송비, 교환은 고객 귀책 교환의 선결제분.
                      `status` = `PENDING` 결제 대기 · `PAID` 결제됨 · `DEDUCTED` 환불액에서 차감 · `COVERED` 교환 결제분으로 충당 ·
                      `VOID` 소멸 · `REFUNDED` 결제 취소. 청구가 없으면 `null`(브랜드 귀책 교환 · 반려 판정 전)
                    - `storage` 는 거절 보류만. `storageDueAt` 은 미결제 고지가 2회 쌓여야 생긴다(최종 고지일 + 3개월) ·
                      `phase` = `NOTICE_PENDING` 고지 부족 · `STORING` 보관 중 · `EXPIRED` 기한 경과
                    - `outcome` 은 완료 탭만 — `REFUND_PENDING` · `RESHIPPING`(`finalized: false`) ·
                      `REFUNDED` · `EXCHANGED` · `REJECTED` · `CANCELLED`(`finalized: true`)
                    - `amount` 는 환불로 가는 반품만 — 환불 확정액, 환불 대기면 그 항목의 상품 금액(단가 × 수량). 교환 · 거절은 `null`
                    - `orderItems` 는 그 하위주문의 항목 전체다(행 확장 ▸) — 신청하지 않은 항목은 `claimedQuantity = 0`.
                      `itemStatusLabel` = 반품 신청 · 교환 신청 · 구매확정 · 배송완료 · 취소 · 반품
                    - 소비자 연락처는 목록에 없다 — 상세에 마스킹해서만 내린다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 — 조건에 맞는 건이 없으면 `content: []`(요약 숫자는 그대로다)"),
            @ApiResponse(responseCode = "400", description = "ORDER_SEARCH_RANGE_EXCEEDED — 기간 1년 초과 · "
                    + "INVALID_INPUT — 시작일 > 종료일 · `size` 1~100 밖 · 정의되지 않은 enum 값 · 날짜 형식 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "기간 1년 초과", value = """
                                            {
                                              "code": "ORDER_SEARCH_RANGE_EXCEEDED",
                                              "message": "조회 기간은 최대 1년까지 설정할 수 있습니다."
                                            }
                                            """),
                                    @ExampleObject(name = "시작일 > 종료일", value = """
                                            {
                                              "code": "INVALID_INPUT",
                                              "message": "조회 시작일은 종료일보다 늦을 수 없습니다."
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — SELLER 가 아님(CREATOR 포함)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<SellerClaimListItem>> getClaims(
            @Parameter(description = "탭 — COLLECT_WAIT(기본) · ALL · COLLECTING · INSPECTION · RESHIP · REJECT_HOLD · DONE",
                    example = "COLLECT_WAIT")
            @RequestParam(value = "tab", required = false) ClaimTab tab,
            @Parameter(description = "유형 — RETURN · EXCHANGE. 복수 선택(`types=RETURN&types=EXCHANGE`) · 생략 시 전체",
                    example = "RETURN")
            @RequestParam(value = "types", required = false) Set<ClaimType> types,
            @Parameter(description = "사유 — CHANGE_OF_MIND · ORDER_MISTAKE · SIZE_MISMATCH · DAMAGED_OR_DEFECTIVE · "
                    + "WRONG_OR_LATE_DELIVERY · 생략 시 전체", example = "CHANGE_OF_MIND")
            @RequestParam(value = "reason", required = false) ClaimReason reason,
            @Parameter(description = "신청일 시작(yyyy-MM-dd · 포함) — 생략 시 종료일 30일 전", example = "2026-09-05")
            @RequestParam(value = "from", required = false) LocalDate from,
            @Parameter(description = "신청일 종료(yyyy-MM-dd · 포함) — 생략 시 오늘", example = "2026-10-05")
            @RequestParam(value = "to", required = false) LocalDate to,
            @Parameter(description = "접수번호(`CLM-3021` · 대소문자·하이픈 무관) 또는 주문번호(정확히 일치)", example = "CLM-3021")
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "반품·교환 요약 (KPI · 탭 카운트)",
            description = """
                    KPI 4칸 + 탭 카운트 7종 + 유형 카운트를 **한 응답**으로 내린다 — 목록 액션 뒤 이 API 하나로 숫자를 다시 그린다.

                    **권한:** SELLER · **파라미터를 받지 않는다**

                    - 전부 **검색 조건·기간과 무관한 전체 기준**이다 — 목록에 검색어를 넣어도 이 숫자는 변하지 않는다
                    - `tabCounts` — 탭 코드 7종이 **항상 모두** 들어 있다(0건도 0으로). `ALL` 은 여섯 탭의 합이고,
                      대탭 「반품·교환 관리 N」의 숫자다
                    - `typeCounts` — `RETURN` · `EXCHANGE` 가 항상 들어 있다. 여섯 탭(= `ALL`) 기준이다
                    - `kpi.collectWait` = `tabCounts.COLLECT_WAIT`, `kpi.inspection` = `tabCounts.INSPECTION`(입고 확인 전 + 검수 대기),
                      `kpi.reship` = `tabCounts.RESHIP`
                    - `kpi.overdue` = 회수 대기 방치(신청 + 2영업일 경과) + 검수 기한 경과. 0이면 경고 톤을 뺀다
                    - 재발송 배송비 결제를 기다리는 교환 요청(아직 접수 전)은 어디에도 세지 않는다
                    - 주문 관리 요약(`GET /v1/seller/orders/summary`)의 `incomingCheck` · `reshipExchange` 는 각각
                      여기의 `tabCounts.INSPECTION` · `tabCounts.RESHIP` 과 같은 수다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = SellerClaimSummaryResponse.class),
                            examples = @ExampleObject(name = "요약", value = """
                                    {
                                      "kpi": { "collectWait": 3, "inspection": 2, "reship": 1, "overdue": 1 },
                                      "tabCounts": {
                                        "ALL": 12, "COLLECT_WAIT": 3, "COLLECTING": 2, "INSPECTION": 2,
                                        "RESHIP": 1, "REJECT_HOLD": 1, "DONE": 3
                                      },
                                      "typeCounts": { "RETURN": 7, "EXCHANGE": 5 }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimSummaryResponse> getSummary();

    @Operation(
            summary = "반품·교환 상세",
            description = """
                    상세 모달. 목록 행과 같은 값은 `summary` 에 있고(목록 행과 같은 모양), 상세에서만 보이는 것을 더한다.
                    단계에 해당하지 않는 블록은 `null` 이다.

                    **권한:** SELLER (내 브랜드 클레임만 — 남의 브랜드 것은 404 · 존재 비노출)

                    - `orderNumber` · `deliveryGroupId` — 주문 상세(`GET /v1/seller/orders/{deliveryGroupId}`)로 가는 키
                    - `consumerPhone` 은 마스킹된 값이다(`010-****-4412`). **재발송 수취 정보(원문)는 여기 없다** — 재발송 목록
                      엑셀(`POST /reshipments/export`)에만 나간다
                    - `reasonDetail` — 소비자가 적은 상세 내용(브랜드 부담 사유는 필수) · `consumerAttachments` — 소비자 첨부 사진 URL
                    - `exchangeFeeCharged`(교환만) — 고객 귀책 교환이라 요청 때 재발송 배송비를 결제했으면 `true`. 반품은 `null`
                    - `refund`(반품만) — 환불 예정액은 **요청(박스) 단위**다. `itemAmount`(이 항목의 상품 금액)와
                      `requestDeduction`(요청의 배송비 차감 — 요청당 한 번) · `requestExpectedAmount`(요청 예정액 —
                      판정이 다 끝났으면 확정액)를 따로 내린다. 묶음의 항목마다 같은 차감을 반복해 그리지 않는다.
                      `basisLabel` = 「상품 금액 − 최초 배송비」 · 「상품 금액(배송비 차감 없음)」 · 「브랜드 부담 사유 — 배송비 차감 없음」
                    - `sellerEvidences` · `rejectDetail` — 검수 거절 때 브랜드가 올린 증빙과 설명(소비자에게 그대로 보인다)
                    - `result`(완료 단계만) — 교환 완료면 `confirmDueAt`(구매확정 재시작 예정), 거절 종결이면
                      `rejectionEnd`(`RETURNED` 반송 완료 · `DISPOSED` 보관 기간 만료 후 폐기)
                    - `notices`(거절 보류만 · 그 밖은 빈 배열) — 미결제 고지 회차. 보관 기한은 `summary.storage`
                    - `history` — 처리 이력 최신순. `eventType` 예: `REQUESTED` 신청 접수 · `RECEIVED` 입고 확인 ·
                      `INSPECTION_PASSED` · `INSPECTION_REJECTED`(`detail` = 거절 사유) · `RESHIP_FEE_SETTLED`(`detail` = 환불액 차감 / 교환 결제분 충당) ·
                      `RESHIP_INVOICE_REGISTERED`(`detail` = 「CJ대한통운 640012345678」) · `RESHIP_INVOICE_UPDATED`(「구 → 신」) ·
                      `RESHIP_DELIVERED` · `REFUND_EXECUTED` · `DISPOSED`
                    - 처리 API(검수 통과 · 거절 · 재발송 송장 수정)도 성공 시 이 응답을 그대로 돌려준다

                    **`actions` — 버튼 노출의 정본**

                    | 버튼 | 노출 조건 |
                    |---|---|
                    | `canConfirmReceipt` 입고 확인 | `ARRIVED`(추적상 도착). 택배 추적이 꺼진 기간에는 `COLLECTING` 도 |
                    | `canPass` · `canReject` 검수 통과 / 거절 | `RECEIVED` |
                    | `canRegisterReshipment` 재발송 송장 등록 | `RESHIP_READY` |
                    | `canUpdateReshipment` 재발송 송장 수정 | `RESHIPPING` |
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND — 없는 클레임이거나 내 브랜드 것이 아님 · "
                    + "SELLER_NOT_FOUND · MARKET_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "없는 클레임", value = """
                                    {
                                      "code": "CLAIM_NOT_FOUND",
                                      "message": "반품·교환 요청을 찾을 수 없습니다."
                                    }
                                    """)))
    })
    ResponseEntity<SellerClaimDetailResponse> getClaim(
            @Parameter(description = "클레임 id(목록의 `claimId`)", example = "3021") @PathVariable Long claimId);

    // ── 입고 · 검수 ─────────────────────────────────────────────────────────

    @Operation(
            summary = "입고 확인 (다건)",
            description = """
                    회수된 상품이 도착했음을 확인한다(`ARRIVED` → `RECEIVED` 검수 대기). **검수 기한(입고 확인 + 2영업일)이
                    이 순간 발급된다** — 지나면 `overdue` 로 잡힌다(브랜드 귀책).

                    **권한:** SELLER · **버튼 노출:** `actions.canConfirmReceipt`

                    - 다건 · **행 단위 부분 성공** — 처리되지 않은 건만 `skipped` 에 사유와 함께 돌려주고 나머지는 진행한다.
                      전부 제외돼도 200 이다
                    - 대상은 「입고 확인 전」(추적상 도착) 건이다. 택배 추적이 꺼져 있는 동안에는 도착이 감지되지 않으므로
                      「회수 중」 건도 받는다(`actions.canConfirmReceipt` 가 정본)
                    - 한 박스로 같이 온 건 중 일부만 먼저 확인해도 된다
                    - 중복 id 는 한 번만 처리한다
                    - **되돌리기는 없다**

                    **`skipped[].code`** — 하나뿐이다
                    - `CLAIM_STATE_CHANGED` — 입고 확인할 단계가 아님(이미 확인됨 · 회수 대기 등). **없는 id · 남의 브랜드 건도
                      같은 사유다**(존재 비노출)
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SellerClaimReceiveRequest.class),
                    examples = @ExampleObject(name = "묶음 2건 입고 확인", value = """
                            {
                              "claimIds": [3021, 3022]
                            }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 결과 — `succeeded` + `skipped[]` · 전부 제외돼도 200",
                    content = @Content(schema = @Schema(implementation = SellerClaimBatchResponse.class),
                            examples = @ExampleObject(name = "부분 성공", value = """
                                    {
                                      "succeeded": 1,
                                      "skipped": [
                                        {
                                          "claimId": 3022,
                                          "code": "CLAIM_STATE_CHANGED",
                                          "message": "요청 상태가 변경되었습니다. 새로고침 후 다시 확인해 주세요."
                                        }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `claimIds` 비었음 · 500건 초과",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "선택 없음", value = """
                                    {
                                      "code": "INVALID_INPUT",
                                      "message": "입고 확인할 건을 선택해 주세요."
                                    }
                                    """)))
    })
    ResponseEntity<SellerClaimBatchResponse> receive(@Valid @RequestBody SellerClaimReceiveRequest request);

    @Operation(
            summary = "검수 통과 (단건)",
            description = """
                    「검수 대기」(`RECEIVED`) 건을 통과시킨다. **바디 없음.** 응답은 갱신된 상세다.
                    일괄 통과는 없다 — 검수 근거가 건마다 다르다.

                    **권한:** SELLER · **버튼 노출:** `actions.canPass`

                    - **반품** → `REFUND_PENDING` 환불 대기. 그 항목의 반품 수량이 이 순간 반영된다(전량이면 항목이 반품으로 종결).
                      돌아온 상품의 재고는 자동으로 원복하지 않는다 — 다시 팔지는 브랜드가 정한다.
                      **환불은 브랜드가 실행하지 않는다** — 같은 박스의 판정이 전부 끝나면 환불 큐에 **요청당 1건** 오르고 **PG 가 즉시 자동 환불**한다(되돌릴 수 없다)
                    - **교환** → `RESHIP_READY` 재발송 대기. 재발송 송장을 등록하면 된다
                    - 환불 예정액은 **요청(박스) 단위**라 같은 박스에 판정이 남은 건이 있으면 아직 확정되지 않는다
                      (상세 `refund.requestExpectedAmount` 는 그때까지 예정액)
                    - 같은 박스에 거절된 건이 있으면, 마지막 판정 순간 그 재발송비를 이 환불액에서 차감할 수 있다 → 거절 건이 바로 재발송 대기로 간다
                    - **되돌리기는 없다.** 통과와 거절이 동시에 들어오면 하나만 성공하고 나머지는 409 다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "통과 — 갱신된 상세"),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND — 없는 클레임이거나 내 브랜드 것이 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CLAIM_STATE_CHANGED — 검수 대기가 아님(입고 확인 전 · 이미 판정됨 포함)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "이미 판정됨", value = """
                                    {
                                      "code": "CLAIM_STATE_CHANGED",
                                      "message": "요청 상태가 변경되었습니다. 새로고침 후 다시 확인해 주세요."
                                    }
                                    """)))
    })
    ResponseEntity<SellerClaimDetailResponse> passInspection(
            @Parameter(description = "클레임 id", example = "3021") @PathVariable Long claimId);

    @Operation(
            summary = "검수 반려 (단건)",
            description = """
                    「검수 대기」(`RECEIVED`) 건을 반려한다. **제출 = 즉시 확정이고 되돌릴 수 없다.** 응답은 갱신된 상세다.
                    입력 6항목은 소비자 반품·교환 상세(C10-5)에 노출되는 값과 1:1 이다(1009 기획 수정본 5-b).

                    **권한:** SELLER · **버튼 노출:** `actions.canReject`

                    **필수** — 하나라도 빠지면 400 `CLAIM_REJECT_INCOMPLETE`(무엇이 빠졌든 같은 코드)
                    - `reasonCode` — `USED` 개봉·사용 흔적 · `PACKAGE_DAMAGED` 포장 훼손 · `PRODUCT_MISMATCH` 상품 불일치 ·
                      `PERIOD_EXPIRED` 기간 경과 · `ETC` 기타
                    - `detail` — 상세 설명(공백만은 누락). 브랜드 · 어드민 기록용
                    - `legalBasis` — 법적 근거(전자상거래법 제17조②): `ART17_2_1` 소비자 책임 멸실·훼손 · `ART17_2_2` 사용·소비로 가치
                      감소 · `ART17_2_3` 시간 경과로 재판매 곤란 · `ART17_2_5` 복제 가능 상품 포장 훼손. 소비자에게 보인다
                    - `consumerMessage` — 소비자에게 보낼 메시지. **그대로 전달된다**
                    - `evidenceImageUrls` — 증빙 사진 **1~5장**. 빈 문자열이 섞이면 누락. 소비자에게 그대로 보인다

                    **선택**
                    - `rejectedQuantity` — 반려 범위. 생략하거나 신청 수량과 같으면 **전체 반려**, 작으면 **일부 반려** — 나머지 수량은
                      검수 통과로 처리되고(반품이면 PG 자동 환불), 반려 수량은 같은 요청의 새 접수번호로 갈라진다(상세
                      `rejection.splitFromClaimNumber`). 1 미만 · 신청 수량 초과는 400 `CLAIM_QUANTITY_EXCEEDED`
                    - `faultChangedToSeller` — `true` 면 **브랜드 귀책으로 인정**: 반품 배송비 차감이 환불에 돌아오고, 반려 재발송비는
                      브랜드가 진다(0원 — 반려 상품이 바로 「재발송 대기」로 간다). 기본 `false`(변경 없음 · 소비자 귀책)

                    **검사 순서:** 입력 누락(400) → 클레임 소유(404) → 상태(409) → 반려 수량(400)

                    **반려 뒤 흐름** — 반려된 상품은 소비자에게 돌려보낸다. 재발송 배송비는 소비자 부담이다(브랜드 귀책 인정이면 0원)
                    - 같은 박스에 통과된 반품이 있으면 그 환불액에서 빼고 → 바로 「재발송 대기」
                    - 교환 요청 때 낸 배송비가 있으면 그것으로 충당하고 → 바로 「재발송 대기」
                    - 둘 다 아니면 소비자 결제를 기다린다 → 「반려 보류」(결제 기한 14일 · 이후 미결제 고지 · 약관 반영 전 폐기 없음)
                    - 같은 박스에 판정이 남은 건이 있으면 위 정산은 그 판정이 끝날 때 정해진다(그때까지 「반려 보류」)
                    - 교환 반려면 잡아 둔 새 옵션 재고가 반려 수량만큼 원복된다
                    - **반려는 구매확정 정지를 푼다** — 진행 중 클레임이 남지 않으면 남은 일수부터 다시 센다
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SellerClaimRejectRequest.class),
                    examples = @ExampleObject(name = "사용 흔적 · 일부 반려", value = """
                            {
                              "reasonCode": "USED",
                              "detail": "용기 입구에 사용 흔적이 있고 내용물이 약 30% 줄어 있습니다.",
                              "legalBasis": "ART17_2_2",
                              "rejectedQuantity": 1,
                              "faultChangedToSeller": false,
                              "consumerMessage": "개봉 후 사용 흔적이 있어 1개는 반품이 어렵습니다. 나머지 1개는 환불됩니다.",
                              "evidenceImageUrls": [
                                "https://cdn.showroomz.co.kr/claim/3021/evidence-1.jpg",
                                "https://cdn.showroomz.co.kr/claim/3021/evidence-2.jpg"
                              ]
                            }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "CLAIM_REJECT_INCOMPLETE — 사유 · 상세 · 법적 근거 · 소비자 메시지 · "
                    + "증빙(1~5장) 중 누락 · 증빙 5장 초과 · 빈 URL / CLAIM_QUANTITY_EXCEEDED — 반려 수량 범위 밖",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "증빙 누락", value = """
                                    {
                                      "code": "CLAIM_REJECT_INCOMPLETE",
                                      "message": "반려 사유 · 상세 설명 · 법적 근거 · 소비자 메시지 · 증빙 사진을 모두 입력해 주세요."
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND — 없는 클레임이거나 내 브랜드 것이 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CLAIM_STATE_CHANGED — 검수 대기가 아님(이미 판정됨 포함)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimDetailResponse> rejectInspection(
            @Parameter(description = "클레임 id", example = "3021") @PathVariable Long claimId,
            @RequestBody SellerClaimRejectRequest request);

    // ── 재발송 송장 ─────────────────────────────────────────────────────────

    @Operation(
            summary = "재발송 송장 등록 (다건)",
            description = """
                    「재발송 대기」(`RESHIP_READY`) 건에 송장을 등록한다. 교환 새 상품이든 거절된 상품의 반송이든 같은 API 다
                    (`reshipReason` 으로 구분되고, 보낼 물건은 목록의 `shipLabel` 이다). 셀 입력 · 엑셀 업로드 결과는 클라이언트
                    임시값이고 **이 호출이 유일한 확정 지점이다.**

                    **권한:** SELLER · **버튼 노출:** `actions.canRegisterReshipment`

                    - **등록하면 완료가 아니라 「재발송 중」(`RESHIPPING`)으로 넘어간다.** 도착이 확인되면 완료된다(교환 완료 / 거절 종결)
                    - 다건 · **행 단위 부분 성공** — 처리되지 않은 행만 `skipped` 에 사유와 함께 돌려준다. 전부 제외돼도 200
                    - 송장번호는 숫자만 남기고 판정한다(`6400-1234-5678` → `640012345678`). 빈 값 행은 에러 없이 조용히 건너뛴다
                      (`succeeded` · `skipped` 어디에도 세지 않는다)
                    - 택배사는 주문 송장과 같은 추적 연동 12종(`GET /v1/common/delivery-carriers`) — `CJ` · `EPOST` · `HANJIN` ·
                      `LOTTE` · `LOGEN` · `KYUNGDONG` · `DAESIN` · `ILYANG` · `CU` · `GS25` · `HAPDONG` · `WOORI`. `COUPANG` 은 형식 오류로
                      제외된다. 업로드에서 택배사 칸이 비어 `null` 로 남은 행도 형식 오류로 제외된다

                    **행별 검사 순서:** 형식 → 전역 중복 → 상태

                    **`skipped[].code`**
                    - `INVOICE_FORMAT_INVALID` — 택배사 없음 · 택배사 규칙에 맞지 않는 번호
                    - `INVOICE_DUPLICATE` — 배송 중인 주문의 송장, 또는 재발송 중인 다른 건의 송장과 겹침. 내 브랜드 건이면
                      `message` 에 겹치는 번호(주문번호 / 접수번호)를 지목하고, 남의 건이면 「다른 주문에 이미 등록된 번호입니다.」
                    - `CLAIM_STATE_CHANGED` — 재발송 대기가 아님(이미 등록됨 등) · 없는 id · 남의 브랜드 건(존재 비노출)
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SellerClaimReshipDto.RegisterRequest.class),
                    examples = @ExampleObject(name = "2건 등록", value = """
                            {
                              "items": [
                                { "claimId": 3021, "carrier": "CJ", "trackingNumber": "6400-1234-5678" },
                                { "claimId": 3030, "carrier": "HANJIN", "trackingNumber": "512345678901" }
                              ]
                            }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 결과 — 결과 배너(몇 건이 어디로)는 이 응답으로 그린다 · 전부 제외돼도 200",
                    content = @Content(schema = @Schema(implementation = SellerClaimBatchResponse.class),
                            examples = @ExampleObject(name = "부분 성공", value = """
                                    {
                                      "succeeded": 1,
                                      "skipped": [
                                        {
                                          "claimId": 3030,
                                          "code": "INVOICE_DUPLICATE",
                                          "message": "CLM-3011에 이미 등록된 번호입니다."
                                        }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `items` 비었음 · 500건 초과 · `claimId` 누락 · 11종 외 택배사",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimBatchResponse> registerReshipments(
            @Valid @RequestBody SellerClaimReshipDto.RegisterRequest request);

    @Operation(
            summary = "재발송 송장 수정",
            description = """
                    「재발송 중」(`RESHIPPING`) 건의 송장을 고친다 — 형식은 맞지만 다른 건의 송장을 붙여넣은 경우를 고치는 경로다.
                    응답은 갱신된 상세다.

                    **권한:** SELLER · **버튼 노출:** `actions.canUpdateReshipment`

                    - 등록 시각은 유지되고 추적 값만 초기화된다 — 수정 후 감시 배치가 새 송장 기준으로 다시 판정한다
                    - 처리 이력 `detail` 에 「구 → 신」이 남는다(예: 「CJ대한통운 640012345678 → 한진택배 512345678901」)
                    - 송장번호는 숫자만 남기고 판정한다. 형식 · 전역 중복 검사는 등록과 같다

                    **검사 순서:** 클레임 소유(404) → 형식(400) → 전역 중복(409) → 상태(409)
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SellerClaimReshipDto.UpdateRequest.class),
                    examples = @ExampleObject(name = "택배사·번호 정정", value = """
                            {
                              "carrier": "HANJIN",
                              "trackingNumber": "512345678901"
                            }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "INVOICE_FORMAT_INVALID — 송장번호가 비었거나 택배사 규칙에 맞지 않음 · "
                    + "INVALID_INPUT — 택배사 · 송장번호 누락 · 11종 외 택배사",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND — 없는 클레임이거나 내 브랜드 것이 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INVOICE_DUPLICATE — 다른 건에 이미 등록된 번호(`message` 규칙은 등록과 같다) · "
                    + "CLAIM_STATE_CHANGED — 재발송 중이 아님(재발송 대기 · 이미 도착 등)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "중복 — 내 브랜드 주문", value = """
                                    {
                                      "code": "INVOICE_DUPLICATE",
                                      "message": "20260930-000087에 이미 등록된 번호입니다."
                                    }
                                    """)))
    })
    ResponseEntity<SellerClaimDetailResponse> updateReshipment(
            @Parameter(description = "클레임 id", example = "3021") @PathVariable Long claimId,
            @Valid @RequestBody SellerClaimReshipDto.UpdateRequest request);

    // ── 재발송 목록 엑셀 ────────────────────────────────────────────────────

    @Operation(
            summary = "재발송 목록 다운로드 (xlsx)",
            description = """
                    「재발송 대기」 건의 수취 정보를 엑셀로 내려받는다. **재발송 수취인의 연락처·주소가 나가는 유일한 경로**이고
                    다운로드는 반출 이력으로 기록된다(같은 트랜잭션). **상태를 바꾸지 않는다** — 주문 발주서와 달리 다운로드가
                    다음 단계로의 전이가 아니다.

                    **권한:** SELLER

                    **대상**
                    - `claimIds` 를 보내면 선택 건 — 재발송 대기가 아닌 건 · 남의 브랜드 건은 조용히 빠진다
                    - 비우면(또는 생략) 내 브랜드의 재발송 대기 전체 — **최대 2,000건**까지 싣는다
                    - 남는 건이 없으면 400 `CLAIM_EXPORT_EMPTY`

                    **열 구성**
                    - `columns` 선택 순서 = 엑셀 좌→우 열 순서 · 중복은 첫 위치만 남는다
                    - **선택한 열 뒤에 빈 「택배사」 「송장번호」 2열이 항상 붙는다** — 이 파일을 채워 업로드(`/reshipments/parse`)에 그대로 쓴다
                    - 「상품명」 「옵션」은 **보낼 물건**이다 — 교환 재발송은 새 옵션, 반려 반송은 원래 옵션
                    - 수취지는 재발송 수취지다. 교환은 소비자가 검수 전까지 바꿀 수 있지만 재발송 대기부터는 잠긴다 —
                      이 목록에 오른 주소는 바뀌지 않는다
                    - `saveAsDefault: true` — 이 컬럼 구성을 기본값으로 저장(`PUT /reshipments/export/template` 과 같은 저장소)

                    | 코드 | 헤더 | 기본 7종 |
                    |---|---|---|
                    | `CLAIM_NUMBER` | 접수번호 | ✔ |
                    | `RECIPIENT` | 수취인 | ✔ |
                    | `PHONE` | 연락처 | ✔ |
                    | `ZIP_CODE` | 우편번호 | ✔ |
                    | `ADDRESS` | 주소(주소 + 상세 주소) | ✔ |
                    | `PRODUCT_NAME` | 상품명 | ✔ |
                    | `OPTION` | 옵션(보낼 옵션) | ✔ |
                    | `QUANTITY` | 수량 | |
                    | `RESHIP_REASON` | 재발송 사유(교환 재발송 / 반려 반송) | |
                    | `REQUESTED_AT` | 신청일시(`yyyy-MM-dd HH:mm`) | |
                    | `ORDER_NUMBER` | 주문번호 | |
                    | `GROUP_BUY_NAME` | 공구명 | |
                    | `CLAIM_TYPE` | 유형(반품 / 교환) | |

                    업로드 파싱은 「접수번호」 열을 머리글로 찾으므로 **`CLAIM_NUMBER` 를 빼면 채운 파일을 다시 올릴 수 없다.**

                    **응답** — 파일명 `재발송목록_yyyyMMdd_HHmmss.xlsx`(Content-Disposition `filename*=UTF-8''…`)
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SellerClaimReshipDto.ExportRequest.class),
                    examples = {
                            @ExampleObject(name = "선택 건", value = """
                                    {
                                      "claimIds": [3021, 3022],
                                      "columns": ["CLAIM_NUMBER", "RECIPIENT", "PHONE", "ZIP_CODE", "ADDRESS", "PRODUCT_NAME", "OPTION"]
                                    }
                                    """),
                            @ExampleObject(name = "재발송 대기 전체 · 구성 기본값 저장", value = """
                                    {
                                      "claimIds": [],
                                      "columns": ["CLAIM_NUMBER", "RESHIP_REASON", "RECIPIENT", "PHONE", "ADDRESS", "PRODUCT_NAME", "OPTION", "QUANTITY"],
                                      "saveAsDefault": true
                                    }
                                    """)
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx 바이너리(Content-Disposition attachment)",
                    content = @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "CLAIM_EXPORT_EMPTY — 내려받을 재발송 대기 건 없음 · "
                    + "INVALID_INPUT — `columns` 비었음 · 정의되지 않은 컬럼 코드",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "대상 없음", value = """
                                    {
                                      "code": "CLAIM_EXPORT_EMPTY",
                                      "message": "내려받을 재발송 대기 건이 없습니다."
                                    }
                                    """)))
    })
    ResponseEntity<byte[]> exportReshipments(@Valid @RequestBody SellerClaimReshipDto.ExportRequest request);

    @Operation(summary = "재발송 목록 컬럼 기본값 조회",
            description = """
                    저장한 구성(없으면 기본 7종 — 접수번호 · 수취인 · 연락처 · 우편번호 · 주소 · 상품명 · 옵션)과
                    고를 수 있는 컬럼 전체 13종(`available` · enum 선언 순서)을 내린다. 코드표는 다운로드 API 참고.

                    **권한:** SELLER
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = SellerClaimReshipDto.TemplateResponse.class),
                    examples = @ExampleObject(name = "저장 구성 없음(기본 7종)", value = """
                            {
                              "columns": ["CLAIM_NUMBER", "RECIPIENT", "PHONE", "ZIP_CODE", "ADDRESS", "PRODUCT_NAME", "OPTION"],
                              "available": [
                                { "code": "CLAIM_NUMBER", "header": "접수번호", "basic": true },
                                { "code": "RECIPIENT", "header": "수취인", "basic": true },
                                { "code": "QUANTITY", "header": "수량", "basic": false },
                                { "code": "CLAIM_TYPE", "header": "유형", "basic": false }
                              ]
                            }
                            """)))
    ResponseEntity<SellerClaimReshipDto.TemplateResponse> getReshipTemplate();

    @Operation(summary = "재발송 목록 컬럼 기본값 저장",
            description = """
                    컬럼 구성과 순서를 저장한다 — 선택 순서가 곧 열 순서다. 중복 코드는 첫 위치만 남는다.
                    다운로드의 `saveAsDefault: true` 와 같은 저장소를 쓴다(발주서 구성과는 따로 저장된다).

                    **권한:** SELLER
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SellerClaimReshipDto.TemplateUpdateRequest.class),
                    examples = @ExampleObject(name = "구성 저장", value = """
                            {
                              "columns": ["CLAIM_NUMBER", "RESHIP_REASON", "RECIPIENT", "PHONE", "ZIP_CODE", "ADDRESS", "PRODUCT_NAME", "OPTION"]
                            }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 후 구성(조회 API와 같은 응답)"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `columns` 비었음 · 정의되지 않은 컬럼 코드",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimReshipDto.TemplateResponse> updateReshipTemplate(
            @Valid @RequestBody SellerClaimReshipDto.TemplateUpdateRequest request);

    @Operation(
            summary = "재발송 송장 일괄 업로드 검증 (xlsx)",
            description = """
                    채운 재발송 목록 파일을 올려 행별로 분류한다. **상태를 바꾸지 않는다** — 이 응답으로 화면의 셀만 채우고
                    (「N건 목록에 채우기」의 N = `validRows`), 확정은 `POST /reshipments` 다.

                    **권한:** SELLER

                    **양식** — 첫 시트 · 1행 머리글 · 최대 1,000행. 다운로드한 재발송 목록 파일을 그대로 채워 올리면 된다
                    - 열은 **머리글로 찾는다** — 「접수번호」 「택배사」 「송장번호」. 순서 · 다른 열은 상관없다. 셋 중 하나라도 없으면 400
                    - 접수번호는 `CLM-3021` · `CLM3021` 모두 인식한다(대소문자 무관)
                    - 택배사는 한글명(「CJ대한통운」) · 코드(`CJ`) 모두 인식한다. 칸이 비었으면 `carrier = null` 로 통과하고
                      화면에서 고른 뒤 확정한다
                    - 송장번호는 숫자만 남긴다 · 숫자 셀은 자릿수 그대로 읽는다(지수 표기로 깨지지 않는다)
                    - 세 칸이 모두 빈 행은 건너뛴다(`totalRows` 에 세지 않는다)

                    **`rows[].errorCode`** — 위에서부터 처음 걸린 하나만 내린다

                    | 코드 | 사유 |
                    |---|---|
                    | `CLAIM_NOT_FOUND` | 없는 접수번호 · 남의 브랜드 접수번호(같은 사유 — 존재 비노출) |
                    | `CLAIM_DUPLICATE_IN_FILE` | 같은 접수번호가 파일에 두 번 — 첫 행만 정상 |
                    | `NOT_RESHIP_READY` | 재발송 대기 상태가 아님 |
                    | `TRACKING_REQUIRED` | 송장번호 없음 |
                    | `CARRIER_INVALID` | 11종 외 택배사 |
                    | `INVOICE_DUPLICATE` | 파일 안 중복 · 진행 중인 주문/재발송 송장과 겹침(내 브랜드 건이면 번호를 지목) |
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "행별 분류 결과 — 부분 성공 허용",
                    content = @Content(schema = @Schema(implementation = SellerClaimReshipDto.ParseResponse.class),
                            examples = @ExampleObject(name = "정상 2 · 오류 1", value = """
                                    {
                                      "totalRows": 3,
                                      "validRows": 2,
                                      "rows": [
                                        {
                                          "rowNumber": 2,
                                          "claimNumber": "CLM-3021",
                                          "claimId": 3021,
                                          "carrier": "CJ",
                                          "trackingNumber": "640012345678",
                                          "valid": true,
                                          "errorCode": null,
                                          "message": null
                                        },
                                        {
                                          "rowNumber": 3,
                                          "claimNumber": "CLM-3022",
                                          "claimId": 3022,
                                          "carrier": null,
                                          "trackingNumber": "512345678901",
                                          "valid": true,
                                          "errorCode": null,
                                          "message": null
                                        },
                                        {
                                          "rowNumber": 4,
                                          "claimNumber": "CLM-3015",
                                          "claimId": 3015,
                                          "carrier": "CJ",
                                          "trackingNumber": "640099998888",
                                          "valid": false,
                                          "errorCode": "NOT_RESHIP_READY",
                                          "message": "재발송 대기 상태가 아닙니다."
                                        }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "CLAIM_UPLOAD_HEADER_MISSING — 「접수번호」 「택배사」 「송장번호」 머리글 중 없음 · 빈 시트 · "
                    + "SHIPMENT_FILE_INVALID — xlsx 가 아니거나 손상 · SHIPMENT_FILE_TOO_MANY_ROWS — 1,000행 초과",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "머리글 없음", value = """
                                    {
                                      "code": "CLAIM_UPLOAD_HEADER_MISSING",
                                      "message": "파일에 접수번호 · 택배사 · 송장번호 열이 있어야 합니다."
                                    }
                                    """)))
    })
    ResponseEntity<SellerClaimReshipDto.ParseResponse> parseReshipments(
            @Parameter(description = "채운 재발송 목록 xlsx — 「접수번호」 「택배사」 「송장번호」 머리글 필수", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestPart("file") MultipartFile file);
}
