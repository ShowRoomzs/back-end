package showroomz.domain.market.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketRepository extends JpaRepository<Market, Long> {
    boolean existsByMarketName(String marketName);
    Optional<Market> findByAdmin(Seller admin);

    // Admin의 Status가 PENDING인 마켓 목록 조회 (페이징 없음)
    List<Market> findAllByAdmin_Status(SellerStatus status);
    
    // Admin의 Status가 PENDING인 마켓 목록 조회 (페이징)
    Page<Market> findAllByAdmin_Status(SellerStatus status, Pageable pageable);
}

