package showroomz.api.app.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.entity.MarketFollow;
import showroomz.domain.market.repository.MarketFollowRepository;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional
public class MarketFollowService {

    private final MarketFollowRepository marketFollowRepository;
    private final MarketRepository marketRepository;
    private final UserRepository userRepository;

    /**
     * 마켓 팔로우 (찜 하기)
     */
    public void followMarket(String username, Long marketId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Market market = marketRepository.findById(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        // 이미 팔로우 중이 아니면 저장
        if (!marketFollowRepository.existsByUserAndMarket(user, market)) {
            MarketFollow marketFollow = new MarketFollow(user, market);
            marketFollowRepository.save(marketFollow);
        }
    }

    /**
     * 마켓 팔로우 취소 (찜 취소)
     */
    public void unfollowMarket(String username, Long marketId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Market market = marketRepository.findById(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        // 팔로우 중이면 삭제
        if (marketFollowRepository.existsByUserAndMarket(user, market)) {
            marketFollowRepository.deleteByUserAndMarket(user, market);
        }
    }
    
    /**
     * 특정 유저가 특정 마켓을 팔로우했는지 확인 (조회용)
     */
    @Transactional(readOnly = true)
    public boolean isFollowing(String username, Long marketId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Market market = marketRepository.getReferenceById(marketId);
        return marketFollowRepository.existsByUserAndMarket(user, market);
    }
}

