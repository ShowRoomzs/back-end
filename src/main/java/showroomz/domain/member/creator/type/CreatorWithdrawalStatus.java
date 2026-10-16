package showroomz.domain.member.creator.type;

/** 탈퇴 신청 1건의 생애(53 설계서 1-3). 회원 상태가 아니다 — 승인된 순간에만 {@link CreatorStatus#WITHDRAWN}이 된다. */
public enum CreatorWithdrawalStatus {
    PENDING, APPROVED, REJECTED, CANCELED
}
