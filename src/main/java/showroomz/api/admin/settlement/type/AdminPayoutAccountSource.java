package showroomz.api.admin.settlement.type;

/** M3 재분배의 계좌 출처(44 어드민 설계서 7-5) — 운영자는 계좌를 입력하지 않는다. */
public enum AdminPayoutAccountSource {
    /** 회원 정보(인플루언서 · 판매자)의 현재 계좌로 다시 스냅샷한다 — 회원이 계좌를 고친 뒤. */
    CURRENT_PROFILE,
    /** 지난 지시의 스냅샷 그대로 — PG 일시 장애 등 계좌와 무관한 실패. */
    PREVIOUS
}
