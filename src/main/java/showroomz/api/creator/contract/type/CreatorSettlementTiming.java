package showroomz.api.creator.contract.type;

/**
 * 판매 리워드 정산 시점 — 계약이 정하는 값이 아니라 <b>플랫폼 고정 정책</b>이다.
 * 시안의 「정산 시점 — 공구 종료 후 정산 관리에서 확인 · 지급됩니다」가 그것이다.
 *
 * <p>계약별로 고르는 값인 {@link showroomz.domain.contract.type.FixedFeeTrigger}(고정 지급비
 * 지급 시점)와 <b>다른 축</b>이다. 같은 이름의 상수가 양쪽에 있어도 의미가 다르다.
 *
 * <p>상수 하나를 enum으로 내리는 것이 낭비처럼 보이지만 {@code platformGuaranteed}와 같은 이유다 —
 * 정책이 바뀌는 날 스튜디오 FE에 하드코딩된 문구를 찾아다니지 않아도 된다.
 */
public enum CreatorSettlementTiming {
    GROUP_BUY_ENDED
}
