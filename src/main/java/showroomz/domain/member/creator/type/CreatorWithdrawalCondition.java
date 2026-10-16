package showroomz.domain.member.creator.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 인플루언서 탈퇴 조건 3종(§51-5 · 스튜디오 S4) — 판정은 {@code CreatorWithdrawalEligibility} 하나다. */
@Getter
@RequiredArgsConstructor
public enum CreatorWithdrawalCondition {
    UNFINISHED_GROUP_BUYS("끝나지 않은 공구", "주문이 모두 종결되어야 합니다"),
    UNFINISHED_CONTRACTS("끝나지 않은 계약", "연결된 공구가 끝나야 합니다"),
    UNSETTLED("미정산", "모두 지급 완료되어야 합니다");

    private final String label;
    private final String resolution;
}
