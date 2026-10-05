package showroomz.domain.order.type;

/**
 * 소비자에게 보이는 클레임 단계(앱 클레임 설계서 4-2) — 저장 상태 + 유형 + 재발송비 정산 상태에서 유도한다.
 * 유도 규칙은 {@code UserClaimPresenter} 한 곳에만 있다. 주문 내역 · 주문 상세 · 반품·교환 상세가 같은 값을 쓴다.
 */
public enum UserClaimPhase {
    /** 교환 재발송비 결제 대기 — 아직 접수 전이라 어느 화면에도 진행으로 보이지 않는다. */
    PAYMENT_PENDING,
    REQUESTED,
    COLLECTING,
    INSPECTING,
    /** 반품 검수 통과 — 환불 대기. */
    APPROVED,
    RESHIP_PREPARING,
    RESHIPPING,
    /** 환불 완료 · 교환 완료. */
    DONE,
    /** 거절은 났는데 같은 박스의 나머지 판정이 안 끝났다 — 결제 필요 여부가 아직 정해지지 않았다. */
    REJECTED_WAITING,
    /** 재발송 배송비 결제 필요 — 고객이 해야 할 일이 있다. */
    REJECTED_PAY,
    REJECTED_PREPARING,
    REJECTED_RESHIPPING,
    REJECTED_DONE,
    REJECTED_DISPOSED,
    CANCELLED;

    /** 검수 반려 단계 — 반려된 상품은 고객에게 돌아오는 물건이라 행을 탈색하지 않는다(C10 설계서 1-1). */
    public boolean isRejectedStage() {
        return this == REJECTED_WAITING || this == REJECTED_PAY || this == REJECTED_PREPARING
                || this == REJECTED_RESHIPPING || this == REJECTED_DONE || this == REJECTED_DISPOSED;
    }
}
