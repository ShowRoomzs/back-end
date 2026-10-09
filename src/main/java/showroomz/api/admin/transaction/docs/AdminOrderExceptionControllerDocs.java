package showroomz.api.admin.transaction.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;

import java.util.List;

@Tag(name = "Admin - Transaction · Exceptions", description = "어드민 거래 관리 · 예외 관리(06d)")
public interface AdminOrderExceptionControllerDocs {

    @Operation(summary = "예외 목록 (06d A1 · A2)",
            description = """
                    운영자가 지켜보다가 필요할 때만 대행하는 모니터 — **실행 버튼이 없다**. 행의 [열기]는 06a(주문) · 06b(`claimId` 가 있으면 클레임)
                    상세로 간다. 독촉 버튼도 없다 — 기한 경과 알림은 **시스템 자동**이고 `noticeCount` 로 횟수만 보인다.

                    - `tab=DELAY`(기본 · 처리 지연) — `SHIP_OVERDUE` 발송 기한 경과(공구 마감 + 주문 시점 N영업일 · 판정 = 송장 등록 시각) ·
                      `INSPECT_OVERDUE` 검수 지연(입고 + 2영업일) · `RESHIP_DELAYED` 재발송 지연(검수 통과 · 재발송비 결제 + 2영업일 · 근거 대기).
                      취소 요청 미응답은 1영업일 자동 승인이라 오지 않는다. `actOnBehalfAvailable` — 알림 N회(기본 3) 무응답 = 대행 가능 **조건**
                    - `tab=DELIVERY`(배송 예외 · 플랫폼 미개입) — `PICKUP_UNCONFIRMED` 집화 확인 필요(24시간) · `TRACKING_STALLED` 추적 정지(집화 후 7일) ·
                      `RETURNING` 반송 중(완료 감지 시 PG 자동 환불 · 재발송 없음) · `COLLECTION_UNSCANNED` 회수 송장 미조회(24시간).
                      `handlerLabel`(처리 주체)이 CS 답변 문장이다
                    - 영업일 = 주말 · 공휴일 제외

                    **권한:** ADMIN
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공 — 경과가 긴 순"))
    ResponseEntity<List<AdminTransactionDto.ExceptionItem>> getExceptions(AdminTransactionDto.ExceptionTab tab);

    @Operation(summary = "예외 요약", description = "탭 건수와 사이드바 배지(처리 지연 + 배송 예외).\n\n**권한:** ADMIN")
    ResponseEntity<AdminTransactionDto.ExceptionSummary> getSummary();
}
