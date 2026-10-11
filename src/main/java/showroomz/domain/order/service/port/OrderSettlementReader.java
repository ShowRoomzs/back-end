package showroomz.domain.order.service.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 → 정산 읽기 포트(44 어드민 정산관리 설계서 8-3) — 주문 모듈이 정의하고 정산 모듈이 구현한다. 어드민 06a 상세 ④ 「정산 반영」 ·
 * 06c 환불 상세 「정산」 자리가 「이 주문이 어느 정산에 얼마로 들어갔는가」를 이 포트로 답한다. 주문 모듈은 정산 타입을 알지 않는다 —
 * 상태는 코드 문자열과 라벨로 받는다.
 */
public interface OrderSettlementReader {

    /** 하위주문의 정산 반영 — 정산이 아직 없으면 empty. */
    Optional<GroupSettlement> readByDeliveryGroup(Long deliveryGroupId);

    /** 환불 큐 행 기준 — 그 하위주문의 정산 번호 + 그 환불이 만든 차감(있으면). 정산이 아직 없으면 empty. */
    Optional<RefundSettlement> readByRefundTask(Long refundTaskId, Long deliveryGroupId);

    /**
     * @param settledAmount 이 하위주문 항목들의 정산 반영액 합
     * @param rewardAmount  이 하위주문 항목들의 리워드 합(항목 기준 — 합의 금액이 아니다)
     * @param clawbacks     이 하위주문에서 생긴 차감(정산 후 환불) — 없으면 빈 목록
     */
    record GroupSettlement(Long settlementId, String settlementNumber, String status, String statusLabel,
                           LocalDateTime confirmedAt, long settledAmount, long rewardAmount,
                           List<ClawbackRef> clawbacks) {
    }

    record ClawbackRef(String clawbackNumber, String side, String status, String statusLabel, long amount) {
    }

    record RefundSettlement(Long settlementId, String settlementNumber, ClawbackRef clawback) {
    }
}
