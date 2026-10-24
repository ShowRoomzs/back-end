package showroomz.api.seller.basicinfo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.basicinfo.dto.SellerBasicInfoDto;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.brand.entity.BrandWithdrawalRequest;
import showroomz.domain.member.brand.repository.BrandWithdrawalRequestRepository;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.member.brand.service.BrandWithdrawalChecklist;
import showroomz.domain.member.brand.service.BrandWithdrawalService;
import showroomz.domain.member.brand.type.BrandWithdrawalReason;
import showroomz.domain.member.common.service.SuspendedWithdrawalPolicy;
import showroomz.domain.member.common.type.MemberType;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 파트너센터 계정 탭 — 브랜드 탈퇴 신청 · 취소(§51-5 · 52 설계서 4절 · 정합 계획서 R0-1). 셀러 즉시 탈퇴는 409
 * {@code BRAND_WITHDRAWAL_REQUIRES_REQUEST}로 막혀 있다 — 이 서비스가 그 안내가 가리키는 길이다.
 *
 * <p>판정 · 신청 · 취소는 도메인 서비스({@link BrandWithdrawalService} · {@link BrandWithdrawalChecklist})를 그대로 부르고, 여기서는
 * 로그인 셀러의 마켓을 찾고 화면 응답만 조립한다. 스튜디오 계정 탭({@code CreatorAccountTabService})과 같은 모양이다.
 */
@Service
@RequiredArgsConstructor
public class SellerWithdrawalService {

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final BrandWithdrawalService withdrawalService;
    private final BrandWithdrawalChecklist checklist;
    private final BrandWithdrawalRequestRepository requests;
    private final OperatorChannelService operatorChannelService;
    private final SuspendedWithdrawalPolicy suspendedWithdrawal;

    /**
     * 계정 탭 탈퇴 블록 — ① 열린 신청 ② 조건 4행과 내역(51 설계서 4절). 내역 합계 = 판정식 건수라 「N / 4 충족」이 신청 · 승인 재검증과
     * 어긋나지 않는다. 최근 신청이 반려면 그 안내를 함께 내린다(재신청은 바로 가능). 정지 중 신청 불가(설정)면 조건과 무관하게 BLOCKED 다.
     */
    @Transactional(readOnly = true)
    public SellerBasicInfoDto.Withdrawal read(Market market) {
        Long marketId = market.getId();
        String requesterEmail = market.getSeller() == null ? null : market.getSeller().getEmail();
        BrandWithdrawalChecklist.Checklist read = checklist.read(marketId);
        List<SellerBasicInfoDto.WithdrawalCondition> conditions = read.conditions().stream()
                .map(SellerWithdrawalService::toCondition)
                .toList();
        Optional<BrandWithdrawalRequest> pending = requests
                .findFirstByMarketIdAndStatusOrderByRequestedAtDesc(marketId, MemberWithdrawalStatus.PENDING);
        Optional<BrandWithdrawalRequest> rejected = pending.isPresent() ? Optional.empty()
                : requests.findFirstByMarketIdOrderByRequestedAtDescIdDesc(marketId)
                        .filter(r -> r.getStatus() == MemberWithdrawalStatus.REJECTED);

        // 정지 중 ∧ 설정 false 면 조건과 무관하게 신청 불가 — 신청 409 와 같은 판정 · 같은 문장(55 계획서 S11).
        // 접수된 신청은 그대로라(1021 7-3) PENDING 이면 없다.
        String suspensionBlockedReason = pending.isPresent() ? null
                : suspendedWithdrawal.blockedReason(MemberType.BRAND, market.isSuspended());
        String state;
        if (pending.isPresent()) {
            state = "PENDING";
        } else {
            state = suspensionBlockedReason == null
                    && conditions.stream().allMatch(SellerBasicInfoDto.WithdrawalCondition::isSatisfied)
                    ? "AVAILABLE" : "BLOCKED";
        }

        return SellerBasicInfoDto.Withdrawal.builder()
                .state(state)
                .suspensionBlockedReason(suspensionBlockedReason)
                .satisfiedCount((int) conditions.stream().filter(SellerBasicInfoDto.WithdrawalCondition::isSatisfied).count())
                .totalCount(conditions.size())
                .conditions(conditions)
                .draftContractCount(read.draftContractCount())
                .operatorThreadId(operatorChannelService.findMarketChannelThreadId(market).orElse(null))
                .pendingRequest(pending.map(r -> SellerBasicInfoDto.PendingWithdrawal.builder()
                        .requestId(r.getId())
                        .requestedAt(r.getRequestedAt())
                        .reason(r.getReason() == null ? null : r.getReason().name())
                        .reasonLabel(r.reasonLabel())
                        .message(r.getMessage())
                        .requesterName(r.getRequesterName())
                        .requesterEmail(requesterEmail)
                        .build()).orElse(null))
                .lastRejection(rejected.map(r -> SellerBasicInfoDto.WithdrawalRejection.builder()
                        .requestId(r.getId())
                        .rejectedAt(r.getProcessedAt())
                        .reasonCode(r.getRejectReason() == null ? null : r.getRejectReason().name())
                        .reasonLabel(r.getRejectReason() == null ? null : r.getRejectReason().getLabel())
                        .note(r.getRejectNote())
                        .build()).orElse(null))
                .reasonOptions(Arrays.stream(BrandWithdrawalReason.values())
                        .map(r -> SellerBasicInfoDto.ReasonOption.builder().code(r.name()).label(r.getLabel()).build())
                        .toList())
                .build();
    }

