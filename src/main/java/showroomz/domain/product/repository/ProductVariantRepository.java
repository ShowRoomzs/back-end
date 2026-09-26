package showroomz.domain.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.product.entity.ProductVariant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    @Query("SELECT DISTINCT v FROM ProductVariant v " +
           "LEFT JOIN FETCH v.options " +
           "WHERE v.product.productId = :productId")
    List<ProductVariant> findByProductIdWithOptions(@Param("productId") Long productId);

    Optional<ProductVariant> findByVariantId(Long variantId);

    @Query("SELECT v FROM ProductVariant v " +
           "JOIN FETCH v.product " +
           "WHERE v.product.productId = :productId AND v.variantId IN :variantIds")
    List<ProductVariant> findByProductIdAndVariantIdIn(
            @Param("productId") Long productId,
            @Param("variantIds") List<Long> variantIds
    );

    /**
     * 상품별 재고 합계 일괄 조회 (Batch Fetching)
     * @return List of [productId, stockSum]
     */
    @Query("SELECT v.product.productId, COALESCE(SUM(v.stock), 0) FROM ProductVariant v " +
           "WHERE v.product.productId IN :productIds GROUP BY v.product.productId")
    List<Object[]> sumStockByProductIds(@Param("productIds") List<Long> productIds);

    /**
     * 상품별 재고가 남은 옵션 수 — 0이거나 행이 없으면 품절이다. C7 {@code status.isOutOfStock}과 같은 식
     * (강제 품절 ∨ 재고 있는 옵션 없음)을 페이지 단위로 판정하려고 센다(공구 게시물 설계 6-2 ③).
     *
     * @return List of [productId, inStockVariantCount]
     */
    @Query("SELECT v.product.productId, COUNT(v) FROM ProductVariant v "
            + "WHERE v.product.productId IN :productIds AND v.stock > 0 GROUP BY v.product.productId")
    List<Object[]> countInStockVariantsByProductIds(@Param("productIds") Collection<Long> productIds);
}
