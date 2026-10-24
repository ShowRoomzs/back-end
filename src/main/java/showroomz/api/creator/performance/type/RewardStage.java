package showroomz.api.creator.performance.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 돈의 세 상태(49 스튜디오 성과 설계서 3-5) — 금액 대신 이 값으로 「정산 후 확정」 · 「합의 후 확정」을 말한다.
 * 숫자를 단정하는 것은 {@link #CONFIRMED}(지급 완료)뿐이다.
 */
@Getter
@RequiredArgsConstructor
public enum RewardStage {
    AFTER_SETTLEMENT("정산 후 확정"),
    AFTER_AGREEMENT("합의 후 확정"),
    CONFIRMED("확정");

    private final String label;

    public static RewardStage of(CreatorPerformanceStatus status) {
        return switch (status) {
            case COLLECTING -> AFTER_SETTLEMENT;
            case ADJUSTING -> AFTER_AGREEMENT;
            case SETTLED -> CONFIRMED;
        };
    }
}
