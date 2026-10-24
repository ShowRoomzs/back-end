package showroomz.domain.member.user.type;

/** 본인인증 실패 분류(57 설계서 1-2) — 포트원 {@code pgCode} 와 별개로 우리가 왜 닫았는지를 남긴다. */
public enum IdentityFailReason {
    /** 포트원이 FAILED 를 돌려줬다 — pg_code · pg_message 보존 */
    PG_FAILED,
    /** 만 14세 미만 — 개인정보는 저장하지 않는다(0-8) */
    UNDER_MIN_AGE,
    /** 같은 DI 의 활성 회원이 이미 있다(0-5) — 남의 정보라 저장하지 않는다 */
    DUPLICATE_IDENTITY,
    /** KCP 는 DI 를 항상 주므로 비면 설정 오류다(0-5) */
    DI_MISSING,
    /** 유효 시간 안에 완료되지 않았다 */
    NOT_COMPLETED
}
