package showroomz.domain.inquiry.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 답변 소요 표기 (§23-3) — SLA 잔여가 아니라 <b>실제로 걸린 시간</b>이다 (§23-2).
 *
 * <p>단위가 바뀌는 경계(60분, 24시간)에서 문구가 바뀌므로 그 지점을 고정한다.
 * 일 단위로 넘어가면 분은 떼고 시간까지만 적는다 — "3일 2시간 17분"은 읽히지 않는다.
 */
class AnswerElapsedFormatterTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 3, 1, 9, 0);

    private String elapsedAfter(long days, long hours, long minutes) {
        return AnswerElapsedFormatter.format(FROM,
                FROM.plusDays(days).plusHours(hours).plusMinutes(minutes));
    }

    @Test
    @DisplayName("한 시간이 안 되면 분만 적는다")
    void underAnHourShowsMinutesOnly() {
        assertThat(elapsedAfter(0, 0, 42)).isEqualTo("42분");
    }

    @Test
    @DisplayName("한 시간을 넘기면 시간과 분을 함께 적는다")
    void overAnHourShowsHoursAndMinutes() {
        assertThat(elapsedAfter(0, 1, 43)).isEqualTo("1시간 43분");
    }

    @Test
    @DisplayName("하루를 넘기면 분은 떼고 일과 시간만 적는다")
    void overADayDropsMinutes() {
        assertThat(elapsedAfter(3, 2, 17)).isEqualTo("3일 2시간");
    }

    @Test
    @DisplayName("정확히 60분은 시간 단위로 넘어간다")
    void exactlyOneHourRollsOver() {
        assertThat(elapsedAfter(0, 1, 0)).isEqualTo("1시간 0분");
    }

    @Test
    @DisplayName("정확히 24시간은 일 단위로 넘어간다")
    void exactlyOneDayRollsOver() {
        assertThat(elapsedAfter(1, 0, 0)).isEqualTo("1일 0시간");
    }

    @Test
    @DisplayName("59분은 아직 분 단위다 — 경계에서 한 칸 밀리지 않는다")
    void justUnderAnHourStaysInMinutes() {
        assertThat(elapsedAfter(0, 0, 59)).isEqualTo("59분");
    }

    @Test
    @DisplayName("초 단위는 버려 0분으로 적는다")
    void secondsAreTruncated() {
        assertThat(AnswerElapsedFormatter.format(FROM, FROM.plusSeconds(59))).isEqualTo("0분");
    }

    /**
     * 답변 시각이 등록 시각보다 앞서는 조합은 정상 흐름에서는 없지만, 시계 보정이나 수동 데이터 이관으로
     * 생길 수 있다 — 그때 "-3분"이 화면에 나가는 대신 0분으로 눌러 둔다.
     */
    @Test
    @DisplayName("역순 시각은 음수 대신 0분으로 눌러 둔다")
    void negativeDurationIsClampedToZero() {
        assertThat(AnswerElapsedFormatter.format(FROM, FROM.minusHours(3))).isEqualTo("0분");
    }

    @Test
    @DisplayName("아직 답변이 없으면(시각이 비면) 표기하지 않는다")
    void nullTimestampsProduceNoText() {
        assertThat(AnswerElapsedFormatter.format(FROM, null)).isNull();
        assertThat(AnswerElapsedFormatter.format(null, FROM)).isNull();
        assertThat(AnswerElapsedFormatter.format(null, null)).isNull();
    }
}
