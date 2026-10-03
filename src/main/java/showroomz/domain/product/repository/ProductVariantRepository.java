package showroomz.domain.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 여러 상품의 옵션 전량 — 계약 폼 선택지·계약 옵션 행 구성·검토 요청 검증(옵션 계획서 3-1~3-3).
     * 상품 N건에 N번 쿼리하지 않도록 한 번에 읽는다. 순서는 상품 · variant id — 계약 옵션 행의 순서다.
     */
    @Query("SELECT v FROM ProductVariant v "
            + "WHERE v.product.productId IN :productIds ORDER BY v.product.productId ASC, v.variantId ASC")
    List<ProductVariant> findByProductIdsOrderByVariantId(@Param("productIds") Collection<Long> productIds);

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

    /** 옵션 재고 합계 — 어드민 공구 B4 「준비 물량」의 잔여분. 없는 옵션은 0으로 센다. */
    @Query("SELECT COALESCE(SUM(v.stock), 0) FROM ProductVariant v WHERE v.variantId IN :variantIds")
    Long sumStockByVariantIds(@Param("variantIds") Collection<Long> variantIds);

    /**
     * 상품별 재고가 남은 옵션 수 — 0이거나 행이 없으면 품절이다. C7 {@code status.isOutOfStock}과 같은 식
     * (강제 품절 ∨ 재고 있는 옵션 없음)을 페이지 단위로 판정하려고 센다(공구 게시물 설계 6-2 ③).
     *
     * @return List of [productId, inStockVariantCount]
     */
    @Query("SELECT v.product.productId, COUNT(v) FROM ProductVariant v "
            + "WHERE v.product.productId IN :productIds AND v.stock > 0 GROUP BY v.product.productId")
    List<Object[]> countInStockVariantsByProductIds(@Param("productIds") Collection<Long> productIds);

    /**
     * 재고 예약 — 주문 생성 시 차감(결제 계획서 4-3). 0행이면 재고 부족이다. 호출자는 {@code variant_id} 오름차순으로
     * 부른다 — A,B와 B,A 순으로 잠그면 데드락이다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE ProductVariant v SET v.stock = v.stock - :quantity "
            + "WHERE v.variantId = :variantId AND v.stock >= :quantity")
    int reserveStock(@Param("variantId") Long variantId, @Param("quantity") int quantity);

    /** 재고 복원 — 주문당 1회 규칙은 {@code orders.stock_released_at}이 지킨다. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE ProductVariant v SET v.stock = v.stock + :quantity WHERE v.variantId = :variantId")
    int restoreStock(@Param("variantId") Long variantId, @Param("quantity") int quantity);
}
