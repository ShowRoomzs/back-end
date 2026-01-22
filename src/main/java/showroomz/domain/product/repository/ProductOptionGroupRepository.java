package showroomz.domain.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.product.entity.ProductOptionGroup;

import java.util.List;

@Repository
public interface ProductOptionGroupRepository extends JpaRepository<ProductOptionGroup, Long> {
    @Query("SELECT DISTINCT og FROM ProductOptionGroup og " +
           "LEFT JOIN FETCH og.options " +
           "WHERE og.product.productId = :productId")
    List<ProductOptionGroup> findByProductIdWithOptions(@Param("productId") Long productId);
}
