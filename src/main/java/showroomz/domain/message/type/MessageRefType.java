package showroomz.domain.message.type;

/** 카드가 가리키는 객체 — 액션 상태는 스냅샷이 아니라 이 객체에서 읽는다(36 설계 0-3). */
public enum MessageRefType {
    CONTRACT_RESEND_REQUEST,
    CONTRACT,
    /** 계약 해지 요청 1건 — 동의 확인 카드(1021 설계서 1-6). */
    CONTRACT_TERMINATION_REQUEST,
    /** 직권 중단 통지 1건 — 사전 통지 카드(1021 설계서 2-3). */
    GROUP_BUY_ADMIN_SUSPENSION,
    /** 정산 조정 협의 — 개설 · D-1 · 결과 카드(44 이슈 스레드 설계서 5-1). */
    SETTLEMENT_ADJUSTMENT,
    /** 정산 조정 제안 — 요청 · 다른 금액 제안 · 동의 · 반대 카드. 카드의 버튼 상태를 이 행에서 읽는다. */
    SETTLEMENT_ADJUSTMENT_PROPOSAL,
    /** 계정 정지 1건 — 정지 · 해제 카드(52 설계서 0-12). 브랜드는 brand_sanction, 인플루언서는 그쪽 정지 행의 id 다. */
    MEMBER_SANCTION
}
