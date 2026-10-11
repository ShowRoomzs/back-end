package showroomz.domain.order.event;

import showroomz.domain.order.type.RefundTaskOrigin;

/**
 * 환불 큐 적재 — 커밋 뒤 {@code RefundExecutor}가 받아 PG 자동 환불을 집행한다(1009 기획 수정본 2-4). 큐를 쌓는 도메인 서비스가
 * 집행기를 직접 알지 않게 이벤트로 끊는다(집행 완료 훅이 다시 도메인 서비스를 부르므로 직접 의존하면 순환이 된다).
 */
public record RefundTaskEnqueuedEvent(Long refundTaskId, RefundTaskOrigin origin) {
}
