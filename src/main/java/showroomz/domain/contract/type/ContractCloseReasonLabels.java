package showroomz.domain.contract.type;

/**
 * 종결 사유 코드 → 화면 라벨.
 *
 * <p>{@code close_reason_code} 한 컬럼에 브랜드의 취소 사유({@link ContractCloseReasonCode})와
 * 인플루언서의 거절 사유({@link ContractDeclineReason})가 함께 들어간다. 어느 쪽 enum으로 읽을지는
 * {@code close_actor_type}이 정하지만(설계서 0-5), 라벨을 뽑는 쪽에서는 주체를 몰라도
 * 코드만으로 고를 수 있다 — 두 enum의 값이 하나도 겹치지 않기 때문이다.
 *
 * <p>모르는 코드에는 그럴듯한 문구를 지어내지 않고 {@code null}을 돌려준다. 만료는 사유 자체가
 * 없어(§27-6) 코드가 NULL이고, 그때도 {@code null}이다.
 */
public final class ContractCloseReasonLabels {

    private ContractCloseReasonLabels() {
    }

    public static String labelOf(String reasonCode) {
        if (reasonCode == null) {
            return null;
        }
        try {
            return ContractCloseReasonCode.valueOf(reasonCode).getLabel();
        } catch (IllegalArgumentException ignored) {
            // 브랜드 취소 사유가 아니면 인플루언서 거절 사유다.
        }
        try {
            return ContractDeclineReason.valueOf(reasonCode).getLabel();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
