package showroomz.domain.product.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.type.ProductDisplayStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product>, ProductRepositoryCustom {
    Optional<Product> findByProductId(Long productId);
    Optional<Product> findByProductNumber(String productNumber);

    List<Product> findByProductIdIn(Collection<Long> productIds);

    @Query("SELECT p FROM Product p JOIN FETCH p.market m JOIN FETCH m.seller WHERE p.productId = :productId")
    Optional<Product> findByProductIdWithMarketAndSeller(@Param("productId") Long productId);

    @Query("SELECT p FROM Product p WHERE p.productId IN :productIds AND p.market.seller.id = :sellerId")
    List<Product> findAllByProductIdsAndSellerId(@Param("productIds") Collection<Long> productIds, @Param("sellerId") Long sellerId);

    /**
     * C7 상품 상세용 단건 조회.
     * 판매자 정보 탭이 셀러의 사업자 정보를 쓰므로 market과 seller까지 함께 가져온다.
     */
    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.market m " +
           "LEFT JOIN FETCH m.seller " +
           "LEFT JOIN FETCH p.category " +
           "LEFT JOIN FETCH p.productImages " +
           "WHERE p.productId = :productId")
    Optional<Product> findDetailByProductId(@Param("productId") Long productId);
    
    // 특정 마켓의 상품만 조회
    Page<Product> findByMarket_Id(Long marketId, Pageable pageable);

    List<Product> findAllByMarket(Market market);

    long countByMarket_Id(Long marketId);

    // 검색어로 상품 검색 (상품명, 상품번호, 판매자코드)
    @Query("SELECT p FROM Product p WHERE p.market.id = :marketId " +
           "AND (p.name LIKE %:searchTerm% OR p.productNumber LIKE %:searchTerm% OR p.sellerProductCode LIKE %:searchTerm%)")
    Page<Product> findByMarketIdAndSearchTerm(@Param("marketId") Long marketId, @Param("searchTerm") String searchTerm, Pageable pageable);
    
    // 카테고리로 필터링
    @Query("SELECT p FROM Product p WHERE p.market.id = :marketId AND p.category.categoryId = :categoryId")
    Page<Product> findByMarketIdAndCategoryId(@Param("marketId") Long marketId, @Param("categoryId") Long categoryId, Pageable pageable);
    
    // 등록일 범위로 필터링
    @Query("SELECT p FROM Product p WHERE p.market.id = :marketId " +
           "AND p.createdAt >= :startDate AND p.createdAt <= :endDate")
    Page<Product> findByMarketIdAndCreatedAtBetween(@Param("marketId") Long marketId, 
                                                    @Param("startDate") Instant startDate, 
                                                    @Param("endDate") Instant endDate, 
                                                    Pageable pageable);
    
    // 특정 카테고리를 사용하는 상품 조회
    @Query("SELECT p FROM Product p WHERE p.category.categoryId = :categoryId")
    List<Product> findByCategory_CategoryId(@Param("categoryId") Long categoryId);
    
    // Market별 상품 조회 (페이징, 필터링, 검색 포함)
    // 품절 상태는 variants의 stock 합계를 기반으로 계산
    // categoryIds는 상위 카테고리를 포함한 모든 하위 카테고리 ID 리스트
    // displayStatus가 null이면 전체 조회
    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN p.variants v " +
           "WHERE p.market.id = :marketId " +
           "AND (:categoryIds IS NULL OR p.category.categoryId IN :categoryIds) " +
           "AND (:displayStatus IS NULL OR p.displayStatus = :displayStatus) " +
           "AND (p.isOutOfStockForced = true OR " +
           "     (:stockStatus = 'ALL') OR " +
           "     (:stockStatus = 'OUT_OF_STOCK' AND (p.isOutOfStockForced = true OR " +
           "          (SELECT COALESCE(SUM(v2.stock), 0) FROM ProductVariant v2 WHERE v2.product = p) = 0)) OR " +
           "     (:stockStatus = 'IN_STOCK' AND p.isOutOfStockForced = false AND " +
           "          (SELECT COALESCE(SUM(v2.stock), 0) FROM ProductVariant v2 WHERE v2.product = p) > 0)) " +
           "AND (:keyword IS NULL OR :keyword = '' OR " +
           "     (:keywordType = 'productNumber' AND p.productNumber LIKE %:keyword%) OR " +
           "     (:keywordType = 'sellerProductCode' AND p.sellerProductCode LIKE %:keyword%) OR " +
           "     (:keywordType = 'name' AND p.name LIKE %:keyword%) OR " +
           "     (:keywordType IS NULL AND (p.productNumber LIKE %:keyword% OR p.sellerProductCode LIKE %:keyword% OR p.name LIKE %:keyword%)))")
    Page<Product> findByMarketIdWithFilters(
            @Param("marketId") Long marketId,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("displayStatus") ProductDisplayStatus displayStatus,
            @Param("stockStatus") String stockStatus,
            @Param("keyword") String keyword,
            @Param("keywordType") String keywordType,
            Pageable pageable
    );

    /**
     * 마켓별 대표 상품 3개 조회
     * - isRecommended=true 우선, 그 다음 최신순
     * - displayStatus=DISPLAY인 상품만
     * - 카테고리 필터링 지원
     */
    @Query("SELECT p FROM Product p " +
           "LEFT JOIN FETCH p.productImages " +
           "LEFT JOIN FETCH p.category " +
           "WHERE p.market.id = :marketId " +
           "AND p.displayStatus = :displayStatus " +
           "AND (:categoryIds IS NULL OR p.category.categoryId IN :categoryIds) " +
           "ORDER BY p.isRecommended DESC, p.createdAt DESC")
    List<Product> findTop3RepresentativeProductsByMarket(
            @Param("marketId") Long marketId,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("displayStatus") ProductDisplayStatus displayStatus,
            Pageable pageable
    );

    /**
     * 여러 마켓의 전시 중인 상품 일괄 조회 (Batch Fetching)
     * - displayStatus=DISPLAY인 상품만
     * - 마켓별 정렬: isRecommended DESC, createdAt DESC
     * - Market JOIN FETCH로 N+1 방지
     */
    @Query("SELECT p FROM Product p JOIN FETCH p.market " +
           "WHERE p.market.id IN :marketIds AND p.displayStatus = :displayStatus " +
           "ORDER BY p.market.id, p.isRecommended DESC, p.createdAt DESC")
    List<Product> findByMarketIdInAndDisplayStatus(
            @Param("marketIds") List<Long> marketIds,
            @Param("displayStatus") ProductDisplayStatus displayStatus
    );

}

