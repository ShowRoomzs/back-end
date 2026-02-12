package showroomz.domain.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.product.entity.ProductVariant;

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
}
