package showroomz.domain.member.brand.type;

/**
 * 계약 내역 행에서 브랜드가 할 수 있는 일(51 설계서 4-2 · D-5) — 검토 대기는 검토 요청 취소로, 검토 반려는 삭제로 조건에서 빠진다. 서명 진행 ·
 * 체결 대기는 검토를 통과해 브랜드가 취소할 수 없다 — 운영팀 문의({@code withdrawal.operatorThreadId}).
 */
public enum BrandWithdrawalAction {
    CANCEL_REVIEW,
    DELETE,
    CONTACT_OPERATOR
}
