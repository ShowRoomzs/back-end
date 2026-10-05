package showroomz.domain.order.type;

/** 거절 보류 상품의 보관 단계(35 설계서 1-9) — 저장하지 않는 파생값이다. */
public enum StoragePhase {
    /** 고지 2회 미만 — 보관 기한 미정. */
    NOTICE_PENDING,
    /** 보관 중. */
    STORING,
    /** 기한 경과 — 폐기할 수 있다(자동으로 폐기되지 않는다). */
    EXPIRED
}
