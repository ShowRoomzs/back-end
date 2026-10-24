package showroomz.domain.contract.type;

/**
 * 계약이 누구의 정지로 보류 중인가(1017 설계서 8-4) — 양측이 모두 정지면 {@link #BOTH}. 한쪽이 해제되면 남은 쪽으로 줄고,
 * 둘 다 풀려야 보류가 끝나 서명 기한이 다시 흐른다.
 */
public enum SanctionHoldParty {
    BRAND,
    CREATOR,
    BOTH;

    public boolean includes(SanctionHoldParty party) {
        return this == BOTH || this == party;
    }

    /** {@code party}가 풀린 뒤 남는 보류 당사자 — 없으면 null. */
    public SanctionHoldParty without(SanctionHoldParty party) {
        if (this == BOTH) {
            return party == BRAND ? CREATOR : BRAND;
        }
        return this == party ? null : this;
    }
}
