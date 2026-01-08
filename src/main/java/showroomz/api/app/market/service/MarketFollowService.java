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
     * 마켓 팔로우 토글 (Follow/Unfollow)
     * @return true: 팔로우 성공, false: 팔로우 취소(언팔로우)
     */
    public boolean toggleFollow(String username, Long marketId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Market market = marketRepository.findById(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); // 마켓 없음 에러 재사용 혹은 전용 에러 생성

        // 이미 팔로우 중이면 삭제 (언팔로우)
        if (marketFollowRepository.existsByUserAndMarket(user, market)) {
            marketFollowRepository.deleteByUserAndMarket(user, market);
            return false;
        } else {
            // 팔로우 안 했으면 저장 (팔로우)
            MarketFollow marketFollow = new MarketFollow(user, market);
            marketFollowRepository.save(marketFollow);
            return true;
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

