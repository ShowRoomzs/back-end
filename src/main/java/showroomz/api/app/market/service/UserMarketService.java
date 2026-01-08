package showroomz.api.app.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.app.market.DTO.MarketDetailResponse;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketFollowRepository;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.error.exception.ErrorCode;

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
            Users user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            isFollowed = marketFollowRepository.existsByUserAndMarket(user, market);
        }

        // 4. 응답 생성
        return MarketDetailResponse.builder()
                .marketId(market.getId())
                .marketName(market.getMarketName())
                .marketImageUrl(market.getMarketImageUrl())
                .marketDescription(market.getMarketDescription())
                .marketUrl(market.getMarketUrl())
                .mainCategory(market.getMainCategory())
                .csNumber(market.getCsNumber())
                .snsLink1(market.getSnsLink1())
                .snsLink2(market.getSnsLink2())
                .snsLink3(market.getSnsLink3())
                .followerCount(followerCount)
                .isFollowed(isFollowed)
                .build();
    }
}

