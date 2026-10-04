package showroomz.global.delivery.tracker.sweettracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerBlockedException;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.ValidationResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 스마트택배 응답 → 포트 계약 변환(택배 추적 설계서 3-2 · 3-3). 응답 JSON 은 API 명세의 필드로 만든 것이다 —
 * 실응답과의 대조는 키 발급 후 스파이크에서 한다.
 */
class SweetTrackerDeliveryTrackerTest {

    private static final String BASE_URL = "https://info.sweettracker.co.kr";
    private static final String TRACKING_URL = BASE_URL + "/api/v1/trackingInfo";
    private static final String INVOICE = "123412341234";

    private MockRestServiceServer server;
    private DeliveryTrackerProperties properties;
    private SweetTrackerDeliveryTracker tracker;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new DeliveryTrackerProperties();
        tracker = new SweetTrackerDeliveryTracker(new SweetTrackerClient("test-key", builder.build()), properties);
    }

    @Test
    @DisplayName("추적 — 키·택배사 코드·송장번호를 폼 본문으로 보낸다(URL 에 키를 싣지 않는다)")
    void trackSendsFormBody() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("t_key", "test-key");
        form.add("t_code", "04");
        form.add("t_invoice", INVOICE);
        server.expect(requestTo(TRACKING_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(form))
                .andRespond(withSuccess("{\"level\":0,\"complete\":false,\"trackingDetails\":[]}",
                        MediaType.APPLICATION_JSON));

        tracker.track(DeliveryCarrier.CJ, INVOICE);

        server.verify();
    }

    @Test
    @DisplayName("추적 — 스캔 정보 없음(level 0)은 이벤트 없는 스냅샷이다(조회 실패가 아니다)")
    void trackNoScanYet() {
        respond("{\"level\":0,\"complete\":false,\"trackingDetails\":[]}");

        assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE))
                .contains(new TrackSnapshot(null, null, false, false));
    }

    @Test
    @DisplayName("추적 — 배송중(level 3)은 가장 늦은 이력 시각이 마지막 이벤트 · 배송완료 시각은 없다")
    void trackInTransit() {
        respond("""
                {"level":3,"complete":false,"trackingDetails":[
                  {"timeString":"2026-10-03 21:10:00","level":3,"kind":"간선상차"},
                  {"timeString":"2026-10-03 14:05:00","level":2,"kind":"집화처리"}]}""");

        assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE))
                .contains(new TrackSnapshot(kst(2026, 10, 3, 21, 10), null, false, false));
    }

    @Test
    @DisplayName("추적 — 배송완료(level 6)는 마지막 이력 시각이 배송완료 시각이다 · 반송 플래그는 항상 false")
    void trackDelivered() {
        respond("""
                {"level":6,"complete":true,"trackingDetails":[
                  {"timeString":"2026-10-03 14:05:00","level":2,"kind":"집화처리"},
                  {"timeString":"2026-10-04 13:30:00","level":6,"kind":"배달완료"}]}""");

        LocalDateTime deliveredAt = kst(2026, 10, 4, 13, 30);
        assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE))
                .contains(new TrackSnapshot(deliveredAt, deliveredAt, false, false));
    }

    @Test
    @DisplayName("추적 — 시각 문자열이 없으면 epoch 로 읽는다(밀리초·초 모두)")
    void trackFallsBackToEpoch() {
        long epochSecond = kst(2026, 10, 4, 13, 30).atZone(ZoneId.systemDefault()).toEpochSecond();
        respond("{\"level\":5,\"complete\":false,\"trackingDetails\":[{\"time\":" + epochSecond * 1000 + ",\"level\":5}]}");
        respond("{\"level\":5,\"complete\":false,\"trackingDetails\":[{\"time\":" + epochSecond + ",\"level\":5}]}");

        TrackSnapshot expected = new TrackSnapshot(kst(2026, 10, 4, 13, 30), null, false, false);
        assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).contains(expected);
        assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).contains(expected);
    }

    @Test
    @DisplayName("추적 — 104(유효하지 않은 운송장)는 이벤트 없는 스냅샷이다 · 집화 전 송장이 이 코드로 올 수 있다")
    void trackInvalidInvoiceIsNoEvent() {
        respond(error("104"));

        assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE))
                .contains(new TrackSnapshot(null, null, false, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"105", "106"})
    @DisplayName("추적 — 105(같은 송장 일 한도)·106(조회 에러)은 판정하지 않는다(empty)")
    void trackSkipsOnPerInvoiceErrors(String code) {
        respond(error(code));

        assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"101", "102", "103"})
    @DisplayName("추적 — 101·102·103(키 없음·만료·사용량 초과)은 차단 예외다 · 회차를 멈춰야 한다")
    void trackThrowsWhenKeyBlocked(String code) {
        respond(error(code));

        assertThatThrownBy(() -> tracker.track(DeliveryCarrier.CJ, INVOICE))
                .isInstanceOf(DeliveryTrackerBlockedException.class)
                .hasMessageContaining(code)
                .hasMessageNotContaining("test-key");
    }

    @Test
    @DisplayName("추적 — 5xx·해석 불가 응답은 판정하지 않는다(empty)")
    void trackSkipsOnServerFailure() {
        server.expect(requestTo(TRACKING_URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>Bad Gateway</html>")
                        .contentType(MediaType.TEXT_HTML));

        assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).isEmpty();
    }

    @Test
    @DisplayName("택배사 코드가 없으면 호출하지 않는다 — 추적 empty · 검증 UNAVAILABLE")
    void unmappedCarrierIsNotCalled() {
        properties.setValidationEnabled(true);

        assertThat(tracker.track(DeliveryCarrier.COUPANG, INVOICE)).isEmpty();
        assertThat(tracker.validateInvoice(DeliveryCarrier.COUPANG, INVOICE)).isEqualTo(ValidationResult.UNAVAILABLE);
        server.verify();
    }

    @Test
    @DisplayName("검증 — 꺼져 있으면(기본) 호출 없이 UNAVAILABLE")
    void validationDisabledByDefault() {
        assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.UNAVAILABLE);
        server.verify();
    }

    @Test
    @DisplayName("검증 — 켜면 정상 응답(level 0 포함)은 VALID · 104 는 INVALID · 그 외 에러와 장애는 UNAVAILABLE")
    void validationWhenEnabled() {
        properties.setValidationEnabled(true);
        respond("{\"level\":0,\"complete\":false,\"trackingDetails\":[]}");
        respond(error("104"));
        respond(error("105"));
        respond(error("103"));
        server.expect(requestTo(TRACKING_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.VALID);
        assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.INVALID);
        assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.UNAVAILABLE);
        assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.UNAVAILABLE);
        assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.UNAVAILABLE);
    }

    @Test
    @DisplayName("API 키가 비면 생성에서 실패한다 — 조용히 스텁처럼 돌지 않는다")
    void blankApiKeyFailsFast() {
        assertThatThrownBy(() -> new SweetTrackerClient(" ", RestClient.create()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery.tracker.api-key");
    }

    // ------------------------------------------------------------------ 보조

    private void respond(String json) {
        server.expect(requestTo(TRACKING_URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static String error(String code) {
        return "{\"status\":false,\"msg\":\"에러\",\"code\":\"" + code + "\"}";
    }

    /** 업체 시각(KST)을 서버 시간대의 LocalDateTime 으로 — 어댑터와 같은 변환. */
    private static LocalDateTime kst(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
