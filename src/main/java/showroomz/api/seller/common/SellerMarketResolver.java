package showroomz.api.seller.common;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 로그인한 판매자의 브랜드(마켓) — 1017 신규 API(홈 · 배지 · 알림 · 계정 상태)가 같은 진입 규칙을 쓴다.
 * 판매자 → 마켓을 못 찾으면 404 를 그대로 내린다(기존 {@code ContractAccessGuard#resolveMarket}과 같은 판정).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerMarketResolver {

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;

    public Market currentMarket() {
        return resolve(currentEmail());
    }

    public Market resolve(String sellerEmail) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        return marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
    }

    public static String currentEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
