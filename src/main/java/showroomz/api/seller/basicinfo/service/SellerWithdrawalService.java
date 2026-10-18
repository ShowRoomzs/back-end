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
import showroomz.domain.member.brand.service.BrandWithdrawalEligibility;
import showroomz.domain.member.brand.service.BrandWithdrawalService;
import showroomz.domain.member.brand.type.BrandWithdrawalCondition;
import showroomz.domain.member.brand.type.BrandWithdrawalReason;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 파트너센터 계정 탭 — 브랜드 탈퇴 신청 · 취소(§51-5 · 52 설계서 4절 · 정합 계획서 R0-1). 셀러 즉시 탈퇴는 409
 * {@code BRAND_WITHDRAWAL_REQUIRES_REQUEST}로 막혀 있다 — 이 서비스가 그 안내가 가리키는 길이다.
 *
 * <p>판정 · 신청 · 취소는 도메인 서비스({@link BrandWithdrawalService} · {@link BrandWithdrawalEligibility})를 그대로 부르고, 여기서는
 * 로그인 셀러의 마켓을 찾고 화면 응답만 조립한다. 스튜디오 계정 탭({@code CreatorAccountTabService})과 같은 모양이다.
 */
@Service
@RequiredArgsConstructor
public class SellerWithdrawalService {

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final BrandWithdrawalService withdrawalService;
    private final BrandWithdrawalEligibility eligibility;
    private final BrandWithdrawalRequestRepository requests;

    /** 계정 탭 탈퇴 블록 — ① 열린 신청 ② 조건 판정식. 최근 신청이 반려면 그 안내를 함께 내린다(재신청은 바로 가능). */
    @Transactional(readOnly = true)
    public SellerBasicInfoDto.Withdrawal read(Market market) {
        Long marketId = market.getId();
        String requesterEmail = market.getSeller() == null ? null : market.getSeller().getEmail();
        Map<BrandWithdrawalCondition, Long> counts = eligibility.evaluate(marketId);
        List<SellerBasicInfoDto.WithdrawalCondition> conditions = counts.entrySet().stream()
                .map(e -> SellerBasicInfoDto.WithdrawalCondition.builder()
                        .code(e.getKey().name())
                        .label(e.getKey().getLabel())
                        .count(e.getValue())
                        .satisfied(e.getValue() == 0)
                        .build())
                .toList();
        Optional<BrandWithdrawalRequest> pending = requests
                .findFirstByMarketIdAndStatusOrderByRequestedAtDesc(marketId, MemberWithdrawalStatus.PENDING);
        Optional<BrandWithdrawalRequest> rejected = pending.isPresent() ? Optional.empty()
                : requests.findFirstByMarketIdOrderByRequestedAtDescIdDesc(marketId)
                        .filter(r -> r.getStatus() == MemberWithdrawalStatus.REJECTED);

        String state;
        if (pending.isPresent()) {
            state = "PENDING";
        } else {
            state = conditions.stream().allMatch(SellerBasicInfoDto.WithdrawalCondition::isSatisfied)
                    ? "AVAILABLE" : "BLOCKED";
        }

        return SellerBasicInfoDto.Withdrawal.builder()
                .state(state)
                .satisfiedCount((int) conditions.stream().filter(SellerBasicInfoDto.WithdrawalCondition::isSatisfied).count())
                .totalCount(conditions.size())
                .conditions(conditions)
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

    private Market myMarket(String sellerEmail) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        return marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
    }
}
