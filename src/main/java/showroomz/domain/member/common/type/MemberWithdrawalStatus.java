package showroomz.domain.member.common.type;

/**
 * 탈퇴 신청 1건의 생애 — 브랜드(52 설계서 1-3) · 인플루언서(53 설계서 1-3)가 같은 값을 쓴다. 회원 상태가 아니다 — 승인된 순간에만 회원이
 * 탈퇴 상태가 된다(§51-0). 반려 · 취소 뒤 재신청은 새 행이다.
 */
public enum MemberWithdrawalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** 회원이 운영자 처리 전에 직접 취소(§51-5). */
    CANCELED
}
