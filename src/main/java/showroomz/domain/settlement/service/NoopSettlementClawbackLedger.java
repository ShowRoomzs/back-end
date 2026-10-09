package showroomz.domain.settlement.service;

import org.springframework.stereotype.Component;
import showroomz.domain.settlement.entity.Settlement;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 차감 모듈(44 구현 계획서 단계 8) 전의 빈 구현 — 회수할 차감이 없다. 차감 서비스가 {@link SettlementClawbackLedger}를 구현하면
 * 이 클래스를 지운다.
 */
@Component
public class NoopSettlementClawbackLedger implements SettlementClawbackLedger {

    @Override
    public Pending pendingFor(Long marketId, Long creatorId) {
        return Pending.NONE;
    }

    @Override
    public void applyTo(Settlement settlement, SettlementAmounts amounts, LocalDateTime now) {
        // 차감 모듈 전 — 반영할 행이 없다.
    }

    @Override
    public List<ClawbackView> findByDeliveryGroup(Long deliveryGroupId) {
        return List.of();
    }

    @Override
    public List<ClawbackView> findByRefundTask(Long refundTaskId) {
        return List.of();
    }
}
