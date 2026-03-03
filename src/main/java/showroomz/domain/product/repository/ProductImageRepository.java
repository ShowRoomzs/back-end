package showroomz.domain.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.product.entity.ProductImage;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /**
     * 상품별 대표 이미지(order=0) 일괄 조회 (Batch Fetching)
     */
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.productId IN :productIds AND pi.order = 0")
    List<ProductImage> findRepresentativeImagesByProductIdIn(@Param("productIds") List<Long> productIds);
}
