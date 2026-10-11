package showroomz.domain.order.type;

/** 거절 보류 상품의 보관 단계(35 설계서 1-9) — 저장하지 않는 파생값이다. */
public enum StoragePhase {
    /** 고지 2회 미만 — 보관 기한 미정. */
    NOTICE_PENDING,
    /** 보관 중. */
    STORING,
    /** 기한 경과 — 약관 반영 후 처리. 폐기 API 는 없다(§38-1 #5 · 도메인 진입점만 보존). */
    EXPIRED
}
