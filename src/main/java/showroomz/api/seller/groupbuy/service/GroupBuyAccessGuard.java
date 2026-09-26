package showroomz.api.seller.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 파트너센터 공구 API의 진입 검사 — 모든 조회·조작이 <b>group_buy.market_id가 내 브랜드인지</b>를 먼저 본다.
 * 404 / 403 구분은 계약({@code ContractAccessGuard})과 같다.
 *
 * <p>마켓 해석은 {@code MarketRepository}로 한다 — 기획 제외 패키지의 {@code MarketService}를 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupBuyAccessGuard {

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final GroupBuyRepository groupBuyRepository;

    public SellerScope resolve(String sellerEmail) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
        return new SellerScope(seller.getId(), market);
    }

    /** 조회용 — 계약·상대·브랜드를 함께 올린다. */
    public GroupBuy loadOwned(Long groupBuyId, Market market) {
        GroupBuy groupBuy = groupBuyRepository.findDetailById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        return requireOwned(groupBuy, market);
    }

    /**
     * 실행용 — 행을 {@code PESSIMISTIC_WRITE}로 잠근다. 판정(permissions)과 집행 사이에 다른 요청이 끼어들지
     * 못하게 한다. 호출자의 쓰기 트랜잭션 안에서만 부른다.
     */
    @Transactional
    public GroupBuy loadOwnedForUpdate(Long groupBuyId, Market market) {
        GroupBuy groupBuy = groupBuyRepository.findForUpdate(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        return requireOwned(groupBuy, market);
    }

    private GroupBuy requireOwned(GroupBuy groupBuy, Market market) {
        if (!groupBuy.isOwnedBy(market.getId())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_NOT_OWNED_BY_SELLER);
        }
        return groupBuy;
    }

    /** @param sellerId 실행 기록({@code stock_confirmed_by} 등)에 남기는 셀러 id */
    public record SellerScope(Long sellerId, Market market) {
    }
}
