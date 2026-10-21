package showroomz.api.seller.home.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.seller.home.dto.SellerHomeDto;

@Tag(name = "Seller - Home", description = "파트너센터 홈(ui-partner-16-home) · GNB 배지")
public interface SellerHomeControllerDocs {

    @Operation(
            summary = "홈 조회",
            description = """
                    홈 진입 시 한 번 부릅니다. 처리 대기는 **진입 시 갱신**이고, 1분 갱신은 실시간 카드(`/home/live`)만 합니다.

                    | 블록 | 내용 |
                    |---|---|
                    | `account` | `GET /v1/seller/account-status` 와 같다 — `sanction != null` 이면 H4(배너 · 탑바 「정지」 칩 · [+ 계약 작성] 비활성) |
                    | `guide` | H3 · H3a 첫 공구 가이드 — `visible = false` 면 그리지 않는다. 단계 판정은 서버, 문구는 FE |
                    | `pendingTasks` | 처리 대기 5묶음 — 메뉴 요약과 **같은 값**이다. `total = 0` 이면 H2 「처리할 일 없음」 |
                    | `live` | 진행 중 공구 실시간 첫 그리기 — 이후 `/home/live` |
                    | `contractsInProgress` | 체결 전 4상태 · 공구 시작일 순 5건 — `hold` 는 정지 보류 배지(체결 보류 · 검토 보류) |
                    | `upcomingGroupBuys` | 준비중 · 준비완료 · 오픈일 순 5건 — `blockedReason` · `hold`(승인 보류) |
                    | `settlement` | 다음 지급 예정 · 정산 확인 중 · 조정 협의 「내 응답 필요」 |

                    **처리 대기 숫자 → 진입**

                    | 숫자 | 진입 | 경고색 |
                    |---|---|---|
                    | `sales.prepareStart` · `invoiceRegister` · `cancelRequests` | 주문 `tab=NEW` · `PREPARING` · `CANCEL_REQUESTED` | |
                    | `sales.shipOverdue` | 주문 `tab=ALL&shipOverdue=true` | ✔ |
                    | `sales.deliveryIssue` | 주문 `tab=SHIPPING`(배송 이상) · `RETURNING` | |
                    | `claims.collectWait` · `inspection` · `reship` | 반품·교환 `tab=COLLECT_WAIT` · `INSPECTION` · `RESHIP` | |
                    | `claims.inspectOverdue` | 반품·교환 `tab=INSPECTION` | ✔ |
                    | `inquiry.unansweredProductInquiries` | 문의 `status=WAITING` | |
                    | `contract.mySignatureNeeded` · `reviewRejected` | 계약 서명 진행중 · 검토 반려 | |
                    | `settlement.adjustmentMyTurn` | 연결·소통 조정 협의 스레드 | |

                    `shipOverdue` · `inspectOverdue` 는 이미 다른 칸에 든 건이라 `total` 에 더하지 않습니다. 0 건은 FE 가 흐리게 그립니다.

                    **권한:** SELLER"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = SellerHomeDto.Home.class))),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND")
    })
    ResponseEntity<SellerHomeDto.Home> getHome();

    @Operation(
            summary = "진행 중 공구 실시간",
            description = """
                    FE 가 1분마다 부릅니다(서버 캐시 없음). 대상은 진행중 · 중단 예정 공구 — 보통 1건이고 겹치면 시작일 순으로 쌓습니다.

                    - **결제 기준 · 취소 전 포함** — 결제된 순간을 세며 뒤에 취소돼도 빠지지 않습니다. 확정 성과는 성과 관리 몫입니다.
                    - 「오늘」은 00:00 기준 · `ordersYesterdaySameTime` = 어제 00:00 ~ 어제 이 시각
                    - `visitorsToday` · `visitorsCumulative` = 그 인플루언서 쇼룸 방문자 **사람 수**(오늘 · 공구 기간)
                    - `stock.remaining` = 공구 상품 옵션 재고 합(공구 전용 재고는 없다) · `minQuantity` = 계약 최소 준비 물량 합
                    - `hourly` 24칸 — 지금 시각 칸이 `collecting = true`(「집계 중」)

                    **권한:** SELLER"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = SellerHomeDto.Live.class),
                            examples = @ExampleObject(name = "진행 중 1건", value = """
                                    {
                                      "asOf": "2026-10-22T14:32:10",
                                      "items": [{
                                        "groupBuyId": 12, "name": "밤 리페어 크림 공구", "creatorId": 7, "creatorName": "뷰티로그",
                                        "startAt": "2026-10-20T10:00:00", "endAt": "2026-10-26T23:59:59", "progressRatio": 0.38,
                                        "visitorsToday": 312, "visitorsCumulative": 1184,
                                        "ordersToday": 38, "ordersYesterdaySameTime": 31, "paidToday": 1254000,
                                        "paidCumulative": 8420000, "ordersCumulative": 256, "soldQuantity": 301,
                                        "stock": {"remaining": 212, "minQuantity": 300, "productId": 501},
                                        "hourly": [{"hour": 0, "orders": 2, "amount": 66000, "collecting": false},
                                                   {"hour": 14, "orders": 6, "amount": 198000, "collecting": true}]
                                      }]
                                    }""")))
    })
    ResponseEntity<SellerHomeDto.Live> getLive();

    @Operation(
            summary = "공구 일정 월 달력",
            description = """
                    `month`(yyyy-MM · 생략 시 이번 달) 앞뒤 7일까지 넉넉히 내립니다(그리드의 앞뒤 달 칸).

                    - `groupBuys` — 기간이 구간과 겹치는 공구 · `status` = IN_PROGRESS(진행중 · 중단 예정) · UPCOMING(준비중 · 준비완료) · ENDED(종결)
                    - `deadlines` — `SHIP_DUE`(발송 기한 · 공구별 날짜) · `SIGN_DUE`(서명 진행중 계약의 서명 기한 · 정지 보류 중이면 기한이 멈춰 빠진다)
                    - `holidays` — `GET /v1/common/business-holidays` 와 같은 값(따로 부르지 않아도 된다)

                    +N · 주말 회색 · 오늘 테두리는 FE 가 그립니다.

                    **권한:** SELLER"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = SellerHomeDto.Calendar.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT_VALUE — month 형식 오류")
    })
    ResponseEntity<SellerHomeDto.Calendar> getCalendar(
            @Parameter(description = "조회 월(yyyy-MM) — 생략 시 이번 달", example = "2026-10") String month);

    @Operation(
            summary = "첫 공구 가이드 숨기기",
            description = """
                    H3 [가이드 숨기기]. `hidden=false` 도 받습니다(다시 표시 — 지금 UI 는 없다). 응답은 갱신된 `guide` 블록입니다.
                    첫 공구가 오픈되면 숨김과 무관하게 가이드가 사라집니다.

                    **권한:** SELLER"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공",
                    content = @Content(schema = @Schema(implementation = SellerHomeDto.Guide.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT_VALUE — hidden 누락")
    })
    ResponseEntity<SellerHomeDto.Guide> updateGuide(SellerHomeDto.GuideRequest request);

    @Operation(
            summary = "GNB 배지",
            description = """
                    사이드바 메뉴 배지를 한 번에 내립니다 — 홈 처리 대기와 **같은 계산**입니다(0 이면 FE 가 숨긴다).
                    기존 메뉴별 `/summary` 는 탭 카운트 용으로 그대로 둡니다.

                    | 배지 | 정의 |
                    |---|---|
                    | `connections` | 안 읽은 메시지(`GET /v1/seller/connections/summary` 와 같다) |
                    | `contracts` | 내 서명 필요 + 검토 반려 |
                    | `groupBuys` | 최소 물량 확인 대기 + 소명 가능한 운영자 중단(공구 요약 `actionRequiredCount` 와 같다) |
                    | `sales` | 준비 시작 + 송장 등록 + 취소 요청 + 배송 이상 + 회수 대기 + 입고·검수 + 재발송·교환(기한 경과는 따로 더하지 않는다) |
                    | `settlements` | 조정 협의 「내 응답 필요」 |
                    | `inquiries` | 답변 대기 상품 문의 |

                    **권한:** SELLER"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = SellerHomeDto.Badges.class),
                            examples = @ExampleObject(value = """
                                    {"connections": 2, "contracts": 2, "groupBuys": 2, "sales": 23, "settlements": 1, "inquiries": 3}""")))
    })
    ResponseEntity<SellerHomeDto.Badges> getBadges();
}
