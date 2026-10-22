package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.order.entity.OrderClaimFreightAdjustment;
import showroomz.domain.order.type.FreightSettledVia;

import java.util.List;

public interface OrderClaimFreightAdjustmentRepository extends JpaRepository<OrderClaimFreightAdjustment, Long> {

    List<OrderClaimFreightAdjustment> findByClaimCollectionIdOrderByIdAsc(Long claimCollectionId);

    /** 정산에 아직 반영되지 않은 공구의 운임 정산 — 추가 결제로 끝난 건은 재발송비로 이미 잡혀 뺀다(설계서 6-1). */
    List<OrderClaimFreightAdjustment> findByGroupBuyIdAndAppliedSettlementIdIsNullAndSettledViaNot(Long groupBuyId,
                                                                                                   FreightSettledVia excluded);

    List<OrderClaimFreightAdjustment> findByAppliedSettlementIdOrderByIdAsc(Long settlementId);
}
