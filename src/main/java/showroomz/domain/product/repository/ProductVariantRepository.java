package showroomz.domain.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.product.entity.ProductVariant;

import java.util.List;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    @Query("SELECT DISTINCT v FROM ProductVariant v " +
           "LEFT JOIN FETCH v.options " +
           "WHERE v.product.productId = :productId")
    List<ProductVariant> findByProductIdWithOptions(@Param("productId") Long productId);
}
