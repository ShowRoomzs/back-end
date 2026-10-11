package showroomz.domain.message.type;

/** 카드가 가리키는 객체 — 액션 상태는 스냅샷이 아니라 이 객체에서 읽는다(36 설계 0-3). */
public enum MessageRefType {
    CONTRACT_RESEND_REQUEST,
    CONTRACT,
    /** 정산 조정 협의 — 개설 · D-1 · 결과 카드(44 이슈 스레드 설계서 5-1). */
    SETTLEMENT_ADJUSTMENT,
    /** 정산 조정 제안 — 요청 · 다른 금액 제안 · 동의 · 반대 카드. 카드의 버튼 상태를 이 행에서 읽는다. */
    SETTLEMENT_ADJUSTMENT_PROPOSAL
}
