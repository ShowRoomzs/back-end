package showroomz.api.creator.showroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** §22-4 쇼룸 도달 카드 — 순방문(횟수) / 방문자 수(사람) / 팔로우 전환율. */
@Getter
@Schema(description = "쇼룸 도달 지표")
public class ReachStats {

    @Schema(description = "순방문 — 방문 횟수(같은 소비자의 재방문은 30분 세션 기준 1회)", example = "3180")
    private final Long visits;

    @Schema(description = "방문자 수 — 중복 제거한 사람 수", example = "2410")
    private final Long visitors;

    @Schema(description = "팔로우 전환율(%) — 기간 내 신규 팔로워 ÷ 방문자 수. 방문자가 없으면 null",
            example = "1.7", nullable = true)
    private final Double followConversionRate;

    public ReachStats(Long visits, Long visitors, Double followConversionRate) {
        this.visits = visits;
        this.visitors = visitors;
        this.followConversionRate = followConversionRate;
    }
}
