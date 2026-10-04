package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.order.entity.MarketPurchaseOrderTemplate;

import java.util.Optional;

public interface MarketPurchaseOrderTemplateRepository extends JpaRepository<MarketPurchaseOrderTemplate, Long> {

    Optional<MarketPurchaseOrderTemplate> findByMarket_Id(Long marketId);
}
