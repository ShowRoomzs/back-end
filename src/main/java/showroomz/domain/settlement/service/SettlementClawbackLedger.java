package showroomz.domain.settlement.service;

import showroomz.domain.settlement.entity.Settlement;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 정산 생성이 차감(클로백)에 묻는 것(44 어드민 설계서 2-5 · 6-2) — 측별로 이 정산에서 회수할 PENDING 합과, 생성 결과의 반영
 * (APPLIED · 이월 · 수정세금계산서). 생성 서비스는 차감 테이블을 직접 알지 않는다.
 */
public interface SettlementClawbackLedger {

    /** 이 정산이 회수할 측별 PENDING 합 — 브랜드 측은 같은 마켓, 인플루언서 측은 같은 인플루언서(0-8). */
    Pending pendingFor(Long marketId, Long creatorId);

    /** 생성 트랜잭션 안에서 — 반영분 APPLIED · 남은 금액 이월 행 · 이력 · 수정세금계산서 행. */
    void applyTo(Settlement settlement, SettlementAmounts amounts, LocalDateTime now);

    /** 하위주문에서 생긴 차감 행 — 06a ④ 「정산 반영」. */
    List<ClawbackView> findByDeliveryGroup(Long deliveryGroupId);

    /** 환불 큐 행이 만든 차감 행(측별 2행) — 06c 「정산」. */
    List<ClawbackView> findByRefundTask(Long refundTaskId);

    record Pending(long brandAmount, long rewardAmount) {
        public static final Pending NONE = new Pending(0, 0);
    }

    record ClawbackView(String clawbackNumber, String side, String status, String statusLabel, long amount) {
    }
}
