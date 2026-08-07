package showroomz.domain.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.product.entity.ProductProcessingHistory;
import showroomz.domain.product.type.ProductProcessingHistoryType;

import java.util.List;
import java.util.Optional;

public interface ProductProcessingHistoryRepository extends JpaRepository<ProductProcessingHistory, Long> {

    List<ProductProcessingHistory> findByProduct_ProductIdOrderByCreatedAtDesc(Long productId);

    Optional<ProductProcessingHistory> findFirstByProduct_ProductIdAndHistoryTypeOrderByCreatedAtDesc(
            Long productId,
            ProductProcessingHistoryType historyType
    );
}
