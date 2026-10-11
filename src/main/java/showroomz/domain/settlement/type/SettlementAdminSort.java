package showroomz.domain.settlement.type;

/**
 * 어드민 07a 정렬(44 어드민 설계서 7-1) — 전체 탭은 사용자가 고르고(일정 최신순 · 확정 거래액순), 상태 탭은 탭별 고정 정렬이다.
 */
public enum SettlementAdminSort {
    /** 「일정」 열(상태별 다음 날짜) 최신순 — 전체 탭 기본. */
    SCHEDULE_DESC,
    /** 확정 거래액 큰 순. */
    SALES_DESC,
    /** 정산 확인 중 탭 — 확인 마감 이른순. */
    REVIEW_DUE_ASC,
    /** 조정 협의 탭 — 합의 기한 이른순. */
    AGREEMENT_DUE_ASC,
    /** 분배 실패 탭 — 실패 경과 오래된순. */
    FAILED_AT_ASC
}
