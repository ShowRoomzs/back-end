package showroomz.api.admin.metrics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.admin.metrics.dto.AdminMetricsDto;
import showroomz.api.admin.metrics.service.MetricsFactsCalculator.Facts;
import showroomz.domain.metrics.repository.MetricsQueryRepository.GroupBuyLine;
import showroomz.domain.metrics.repository.MetricsQueryRepository.PaidLine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수요 탭 분해 표 「쇼룸별 전환」(시안 21 · 55 계획서 A14) — 제목 · 첫 열 · 금칙어 없는 열 이름 · <b>구매 소비자 순</b> 정렬.
 * 전환율 순이던 옛 정렬과 갈리도록 「구매 소비자는 많고 전환율은 낮은」 쇼룸을 둔다.
 */
@DisplayName("AdminMetricsQueryService — 쇼룸별 전환 분해 표")
class AdminMetricsShowroomConversionTest {

    private static final LocalDateTime FROM = LocalDateTime.parse("2026-10-01T00:00:00");

    @Test
    @DisplayName("구매 소비자 순 — 전환율이 높아도 구매 소비자가 적으면 아래다 · 같은 수면 인플루언서 id 순")
    void sortsByBuyers() {
        Facts f = new Facts(FROM, FROM.plusDays(7));
        List<PaidLine> lines = new ArrayList<>();
        buy(lines, 1L, 101L, 102L, 103L);   // 하린 — 구매 소비자 3 · 팔로워 9,800 · 전환 1
        buy(lines, 2L, 201L, 202L);         // 글로우 — 구매 소비자 2 · 팔로워 10 · 전환 2(20%)
        buy(lines, 4L, 401L);               // 소연 — 구매 소비자 1 · 팔로워 0
        buy(lines, 3L, 301L);               // 민재 — 구매 소비자 1 · 팔로워 50 · 전환 1
        f.lines = lines;
        f.followers = Map.of(1L, 9_800L, 2L, 10L, 3L, 50L);
        f.convertedByCreator.putAll(Map.of(1L, 1L, 2L, 2L, 3L, 1L));
        f.groupBuys = List.of(groupBuy(1L, "하린_뷰티랩"), groupBuy(2L, "글로우_지민"), groupBuy(3L, "민재_셀렉트"),
                groupBuy(4L, "소연_쇼룸"));

        AdminMetricsDto.Breakdown table = AdminMetricsQueryService.showroomConversion(f);

        assertThat(table.title()).isEqualTo("쇼룸별 전환 · 구매 소비자 순 · 상위 5곳");
        assertThat(table.columns()).containsExactly("인플루언서(쇼룸)", "팔로워", "구매 소비자", "전환율");
        assertThat(table.columns()).noneMatch(column -> column.contains("구매자"));
        assertThat(table.rows()).extracting(row -> row.get(0))
                .containsExactly("하린_뷰티랩", "글로우_지민", "민재_셀렉트", "소연_쇼룸");
        assertThat(table.rows().get(0)).containsExactly("하린_뷰티랩", "9800", "3", "0.0%");
        assertThat(table.rows().get(1)).containsExactly("글로우_지민", "10", "2", "20.0%");
        assertThat(table.rows().get(3)).containsExactly("소연_쇼룸", "0", "1", "—");
    }

    @Test
    @DisplayName("상위 5곳까지만 — 여섯째 쇼룸은 빠진다")
    void limitsToFive() {
        Facts f = new Facts(FROM, FROM.plusDays(7));
        List<PaidLine> lines = new ArrayList<>();
        for (long creatorId = 1; creatorId <= 6; creatorId++) {
            long[] users = new long[(int) creatorId];
            for (int i = 0; i < users.length; i++) {
                users[i] = creatorId * 100 + i;
            }
            buy(lines, creatorId, users);
        }
        f.lines = lines;
        f.groupBuys = List.of();

        AdminMetricsDto.Breakdown table = AdminMetricsQueryService.showroomConversion(f);

        assertThat(table.rows()).extracting(row -> row.get(2)).containsExactly("6", "5", "4", "3", "2");
        assertThat(table.rows().get(0).get(0)).isEqualTo("#6"); // 이름을 모르면 id
    }

    private static void buy(List<PaidLine> lines, long creatorId, long... userIds) {
        for (long userId : userIds) {
            lines.add(new PaidLine(userId * 10, userId, FROM.plusHours(1), creatorId * 1000, creatorId, 1L, 10_000, 1,
                    false));
        }
    }

    private static GroupBuyLine groupBuy(long creatorId, String creatorName) {
        return new GroupBuyLine(creatorId * 1000, creatorId, 1L, FROM.minusDays(3), FROM.plusDays(4), "IN_PROGRESS",
                FROM.minusDays(10), FROM.minusDays(3), creatorName + " 공구", "글로우랩", creatorName, 0);
    }
}
