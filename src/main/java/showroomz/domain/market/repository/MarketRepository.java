package showroomz.domain.market.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import showroomz.api.seller.auth.entity.Seller;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketRepository extends JpaRepository<Market, Long> {
    boolean existsByMarketName(String marketName);
    Optional<Market> findByAdmin(Seller admin);

    // Admin의 Status가 PENDING인 마켓 목록 조회
    List<Market> findAllByAdmin_Status(SellerStatus status);
}

