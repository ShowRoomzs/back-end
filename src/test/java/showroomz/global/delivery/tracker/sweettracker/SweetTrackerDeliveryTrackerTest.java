package showroomz.global.delivery.tracker.sweettracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerBlockedException;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.ValidationResult;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 스마트택배 응답 → 포트 계약 변환(택배 추적 설계서 2절 · 3-2 · 3-3 · 3-5).
 *
 * <p>요청 형식·에러 응답·데이터 없는 송장의 응답은 2026-10-05 실호출로 확인한 모양이다. 배송 이력이 있는 응답
 * ({@code trackingDetails}의 {@code timeString} 형식 · {@code time} 단위)은 아직 실데이터로 못 봤다 — API 명세의 필드로 만든다.
 */
class SweetTrackerDeliveryTrackerTest {

    private static final String BASE_URL = "https://info.sweettracker.co.kr";
    private static final String TRACKING_URL = BASE_URL + "/api/v1/trackingInfo";
    private static final String API_KEY = "test-key";
    private static final String INVOICE = "123412341234";

    private MockRestServiceServer server;
    private DeliveryTrackerProperties properties;
    private SweetTrackerDeliveryTracker tracker;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new DeliveryTrackerProperties();
        tracker = new SweetTrackerDeliveryTracker(new SweetTrackerClient(API_KEY, builder.build()), properties);
    }

    @Nested
    @DisplayName("요청")
    class Request {

        @Test
        @DisplayName("POST /api/v1/trackingInfo · JSON 본문에 키·택배사 코드·송장번호만 · JSON 응답을 요청한다")
        void sendsJsonBody() {
            server.expect(requestTo(TRACKING_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(content().json(
                            "{\"t_key\":\"" + API_KEY + "\",\"t_code\":\"04\",\"t_invoice\":\"" + INVOICE + "\"}", true))
                    .andRespond(withSuccess(noData(), MediaType.APPLICATION_JSON));

            tracker.track(DeliveryCarrier.CJ, INVOICE);

            server.verify();
        }

        @Test
        @DisplayName("키를 URL 에 싣지 않는다 — 쿼리 문자열이 없다(통신 오류 메시지·접근 로그에 섞이지 않게)")
        void keyIsNotInUrl() {
            server.expect(request -> assertThat(request.getURI().getRawQuery()).isNull())
                    .andRespond(withSuccess(noData(), MediaType.APPLICATION_JSON));

            tracker.track(DeliveryCarrier.CJ, INVOICE);

            server.verify();
        }

        @ParameterizedTest(name = "{0} → t_code {1}")
        @CsvSource({
                "CJ, 04", "LOTTE, 08", "HANJIN, 05", "EPOST, 01", "KYUNGDONG, 23",
                "DAESIN, 22", "LOGEN, 06", "HAPDONG, 32", "WOORI, 45", "CU, 46"})
        @DisplayName("택배사 코드 — companylist 대조값(2026-10-05)이 그대로 나간다")
        void sendsCarrierCode(DeliveryCarrier carrier, String code) {
            server.expect(jsonPath("$.t_code").value(code))
                    .andRespond(withSuccess(noData(), MediaType.APPLICATION_JSON));

            tracker.track(carrier, INVOICE);

            server.verify();
        }

        @Test
        @DisplayName("쿠팡택배는 스마트택배 미지원(코드 없음) — 추적·검증 모두 호출하지 않는다(추적 empty · 검증 UNAVAILABLE)")
        void unsupportedCarrierIsNotCalled() {
            properties.setValidationEnabled(true);
            server.expect(never(), anything());

            assertThat(tracker.track(DeliveryCarrier.COUPANG, INVOICE)).isEmpty();
            assertThat(tracker.validateInvoice(DeliveryCarrier.COUPANG, INVOICE))
                    .isEqualTo(ValidationResult.UNAVAILABLE);
            server.verify();
        }
    }

    @Nested
    @DisplayName("추적 — 정상 응답")
    class TrackSuccess {

        @Test
        @DisplayName("실응답(2026-10-05) — 데이터 없는 송장은 level 0 · 이력 없음 → 이벤트 없는 스냅샷(조회 실패가 아니다)")
        void realNoDataResponse() {
            respond("""
                    {"adUrl":null,"complete":false,"invoiceNo":"657606146365","itemImage":null,"itemName":"","level":0,
                     "receiverAddr":"","receiverName":"","recipient":"","result":"N","senderName":"","orderNumber":null,
                     "estimate":null,"productInfo":null,"zipCode":null,"completeYN":"N"}""");

            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE))).contains(noEvent());
        }

        @Test
        @DisplayName("level 0 · 이력 빈 배열도 이벤트 없음")
        void levelZeroWithEmptyDetails() {
            respond("{\"level\":0,\"complete\":false,\"trackingDetails\":[]}");

            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE))).contains(noEvent());
        }

        @ParameterizedTest(name = "level {0}")
        @ValueSource(ints = {1, 2, 3, 4, 5})
        @DisplayName("level 1~5(준비 ~ 배송 출발) — 마지막 이벤트 시각만 · 배송완료 시각 없음")
        void inProgressLevels(int level) {
            respond(details(level, false, detail("2026-10-03 14:05:00", 2), detail("2026-10-03 21:10:00", level)));

            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE)))
                    .contains(new TrackSnapshot(kst(2026, 10, 3, 21, 10), null, false, false));
        }

        @Test
        @DisplayName("level 6 · complete=true — 마지막 이벤트 시각이 곧 배송완료 시각")
        void delivered() {
            respond(details(6, true, detail("2026-10-03 14:05:00", 2), detail("2026-10-04 13:30:00", 6)));

            LocalDateTime deliveredAt = kst(2026, 10, 4, 13, 30);
            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE)))
                    .contains(new TrackSnapshot(deliveredAt, deliveredAt, false, false));
        }

        @Test
        @DisplayName("complete=true 인데 level 이 6 이 아니어도 배송완료로 본다 — 둘 중 하나만 맞아도 완료")
        void completeFlagAloneMeansDelivered() {
            respond(details(5, true, detail("2026-10-04 13:30:00", 5)));

            LocalDateTime at = kst(2026, 10, 4, 13, 30);
            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE))).contains(new TrackSnapshot(at, at, false, false));
        }

        @Test
        @DisplayName("level 6 인데 complete=false 여도 배송완료로 본다")
        void levelSixAloneMeansDelivered() {
            respond(details(6, false, detail("2026-10-04 13:30:00", 6)));

            LocalDateTime at = kst(2026, 10, 4, 13, 30);
            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE))).contains(new TrackSnapshot(at, at, false, false));
        }

        @Test
        @DisplayName("배송완료라도 이벤트 시각이 하나도 없으면 배송완료 시각을 만들지 않는다 — 구매확정 기점을 지어내지 않는다")
        void deliveredWithoutAnyTimeIsNoEvent() {
            respond("{\"level\":6,\"complete\":true,\"trackingDetails\":[{\"level\":6,\"kind\":\"배달완료\"}]}");

            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE))).contains(noEvent());
        }

        @Test
        @DisplayName("스캔 이력 — 시간순으로 세우고 위치·문구는 원문 그대로, 시각을 못 읽는 줄은 뺀다")
        void scanEvents() {
            respond("""
                    {"level":4,"complete":false,"trackingDetails":[
                      {"timeString":"2026-10-04 06:40:00","level":4,"kind":"간선하차","where":"곤지암Hub"},
                      {"timeString":"","time":0,"level":3,"kind":"간선상차","where":"서울강남"},
                      {"timeString":"2026-10-03 14:05:00","level":2,"kind":"집화처리","where":"서울강남"}]}""");

            TrackSnapshot snapshot = tracker.track(DeliveryCarrier.CJ, INVOICE).orElseThrow();

            assertThat(snapshot.level()).isEqualTo(4);
            assertThat(snapshot.events()).containsExactly(
                    new TrackEvent(kst(2026, 10, 3, 14, 5), "서울강남", "집화처리", 2),
                    new TrackEvent(kst(2026, 10, 4, 6, 40), "곤지암Hub", "간선하차", 4));
        }

        @Test
        @DisplayName("이력 순서를 믿지 않는다 — 배열 순서와 무관하게 가장 늦은 시각")
        void latestRegardlessOfOrder() {
            respond(details(4, false,
                    detail("2026-10-03 21:10:00", 3),
                    detail("2026-10-04 06:40:00", 4),
                    detail("2026-10-03 14:05:00", 2)));

            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE)))
                    .contains(new TrackSnapshot(kst(2026, 10, 4, 6, 40), null, false, false));
        }

        @Test
        @DisplayName("lastDetail 이 이력보다 늦으면 lastDetail 시각을 쓴다")
        void lastDetailCanBeLatest() {
            respond("""
                    {"level":5,"complete":false,
                     "trackingDetails":[{"timeString":"2026-10-04 06:40:00","level":4}],
                     "lastDetail":{"timeString":"2026-10-04 09:15:00","level":5}}""");

            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE)))
                    .contains(new TrackSnapshot(kst(2026, 10, 4, 9, 15), null, false, false));
        }

        @Test
        @DisplayName("이력 배열이 없고 lastDetail 만 있어도 그 시각을 쓴다")
        void lastDetailWithoutDetails() {
            respond("{\"level\":3,\"complete\":false,\"lastDetail\":{\"timeString\":\"2026-10-04 09:15:00\",\"level\":3}}");

            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE)))
                    .contains(new TrackSnapshot(kst(2026, 10, 4, 9, 15), null, false, false));
        }

        @Test
        @DisplayName("반송 문구가 있어도 반송 플래그는 항상 false — 1차는 반송 자동 감지를 하지 않는다(설계서 3-5)")
        void returnIsNeverDetected() {
            respond(details(3, false,
                    detail("2026-10-03 14:05:00", 2, "집화처리"),
                    detail("2026-10-04 10:00:00", 3, "반송출고")));

            assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).hasValueSatisfying(snapshot -> {
                assertThat(snapshot.returnDetected()).isFalse();
                assertThat(snapshot.returnCompleted()).isFalse();
            });
        }

        @Test
        @DisplayName("응답의 모르는 필드는 무시한다 — 업체가 필드를 더해도 깨지지 않는다")
        void ignoresUnknownFields() {
            respond("""
                    {"level":3,"complete":false,"someNewField":{"nested":true},
                     "trackingDetails":[{"timeString":"2026-10-04 09:15:00","level":3,"manName":"김기사","telno":"02-000",
                                         "where":"서울","code":"30","newDetailField":1}]}""");

            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE)))
                    .contains(new TrackSnapshot(kst(2026, 10, 4, 9, 15), null, false, false));
        }
    }

    @Nested
    @DisplayName("추적 — 이벤트 시각 해석(KST → 서버 시간대)")
    class EventTime {

        @Test
        @DisplayName("timeString 「yyyy-MM-dd HH:mm:ss」")
        void secondsFormat() {
            respond(details(3, false, detail("2026-10-04 09:15:42", 3)));

            assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).hasValueSatisfying(snapshot ->
                    assertThat(snapshot.lastEventAt()).isEqualTo(kst(2026, 10, 4, 9, 15).plusSeconds(42)));
        }

        @Test
        @DisplayName("timeString 「yyyy-MM-dd HH:mm」 · 앞뒤 공백 허용")
        void minutesFormatWithSpaces() {
            respond(details(3, false, detail("  2026-10-04 09:15  ", 3)));

            assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).hasValueSatisfying(snapshot ->
                    assertThat(snapshot.lastEventAt()).isEqualTo(kst(2026, 10, 4, 9, 15)));
        }

        @Test
        @DisplayName("timeString 이 없으면 time(epoch) — 밀리초·초 단위를 크기로 가른다")
        void epochMillisAndSeconds() {
            long epochSecond = kst(2026, 10, 4, 13, 30).atZone(ZoneId.systemDefault()).toEpochSecond();
            respond("{\"level\":5,\"complete\":false,\"trackingDetails\":[{\"time\":" + epochSecond * 1000 + ",\"level\":5}]}");
            respond("{\"level\":5,\"complete\":false,\"trackingDetails\":[{\"time\":" + epochSecond + ",\"level\":5}]}");

            TrackSnapshot expected = new TrackSnapshot(kst(2026, 10, 4, 13, 30), null, false, false);
            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE))).contains(expected);
            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE))).contains(expected);
        }

        @Test
        @DisplayName("timeString 을 못 읽으면 time 으로 넘어간다")
        void unparseableTimeStringFallsBackToEpoch() {
            long epochMillis = kst(2026, 10, 4, 13, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            respond("{\"level\":5,\"complete\":false,\"trackingDetails\":[{\"timeString\":\"2026.10.04 13시30분\","
                    + "\"time\":" + epochMillis + ",\"level\":5}]}");

            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE)))
                    .contains(new TrackSnapshot(kst(2026, 10, 4, 13, 30), null, false, false));
        }

        @Test
        @DisplayName("둘 다 못 읽는 이력 · time<=0 은 건너뛰고 나머지 이력으로 판정한다")
        void unreadableDetailsAreSkipped() {
            respond("""
                    {"level":4,"complete":false,"trackingDetails":[
                      {"timeString":"알 수 없음","level":4},
                      {"time":0,"level":4},
                      {"time":-1,"level":4},
                      {"timeString":"2026-10-03 21:10:00","level":3}]}""");

            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE)))
                    .contains(new TrackSnapshot(kst(2026, 10, 3, 21, 10), null, false, false));
        }

        @Test
        @DisplayName("업체 시각은 KST 다 — 서버 시간대가 UTC 여도 같은 순간으로 바뀐다(구매확정 기점이 9시간 밀리지 않는다)")
        void kstIsConvertedToServerZone() {
            respond(details(6, true, detail("2026-10-04 08:00:00", 6)));

            LocalDateTime deliveredAt = tracker.track(DeliveryCarrier.CJ, INVOICE).orElseThrow().deliveredAt();

            assertThat(deliveredAt.atZone(ZoneId.systemDefault()).toInstant())
                    .isEqualTo(LocalDateTime.of(2026, 10, 4, 8, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant());
        }
    }

    @Nested
    @DisplayName("추적 — 업체 에러 코드(HTTP 200 · status=false)")
    class TrackErrors {

        @Test
        @DisplayName("104(유효하지 않은 운송장 · 실응답 문구) → 이벤트 없는 스냅샷 — 24시간 뒤 집화 확인 필요로 이어진다")
        void invalidInvoiceIsNoEvent() {
            respond("{\"status\":false,\"msg\":\"유효하지 않은 운송장번호 이거나 택배사 코드 입니다.\",\"code\":\"104\"}");

            assertThat(judged(tracker.track(DeliveryCarrier.CJ, INVOICE))).contains(noEvent());
        }

        @ParameterizedTest(name = "code {0}")
        @ValueSource(strings = {"105", "106", "999"})
        @DisplayName("105(같은 송장 일 한도) · 106(조회 에러) · 모르는 코드 → 판정하지 않는다(empty)")
        void perInvoiceErrorsAreSkipped(String code) {
            respond(error(code));

            assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).isEmpty();
        }

        @Test
        @DisplayName("코드 없는 에러 → 판정하지 않는다(empty)")
        void errorWithoutCode() {
            respond("{\"status\":false,\"msg\":\"알 수 없는 오류\"}");

            assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).isEmpty();
        }

        @ParameterizedTest(name = "code {0}")
        @ValueSource(strings = {"101", "102", "103"})
        @DisplayName("101 · 102 · 103(키 없음 · 만료 · 사용량 초과) → 차단 예외 · 메시지에 키가 없다")
        void keyErrorsBlock(String code) {
            respond(error(code));

            assertThatThrownBy(() -> tracker.track(DeliveryCarrier.CJ, INVOICE))
                    .isInstanceOf(DeliveryTrackerBlockedException.class)
                    .hasMessageContaining(code)
                    .hasMessageNotContaining(API_KEY);
        }
    }

    @Nested
    @DisplayName("추적 — 통신 장애")
    class TrackFailures {

        @Test
        @DisplayName("5xx HTML → empty")
        void serverErrorHtml() {
            server.expect(requestTo(TRACKING_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                    .body("<html>Bad Gateway</html>").contentType(MediaType.TEXT_HTML));

            assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).isEmpty();
        }

        @ParameterizedTest(name = "HTTP {0}")
        @ValueSource(ints = {400, 415, 500, 503})
        @DisplayName("HTTP 오류에 스프링 에러 JSON 이 와도 empty — 정상 응답(이벤트 없음)으로 읽지 않는다")
        void httpErrorWithJsonBody(int status) {
            server.expect(requestTo(TRACKING_URL)).andRespond(withStatus(HttpStatus.valueOf(status))
                    .body("{\"timestamp\":1791160461590,\"status\":" + status + ",\"error\":\"Unsupported Media Type\","
                            + "\"path\":\"/api/v1/trackingInfo\"}")
                    .contentType(MediaType.APPLICATION_JSON));

            assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).isEmpty();
        }

        @Test
        @DisplayName("200 · 빈 본문 → empty")
        void emptyBody() {
            server.expect(requestTo(TRACKING_URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).isEmpty();
        }

        @Test
        @DisplayName("200 · 깨진 JSON → empty")
        void malformedJson() {
            server.expect(requestTo(TRACKING_URL)).andRespond(withSuccess("{\"level\":3,", MediaType.APPLICATION_JSON));

            assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).isEmpty();
        }

        @Test
        @DisplayName("읽기 타임아웃 → empty — 예외가 배치로 새지 않는다")
        void timeout() {
            server.expect(requestTo(TRACKING_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

            assertThat(tracker.track(DeliveryCarrier.CJ, INVOICE)).isEmpty();
        }
    }

    @Nested
    @DisplayName("송장 형식 검증")
    class Validation {

        @Test
        @DisplayName("꺼져 있으면(기본) 호출 없이 UNAVAILABLE — 조회 한도를 쓰지 않는다")
        void disabledByDefault() {
            server.expect(never(), anything());

            assertThat(properties.isValidationEnabled()).isFalse();
            assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.UNAVAILABLE);
            server.verify();
        }

        @Test
        @DisplayName("정상 응답(level 0 포함) → VALID — 집화 전 송장을 막지 않는다")
        void successIsValid() {
            properties.setValidationEnabled(true);
            respond(noData());
            respond(details(3, false, detail("2026-10-04 09:15:00", 3)));

            assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.VALID);
            assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.VALID);
        }

        @Test
        @DisplayName("104 → INVALID(하드 차단)")
        void invalidInvoice() {
            properties.setValidationEnabled(true);
            respond(error("104"));

            assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.INVALID);
        }

        @ParameterizedTest(name = "code {0}")
        @ValueSource(strings = {"101", "102", "103", "105", "106"})
        @DisplayName("104 외 에러 → UNAVAILABLE — 키 차단(101~103)도 예외로 등록을 막지 않는다")
        void otherErrorsAreUnavailable(String code) {
            properties.setValidationEnabled(true);
            respond(error(code));

            assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.UNAVAILABLE);
        }

        @Test
        @DisplayName("5xx · 타임아웃 → UNAVAILABLE")
        void failuresAreUnavailable() {
            properties.setValidationEnabled(true);
            server.expect(requestTo(TRACKING_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
            server.expect(requestTo(TRACKING_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

            assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.UNAVAILABLE);
            assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.UNAVAILABLE);
        }

        @Test
        @DisplayName("HTTP 오류 + 에러 JSON → UNAVAILABLE — VALID 로 통과시키지 않는다")
        void httpErrorIsNotValid() {
            properties.setValidationEnabled(true);
            server.expect(requestTo(TRACKING_URL)).andRespond(withStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("{\"status\":415,\"error\":\"Unsupported Media Type\"}").contentType(MediaType.APPLICATION_JSON));

            assertThat(tracker.validateInvoice(DeliveryCarrier.CJ, INVOICE)).isEqualTo(ValidationResult.UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("생성")
    class Construction {

        @ParameterizedTest(name = "키 [{0}]")
        @ValueSource(strings = {"", " ", "\t"})
        @DisplayName("API 키가 비면 생성에서 실패한다 — 조용히 스텁처럼 돌지 않는다")
        void blankApiKeyFailsFast(String key) {
            assertThatThrownBy(() -> new SweetTrackerClient(key, RestClient.create()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("delivery.tracker.api-key");
        }

        @Test
        @DisplayName("API 키가 null 이어도 실패한다")
        void nullApiKeyFailsFast() {
            assertThatThrownBy(() -> new SweetTrackerClient(null, RestClient.create()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @ParameterizedTest
        @EnumSource(value = DeliveryCarrier.class, names = "COUPANG", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("쿠팡택배 외 10종은 코드가 있다")
        void carriersHaveCodes(DeliveryCarrier carrier) {
            assertThat(carrier.getTrackerCode()).isNotBlank();
        }
    }

    // ------------------------------------------------------------------ 보조

    private void respond(String json) {
        server.expect(requestTo(TRACKING_URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static String noData() {
        return "{\"level\":0,\"complete\":false,\"trackingDetails\":[],\"result\":\"N\"}";
    }

    private static String error(String code) {
        return "{\"status\":false,\"msg\":\"에러\",\"code\":\"" + code + "\"}";
    }

    private static String details(int level, boolean complete, String... details) {
        return "{\"level\":" + level + ",\"complete\":" + complete + ",\"trackingDetails\":["
                + String.join(",", details) + "]}";
    }

    private static String detail(String timeString, int level) {
        return detail(timeString, level, "처리");
    }

    private static String detail(String timeString, int level, String kind) {
        return "{\"timeString\":\"" + timeString + "\",\"level\":" + level + ",\"kind\":\"" + kind + "\"}";
    }

    private static TrackSnapshot noEvent() {
        return new TrackSnapshot(null, null, false, false);
    }

    /** 판정에 쓰는 네 값만 — 스캔 이력·단계는 {@code scanEvents}가 따로 본다. */
    private static Optional<TrackSnapshot> judged(Optional<TrackSnapshot> snapshot) {
        return snapshot.map(s -> new TrackSnapshot(s.lastEventAt(), s.deliveredAt(), s.returnDetected(),
                s.returnCompleted()));
    }

    /** 업체 시각(KST)을 서버 시간대의 LocalDateTime 으로 — 어댑터와 같은 변환. */
    static LocalDateTime kst(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
