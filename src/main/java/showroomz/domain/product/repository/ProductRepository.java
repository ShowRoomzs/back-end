package showroomz.domain.product.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.product.entity.Product;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByProductId(Long productId);
    Optional<Product> findByProductNumber(String productNumber);
    
    // 특정 마켓의 상품만 조회
    Page<Product> findByMarket_Id(Long marketId, Pageable pageable);
    
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
    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN p.variants v " +
           "WHERE p.market.id = :marketId " +
           "AND (:categoryId IS NULL OR p.category.categoryId = :categoryId) " +
           "AND (:displayStatus = 'ALL' OR " +
           "     (:displayStatus = 'DISPLAY' AND p.isDisplay = true) OR " +
           "     (:displayStatus = 'HIDDEN' AND p.isDisplay = false)) " +
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
            @Param("categoryId") Long categoryId,
            @Param("displayStatus") String displayStatus,
            @Param("stockStatus") String stockStatus,
            @Param("keyword") String keyword,
            @Param("keywordType") String keywordType,
            Pageable pageable
    );
}

