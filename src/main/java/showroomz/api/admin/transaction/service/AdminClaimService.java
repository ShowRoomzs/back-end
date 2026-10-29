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
import showroomz.domain.order.service.port.OrderSettlementReader;
import showroomz.domain.order.type.ClaimOpenedBy;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimSort;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
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

    /** 법정 청약철회 기한 — 공급받은 날부터 3개월 · 그 사실을 안 날부터 30일(전자상거래법 제17조③). */
    private static final int LEGAL_SUPPLY_MONTHS = 3;
    private static final int LEGAL_NOTICE_DAYS = 30;
    private static final Set<ClaimStatus> BEFORE_INSPECTION = EnumSet.of(ClaimStatus.REQUESTED, ClaimStatus.COLLECTING,
            ClaimStatus.ARRIVED, ClaimStatus.RECEIVED);

    private final SellerClaimQueryService claimQueryService;
    private final OrderClaimRepository claimRepository;
    private final OrderClaimService claimService;
    private final OneToOneInquiryRepository inquiryRepository;
    private final ClaimPaymentService claimPaymentService;
    private final BusinessCalendar businessCalendar;
    private final ActOnBehalfPolicy actOnBehalfPolicy;
    private final OrderSettlementReader settlementReader;

    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionDto.ClaimListItem> getClaims(Long marketId, ClaimTab tab, Set<ClaimType> types,
                                                                     ClaimReason reason, LocalDate from, LocalDate to,
                                                                     String keyword, String marketName,
                                                                     String consumerName, ClaimSort sort,
                                                                     Long userId, boolean openOnly,
                                                                     PagingRequest paging) {
        PageResponse<SellerClaimListItem> page = claimQueryService.searchClaims(marketId, tab, types, reason, from, to,
                keyword, marketName, consumerName, sort, userId, openOnly, paging);
        Map<Long, OrderClaim> claims = claimRepository.findAllById(page.getContent().stream()
                        .map(SellerClaimListItem::claimId).toList()).stream()
                .collect(Collectors.toMap(OrderClaim::getId, Function.identity()));
        Map<Long, OneToOneInquiry> disputes = inquiryRepository.findAllById(claims.values().stream()
                        .map(OrderClaim::getDisputeInquiryId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(OneToOneInquiry::getId, Function.identity()));
        LocalDate today = KstDates.today();
        List<AdminTransactionDto.ClaimListItem> rows = page.getContent().stream().map(row -> {
            OrderClaim claim = claims.get(row.claimId());
            return new AdminTransactionDto.ClaimListItem(row, claim == null ? null : claim.getDeliveryGroup().getMarketName(),
                    claim == null ? null : claim.getFeeBearer(), claim == null ? null : feeBearerLabel(claim.getFeeBearer()),
                    claim != null && claim.isFaultChangedToSeller(),
                    claim != null && isDisputeOpen(claim, disputes.get(claim.getDisputeInquiryId())),
                    claim == null ? null : claim.getDisputedAt(),
                    // 「경과」 영업일(46 수정계획 3-1) — 파트너 elapsedDays 와 같은 기준점 · 같은 영업일 함수(검수 기한 초과와 같다).
                    row.stageEnteredAt() == null ? 0
                            : businessCalendar.businessDaysBetween(KstDates.toKstDate(row.stageEnteredAt()), today));
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
                ? businessCalendar.businessDaysBetween(KstDates.toKstDate(claim.getInspectDueAt()), KstDates.today(now)) : null;
        boolean unanswered = claimService.isInspectionUnanswered(claim, now);
        return new AdminTransactionDto.ClaimDetail(detail, claim.getOrderId(), claim.getDeliveryGroup().getId(),
                claim.getDeliveryGroup().getMarketName(), claim.getFeeBearer(),
                feeBearerLabel(claim.getFeeBearer()), acceptable,
                acceptable ? claimService.previewDisputeRefund(claimId) : null,
                unanswered, unanswered ? claimService.previewUnansweredRefund(claimId, now) : null, dispute,
                new AdminTransactionDto.InspectNotice(claim.getInspectNoticeCount(), claim.getLastInspectNoticeAt()),
                inspectOverdue, legalDeadline(claim),
                settlementReader.readByDeliveryGroup(claim.getDeliveryGroup().getId())
                        .map(s -> new AdminTransactionDto.ClaimSettlement(s.settlementNumber(), s.status(),
                                s.statusLabel(), s.confirmedAt(), isDeductedOnPass(claim)))
                        .orElse(null));
    }

    /**
     * B4 법정 청약철회 기한(46 수정계획 3-2 · 전자상거래법 제17조③) — 운영자가 대신 연 클레임만. 공급일 + 3개월과 하자 인지일 + 30일
     * 중 이른 날. 인지일은 B6 개설 때 운영자가 넣는 선택값이라 없으면 공급 기준만 쓴다.
     */
    static AdminTransactionDto.LegalDeadline legalDeadline(OrderClaim claim) {
        if (claim.getOpenedBy() != ClaimOpenedBy.OPERATOR || claim.getDeliveryGroup().getDeliveredAt() == null) {
            return null;
        }
        LocalDate supplied = KstDates.toKstDate(claim.getDeliveryGroup().getDeliveredAt());
        LocalDate supplyDeadline = supplied.plusMonths(LEGAL_SUPPLY_MONTHS);
        LocalDate noticed = claim.getDefectNoticedOn();
        LocalDate noticeDeadline = noticed == null ? null : noticed.plusDays(LEGAL_NOTICE_DAYS);
        LocalDate applied = noticeDeadline != null && noticeDeadline.isBefore(supplyDeadline) ? noticeDeadline
                : supplyDeadline;
        return new AdminTransactionDto.LegalDeadline(supplied, supplyDeadline, noticed, noticeDeadline, applied);
    }

    /**
     * 「통과 시 다음 정산에서 차감」 — 반품이고 아직 검수 판정 전이다. 통과하면 반품 환불이 집행되고, 이미 정산에 들어간 항목이라
     * 차감(SettlementClawbackService)이 생긴다. 교환 · 반려는 환불이 없어 차감도 없다.
     */
    private static boolean isDeductedOnPass(OrderClaim claim) {
        return claim.getType() == ClaimType.RETURN && BEFORE_INSPECTION.contains(claim.getStatus());
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

    /** 검수 무응답 운영자 환불 편입(41 보고 4번) — 06a B5 와 같은 편입 경로 · 집행은 환불 관리. */
    @Transactional
    public AdminTransactionDto.DisputeAcceptResponse refundUnanswered(Long adminId, Long claimId,
                                                                      AdminTransactionDto.ClaimRefundRequest request) {
        OrderClaimService.DisputeAcceptance accepted = claimService.refundUnansweredInspection(claimId, adminId,
                request.detail(), LocalDateTime.now());
        return new AdminTransactionDto.DisputeAcceptResponse(accepted.refundTaskId(),
                AdminRefundNumber.format(accepted.refundTaskId()), accepted.amount());
    }

    private static String feeBearerLabel(ClaimFeeBearer bearer) {
        return bearer == ClaimFeeBearer.SELLER ? "브랜드 귀책" : "소비자 귀책";
    }
}
