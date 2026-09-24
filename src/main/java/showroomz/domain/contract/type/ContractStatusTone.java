package showroomz.domain.contract.type;

/**
 * 상태 배지 색. 같은 상태가 파트너·스튜디오·어드민 세 화면에서 다른 색이면 안 된다(§25-2 원칙 ③).
 * FE 3개가 각자 매핑표를 들면 반드시 어긋나므로 매핑을 {@link ContractStatus} 한 곳에 둔다.
 */
public enum ContractStatusTone {
    NEUTRAL, INFO, WARNING, SUCCESS, DANGER
}