    /** 신청 — 조건 미충족이면 409 {@code BRAND_WITHDRAWAL_CONDITION_UNMET}(응답 {@code unmet}에 조건별 건수). */
    @Transactional
    public SellerBasicInfoDto.WithdrawalRequestResponse request(String sellerEmail,
                                                                SellerBasicInfoDto.WithdrawalRequestBody body) {
        Market market = myMarket(sellerEmail);
        BrandWithdrawalRequest saved = withdrawalService.request(market.getId(),
                body == null ? null : body.getReason(), body == null ? null : body.getMessage(), LocalDateTime.now());
        return SellerBasicInfoDto.WithdrawalRequestResponse.builder()
                .requestId(saved.getId())
                .requestedAt(saved.getRequestedAt())
                .build();
    }

    /** 취소 — 운영자 처리 전까지만. 다른 마켓의 신청 id 는 404. */
    @Transactional
    public void cancel(String sellerEmail, Long requestId) {
        withdrawalService.cancel(myMarket(sellerEmail).getId(), requestId, LocalDateTime.now());
    }

    private static SellerBasicInfoDto.WithdrawalCondition toCondition(BrandWithdrawalChecklist.Condition condition) {
        return SellerBasicInfoDto.WithdrawalCondition.builder()
                .code(condition.condition().name())
                .label(condition.condition().getLabel())
                .count(condition.count())
                .satisfied(condition.satisfied())
                .target(condition.target().name())
                .breakdown(condition.breakdown().stream().map(SellerWithdrawalService::toBreakdown).toList())
                .build();
    }

    private static SellerBasicInfoDto.WithdrawalBreakdown toBreakdown(BrandWithdrawalChecklist.Breakdown row) {
        return SellerBasicInfoDto.WithdrawalBreakdown.builder()
                .key(row.key())
                .label(row.label())
                .count(row.count())
                .group(row.group().name())
                .groupLabel(row.group().getLabel())
                .action(row.action() == null ? null : row.action().name())
                .expectedDate(row.expectedDate())
                .reasonCode(row.reasonCode())
                .reasonLabel(row.reasonLabel())
                .dueNote(row.dueNote())
                .target(row.target() == null ? null : row.target().name())
                .items(row.items().stream().map(item -> SellerBasicInfoDto.WithdrawalAdjustmentItem.builder()
                        .settlementId(item.settlementId())
                        .settlementNumber(item.settlementNumber())
                        .threadId(item.threadId())
                        .deadlineAt(item.deadlineAt())
                        .turn(item.turn() == null ? null : item.turn().name())
                        .build()).toList())
                .build();
    }

    private Market myMarket(String sellerEmail) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        return marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
    }
}
