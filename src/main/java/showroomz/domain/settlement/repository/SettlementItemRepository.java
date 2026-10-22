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

    /**
     * 성과 목록 · 요약(48 파트너 설계서 7-2) — 정산별 [정산 id, 하위주문 수, 반영 수량 합]. 정산은 공구의 전 하위주문이 종결된 뒤에 생기므로
     * 명세의 하위주문 수 = 공구 주문 수다.
     */
    @Query("SELECT i.settlementId, COUNT(DISTINCT i.deliveryGroupId), COALESCE(SUM(i.settledQuantity), 0) "
            + "FROM SettlementItem i WHERE i.settlementId IN :settlementIds GROUP BY i.settlementId")
    List<Object[]> aggregateBySettlement(@Param("settlementIds") Collection<Long> settlementIds);

    /** 정산별 [정산 id, 하위주문 수] — 주어진 명세 상태의 항목이 하나라도 있는 하위주문(구매확정 하위주문 = 확정 · 부분 반품). */
    @Query("SELECT i.settlementId, COUNT(DISTINCT i.deliveryGroupId) FROM SettlementItem i "
            + "WHERE i.settlementId IN :settlementIds AND i.status IN :statuses GROUP BY i.settlementId")
    List<Object[]> countGroupsByStatus(@Param("settlementIds") Collection<Long> settlementIds,
                                       @Param("statuses") Collection<showroomz.domain.settlement.type.SettlementItemStatus> statuses);

    /**
     * 정산별 구매확정 소비자 [정산 id, 소비자 id, 성별] — 중복 제거. 분포만 내고 식별값은 집계 밖으로 나가지 않는다(§47-2).
     */
    @Query("SELECT DISTINCT i.settlementId, i.consumerUserId, u.gender FROM SettlementItem i, Users u "
            + "WHERE u.id = i.consumerUserId AND i.settlementId IN :settlementIds AND i.status IN :statuses")
    List<Object[]> findBuyersBySettlement(@Param("settlementIds") Collection<Long> settlementIds,
                                          @Param("statuses") Collection<showroomz.domain.settlement.type.SettlementItemStatus> statuses);

    /** 재계산(1021 설계서 6-3) — 항목을 다시 만들기 전에 지운다. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("DELETE FROM SettlementItem i WHERE i.settlementId = :settlementId")
    int deleteAllBySettlementId(@Param("settlementId") Long settlementId);
}
