package showroomz.domain.groupbuy.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuySaleState;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("공구 판매 상태 · D-day — 저장하지 않고 파생한다(공구 게시물 설계 4-3 · 4-4)")
class GroupBuySaleStateTest {

    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 9, 29, 23, 59, 59);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 26, 15, 0);

    @Test
    @DisplayName("품절 없음 → ON_SALE · 일부 → PARTIALLY_SOLD_OUT · 전부 → SOLD_OUT")
    void soldOutStates() {
        GroupBuy selling = groupBuy(GroupBuyStatus.IN_PROGRESS, END_AT);

        assertThat(GroupBuySaleState.of(selling, List.of(false, false), NOW)).isEqualTo(GroupBuySaleState.ON_SALE);
        assertThat(GroupBuySaleState.of(selling, List.of(true, false), NOW))
                .isEqualTo(GroupBuySaleState.PARTIALLY_SOLD_OUT);
        assertThat(GroupBuySaleState.of(selling, List.of(true, true), NOW)).isEqualTo(GroupBuySaleState.SOLD_OUT);
    }

    @Test
    @DisplayName("종결 상태면 품절 여부와 무관하게 CLOSED")
    void terminalIsClosed() {
        GroupBuy ended = groupBuy(GroupBuyStatus.ENDED, END_AT);

        assertThat(GroupBuySaleState.of(ended, List.of(true, true), NOW)).isEqualTo(GroupBuySaleState.CLOSED);
    }

    @Test
    @DisplayName("end_at이 지났는데 아직 IN_PROGRESS(스케줄러 지연 최대 1분)여도 CLOSED — 읽는 쪽 보정")
    void endAtPassedIsClosed() {
        GroupBuy lagging = groupBuy(GroupBuyStatus.IN_PROGRESS, END_AT);

        assertThat(GroupBuySaleState.of(lagging, List.of(false), END_AT)).isEqualTo(GroupBuySaleState.CLOSED);
        assertThat(GroupBuySaleState.of(lagging, List.of(false), END_AT.minusSeconds(1)))
                .isEqualTo(GroupBuySaleState.ON_SALE);
        assertThat(lagging.isOngoing(END_AT)).isFalse();
    }

    @Test
    @DisplayName("중단 예정은 아직 판매 중이다")
    void suspensionScheduledIsStillSelling() {
        GroupBuy scheduled = groupBuy(GroupBuyStatus.SUSPENSION_SCHEDULED, END_AT);

        assertThat(GroupBuySaleState.of(scheduled, List.of(false), NOW)).isEqualTo(GroupBuySaleState.ON_SALE);
        assertThat(scheduled.isOngoing(NOW)).isTrue();
    }

    @Test
    @DisplayName("D-day는 KST 날짜 차이다 — 마감 당일 0, 전날 23:59에도 1")
    void dDayByDate() {
        assertThat(GroupBuyPostCardLoader.dDay(END_AT, NOW)).isEqualTo(3);
        assertThat(GroupBuyPostCardLoader.dDay(END_AT, LocalDateTime.of(2026, 9, 28, 23, 59, 59))).isEqualTo(1);
        assertThat(GroupBuyPostCardLoader.dDay(END_AT, LocalDateTime.of(2026, 9, 29, 0, 0))).isZero();
    }

    private static GroupBuy groupBuy(GroupBuyStatus status, LocalDateTime endAt) {
        return GroupBuy.builder()
                .id(1L).status(status)
                .startAt(endAt.minusDays(10)).endAt(endAt)
                .build();
    }
}
