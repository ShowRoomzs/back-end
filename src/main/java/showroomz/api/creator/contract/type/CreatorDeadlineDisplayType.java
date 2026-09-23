package showroomz.api.creator.contract.type;

/**
 * 목록 「내 서명 기한」 열의 <b>표시 종류</b>(§27 설계서 2-2).
 *
 * <p>이 열은 한 종류가 아니라 <b>상태 × 서명 조합</b>으로 여섯 갈래로 갈린다. FE가 이 표를
 * 복제하면 서버 판정과 어긋나므로 서버가 판정해 내린다 — 파트너가 {@code entryMode}·
 * {@code statusTone}을 서버에서 내린 것과 같은 이유다.
 *
 * <p><b>표시 문자열이 아니라 판정 결과를 내린다.</b> 「내 서명 완료」 같은 문구는 FE가 고른다.
 */
public enum CreatorDeadlineDisplayType {

    /** 기한 날짜를 그린다 — {@code deadlineAt}이 함께 온다. `SIGNING` · 내 서명 없음. */
    DEADLINE,

    /** 「내 서명 완료」 — `SIGNING`이지만 내 몫은 끝났다(S3b). */
    MY_SIGNED,

    /** 「양측 서명 완료」 — `CONCLUSION_PENDING`. */
    BOTH_SIGNED,

    /** 「서명 완료」 — `CONCLUDED`. */
    SIGNED,

    /** 「기한 경과」 — `EXPIRED`. */
    PASSED,

    /** 「—」 — `DECLINED` · `CANCELED`. 기한이 의미를 잃은 종결이다. */
    NONE
}
