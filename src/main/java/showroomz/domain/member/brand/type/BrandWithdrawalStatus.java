package showroomz.domain.member.brand.type;

/** 탈퇴 신청 행 상태(52 설계서 1-3) — 반려 뒤 재신청은 새 행이다. */
public enum BrandWithdrawalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** 브랜드가 운영자 처리 전에 직접 취소(§51-5). */
    CANCELED
}
