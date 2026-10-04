package showroomz.domain.order.type;

/** 취소 요청 상태 — PENDING 행의 존재가 곧 「취소 요청 탭」 오버레이다(34 설계서 0-2). */
public enum CancelRequestStatus {
    PENDING, APPROVED, REJECTED,
    /**
     * 검토 중에 소비자가 주문 전체를 직접 취소(준비 시작 전 · PG 자동)해 요청이 무의미해진 경우 — 시스템이 닫는다.
     * 브랜드의 판단(승인·거부)이 아니므로 둘 중 어느 것으로도 적지 않는다. 남겨 두면 취소 탭의 그룹에 「검토 중」이
     * 붙어 카운트와 목록이 어긋나고, 승인하면 0원 환불 큐가 생긴다.
     */
    VOIDED
}
