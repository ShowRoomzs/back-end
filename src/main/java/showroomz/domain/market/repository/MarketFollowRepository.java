package showroomz.domain.market.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import showroomz.domain.market.entity.MarketFollow;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.user.entity.Users;

@Repository
public interface MarketFollowRepository extends JpaRepository<MarketFollow, Long> {
    // 팔로우 여부 확인
    boolean existsByUserAndMarket(Users user, Market market);
    
    // 팔로우 취소 (삭제)
    void deleteByUserAndMarket(Users user, Market market);

    // 마켓의 팔로워 수 카운트
    long countByMarket(Market market);

    // 유저의 팔로잉 수 카운트
    long countByUser(Users user);
}

