package showroomz.api.admin.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.api.seller.claim.service.SellerClaimQueryService;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 어드민 반품·교환(06b · 1009 기획 수정본 8-3) — 파트너 11 과 <b>같은 탭 · 같은 건수 · 같은 행</b>에 어드민 전용 열(브랜드 · 귀책)만
 * 더한다. 운영자가 실행하는 일은 반려 이의 인용(운영자 사유 환불 편입) 하나다 — 회수 · 입고 · 검수 · 재발송은 소비자와 브랜드의 일이다.
 */
@Service
@RequiredArgsConstructor
public class AdminClaimService {

    private final SellerClaimQueryService claimQueryService;
    private final OrderClaimRepository claimRepository;
    private final OrderClaimService claimService;

    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionDto.ClaimListItem> getClaims(Long marketId, ClaimTab tab, Set<ClaimType> types,
                                                                     ClaimReason reason, LocalDate from, LocalDate to,
                                                                     String keyword, PagingRequest paging) {
        PageResponse<SellerClaimListItem> page = claimQueryService.searchClaims(marketId, tab, types, reason, from, to,
                keyword, paging);
        Map<Long, OrderClaim> claims = claimRepository.findAllById(page.getContent().stream()
                        .map(SellerClaimListItem::claimId).toList()).stream()
                .collect(Collectors.toMap(OrderClaim::getId, Function.identity()));
        List<AdminTransactionDto.ClaimListItem> rows = page.getContent().stream().map(row -> {
            OrderClaim claim = claims.get(row.claimId());
            return new AdminTransactionDto.ClaimListItem(row, claim == null ? null : claim.getDeliveryGroup().getMarketName(),
                    claim == null ? null : claim.getFeeBearer(), claim == null ? null : feeBearerLabel(claim.getFeeBearer()),
                    claim != null && claim.isFaultChangedToSeller());
        }).toList();
        // 같은 쪽수 정보로 다시 싼다 — 행만 어드민 열을 더한 것으로 바꾼다.
        return new PageResponse<>(rows, new PageImpl<>(rows, PageRequest.of(Math.max(page.getPageInfo().getCurrentPage() - 1, 0),
                Math.max(page.getPageInfo().getLimit(), 1)), page.getPageInfo().getTotalResults()));
    }

    @Transactional(readOnly = true)
    public SellerClaimSummaryResponse getSummary(Long marketId) {
        return claimQueryService.summarize(marketId);
    }

    @Transactional(readOnly = true)
    public AdminTransactionDto.ClaimDetail getClaim(Long claimId) {
        SellerClaimDetailResponse detail = claimQueryService.getClaimForAdmin(claimId);
        OrderClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        return new AdminTransactionDto.ClaimDetail(detail, claim.getDeliveryGroup().getMarketName(), claim.getFeeBearer(),
                feeBearerLabel(claim.getFeeBearer()),
                claim.getType() == ClaimType.RETURN && claim.getStatus() == ClaimStatus.REJECT_HOLD);
    }

    /** B2 반려 이의 인용 — 운영자 사유 환불로 편입하고 반려를 환불로 닫는다(재발송 없음). 돈은 환불 관리에서 나간다. */
    @Transactional
    public AdminTransactionDto.DisputeAcceptResponse acceptDispute(Long adminId, Long claimId,
                                                                   AdminTransactionDto.DisputeAcceptRequest request) {
        return new AdminTransactionDto.DisputeAcceptResponse(claimService.acceptRejectionDispute(claimId, adminId,
                request.amount(), request.detail(), LocalDateTime.now()));
    }

    private static String feeBearerLabel(ClaimFeeBearer bearer) {
        return bearer == ClaimFeeBearer.SELLER ? "브랜드 귀책" : "소비자 귀책";
    }
}
