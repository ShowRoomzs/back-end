package showroomz.api.app.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.market.DTO.MarketDetailResponse;
import showroomz.api.app.market.DTO.MarketListResponse;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.type.ShopType;
import showroomz.domain.category.entity.Category;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserMarketService {

    private final MarketRepository marketRepository;

    public MarketDetailResponse getMarketDetail(Long marketId) {
        // 1. 마켓 조회 (승인된 Shop만 조회)
        Market market = marketRepository.findByIdAndSellerStatus(marketId, SellerStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        // 2. SNS 링크 변환 (Enum -> String)
        List<MarketDetailResponse.SnsLinkResponse> snsLinks = market.getSnsLinks().stream()
                .map(sns -> new MarketDetailResponse.SnsLinkResponse(sns.getSnsType().name(), sns.getSnsUrl()))
                .collect(Collectors.toList());

        // 3. 응답 생성
        Category mainCategory = market.getMainCategory();
        return MarketDetailResponse.builder()
                .shopId(market.getId())
                .shopName(market.getMarketName())
                .shopImageUrl(market.getMarketImageUrl())
                .shopDescription(market.getMarketDescription())
                .shopUrl(market.getMarketUrl())
                .shopType(toShopType(market.getSeller().getRoleType()))
                .mainCategoryId(mainCategory != null ? mainCategory.getCategoryId() : null)
                .mainCategoryName(mainCategory != null ? mainCategory.getName() : null)
                .snsLinks(snsLinks)
                .build();
    }

    /**
     * 마켓 목록 조회 (유저용)
     */
    public PageResponse<MarketListResponse> getMarkets(Long mainCategoryId, String keyword, Pageable pageable) {
        Page<MarketListResponse> page = marketRepository.findAllForUser(
                mainCategoryId, keyword, SellerStatus.APPROVED, pageable);
        return new PageResponse<>(page.getContent(), page);
    }

    private ShopType toShopType(RoleType roleType) {
        return roleType == RoleType.CREATOR ? ShopType.SHOWROOM : ShopType.MARKET;
    }
}

