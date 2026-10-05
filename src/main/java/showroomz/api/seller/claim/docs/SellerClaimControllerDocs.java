package showroomz.api.seller.claim.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.time.LocalDate;
import java.util.Set;

@Tag(name = "Seller - Claim", description = "파트너센터 반품·교환 관리 API — 소비자가 신청한 반품·교환을 회수 · 검수 · 재발송까지 따라간다(§35).")
public interface SellerClaimControllerDocs {

    @Operation(
            summary = "반품·교환 목록",
            description = """
                    탭별 반품·교환 목록. 행은 **클레임(주문 항목 단위)** 이고, 한 박스로 같이 온 클레임은
                    `collection.size > 1` 로 묶어 그린다(「`collection.leadClaimNumber` 외 N건」). 같은 묶음이 인접하도록 정렬된다.

                    **권한:** SELLER (CREATOR 는 403)

                    **탭(`tab`)** — 생략 시 `COLLECT_WAIT`. 탭이 조건과 기본 정렬을 소유한다.
                    - `ALL` 전체(신청 최신순) · `COLLECT_WAIT` 회수 대기(신청 오래된순) · `COLLECTING` 회수 중
                    - `INSPECTION` 입고·검수(도착 오래된순) · `RESHIP` 재발송 대기 · `REJECT_HOLD` 거절 보류
                    - `DONE` 완료(환불 대기 · 재발송 중 · 종결 — 최신순)

                    **필터**
                    - `types` 유형(복수) · `reason` 사유
                    - `from` · `to` — **신청일시** 기준. 생략 시 최근 30일 · 최대 1년
                    - `keyword` — 접수번호(`CLM-3021`) 또는 주문번호. 한 입력으로 둘 다 받는다

                    **행의 값**
                    - 라벨 · 버튼 가능 여부(`actions`) · 기한 초과(`overdue`) · 보관 기한(`storage`)은 서버가 계산해 내린다
                    - `shipLabel` 은 재발송·거절 보류 단계에서 **실제로 보낼(보관 중인) 물건**이다 — 교환 재발송은 새 옵션,
                      거절은 원래 옵션
                    - `storage` 는 거절 보류만. `storageDueAt` 은 미결제 고지가 2회 쌓여야 생긴다(최종 고지일 + 3개월)
                    - `orderItems` 는 그 하위주문의 항목 전체다(행 확장) — 신청하지 않은 항목은 `claimedQuantity = 0`
                    - 소비자 연락처는 목록에 없다 — 상세에 마스킹해서만 내린다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "ORDER_SEARCH_RANGE_EXCEEDED — 기간 1년 초과 · "
                    + "INVALID_INPUT — 시작일 > 종료일 · `size` 범위 밖 · 정의되지 않은 enum 값",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SELLER 가 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<SellerClaimListItem>> getClaims(
            @Parameter(description = "탭 — 생략 시 COLLECT_WAIT", example = "COLLECT_WAIT")
            @RequestParam(value = "tab", required = false) ClaimTab tab,
            @Parameter(description = "유형 — 복수 선택. 생략 시 전체", example = "RETURN")
            @RequestParam(value = "types", required = false) Set<ClaimType> types,
            @Parameter(description = "사유", example = "CHANGE_OF_MIND")
            @RequestParam(value = "reason", required = false) ClaimReason reason,
            @Parameter(description = "신청일 시작 — 생략 시 종료일 30일 전", example = "2026-09-05")
            @RequestParam(value = "from", required = false) LocalDate from,
            @Parameter(description = "신청일 종료 — 생략 시 오늘", example = "2026-10-05")
            @RequestParam(value = "to", required = false) LocalDate to,
            @Parameter(description = "접수번호(CLM-3021) 또는 주문번호", example = "CLM-3021")
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "반품·교환 요약 (KPI · 탭 카운트)",
            description = """
                    KPI 4칸 + 탭 카운트 7종 + 유형 카운트를 한 응답으로 내린다.

                    **권한:** SELLER

                    - 전부 **검색 조건·기간과 무관한 전체 기준**이다 — 목록에 검색어를 넣어도 이 숫자는 변하지 않는다
                    - `tabCounts.ALL` 은 여섯 탭의 합이고, 대탭 「반품·교환 관리 N」의 숫자다
                    - `kpi.overdue` = 회수 대기 방치(신청 + 2영업일 경과) + 검수 기한 경과. 0이면 경고 톤을 뺀다
                    - 재발송 배송비 결제를 기다리는 교환 요청(아직 접수 전)은 어디에도 세지 않는다
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<SellerClaimSummaryResponse> getSummary();

    @Operation(
            summary = "반품·교환 상세",
            description = """
                    상세 모달. 목록 행과 같은 값은 `summary` 에 있고, 상세에서만 보이는 것을 더한다. 단계에 해당하지 않는 블록은 null 이다.

                    **권한:** SELLER (내 마켓 클레임만 — 남의 마켓 것은 404)

                    - `consumerPhone` 은 마스킹된 값이다(`010-****-4412`). **재발송 수취 정보(원문)는 여기 없다** — 재발송 목록
                      엑셀에만 나간다
                    - `refund`(반품만) — 환불 예정액은 **요청(박스) 단위**다. `itemAmount`(이 항목의 상품 금액)와
                      `requestDeduction`(요청의 배송비 차감 — 요청당 한 번) · `requestExpectedAmount`(요청 예정액)를 따로 내린다.
                      묶음의 항목마다 같은 차감을 반복해 그리지 않는다
                    - `result`(완료 단계만) — 교환 완료면 `confirmDueAt`(구매확정 재시작 예정), 거절 종결이면
                      `rejectionEnd`(`RETURNED` 반송 완료 · `DISPOSED` 폐기)
                    - `notices`(거절 보류만) — 미결제 고지 회차. 보관 기한은 `summary.storage`
                    - `history` — 처리 이력 최신순
                    - `actions` — 버튼 노출의 정본은 서버다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND — 없는 클레임이거나 내 마켓 것이 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimDetailResponse> getClaim(
            @Parameter(description = "클레임 id", example = "3021") @PathVariable Long claimId);
}
