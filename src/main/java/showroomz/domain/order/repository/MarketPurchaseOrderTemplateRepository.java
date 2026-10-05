package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.order.entity.MarketPurchaseOrderTemplate;

import java.util.Optional;

public interface MarketPurchaseOrderTemplateRepository extends JpaRepository<MarketPurchaseOrderTemplate, Long> {

    /** 마켓당 종류별 1행 — 발주서와 재발송 목록이 한 테이블을 쓴다. */
    Optional<MarketPurchaseOrderTemplate> findByMarket_IdAndTemplateType(Long marketId, String templateType);
}
