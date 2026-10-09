package showroomz.api.admin.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.transaction.AdminRefundNumber;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.app.claim.service.ClaimPaymentService;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.api.seller.claim.service.SellerClaimQueryService;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
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
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final OneToOneInquiryRepository inquiryRepository;
    private final ClaimPaymentService claimPaymentService;
    private final BusinessCalendar businessCalendar;

    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionDto.ClaimListItem> getClaims(Long marketId, ClaimTab tab, Set<ClaimType> types,
                                                                     ClaimReason reason, LocalDate from, LocalDate to,
                                                                     String keyword, PagingRequest paging) {
        PageResponse<SellerClaimListItem> page = claimQueryService.searchClaims(marketId, tab, types, reason, from, to,
                keyword, paging);
        Map<Long, OrderClaim> claims = claimRepository.findAllById(page.getContent().stream()
                        .map(SellerClaimListItem::claimId).toList()).stream()
                .collect(Collectors.toMap(OrderClaim::getId, Function.identity()));
        Map<Long, OneToOneInquiry> disputes = inquiryRepository.findAllById(claims.values().stream()
                        .map(OrderClaim::getDisputeInquiryId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(OneToOneInquiry::getId, Function.identity()));
        List<AdminTransactionDto.ClaimListItem> rows = page.getContent().stream().map(row -> {
            OrderClaim claim = claims.get(row.claimId());
            return new AdminTransactionDto.ClaimListItem(row, claim == null ? null : claim.getDeliveryGroup().getMarketName(),
                    claim == null ? null : claim.getFeeBearer(), claim == null ? null : feeBearerLabel(claim.getFeeBearer()),
                    claim != null && claim.isFaultChangedToSeller(),
                    claim != null && isDisputeOpen(claim, disputes.get(claim.getDisputeInquiryId())),
                    claim == null ? null : claim.getDisputedAt());
        }).toList();
        // 같은 쪽수 정보로 다시 싼다 — 행만 어드민 열을 더한 것으로 바꾼다.
        return new PageResponse<>(rows, new PageImpl<>(rows, PageRequest.of(Math.max(page.getPageInfo().getCurrentPage() - 1, 0),
                Math.max(page.getPageInfo().getLimit(), 1)), page.getPageInfo().getTotalResults()));
    }

    @Transactional(readOnly = true)
    public AdminTransactionDto.ClaimSummary getSummary(Long marketId) {
        SellerClaimSummaryResponse base = claimQueryService.summarize(marketId);
        return AdminTransactionDto.ClaimSummary.of(base, claimRepository.countOpenDisputes(marketId));
    }

    @Transactional(readOnly = true)
    public AdminTransactionDto.ClaimDetail getClaim(Long claimId) {
        SellerClaimDetailResponse detail = claimQueryService.getClaimForAdmin(claimId);
        OrderClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
        AdminTransactionDto.Dispute dispute = claim.getDisputeInquiryId() == null ? null
                : inquiryRepository.findById(claim.getDisputeInquiryId()).map(AdminClaimService::toDispute).orElse(null);
        boolean acceptable = OrderClaimService.isDisputeAcceptable(claim);
        LocalDateTime now = LocalDateTime.now();
        Integer inspectOverdue = claim.getStatus() == ClaimStatus.RECEIVED && claim.getInspectDueAt() != null
                && claim.getInspectDueAt().isBefore(now)
                ? businessCalendar.businessDaysBetween(claim.getInspectDueAt().toLocalDate(), now.toLocalDate()) : null;
        return new AdminTransactionDto.ClaimDetail(detail, claim.getDeliveryGroup().getMarketName(), claim.getFeeBearer(),
                feeBearerLabel(claim.getFeeBearer()), acceptable,
                acceptable ? claimService.previewDisputeRefund(claimId) : null, dispute,
                new AdminTransactionDto.InspectNotice(claim.getInspectNoticeCount(), claim.getLastInspectNoticeAt()),
                inspectOverdue);
    }

    /** 미처리 이의 — 반려 보류 중이고 걸린 문의가 답변 전. 요약 {@code countOpenDisputes}와 같은 식이다. */
    private static boolean isDisputeOpen(OrderClaim claim, OneToOneInquiry inquiry) {
        return claim.getStatus() == ClaimStatus.REJECT_HOLD && inquiry != null && !inquiry.isAnswered();
    }

    private static AdminTransactionDto.Dispute toDispute(OneToOneInquiry inquiry) {
        // 지연 로딩 컬렉션이라 트랜잭션 안에서 복사한다.
        return new AdminTransactionDto.Dispute(inquiry.getId(), inquiry.getContent(), List.copyOf(inquiry.getImageUrls()),
                inquiry.getCreatedAt(), inquiry.isAnswered(), inquiry.getAnsweredAt());
    }

    /**
     * B2 반려 이의 인용 — 운영자 사유 환불로 편입하고 반려를 환불로 닫는다(재발송 없음). 돈은 환불 관리에서 나간다.
     * 트랜잭션 밖이다 — 결제된 재발송비의 포트원 취소는 인용이 커밋된 뒤에 한다(실패하면 정리 배치가 재시도 · 교환 철회와 같은 길).
     */
    public AdminTransactionDto.DisputeAcceptResponse acceptDispute(Long adminId, Long claimId,
                                                                   AdminTransactionDto.DisputeAcceptRequest request) {
        OrderClaimService.DisputeAcceptance accepted = claimService.acceptRejectionDispute(claimId, adminId,
                request.detail(), LocalDateTime.now());
        claimPaymentService.cancelRequested();
        return new AdminTransactionDto.DisputeAcceptResponse(accepted.refundTaskId(),
                AdminRefundNumber.format(accepted.refundTaskId()), accepted.amount());
    }

    private static String feeBearerLabel(ClaimFeeBearer bearer) {
        return bearer == ClaimFeeBearer.SELLER ? "브랜드 귀책" : "소비자 귀책";
    }
}
