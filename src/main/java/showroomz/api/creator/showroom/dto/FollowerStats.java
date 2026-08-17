package showroomz.api.creator.showroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** §22-4 팔로워 카드 — 총 팔로워 / 기간 내 신규 / 직전 동일 기간 대비 증감률. */
@Getter
@Schema(description = "팔로워 지표")
public class FollowerStats {

    @Schema(description = "총 팔로워 — 기간과 무관한 현재 값", example = "1240")
    private final Long total;

    @Schema(description = "기간 내 신규 팔로워", example = "42")
    private final Long newFollowers;

    @Schema(description = "직전 동일 기간 대비 신규 팔로워 증감률(%) — 직전 기간이 0이면 null(비교 불가)",
            example = "3.5", nullable = true)
    private final Double changeRate;

    public FollowerStats(Long total, Long newFollowers, Double changeRate) {
        this.total = total;
        this.newFollowers = newFollowers;
        this.changeRate = changeRate;
    }
}
