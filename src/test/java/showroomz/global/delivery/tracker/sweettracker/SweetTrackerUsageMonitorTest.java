package showroomz.global.delivery.tracker.sweettracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.sweettracker.SweetTrackerUsageMonitor.UsageLevel;
import showroomz.global.delivery.tracker.sweettracker.dto.SweetTrackerUsageResponse;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 이용권 사용량 사전 경고(택배 추적 설계서 3-4). 응답 모양은 2026-10-05 실호출 그대로다 — 성공 응답에는 {@code status}가 없다.
 */
class SweetTrackerUsageMonitorTest {

    private static final String USAGE_URL = "https://info.sweettracker.co.kr/api/v1/key/usage";

    private MockRestServiceServer server;
    private SweetTrackerClient client;
    private DeliveryTrackerProperties properties;
    private SweetTrackerUsageMonitor monitor;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://info.sweettracker.co.kr");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SweetTrackerClient("test-key", builder.build());
        properties = new DeliveryTrackerProperties();
        monitor = new SweetTrackerUsageMonitor(client, properties);
    }

    @Test
    @DisplayName("요청 — POST /api/v1/key/usage · JSON 본문에 키만")
    void requestShape() {
        server.expect(requestTo(USAGE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"t_key\":\"test-key\"}", true))
                .andRespond(withSuccess(usage(100, 99), MediaType.APPLICATION_JSON));

        monitor.inspect();

        server.verify();
    }

    @Test
    @DisplayName("실응답(2026-10-05) 해석 — 총량 · 잔여 · 이용권 기간(1개월)")
    void parsesRealResponse() {
        respond(usage(100, 99));

        SweetTrackerUsageResponse usage = client.usage();

        assertThat(usage.isError()).isFalse();
        assertThat(usage.totalAmount()).isEqualTo(100);
        assertThat(usage.leftAmount()).isEqualTo(99);
        assertThat(usage.startDate()).isEqualTo("2026-10-05 09:32:02");
        assertThat(usage.endDate()).isEqualTo("2026-11-05 09:32:02");
    }

    @ParameterizedTest(name = "잔여 {1}/{0} → {2}")
    @CsvSource({
            "100, 100, OK",
            "100, 21, OK",
            "100, 20, OK",   // 경계 — 20% 「미만」만 경고
            "100, 19, LOW",
            "100, 0, LOW",
            "1000, 199, LOW",
            "1000, 200, OK"})
    @DisplayName("기본 기준 20% — 미만이면 LOW(error 로그)")
    void defaultThreshold(long total, long left, UsageLevel expected) {
        respond(usage(total, left));

        assertThat(monitor.inspect()).isEqualTo(expected);
    }

    @Test
    @DisplayName("기준 비율은 설정값을 따른다 — 50% 로 올리면 잔여 40% 도 LOW")
    void customThreshold() {
        properties.setUsageAlertRatio(0.5);
        respond(usage(100, 40));
        respond(usage(100, 50));

        assertThat(monitor.inspect()).isEqualTo(UsageLevel.LOW);
        assertThat(monitor.inspect()).isEqualTo(UsageLevel.OK);
    }

    @Test
    @DisplayName("업체 에러(101 등) → UNKNOWN · 예외 없음")
    void vendorError() {
        respond("{\"status\":false,\"msg\":\"발급된 고유키가 존재 하지 않음\",\"code\":\"101\"}");

        assertThat(monitor.inspect()).isEqualTo(UsageLevel.UNKNOWN);
    }

    @Test
    @DisplayName("총량 0 · 필드 누락 → UNKNOWN — 0 나누기로 터지지 않는다")
    void unreadableAmounts() {
        respond(usage(0, 0));
        respond("{\"key\":\"<KEY>\",\"leftAmount\":10}");
        respond("{\"key\":\"<KEY>\",\"totalAmount\":100}");

        assertThat(monitor.inspect()).isEqualTo(UsageLevel.UNKNOWN);
        assertThat(monitor.inspect()).isEqualTo(UsageLevel.UNKNOWN);
        assertThat(monitor.inspect()).isEqualTo(UsageLevel.UNKNOWN);
    }

    @Test
    @DisplayName("HTTP 오류 · 타임아웃 → UNKNOWN · 스케줄 진입점(check)이 예외를 던지지 않는다")
    void failuresDoNotThrow() {
        server.expect(requestTo(USAGE_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(USAGE_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(USAGE_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(monitor.inspect()).isEqualTo(UsageLevel.UNKNOWN);
        assertThat(monitor.inspect()).isEqualTo(UsageLevel.UNKNOWN);
        assertThatCode(monitor::check).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ 보조

    private void respond(String json) {
        server.expect(requestTo(USAGE_URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static String usage(long total, long left) {
        return "{\"key\":\"<KEY>\",\"totalAmount\":" + total + ",\"leftAmount\":" + left
                + ",\"startDate\":\"2026-10-05 09:32:02\",\"endDate\":\"2026-11-05 09:32:02\"}";
    }
}
