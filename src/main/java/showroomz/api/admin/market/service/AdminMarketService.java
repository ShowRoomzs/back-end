package showroomz.api.admin.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.admin.market.DTO.MarketAdminDto;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminMarketService {

    private final MarketRepository marketRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<AdminMarketDto.MarketResponse> getMarkets(
            AdminMarketDto.MarketSearchRequest request, Pageable pageable) {
        return marketRepository.searchAdminMarkets(
                request.getMainCategoryId(),
                request.getKeywordType(),
                request.getKeyword(),
                request.getStatus(),
                SellerStatus.APPROVED,
                pageable);
    }

    @Transactional
    public void updateMarketStatus(Long marketId, AdminMarketDto.UpdateMarketStatusRequest request) {
        Market market = marketRepository.findById(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        MarketStatus newStatus = request.getStatus();

        if (market.getStatus() == newStatus) {
            return;
        }

        market.setStatus(newStatus);

        List<Product> products = productRepository.findAllByMarket(market);

        if (newStatus == MarketStatus.SUSPENDED) {
            for (Product product : products) {
                product.setPreviousIsDisplay(product.getIsDisplay());
                product.setIsDisplay(false);
            }
        } else if (newStatus == MarketStatus.ACTIVE) {
            for (Product product : products) {
                if (product.getPreviousIsDisplay() != null) {
                    product.setIsDisplay(product.getPreviousIsDisplay());
                    product.setPreviousIsDisplay(null);
                } else {
                    product.setIsDisplay(true);
                }
            }
        }
    }

    @Transactional
    public void updateMarketAdminMemo(Long marketId, MarketAdminDto.UpdateAdminMemoRequest request) {
        Market market = marketRepository.findById(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
        market.updateAdminMemo(request.getAdminMemo());
    }
}
