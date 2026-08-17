package showroomz.api.creator.showroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** §22-4 팔로워 행동 카드 — 방문 로그와 팔로우 관계를 겹쳐서만 나오는 값들이다. */
@Getter
@Schema(description = "팔로워 행동 지표")
public class BehaviorStats {

    @Schema(description = "팔로워 평균 방문 횟수 — 기간 내 방문한 팔로워 기준. 방문 팔로워가 없으면 null",
            example = "2.4", nullable = true)
    private final Double averageVisitsPerFollower;

    @Schema(description = "팔로워 재방문율(%) — 기간 내 2회 이상 방문한 팔로워 비중. 방문 팔로워가 없으면 null",
            example = "38.0", nullable = true)
    private final Double followerRevisitRate;

    @Schema(description = "방문자 중 팔로워 비중(%) — 방문자가 없으면 null", example = "61.0", nullable = true)
    private final Double followerShareOfVisitors;

    public BehaviorStats(Double averageVisitsPerFollower, Double followerRevisitRate,
                         Double followerShareOfVisitors) {
        this.averageVisitsPerFollower = averageVisitsPerFollower;
        this.followerRevisitRate = followerRevisitRate;
        this.followerShareOfVisitors = followerShareOfVisitors;
    }
}
