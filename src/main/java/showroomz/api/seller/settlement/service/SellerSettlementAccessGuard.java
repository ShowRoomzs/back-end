package showroomz.api.seller.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 파트너센터 정산 진입 검사(44 파트너 설계서 2절) — 셀러 → 마켓 해석 · 남의 마켓 정산은 <b>404</b>(존재를 알리지 않는다 · 스튜디오 ·
 * 이슈 스레드 설계서와 같은 규칙). 마켓 해석은 {@code MarketRepository}로 한다 — 기획 제외 패키지의 {@code MarketService}를 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerSettlementAccessGuard {

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final SettlementRepository settlementRepository;

    public Market resolveMarket(String sellerEmail) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        return marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
    }

    /** 내 마켓의 정산 — 공구 · 계약 · 브랜드 · 인플루언서를 함께 올린다. 남의 것 · 없음은 404. */
    public Settlement loadOwned(Long settlementId, Market market) {
        return settlementRepository.findDetailById(settlementId)
                .filter(settlement -> settlement.getMarketId().equals(market.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
    }
}
