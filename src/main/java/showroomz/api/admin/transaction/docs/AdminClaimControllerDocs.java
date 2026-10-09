package showroomz.api.admin.transaction.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimSort;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.time.LocalDate;
import java.util.Set;

@Tag(name = "Admin - Transaction · Claims", description = "어드민 거래 관리 · 반품·교환(06b)")
public interface AdminClaimControllerDocs {

    @Operation(summary = "반품·교환 목록 (06b A1)",
            description = """
                    파트너 11 과 **같은 탭 · 같은 건수 · 같은 행**(`claim`)에 어드민 전용 열 둘 — `brandName` · `feeBearer`(귀책 · 항목 단위)를 더한다.
                    반품·교환은 탭이 아니라 `types` 로 거른다. 조건은 파트너 목록과 같다(`tab` · `types` · `reason` · `from` · `to` · `keyword`).
                    `marketId` 를 주면 그 브랜드만.
                    어드민 전용 조건 — `marketName`(브랜드명 부분 일치) · `consumerName`(소비자명 부분 일치) · `sort`(REQUESTED_DESC · ELAPSED_ASC — 없으면 탭 기본 정렬).
                    전체 탭은 **검수 지연(입고 · 기한 경과)을 상단에 고정**한다(페이지를 넘어서도).
                    `disputeOpen` 이 참이면 보조줄 「소비자 이의 접수」(`disputedAt`) — 앱 「이의 제기」 문의가 답변 전인 반려 보류 건.

                    **권한:** ADMIN
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<PageResponse<AdminTransactionDto.ClaimListItem>> getClaims(Long marketId, ClaimTab tab,
                                                                              Set<ClaimType> types, ClaimReason reason,
                                                                              LocalDate from, LocalDate to,
                                                                              String keyword, String marketName,
                                                                              String consumerName, ClaimSort sort,
                                                                              PagingRequest pagingRequest);

    @Operation(summary = "반품·교환 요약", description = "파트너 11 과 같은 KPI · 탭 · 유형 건수 — 전 브랜드(또는 `marketId`).\n\n"
            + "`disputeCount` — 반려 이의 미처리(반려 보류 중 · 이의 문의 답변 전). 인용하면 반려 보류를 벗어나고, 기각은 문의 답변으로 빠진다.\n\n**권한:** ADMIN")
    ResponseEntity<AdminTransactionDto.ClaimSummary> getSummary(Long marketId);

    @Operation(summary = "반품·교환 상세 (06b B1)",
            description = """
                    파트너 상세와 같은 값(`claim` — 반려 6항목 · 구매확정 타이머 「정지 · 남은 N일」 · 운영자 개설 표시 포함)에 브랜드 · 귀책을 더한다.
                    `canAcceptDispute` — 반려된 반품이 아직 반송 전(반려 보류 · 재발송 대기)이면 B2 반려 이의 인용을 할 수 있다.
                    `disputeRefundAmount` — 인용 환불액(서버 계산 · 수정 불가). FE 는 비활성 입력으로 보인다.
                    `inspectNotice` — 검수 기한 경과 자동 알림 횟수 · 마지막 시각(B1 레일 「자동 알림 N회」).
                    `inspectOverdueBusinessDays` — 입고 · 검수 대기에서 기한을 넘긴 영업일 수(아니면 null).
                    `dispute` — ④ 소비자 이의. 앱 「이의 제기」로 걸린 가장 최근 1:1 문의의 원문 · 사진 · 접수 시각 · 답변 여부(없으면 null).

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND")
    })
    ResponseEntity<AdminTransactionDto.ClaimDetail> getClaim(Long claimId);

    @Operation(summary = "검수 무응답 · 운영자 사유 환불 편입",
            description = """
                    브랜드가 입고 뒤 검수를 끝내 하지 않아 **자동 알림이 대행 조건 횟수(기본 3회)에 닿은** 건의 출구(41 보고 4번 · 권고 1).
                    운영자는 검수를 대신하지 않는다 — 대신 소비자에게 돈을 돌려준다. 상품은 브랜드 창고에 있으므로 반품 수량에 반영하고,
                    **환불액은 서버 계산**(단가 × 수량 · 차감 없음)으로 운영자 사유 환불에 편입한다. 집행은 환불 관리(06c)에서.
                    교환이면 잡아 둔 새 옵션 재고를 되돌린다. 귀책은 바꾸지 않는다. 상세 `canRefundUnanswered` · `unansweredRefundAmount`.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "편입 — 환불 큐 id · 환불번호 · 서버 계산 금액"),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "CLAIM_STATE_CHANGED — 검수 대기가 아니거나 알림 횟수 미달")
    })
    ResponseEntity<AdminTransactionDto.DisputeAcceptResponse> refundUnanswered(
            @Parameter(hidden = true) UserPrincipal principal, Long claimId,
            AdminTransactionDto.ClaimRefundRequest request);

    @Operation(summary = "B2 반려 이의 인용",
            description = """
                    검수 반려에 대한 소비자 이의(1:1 문의)를 받아들인다 — **운영자가 실행하는 유일한 일**이다.
                    반려된 반품이 아직 반송 전(반려 보류 · 재발송 대기)일 때만. 재발송 송장이 나간 뒤는 409.

                    효과는 한 번에 일어난다.
                    - 반려를 환불로 닫는다(재발송 없음) · 귀책을 브랜드로 돌린다(`faultChangedToSeller = true`) · 반품 수량에 반영한다.
                    - **환불액은 서버 계산** — 단가 × 수량 + 통과분 환불에서 차감했던 재발송비(환원). 요청에 금액이 없다.
                    - 반려 재발송비 — 같은 박스에 재발송 대상 반려가 이 건뿐이면: 결제 전 → 요청 소멸 · **결제됨 → 결제 자동 취소** ·
                      차감됨 → 소멸하고 환불액에 가산. 다른 반려가 남아 있으면 그 건의 것이라 건드리지 않는다.
                    - **운영자 사유 환불로 편입**한다 — 돈은 환불 관리(06c)의 재확인 다이얼로그에서만 나간다.

                    기각은 버튼이 아니라 이의 문의의 답변이다(반려가 그대로 유지된다). 교환 반려 이의는 기획 확정 전이라 받지 않는다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인용 — 편입된 환불 큐 id · 환불번호 · 서버 계산 금액"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT_VALUE — 근거(detail) 누락 · 500자 초과"),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "CLAIM_STATE_CHANGED — 반려된 반품이 아니거나 이미 반송 중 · 종결")
    })
    ResponseEntity<AdminTransactionDto.DisputeAcceptResponse> acceptDispute(
            @Parameter(hidden = true) UserPrincipal principal, Long claimId,
            AdminTransactionDto.DisputeAcceptRequest request);
}
