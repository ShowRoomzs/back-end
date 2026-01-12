package showroomz.domain.market.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketRepository extends JpaRepository<Market, Long> {
    boolean existsByMarketName(String marketName);
    
    /**
     * REJECTED 상태가 아닌 판매자의 마켓명 존재 여부 확인
     * 반려된 계정의 마켓명은 재사용 가능하도록 제외
     */
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Market m " +
           "JOIN m.seller s WHERE m.marketName = :marketName AND s.status != :rejectedStatus")
    boolean existsByMarketNameAndSellerStatusNotRejected(@Param("marketName") String marketName,
                                                          @Param("rejectedStatus") SellerStatus rejectedStatus);
    
    Optional<Market> findBySeller(Seller seller);

    // Seller의 Status가 PENDING인 마켓 목록 조회 (페이징 없음)
    List<Market> findAllBySeller_Status(SellerStatus status);
    
    // Seller의 Status가 PENDING인 마켓 목록 조회 (페이징)
    Page<Market> findAllBySeller_Status(SellerStatus status, Pageable pageable);

    // 검색 조건(상태, 기간, 키워드)에 따른 마켓 목록 조회
    @Query("SELECT m FROM Market m JOIN FETCH m.seller s " +
           "WHERE (:status IS NULL OR s.status = :status) " +
           "AND (:startDate IS NULL OR s.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR s.createdAt <= :endDate) " +
           "AND (:keyword IS NULL OR :keyword = '' OR " +
           "    (:keywordType = 'SELLER_ID'    AND CAST(s.id AS string) LIKE CONCAT('%', :keyword, '%')) OR " +
           "    (:keywordType = 'MARKET_NAME'  AND m.marketName LIKE CONCAT('%', :keyword, '%')) OR " +
           "    (:keywordType = 'NAME'         AND s.name LIKE CONCAT('%', :keyword, '%')) OR " +
           "    (:keywordType = 'PHONE_NUMBER' AND s.phoneNumber LIKE CONCAT('%', :keyword, '%'))" +
           ")")
    Page<Market> searchApplications(@Param("status") SellerStatus status,
                                    @Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate,
                                    @Param("keyword") String keyword,
                                    @Param("keywordType") String keywordType,
                                    Pageable pageable);

    /**
     * 어드민 마켓 목록 조회
     * - 마켓명, 카테고리 필터링
     * - 등록된 상품 수(productCount) 포함
     * - 승인된(APPROVED) 판매자만 조회
     */
    @Query("SELECT new showroomz.api.admin.market.DTO.AdminMarketDto$MarketResponse(" +
           "m.id, m.marketName, m.mainCategory, s.name, s.phoneNumber, " +
           "(SELECT COUNT(p) FROM Product p WHERE p.market = m), " +
           "s.createdAt) " +
           "FROM Market m JOIN m.seller s " +
           "WHERE s.status = :approvedStatus " +
           "AND (:mainCategory IS NULL OR :mainCategory = '' OR m.mainCategory = :mainCategory) " +
           "AND (:marketName IS NULL OR :marketName = '' OR m.marketName LIKE CONCAT('%', :marketName, '%'))")
    Page<AdminMarketDto.MarketResponse> findMarketsWithProductCount(
            @Param("mainCategory") String mainCategory,
            @Param("marketName") String marketName,
            @Param("approvedStatus") SellerStatus approvedStatus,
            Pageable pageable);
}

