package showroomz.api.admin.transaction.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.domain.order.type.ClaimReason;
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

                    **권한:** ADMIN
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<PageResponse<AdminTransactionDto.ClaimListItem>> getClaims(Long marketId, ClaimTab tab,
                                                                              Set<ClaimType> types, ClaimReason reason,
                                                                              LocalDate from, LocalDate to,
                                                                              String keyword, PagingRequest pagingRequest);

    @Operation(summary = "반품·교환 요약", description = "파트너 11 과 같은 KPI · 탭 · 유형 건수 — 전 브랜드(또는 `marketId`).\n\n**권한:** ADMIN")
    ResponseEntity<SellerClaimSummaryResponse> getSummary(Long marketId);

    @Operation(summary = "반품·교환 상세 (06b B1)",
            description = """
                    파트너 상세와 같은 값(`claim` — 반려 6항목 · 구매확정 타이머 「정지 · 남은 N일」 · 운영자 개설 표시 포함)에 브랜드 · 귀책을 더한다.
                    `canAcceptDispute` — 반려 보류 중인 반품이면 B2 반려 이의 인용을 할 수 있다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND")
    })
    ResponseEntity<AdminTransactionDto.ClaimDetail> getClaim(Long claimId);

    @Operation(summary = "B2 반려 이의 인용",
            description = """
                    검수 반려에 대한 소비자 이의(1:1 문의)를 받아들인다 — **운영자가 실행하는 유일한 일**이다. 반려 보류(아직 반송 전)인 반품만.
                    반려를 환불로 닫고(재발송 없음 · 같은 박스에 반려 보류가 더 없으면 재발송비 청구 소멸) **운영자 사유 환불로 편입**한다 —
                    돈은 환불 관리(06c)의 재확인 다이얼로그에서만 나간다. 기각은 버튼이 아니라 스레드 답변이다(반려가 그대로 유지된다).
                    교환 반려 이의는 기획 확정 전이라 받지 않는다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인용 — 편입된 환불 큐 id"),
            @ApiResponse(responseCode = "409", description = "CLAIM_STATE_CHANGED — 반려 보류 중인 반품이 아님")
    })
    ResponseEntity<AdminTransactionDto.DisputeAcceptResponse> acceptDispute(
            @Parameter(hidden = true) UserPrincipal principal, Long claimId,
            AdminTransactionDto.DisputeAcceptRequest request);
}
