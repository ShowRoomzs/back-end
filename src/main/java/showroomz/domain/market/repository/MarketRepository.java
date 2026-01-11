package showroomz.domain.market.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketRepository extends JpaRepository<Market, Long> {
    boolean existsByMarketName(String marketName);
    Optional<Market> findBySeller(Seller seller);

    // Seller의 Status가 PENDING인 마켓 목록 조회 (페이징 없음)
    List<Market> findAllBySeller_Status(SellerStatus status);
    
    // Seller의 Status가 PENDING인 마켓 목록 조회 (페이징)
    Page<Market> findAllBySeller_Status(SellerStatus status, Pageable pageable);

    // 검색 조건(상태, 기간)에 따른 마켓 목록 조회
    @Query("SELECT m FROM Market m JOIN FETCH m.seller s " +
           "WHERE (:status IS NULL OR s.status = :status) " +
           "AND (:startDate IS NULL OR s.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR s.createdAt <= :endDate)")
    Page<Market> searchApplications(@Param("status") SellerStatus status,
                                    @Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate,
                                    Pageable pageable);
}

