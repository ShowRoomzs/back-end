package showroomz.domain.member.brand.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.brand.entity.BrandWithdrawalRequest;
import showroomz.domain.member.brand.repository.BrandWithdrawalRequestRepository;
import showroomz.domain.member.brand.type.BrandAdminEventType;
import showroomz.domain.member.brand.type.BrandWithdrawalReason;
import showroomz.domain.member.brand.type.BrandWithdrawalRejectReason;
import showroomz.domain.member.common.service.MemberNotifier;
import showroomz.domain.member.common.service.MemberSanctionRules;
import showroomz.domain.member.common.service.SuspendedWithdrawalPolicy;
import showroomz.domain.member.common.type.MemberType;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 브랜드 탈퇴 — 신청 · 승인제(§51-5 · 52 설계서 4절). 회원 상태는 승인 전까지 <b>활성 그대로</b>다 — 신청은 상태가 아니라 처리할 건이다.
 *
 * <p>신청 · 취소는 파트너센터 계정 탭({@code SellerWithdrawalService})이, 승인 · 반려는 어드민이 부른다. 신청 · 승인 모두
 * {@link BrandWithdrawalEligibility} 같은 판정식을 쓴다 — 승인은 그 시점에 다시 판정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BrandWithdrawalService {

    private final BrandSanctionService sanctionService;
    private final BrandWithdrawalRequestRepository requestRepository;
    private final BrandWithdrawalEligibility eligibility;
    private final BrandWithdrawalPurger purger;
    private final BrandAdminHistoryRecorder historyRecorder;
    private final MemberNotifier notifier;
    private final SuspendedWithdrawalPolicy suspendedWithdrawal;

    /**
     * 신청(파트너) — 조건 4종이 전부 0 이어야 한다. 정지 중 신청은 설정({@code member.sanction.allow-withdrawal-while-suspended})에
     * 따라 — 기본 허용 · 끄면 409 {@code BRAND_SUSPENDED}(55 계획서 S11 · 1021 7-3). 조건은 정지와 무관하게 그대로다.
     */
    public BrandWithdrawalRequest request(Long marketId, BrandWithdrawalReason reason, String message, LocalDateTime now) {
        Market market = sanctionService.lockBrand(marketId);
        if (market.isWithdrawn()) {
            throw new BusinessException(ErrorCode.BRAND_WITHDRAWN);
        }
        suspendedWithdrawal.requireCanRequest(MemberType.BRAND, market.isSuspended());
        if (requestRepository.existsByMarketIdAndStatus(marketId, MemberWithdrawalStatus.PENDING)) {
            throw new BusinessException(ErrorCode.BRAND_WITHDRAWAL_ALREADY_PENDING);
        }
        eligibility.requireEligible(marketId);
        String requesterName = market.getSeller().getName();
        BrandWithdrawalRequest request = requestRepository.save(
                BrandWithdrawalRequest.open(marketId, reason, MemberSanctionRules.trimToNull(message), requesterName, now));
        historyRecorder.byBrand(marketId, BrandAdminEventType.WITHDRAWAL_REQUESTED, request.getRequesterName(),
                "사유: " + request.reasonLabel(), BrandAdminHistoryRecorder.REF_WITHDRAWAL, request.getId(), now);
        return request;
    }

    /** 취소(파트너) — 운영자 처리 전까지 회원이 직접. 취소하면 대기 중 제약도 풀린다(행이 대기가 아니게 되므로). */
    public BrandWithdrawalRequest cancel(Long marketId, Long requestId, LocalDateTime now) {
        sanctionService.lockBrand(marketId);
        BrandWithdrawalRequest request = lockPending(marketId, requestId);
        request.cancel(now);
        historyRecorder.byBrand(marketId, BrandAdminEventType.WITHDRAWAL_CANCELED, request.getRequesterName(), null,
                BrandAdminHistoryRecorder.REF_WITHDRAWAL, request.getId(), now);
        return request;
    }

    /**
     * 승인(어드민 M2) — 되돌릴 수 없다. 재검증 → 신청 승인 → 열린 정지 종결 → 회원 상태 탈퇴 → 파기 · 차단 → 이력. 알림 수신처는 파기 전에 읽는다.
     */
    public BrandWithdrawalRequest approve(Long marketId, Long requestId, Long operatorId, String operatorName,
                                          LocalDateTime now) {
        Market market = sanctionService.lockBrand(marketId);
        if (market.isWithdrawn()) {
            throw new BusinessException(ErrorCode.BRAND_WITHDRAWN);
        }
        BrandWithdrawalRequest request = lockPending(marketId, requestId);
        eligibility.requireEligible(marketId);

        Seller seller = market.getSeller();
        String recipient = seller.getEmail();
        String marketName = market.getMarketName();
        request.approve(operatorId, operatorName, now);
        sanctionService.closeByWithdrawal(marketId, now);
        market.markWithdrawn();
        purger.purge(seller, market, now);
        historyRecorder.byAdmin(marketId, BrandAdminEventType.WITHDRAWAL_APPROVED, operatorId, operatorName,
                "사유: " + request.reasonLabel(), BrandAdminHistoryRecorder.REF_WITHDRAWAL, request.getId(), now);
        AfterCommit.run(() -> notifier.withdrawalApproved(MemberType.BRAND, marketId, recipient, marketName, now));
        log.info("브랜드 탈퇴 승인 - marketId: {}, requestId: {}", marketId, requestId);
        return request;
    }

    /** 반려(어드민 M3) — 회원 상태는 건드리지 않는다(내내 활성). 브랜드는 즉시 재신청할 수 있다(새 행). */
    public BrandWithdrawalRequest reject(Long marketId, Long requestId, BrandWithdrawalRejectReason reason, String note,
                                         Long operatorId, String operatorName, LocalDateTime now) {
        String normalized = MemberSanctionRules.requireDetail(reason, note,
                ErrorCode.BRAND_WITHDRAWAL_REJECT_DETAIL_REQUIRED);
        Market market = sanctionService.lockBrand(marketId);
        BrandWithdrawalRequest request = lockPending(marketId, requestId);
        request.reject(reason, normalized, operatorId, operatorName, now);
        String recipient = market.getSeller() == null ? null : market.getSeller().getEmail();
        String marketName = market.getMarketName();
        historyRecorder.byAdmin(marketId, BrandAdminEventType.WITHDRAWAL_REJECTED, operatorId, operatorName,
                MemberSanctionRules.reasonText(reason, normalized),
                BrandAdminHistoryRecorder.REF_WITHDRAWAL, request.getId(), now);
        AfterCommit.run(() -> notifier.withdrawalRejected(MemberType.BRAND, marketId, recipient, marketName,
                reason.getLabel(), normalized, now));
        return request;
    }

    private BrandWithdrawalRequest lockPending(Long marketId, Long requestId) {
        BrandWithdrawalRequest request = requestRepository.findForUpdate(requestId)
                .filter(r -> r.getMarketId().equals(marketId))
                .orElseThrow(() -> new BusinessException(ErrorCode.BRAND_WITHDRAWAL_NOT_FOUND));
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.BRAND_WITHDRAWAL_NOT_PENDING);
        }
        return request;
    }
}
