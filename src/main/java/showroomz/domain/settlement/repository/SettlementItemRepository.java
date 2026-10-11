package showroomz.domain.settlement.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.settlement.entity.SettlementItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

    /** 명세 — 주문번호 내림차순 · 같은 주문은 항목 id 순(44 어드민 설계서 7-8). */
    @Query(value = "SELECT i FROM SettlementItem i WHERE i.settlementId = :settlementId "
            + "ORDER BY i.orderNumber DESC, i.id ASC",
            countQuery = "SELECT COUNT(i) FROM SettlementItem i WHERE i.settlementId = :settlementId")
    Page<SettlementItem> findPageBySettlementId(@Param("settlementId") Long settlementId, Pageable pageable);

    @Query("SELECT i FROM SettlementItem i WHERE i.settlementId = :settlementId ORDER BY i.orderNumber DESC, i.id ASC")
    List<SettlementItem> findAllBySettlementId(@Param("settlementId") Long settlementId);

    long countBySettlementId(Long settlementId);

    Optional<SettlementItem> findByOrderProductId(Long orderProductId);

    List<SettlementItem> findByOrderProductIdIn(Collection<Long> orderProductIds);

    List<SettlementItem> findByDeliveryGroupId(Long deliveryGroupId);

    List<SettlementItem> findBySettlementIdIn(Collection<Long> settlementIds);

    /** 정산별 리워드율 — [settlementId, rate, productName]. 목록 「리워드율」 열(상품별로 다르면 「상품별」). */
    @Query("SELECT DISTINCT i.settlementId, i.rewardRate, i.productName FROM SettlementItem i "
            + "WHERE i.settlementId IN :settlementIds")
    List<Object[]> findRewardRates(@Param("settlementIds") Collection<Long> settlementIds);

    /** 명세 합계 — [결제금 합, 정산 반영액 합, 항목 리워드 합, 하위주문 수]. 07b 명세 tfoot · 개요 「주문 n건」. */
    @Query("SELECT COALESCE(SUM(i.paidAmount), 0), COALESCE(SUM(i.settledAmount), 0), COALESCE(SUM(i.rewardAmount), 0), "
            + "COUNT(DISTINCT i.deliveryGroupId) FROM SettlementItem i WHERE i.settlementId = :settlementId")
    List<Object[]> totalsOf(@Param("settlementId") Long settlementId);
}
