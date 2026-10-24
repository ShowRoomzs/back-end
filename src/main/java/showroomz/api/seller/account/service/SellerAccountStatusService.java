package showroomz.api.seller.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.account.dto.MemberAccountStatusResponse;
import showroomz.api.seller.common.SellerMarketResolver;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.brand.entity.BrandSanction;
import showroomz.domain.member.brand.service.BrandSanctionReader;

/** 파트너센터 계정 상태 — 정지 배너 1쿼리 경로(1017 설계서 8-10). 기본정보의 {@code sanction} 블록은 호환을 위해 그대로 둔다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerAccountStatusService {

    private final SellerMarketResolver marketResolver;
    private final showroomz.domain.member.common.service.SanctionTermPolicy termPolicy;
    private final BrandSanctionReader sanctionReader;
    private final OperatorChannelService operatorChannelService;

    public MemberAccountStatusResponse getStatus(String sellerEmail) {
        return of(marketResolver.resolve(sellerEmail));
    }

    /** 홈(4-1)의 {@code account} 블록도 같은 조립을 쓴다. */
    public MemberAccountStatusResponse of(Market market) {
        MemberAccountStatusResponse.Sanction sanction = sanctionReader.openSanction(market.getId())
                .map(s -> toSanction(s, market))
                .orElse(null);
        // 탈퇴 신청 심사 중 — 파트너 홈 · 계정 탭 잠금 안내(51 설계서 1-4 · 55 계획서 P5).
        return new MemberAccountStatusResponse(market.getStatus().name(), sanction,
                sanctionReader.hasPendingWithdrawal(market.getId()));
    }

    private MemberAccountStatusResponse.Sanction toSanction(BrandSanction s, Market market) {
        return new MemberAccountStatusResponse.Sanction(s.getReason().name(), s.getReason().getLabel(),
                MemberAccountStatusResponse.blankToNull(s.getReasonDetail()), s.getSuspendedAt(), s.isPayoutHeld(),
                operatorChannelService.findMarketChannelThreadId(market).orElse(null), s.getSuspendCardId(),
                MemberAccountStatusResponse.Term.of(s.getEndsOn(), s.getSuspendedAt(), s.getAppealDeadlineAt(),
                        termPolicy.appealReviewBusinessDays()));
    }
}
