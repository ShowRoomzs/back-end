package showroomz.api.seller.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 파트너센터 계약 API의 진입 검사.
 *
 * <p>모든 조회·조작이 <b>contract.market_id가 내 브랜드인지</b>를 먼저 본다(설계서 4).
 * 이 판정을 서비스마다 베껴 쓰면 한 곳만 빠뜨려도 남의 계약이 열린다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractAccessGuard {

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final ContractRepository contractRepository;

    public Market resolveMarket(String sellerEmail) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        return marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
    }

    /**
     * 존재하지 않으면 404, 남의 브랜드 계약이면 403.
     * 둘을 구분해 내린다 — 403이면 "있긴 한데 내 것이 아니다"가 드러나지만, 계약 ID는 추측 대상이 아니고
     * 브랜드가 자기 계약을 못 찾는 상황(404)과 권한 문제(403)는 화면이 달라야 한다.
     */
    public Contract loadOwned(Long contractId, Market market) {
        Contract contract = contractRepository.findDetailById(contractId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND));
        if (!contract.isOwnedBy(market.getId())) {
            throw new BusinessException(ErrorCode.CONTRACT_NOT_OWNED_BY_SELLER);
        }
        return contract;
    }
}
