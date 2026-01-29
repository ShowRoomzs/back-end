package showroomz.api.app.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.app.market.DTO.MarketDetailResponse;
import showroomz.api.app.market.DTO.MarketListResponse;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.category.entity.Category;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketFollowRepository;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserMarketService {

    private final MarketRepository marketRepository;
    private final MarketFollowRepository marketFollowRepository;
    private final UserRepository userRepository;

    public MarketDetailResponse getMarketDetail(Long marketId, String username) {
        // 1. 마켓 조회
        Market market = marketRepository.findById(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        // 2. 팔로워 수 조회
        long followerCount = marketFollowRepository.countByMarket(market);

        // 3. 현재 유저의 팔로우 여부 조회
        boolean isFollowed = false;
        if (username != null && !username.equals("anonymousUser")) {
            Users user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                isFollowed = marketFollowRepository.existsByUserAndMarket(user, market);
            }
        }

        // 4. SNS 링크 변환 (Enum -> String)
        List<MarketDetailResponse.SnsLinkResponse> snsLinks = market.getSnsLinks().stream()
                .map(sns -> new MarketDetailResponse.SnsLinkResponse(sns.getSnsType().name(), sns.getSnsUrl()))
                .collect(Collectors.toList());

        // 5. 응답 생성
        Category mainCategory = market.getMainCategory();
        return MarketDetailResponse.builder()
                .shopId(market.getId())
                .shopName(market.getMarketName())
                .shopImageUrl(market.getMarketImageUrl())
                .shopDescription(market.getMarketDescription())
                .shopUrl(market.getMarketUrl())
                .shopType(market.getShopType())
                .mainCategoryId(mainCategory != null ? mainCategory.getCategoryId() : null)
                .mainCategoryName(mainCategory != null ? mainCategory.getName() : null)
                .snsLinks(snsLinks)
                .followerCount(followerCount)
                .isFollowed(isFollowed)
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
}

