package showroomz.domain.contract.type;

/**
 * 스튜디오 목록 정렬(§27 설계서 2-1).
 *
 * <p>파트너의 {@link ContractSortType}과 공유하지 않는다 — 기본값이 가리키는 컬럼이 다르다.
 * §27-1 #3 「생성일은 브랜드의 사정이다」가 정렬에도 그대로 적용돼, 스튜디오는 계약이
 * <b>내게 도착한</b> 시각으로 줄을 세운다.
 */
public enum CreatorContractSortType {

    /** 기본 — 받은 순({@code signature_requested_at DESC}). 파트너의 CREATED_DESC와 다른 컬럼이다. */
    RECEIVED_DESC,

    /** 내 서명 기한순({@code signature_deadline_at ASC}) · 기한 없는 종결 계약은 뒤로 보낸다. */
    DEADLINE_ASC,

    /** 공구 시작일순. */
    START_AT_ASC
}
