package showroomz.domain.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.product.entity.ProductProcessingHistory;

import java.util.List;

public interface ProductProcessingHistoryRepository extends JpaRepository<ProductProcessingHistory, Long> {

    List<ProductProcessingHistory> findByProduct_ProductIdOrderByCreatedAtDesc(Long productId);
}
