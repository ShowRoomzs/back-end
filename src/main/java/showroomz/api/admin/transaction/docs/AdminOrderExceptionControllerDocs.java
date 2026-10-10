package showroomz.api.admin.transaction.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.transaction.dto.AdminOrderExceptionDto;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.global.dto.PagingRequest;

import static showroomz.api.admin.transaction.docs.AdminTransactionDocsExamples.*;

@Tag(name = "Admin - Transaction · Exceptions", description = """
        어드민 거래 관리 · 예외 관리(06d). 새 데이터 없이 주문 · 클레임에서 조건에 맞는 것을 모아 보여 주는 **모니터**다 — **쓰기 API 가 없다**
        (실행 · 독촉 버튼 없음). 기한 경과 알림은 시스템이 자동으로 보내고, 운영자는 조건이 찬 건만 06a · 06b 상세에서 대행한다.""")
public interface AdminOrderExceptionControllerDocs {

    @Operation(summary = "예외 목록 (06d A1 · A2)", description = """
            처리 지연 · 배송 예외 건을 **경과 오래된 순(고정)**으로 봅니다. 행의 [열기]는 `link`가 정합니다 —
            `ORDER`면 06a 주문 상세(`orderId`), `CLAIM`이면 06b 클레임 상세(`claimId`). FE 는 `kind`로 목적지를 고르지 않습니다.

            **권한:** ADMIN

            **탭 `tab`** — 없으면 `DELAY`. 탭 · 유형 숫자는 `GET /v1/admin/order-exceptions/summary`.

            **처리 지연 `DELAY`** — 당사자가 기한을 넘긴 건. 운영자가 개입할 수 있는 유일한 탭입니다.

            | `kind` | 대상 | 기한(`dueBasisLabel`) | 자동 알림 | 대행 가능이면 |
            |---|---|---|---|---|
            | `SHIP_OVERDUE` 발송 기한 경과 | 주문 | 공구 마감 + N영업일(**주문 시점 값**) | 있음 | 06a 대행 송장 · 직권 취소 |
            | `INSPECT_OVERDUE` 검수 지연 | 클레임 | 입고 + 2영업일 | 있음 | 06b 검수 무응답 환불(검수는 대신하지 않는다) |
            | `RESHIP_DELAYED` 재발송 지연 | 클레임 | 검수 통과 · 재발송비 결제 + 2영업일 | **없음**(`noticeCount = null`) | — |

            - 취소 요청 미응답은 1영업일 자동 승인이라 오지 않습니다
            - 준비 시작(신규 → 상품준비중)으로는 빠지지 않습니다 — 브랜드의 「응답」은 송장 등록 · 직권 취소뿐입니다
            - `actOnBehalfAvailable` — 자동 알림 N회(기본 3) 무응답 = 대행 가능 **조건**(상태가 아니다). 06a 상세 `actions.canRegisterShipment`와 같은 판정
            - `nextStepLabel` — 「자동 알림 대기 · 2회차 10.12」 · 「자동 알림 3회 무응답 · 대행 가능」 · 「브랜드가 재발송 송장을 등록해야 합니다」
            - `nextNoticeAt` — 다음 자동 알림 예정(영업일 10시 · 15시 중 하루 1회). 대행 조건에 닿았거나 알림이 없는 유형이면 null

            **배송 예외 `DELIVERY`** — 택배 · 추적 이상. 플랫폼이 개입하지 않고, 처리 주체 열(`handlerLabel`)이 CS 답변 문장입니다.

            | `kind` | 기준(`basisLabel`) | 처리 주체 |
            |---|---|---|
            | `PICKUP_UNCONFIRMED` 집화 확인 필요 | 등록 후 24시간 | 브랜드 확인 · 시스템 알림(알림은 아직 연결 전) |
            | `TRACKING_STALLED` 추적 정지 | 집화 후 7일 | 소비자 · 브랜드가 택배사 조회. 마지막 추적 + 28일이 지나면 `actOnBehalfAvailable` — 06a 에서 분실 · 배송완료 판정 |
            | `RETURNING` 반송 중 | 반송 감지 | 완료 감지 시 PG 자동 환불(완료가 감지된 건은 빠진다) |
            | `COLLECTION_UNSCANNED` 회수 송장 미조회 | 등록 후 N시간 | 소비자가 회수 송장 확인 · 수정. 추적 연동이 꺼져 있으면 비어 있다 |

            - `invoice` — 송장(주문 송장 또는 회수 송장). **가리지 않습니다**(CS 가 택배사에 조회할 번호)
            - 배송 예외 행은 기한 · 알림 · 다음 단계가 null 이고, 처리 지연 행은 송장 · 처리 주체가 null 입니다

            **조건**
            - `kind` — 유형 셀렉트. **그 탭에 속하지 않는 유형이면 400**(`tab=DELAY&kind=RETURNING`)
            - `keyword` — 주문번호 · 하위주문번호 · 브랜드명 부분 일치. `CLM-`으로 시작하면 **접수번호 정확 일치**

            **페이징** — `page`(1부터) · `size`(기본 20 · **1~100**, 벗어나면 400)

            **응답 모양** — `{ asOf, page }`. `asOf`가 경과(`elapsedHours` · `elapsedBusinessDays`) · 다음 회차 계산의 **서버 기준 시각**입니다.
            서버 문장(`dueBasisLabel` · `elapsedLabel` · `nextStepLabel` · `handlerLabel`)과 숫자를 함께 내리므로 FE 는 문장을 그대로 씁니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "기준 시각과 예외 목록",
                    content = @Content(schema = @Schema(implementation = AdminOrderExceptionDto.ExceptionPage.class), examples = {
                            @ExampleObject(name = "처리 지연", summary = "발송 기한 경과(대행 가능) · 검수 지연(운영자 환불 가능) · 발송 기한 경과(알림 1회)",
                                    value = EXCEPTION_LIST_DELAY),
                            @ExampleObject(name = "배송 예외", summary = "추적 정지 28일 경과(판정 가능) · 집화 확인 필요", value = EXCEPTION_LIST_DELIVERY)
                    })),
            @ApiResponse(responseCode = "400", description = "탭에 없는 유형 · 페이지 크기 1~100 밖 · 없는 enum 값 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "탭에 없는 유형", value = ERR_KIND_NOT_IN_TAB),
                            @ExampleObject(name = "페이지 크기", value = ERR_PAGE_SIZE),
                            @ExampleObject(name = "enum", value = ERR_INVALID_INPUT)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_FORBIDDEN)))
    })
    ResponseEntity<AdminOrderExceptionDto.ExceptionPage> getExceptions(
            @Parameter(description = "탭 — DELAY(처리 지연 · 기본) · DELIVERY(배송 예외)", example = "DELAY") AdminOrderExceptionDto.ExceptionTab tab,
            @Parameter(description = "유형 — DELAY: SHIP_OVERDUE · INSPECT_OVERDUE · RESHIP_DELAYED / "
                    + "DELIVERY: PICKUP_UNCONFIRMED · TRACKING_STALLED · RETURNING · COLLECTION_UNSCANNED. 탭에 없는 유형이면 400",
                    example = "SHIP_OVERDUE") AdminOrderExceptionDto.ExceptionKind kind,
            @Parameter(description = "주문번호 · 하위주문번호 · 브랜드명 부분 일치, 또는 접수번호(CLM-3021) 정확 일치", example = "CLM-3021") String keyword,
            PagingRequest pagingRequest);

    @Operation(summary = "예외 요약", description = """
            탭 · 유형 숫자와 툴바 · 사이드바 값을 내립니다. **필터 · 검색과 무관한 전체 기준**이고 파라미터를 받지 않습니다. 폴링 대상입니다.

            **권한:** ADMIN

            | 필드 | 쓰는 곳 |
            |---|---|
            | `tabCounts` | 탭 숫자(`DELAY` · `DELIVERY`). **`tabCounts.DELAY`가 06a 툴바 「처리 지연 N건」**이다(06a 응답에 합치지 않는다) |
            | `kindCounts` | 유형 셀렉트 건수 — 7종 전부(0건 포함) |
            | `actOnBehalfCount` | 툴바 「대행 가능 N건」 — 처리 지연 중 대행 조건 충족 |
            | `badge` · `badgeScope` | 사이드바 배지와 그 범위 — `ALL`(처리 지연 + 배송 예외) · `DELAY`(처리 지연만). 범위는 서버 설정값(`order.exception.badge-scope`) |
            | `asOf` | 서버 기준 시각 |
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탭 · 유형 건수 · 대행 가능 · 배지",
                    content = @Content(schema = @Schema(implementation = AdminOrderExceptionDto.ExceptionSummary.class),
                            examples = @ExampleObject(value = EXCEPTION_SUMMARY))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminOrderExceptionDto.ExceptionSummary> getSummary();
}
