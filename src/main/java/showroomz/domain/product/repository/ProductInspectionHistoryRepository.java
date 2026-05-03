package showroomz.domain.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import showroomz.domain.product.entity.ProductInspectionHistory;

import java.util.List;

@Repository
public interface ProductInspectionHistoryRepository extends JpaRepository<ProductInspectionHistory, Long> {

    List<ProductInspectionHistory> findByProduct_ProductIdOrderByCreatedAtAsc(Long productId);
}
