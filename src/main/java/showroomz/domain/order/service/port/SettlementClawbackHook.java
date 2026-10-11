package showroomz.domain.order.service.port;

import showroomz.domain.order.entity.OrderRefundTask;

/**
 * 환불 집행 → 정산 훅(44 어드민 정산관리 설계서 6-1 · 8-4) — 주문 모듈이 정의하고 정산 모듈이 구현한다. 환불 큐가 DONE 이 되는
 * 트랜잭션 끝에서 부른다. 이미 생성된 정산(상태 무관)의 항목이 운영자 사유 · 반품 통과 환불로 나갔으면 차감(클로백) 행이 생긴다 —
 * 이번 정산 금액은 고치지 않는다(스냅샷).
 */
public interface SettlementClawbackHook {

    void onRefundExecuted(OrderRefundTask task);
}
