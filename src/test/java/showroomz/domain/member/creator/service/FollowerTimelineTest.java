package showroomz.domain.member.creator.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.member.creator.entity.CreatorFollowEvent;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사건 시각 팔로워 판정(49 스튜디오 성과 설계서 0-8 · 1-6) — 「t 이전(포함) 마지막 이벤트가 FOLLOW」 한 줄이다.
 * 현재 관계로 판정하면 공구 중에 팔로우한 사람이 그 전 주문까지 「팔로워가 사줬다」로 세어진다.
 */
@DisplayName("FollowerTimeline — 사건 시각 팔로워 · 총량 · 증감")
class FollowerTimelineTest {

    private static final long CREATOR = 1L;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 8, 1, 0, 0);

    private static CreatorFollowEvent follow(long user, int day) {
        return CreatorFollowEvent.follow(CREATOR, user, T0.plusDays(day), null);
    }

    private static CreatorFollowEvent unfollow(long user, int day) {
        return CreatorFollowEvent.unfollow(CREATOR, user, T0.plusDays(day));
    }

    @Test
    @DisplayName("팔로우 전 · 팔로우 시각 · 언팔로우 뒤 · 재팔로우 뒤를 각각 판정한다 — 사용자 없음은 비팔로워")
    void judgesAtEventTime() {
        FollowerTimeline timeline = new FollowerTimeline(List.of(
                follow(10, 2), unfollow(10, 4), follow(10, 6)));

        assertThat(timeline.isFollowerAt(10L, T0.plusDays(1))).isFalse();
        assertThat(timeline.isFollowerAt(10L, T0.plusDays(2))).isTrue();     // 같은 시각은 포함
        assertThat(timeline.isFollowerAt(10L, T0.plusDays(3))).isTrue();
        assertThat(timeline.isFollowerAt(10L, T0.plusDays(5))).isFalse();
        assertThat(timeline.isFollowerAt(10L, T0.plusDays(7))).isTrue();
        assertThat(timeline.isFollowerAt(null, T0.plusDays(7))).isFalse();
        assertThat(timeline.isFollowerAt(99L, T0.plusDays(7))).isFalse();
    }

    @Test
    @DisplayName("시각 t 의 총량 · 구간 증감(FOLLOW − UNFOLLOW · 반열림) — 언팔로우 수 자체는 내지 않는다")
    void countAndDelta() {
        FollowerTimeline timeline = new FollowerTimeline(List.of(
                follow(1, 0), follow(2, 0), follow(3, 1),      // 기간 전 2명 + 기간 첫날 1명
                follow(4, 3), unfollow(2, 4), follow(5, 7)));  // 기간 안 +1 −1 · 기간 끝(7일) 이후 +1

        LocalDateTime from = T0.plusDays(1);
        LocalDateTime to = T0.plusDays(7);
        assertThat(timeline.countAt(to.minusNanos(1))).isEqualTo(3);           // 1 · 3 · 4
        assertThat(timeline.delta(from, to)).isEqualTo(1);                     // +3 +4 −2
        assertThat(timeline.delta(T0, T0.plusDays(8))).isEqualTo(4);
    }
}
