package showroomz.domain.contract.type;

/**
 * 위반의 종류 — 프로젝트 절대 규칙(§25-6)을 서버도 지킨다.
 *
 * <p>필수 미입력은 <b>에러 문구 없이 버튼 비활성</b>, 에러 문구는 <b>규칙 위반에만</b>.
 * 이 구분 없이 메시지를 전부 뿌리면 빈 폼에 빨간 문구가 8개 뜬다(설계서 2-5).
 */
public enum ContractViolationKind {
    /** 미입력 — FE는 문구를 띄우지 않고 [검토 요청] 버튼만 비활성한다. */
    REQUIRED,
    /** 규칙 위반 — FE가 해당 필드에 에러 문구를 띄운다. */
    RULE
}
