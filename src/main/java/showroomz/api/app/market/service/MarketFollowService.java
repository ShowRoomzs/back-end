package showroomz.api.app.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.market.DTO.FollowingMarketResponse;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.entity.MarketFollow;
import showroomz.domain.market.type.ShopType;
import showroomz.domain.market.repository.MarketFollowRepository;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorFollow;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MarketFollowService {

    private final MarketFollowRepository marketFollowRepository;
    private final CreatorFollowRepository creatorFollowRepository;
    private final MarketRepository marketRepository;
    private final CreatorRepository creatorRepository;
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
     * 크리에이터(쇼룸) 팔로우 (찜 하기)
     */
    public void followCreator(String username, Long creatorId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Creator creator = creatorRepository.findById(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); // 추후 CREATOR_NOT_FOUND 에러코드 추가 권장

        if (!creatorFollowRepository.existsByUserAndCreator(user, creator)) {
            CreatorFollow creatorFollow = new CreatorFollow(user, creator);
            creatorFollowRepository.save(creatorFollow);
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
     * 크리에이터(쇼룸) 팔로우 취소 (찜 취소)
     */
    public void unfollowCreator(String username, Long creatorId) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Creator creator = creatorRepository.findById(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (creatorFollowRepository.existsByUserAndCreator(user, creator)) {
            creatorFollowRepository.deleteByUserAndCreator(user, creator);
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

    /**
     * 팔로우한 마켓 및 쇼룸 목록 통합 조회 (수동 병합 및 페이징)
     */
    @Transactional(readOnly = true)
    public PageResponse<FollowingMarketResponse> getFollowedMarkets(String username, PagingRequest pagingRequest) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 1. 데이터베이스에서 각각의 팔로우 전체 리스트 조회
        List<MarketFollow> marketFollows = marketFollowRepository.findByUser(user);
        List<CreatorFollow> creatorFollows = creatorFollowRepository.findByUser(user);

        // 2. 정렬을 위한 임시 래퍼 객체 리스트 생성
        List<FollowItem> combinedList = new ArrayList<>();

        // 마켓 매핑
        for (MarketFollow follow : marketFollows) {
            Market market = follow.getMarket();
            FollowingMarketResponse response = FollowingMarketResponse.builder()
                    .shopId(market.getId())
                    .shopName(market.getMarketName())
                    .shopImageUrl(market.getMarketImageUrl())
                    .shopType(ShopType.MARKET)
                    .build();
            combinedList.add(new FollowItem(response, follow.getCreatedAt()));
        }

        // 크리에이터(쇼룸) 매핑
        for (CreatorFollow follow : creatorFollows) {
            Creator creator = follow.getCreator();
            FollowingMarketResponse response = FollowingMarketResponse.builder()
                    .shopId(creator.getId())
                    .shopName(creator.getUser().getNickname())
                    .shopImageUrl(creator.getUser().getProfileImageUrl())
                    .shopType(ShopType.SHOWROOM)
                    .build();
            combinedList.add(new FollowItem(response, follow.getCreatedAt()));
        }

        // 3. 최신순(createdAt 내림차순) 정렬 수행
        combinedList.sort(Comparator.comparing(FollowItem::createdAt).reversed());

        // 4. 수동 페이징 처리 로직
        Pageable pageable = pagingRequest.toPageable();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), combinedList.size());

        List<FollowingMarketResponse> pagedContent = new ArrayList<>();
        if (start <= combinedList.size()) {
            List<FollowItem> subList = combinedList.subList(start, end);
            pagedContent = subList.stream()
                    .map(FollowItem::response)
                    .toList();
        }

        // 5. PageImpl 객체로 감싼 뒤 커스텀 PageResponse 반환
        PageImpl<FollowingMarketResponse> pageImpl =
                new PageImpl<>(pagedContent, pageable, combinedList.size());
        return new PageResponse<>(pagedContent, pageImpl);
    }

    // 내부 정렬을 돕기 위한 레코드 클래스 선언
    private record FollowItem(FollowingMarketResponse response, LocalDateTime createdAt) {
    }
}

