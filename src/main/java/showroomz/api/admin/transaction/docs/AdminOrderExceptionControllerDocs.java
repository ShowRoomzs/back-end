package showroomz.api.admin.transaction.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.transaction.dto.AdminOrderExceptionDto;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Transaction · Exceptions", description = "어드민 거래 관리 · 예외 관리(06d)")
public interface AdminOrderExceptionControllerDocs {

    @Operation(summary = "예외 목록 (06d A1 · A2)",
            description = """
                    운영자가 지켜보다가 필요할 때만 대행하는 모니터 — **쓰기가 없다**(실행 · 독촉 버튼 없음). 행의 [열기]는 `link` 가 정한다 —
                    `ORDER` 면 06a 주문 상세, `CLAIM` 이면 06b 클레임 상세. 기한 경과 알림은 **시스템 자동**이고 `noticeCount` 로 횟수만 보인다.

                    - `tab=DELAY`(기본 · 처리 지연) — `SHIP_OVERDUE` 발송 기한 경과(공구 마감 + 주문 시점 N영업일) ·
                      `INSPECT_OVERDUE` 검수 지연(입고 + 2영업일) · `RESHIP_DELAYED` 재발송 지연(검수 통과 · 재발송비 결제 + 2영업일 · 알림 없음).
                      취소 요청 미응답은 1영업일 자동 승인이라 오지 않는다. 준비 시작(신규 → 상품준비중)으로는 빠지지 않는다
                    - `tab=DELIVERY`(배송 예외 · 플랫폼 미개입) — `PICKUP_UNCONFIRMED` 집화 확인 필요(24시간) · `TRACKING_STALLED` 추적 정지(7일) ·
                      `RETURNING` 반송 중(완료 감지 시 PG 자동 환불 · 완료가 감지된 건은 빠진다) · `COLLECTION_UNSCANNED` 회수 송장 미조회(24시간 ·
                      추적 연동이 꺼져 있으면 비어 있다)
                    - `kind` — 유형 셀렉트. 탭에 속하지 않는 유형이면 400
                    - `keyword` — 주문번호 · 하위주문번호 · 브랜드명 부분 일치, `CLM-N` 은 접수번호 정확 일치
                    - 정렬은 **경과 오래된 순 고정**(처리 지연 = 기한 · 배송 예외 = 기준 시각)
                    - 기한 · 경과 · 다음 단계 · 처리 주체는 **서버 문장**(`dueBasisLabel` · `elapsedLabel` · `nextStepLabel` · `handlerLabel`)과
                      숫자(`dueAt` · `elapsedHours` · `elapsedBusinessDays` · `noticeCount` · `nextNoticeAt`)를 함께 내린다
                    - `actOnBehalfAvailable` — 알림 N회(기본 3) 무응답 = 대행 가능 **조건**. 06a 상세 `actions.canRegisterShipment` 와 같은 판정이다.
                      검수 지연은 조건만 보이고 대행 행동은 없다(대표 확정 대기)
                    - 집화 확인 필요의 「시스템 알림」은 아직 연결되지 않았다(알림 모듈)
                    - 송장번호는 가리지 않는다(CS 가 택배사에 조회할 번호)
                    - 응답은 `{ asOf, page }` — `asOf` 가 경과 · 다음 회차 계산의 서버 기준 시각

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT_VALUE — 탭에 없는 유형 · ORDER_PAGE_SIZE_INVALID")
    })
    ResponseEntity<AdminOrderExceptionDto.ExceptionPage> getExceptions(AdminOrderExceptionDto.ExceptionTab tab,
                                                                       AdminOrderExceptionDto.ExceptionKind kind,
                                                                       String keyword, PagingRequest pagingRequest);

    @Operation(summary = "예외 요약",
            description = """
                    필터 · 검색과 무관한 전체 기준.
                    - `tabCounts` — 탭 숫자. **`tabCounts.DELAY` 가 06a 툴바 「처리 지연 N건」**이다(06a 응답에 합치지 않는다)
                    - `kindCounts` — 유형 7종 전부(0건 포함)
                    - `actOnBehalfCount` — 툴바 「대행 가능 N건」
                    - `badge` · `badgeScope` — 사이드바 배지와 그 범위(`ALL` 처리 지연 + 배송 예외 · `DELAY` 처리 지연만 — 설정값)

                    **권한:** ADMIN
                    """)
    ResponseEntity<AdminOrderExceptionDto.ExceptionSummary> getSummary();
}
