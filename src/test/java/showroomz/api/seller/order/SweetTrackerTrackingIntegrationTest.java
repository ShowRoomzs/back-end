package showroomz.api.seller.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.client.match.JsonPathRequestMatchers;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.web.client.RestClient;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderFulfillmentHistory;
import showroomz.domain.order.type.DeliveredSource;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.sweettracker.SweetTrackerClient;
import showroomz.global.delivery.tracker.sweettracker.SweetTrackerDeliveryTracker;
import showroomz.global.scheduler.OrderDeliveryTrackingScheduler;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;

import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스마트택배 응답 → 감시 배치 → 이행 전이까지 한 번에(택배 추적 설계서 3 · 4절 · 34 설계서 3-3).
 *
 * <p>다른 추적 테스트는 {@code TrackSnapshot}을 직접 주입하므로 「업체 응답 모양이 실제 DB 전이로 이어지는가」는 여기서만 본다.
 * 통합 컨텍스트는 연동을 꺼 두므로(Noop) 실제 서비스 빈과 스마트택배 어댑터(HTTP 는 {@link MockRestServiceServer})로
 * 감시 배치를 직접 조립해 {@code tick()}을 부른다. 업체 응답은 송장번호({@code t_invoice})로 골라 준다.
 */
@IntegrationTest
class SweetTrackerTrackingIntegrationTest extends SellerOrderTestSupport {

    private static final String BASE_URL = "https://info.sweettracker.co.kr";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter VENDOR_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MockRestServiceServer server;
    private DeliveryTrackerProperties properties;
    private OrderDeliveryTrackingScheduler scheduler;

    @BeforeEach
    void setUpTracker() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        properties = new DeliveryTrackerProperties();
        properties.setCallGapMs(0);
        SweetTrackerDeliveryTracker tracker =
                new SweetTrackerDeliveryTracker(new SweetTrackerClient("test-key", builder.build()), properties);
        scheduler = new OrderDeliveryTrackingScheduler(fulfillmentService, tracker, properties);
    }

    @Nested
    @DisplayName("정상 응답 → 전이")
    class Transitions {

        @Test
        @DisplayName("배송중(level 3) — 최종 갱신 = 업체 이벤트 시각(KST 변환) · 배송중 유지 · 배지·이력 없음")
        void inTransitTouchesLastTracking() throws Exception {
            OrderDeliveryGroup group = shippingGroup("810000000001");
            LocalDateTime eventAt = minutesAgo(40);
            int historyBefore = history(group).size();
            respondFor("810000000001", details(3, false, detail(eventAt.minusHours(5), 2), detail(eventAt, 3)));

            scheduler.tick();

            OrderDeliveryGroup result = reload(group);
            assertThat(result.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(result.getLastTrackingAt()).isEqualTo(eventAt);
            assertThat(result.getTrackingAlert()).isNull();
            assertThat(history(group)).hasSize(historyBefore);
            server.verify();
        }

        @Test
        @DisplayName("배송완료(level 6) — DELIVERED · 배송완료 시각 = 업체 시각(폴링 시각 아님) · 출처 TRACKER · 「자동 확인」 이력")
        void deliveredByVendorResponse() throws Exception {
            OrderDeliveryGroup group = shippingGroup("810000000002");
            LocalDateTime deliveredAt = minutesAgo(90);
            respondFor("810000000002", details(6, true, detail(deliveredAt.minusDays(1), 2), detail(deliveredAt, 6)));

            scheduler.tick();

            OrderDeliveryGroup result = reload(group);
            assertThat(result.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
            assertThat(result.getDeliveredAt()).isEqualTo(deliveredAt);
            assertThat(result.getDeliveredSource()).isEqualTo(DeliveredSource.TRACKER);
            OrderFulfillmentHistory latest = history(group).get(0);
            assertThat(latest.getEventType()).isEqualTo(FulfillmentEventType.DELIVERED);
            assertThat(latest.getActorType()).isEqualTo(FulfillmentActorType.TRACKER);
            assertThat(latest.getDetail()).isEqualTo("자동 확인");
            sellerGet(SELLER_ORDERS + "?tab=DELIVERED")
                    .andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()))
                    .andExpect(jsonPath("$.content[0].deliveredSourceLabel").value("자동 확인"));
        }

        @Test
        @DisplayName("구매확정 기점은 업체 배송완료 시각이다 — 8일 전 배송완료를 오늘 폴링해도 바로 구매확정 대상")
        void purchaseConfirmCountsFromVendorTime() throws Exception {
            OrderDeliveryGroup group = shippingGroup("810000000003");
            respondFor("810000000003", details(6, true, detail(minutesAgo(8 * 24 * 60), 6)));

            scheduler.tick();
            LocalDateTime now = batchNow();

            assertThat(fulfillmentService.findIdsToConfirm(now.minusDays(7), 100)).contains(group.getId());
            assertThat(fulfillmentService.confirmPurchase(group.getId(), now, now.minusDays(7))).isTrue();
            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
        }

        @Test
        @DisplayName("배송완료 뒤에는 대상에서 빠진다 — 다음 회차는 그 송장을 조회하지 않는다(한도 절약) · 이력 1회")
        void deliveredIsNotPolledAgain() throws Exception {
            OrderDeliveryGroup group = shippingGroup("810000000004");
            respondFor("810000000004", details(6, true, detail(minutesAgo(30), 6)));

            scheduler.tick();
            scheduler.tick();

            server.verify();
            assertThat(historyCount(group, FulfillmentEventType.DELIVERED)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("배송 이상 배지")
    class Alerts {

        @Test
        @DisplayName("집화 전(level 0) — 등록 23시간이면 아무것도 없다 · 25시간이면 집화 확인 필요(이력 1회)")
        void pickupUnconfirmedFromLevelZero() throws Exception {
            OrderDeliveryGroup group = shippingGroup("810000000011");
            server.expect(once(), invoice("810000000011")).andRespond(json(noData()));
            server.expect(once(), invoice("810000000011")).andRespond(json(noData()));
            server.expect(once(), invoice("810000000011")).andRespond(json(noData()));

            backdateShippedAt(group, LocalDateTime.now().minusHours(23));
            scheduler.tick();
            assertThat(reload(group).getTrackingAlert()).isNull();

            backdateShippedAt(group, LocalDateTime.now().minusHours(25));
            scheduler.tick();
            scheduler.tick();

            assertThat(reload(group).getTrackingAlert()).isEqualTo(TrackingAlert.PICKUP_UNCONFIRMED);
            assertThat(historyCount(group, FulfillmentEventType.PICKUP_UNCONFIRMED)).isEqualTo(1);
            server.verify();
        }

        @Test
        @DisplayName("104(유효하지 않은 운송장)도 집화 전과 같다 — 25시간이면 집화 확인 필요")
        void pickupUnconfirmedFromInvalidInvoice() throws Exception {
            OrderDeliveryGroup group = shippingGroup("810000000012");
            backdateShippedAt(group, LocalDateTime.now().minusHours(25));
            respondFor("810000000012", error("104"));

            scheduler.tick();

            assertThat(reload(group).getTrackingAlert()).isEqualTo(TrackingAlert.PICKUP_UNCONFIRMED);
        }

        @Test
        @DisplayName("집화 확인 필요 뒤 이벤트가 오면 배지가 풀리고 최종 갱신이 찍힌다")
        void alertClearsOnFirstEvent() throws Exception {
            OrderDeliveryGroup group = shippingGroup("810000000013");
            backdateShippedAt(group, LocalDateTime.now().minusHours(30));
            LocalDateTime pickedUpAt = minutesAgo(20);
            server.expect(once(), invoice("810000000013")).andRespond(json(noData()));
            server.expect(once(), invoice("810000000013")).andRespond(json(details(2, false, detail(pickedUpAt, 2))));

            scheduler.tick();
            assertThat(reload(group).getTrackingAlert()).isEqualTo(TrackingAlert.PICKUP_UNCONFIRMED);
            scheduler.tick();

            OrderDeliveryGroup result = reload(group);
            assertThat(result.getTrackingAlert()).isNull();
            assertThat(result.getLastTrackingAt()).isEqualTo(pickedUpAt);
        }

        @Test
        @DisplayName("마지막 이벤트가 8일 전 — 추적 정지(이력 1회)")
        void stalledFromOldEvent() throws Exception {
            OrderDeliveryGroup group = shippingGroup("810000000014");
            String stale = details(3, false, detail(minutesAgo(8 * 24 * 60), 3));
            server.expect(once(), invoice("810000000014")).andRespond(json(stale));
            server.expect(once(), invoice("810000000014")).andRespond(json(stale));

            scheduler.tick();
            scheduler.tick();

            assertThat(reload(group).getTrackingAlert()).isEqualTo(TrackingAlert.STALLED);
            assertThat(historyCount(group, FulfillmentEventType.TRACKING_STALLED)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("업체 장애·한도")
    class Failures {

        @Test
        @DisplayName("105(같은 송장 일 한도) · 5xx — 판정하지 않는다: 상태·최종 갱신·배지 그대로")
        void perInvoiceFailuresChangeNothing() throws Exception {
            OrderDeliveryGroup limited = shippingGroup("810000000021");
            OrderDeliveryGroup outage = shippingGroup("810000000022");
            LocalDateTime seenAt = minutesAgo(300);
            respondFor("810000000021", noData());
            respondFor("810000000022", details(3, false, detail(seenAt, 3)));
            scheduler.tick(); // outage 는 한 번 이벤트를 받아 둔다
            server.reset();
            respondFor("810000000021", error("105"));
            server.expect(once(), invoice("810000000022")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

            scheduler.tick();

            assertThat(reload(limited).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(reload(limited).getLastTrackingAt()).isNull();
            assertThat(reload(limited).getTrackingAlert()).isNull();
            assertThat(reload(outage).getLastTrackingAt()).isEqualTo(seenAt);
            assertThat(reload(outage).getTrackingAlert()).isNull();
            server.verify();
        }

        @Test
        @DisplayName("103(키 사용량 초과) — 그 회차를 멈춘다: 앞 건은 반영 · 차단 건 이후는 조회하지 않는다")
        void usageExceededStopsSweep() throws Exception {
            OrderDeliveryGroup first = shippingGroup("810000000023");
            OrderDeliveryGroup blocked = shippingGroup("810000000024");
            OrderDeliveryGroup after = shippingGroup("810000000025");
            respondFor("810000000023", details(6, true, detail(minutesAgo(60), 6)));
            respondFor("810000000024", error("103"));
            server.expect(never(), invoice("810000000025"));

            scheduler.tick();

            server.verify();
            assertThat(reload(first).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
            assertThat(reload(blocked).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(reload(after).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
        }

        @Test
        @DisplayName("한 회차 차단 뒤 다음 회차는 처음부터 다시 돈다")
        void nextSweepStartsOver() throws Exception {
            OrderDeliveryGroup first = shippingGroup("810000000026");
            OrderDeliveryGroup second = shippingGroup("810000000027");
            server.expect(once(), invoice("810000000026")).andRespond(json(error("103")));
            scheduler.tick();
            server.verify();
            server.reset();
            respondFor("810000000026", details(6, true, detail(minutesAgo(60), 6)));
            respondFor("810000000027", details(6, true, detail(minutesAgo(50), 6)));

            scheduler.tick();

            server.verify();
            assertThat(reload(first).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
            assertThat(reload(second).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        }
    }

    @Nested
    @DisplayName("택배사 · 송장")
    class Carriers {

        @Test
        @DisplayName("하위주문의 택배사가 코드로 나간다 — CJ 04 · 한진 05 · 롯데 08 · 우체국 01")
        void carrierCodeFollowsGroup() throws Exception {
            shipped(preparingGroup(), "CJ", "810000000031");
            shipped(preparingGroup(), "HANJIN", "810000000032");
            shipped(preparingGroup(), "LOTTE", "810000000033");
            shipped(preparingGroup(), "EPOST", "8100000000340");
            expectCode("810000000031", "04");
            expectCode("810000000032", "05");
            expectCode("810000000033", "08");
            expectCode("8100000000340", "01");

            scheduler.tick();

            server.verify();
        }

        @Test
        @DisplayName("송장을 수정하면 다음 회차부터 새 택배사·새 번호로 조회한다")
        void pollsUpdatedInvoice() throws Exception {
            OrderDeliveryGroup group = shippingGroup("810000000035");
            updateShipment(group.getId(), "HANJIN", "810000000036").andExpect(status().isOk());
            server.expect(never(), invoice("810000000035"));
            server.expect(once(), request -> {
                vendorJson("$.t_invoice").value("810000000036").match(request);
                vendorJson("$.t_code").value("05").match(request);
            }).andRespond(json(details(6, true, detail(minutesAgo(10), 6))));

            scheduler.tick();

            server.verify();
            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        }

        @Test
        @DisplayName("쿠팡택배로 이미 등록된 옛 송장 — 새로 고를 수는 없지만(1009 기획 수정본 4절) 남은 행은 조회하지 않는다 · 24시간 뒤 집화 확인 필요")
        void coupangIsNeverPolled() throws Exception {
            registerShipment(preparingGroup().getId(), "COUPANG", "810000000039").andExpect(status().isOk())
                    .andExpect(jsonPath("$.succeeded").value(0))
                    .andExpect(jsonPath("$.skipped[0].code").value("CARRIER_INVALID"));
            OrderDeliveryGroup group = shipped(preparingGroup(), "CJ", "810000000037");
            jdbc.update("UPDATE order_delivery_group SET carrier = 'COUPANG' WHERE delivery_group_id = ?", group.getId());
            backdateShippedAt(group, LocalDateTime.now().minusHours(25));
            server.expect(never(), anything());

            scheduler.tick();

            server.verify();
            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(reload(group).getTrackingAlert()).isEqualTo(TrackingAlert.PICKUP_UNCONFIRMED);
        }

        @Test
        @DisplayName("반송중 하위주문도 조회한다 — 배송완료 응답이 와도 반송중에서 나가지 않는다")
        void returningIsPolledButStays() throws Exception {
            OrderDeliveryGroup group = returning(shippingGroup("810000000038"));
            respondFor("810000000038", details(6, true, detail(minutesAgo(10), 6)));

            scheduler.tick();

            server.verify();
            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.RETURNING);
            assertThat(reload(group).getDeliveredAt()).isNull();
        }
    }

    // ------------------------------------------------------------------ 보조

    private void respondFor(String invoice, String json) {
        server.expect(once(), invoice(invoice)).andRespond(json(json));
    }

    private void expectCode(String invoice, String code) {
        server.expect(once(), request -> {
            vendorJson("$.t_invoice").value(invoice).match(request);
            vendorJson("$.t_code").value(code).match(request);
        }).andRespond(json(noData()));
    }

    /** 업체로 나간 요청 본문의 JSON 경로 — MockMvc 응답용 {@code jsonPath}와 이름이 겹쳐 따로 둔다. */
    private static JsonPathRequestMatchers vendorJson(String expression) {
        return MockRestRequestMatchers.jsonPath(expression);
    }

    private static RequestMatcher invoice(String invoice) {
        return vendorJson("$.t_invoice").value(invoice);
    }

    private static ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private static String noData() {
        return "{\"complete\":false,\"level\":0,\"result\":\"N\",\"completeYN\":\"N\"}";
    }

    private static String error(String code) {
        return "{\"status\":false,\"msg\":\"에러\",\"code\":\"" + code + "\"}";
    }

    private static String details(int level, boolean complete, String... details) {
        return "{\"level\":" + level + ",\"complete\":" + complete + ",\"trackingDetails\":["
                + String.join(",", details) + "]}";
    }

    /** 업체 이력 1건 — 서버 시각을 업체 형식(KST 문자열)으로 바꿔 싣는다. */
    private static String detail(LocalDateTime serverTime, int level) {
        String kstText = serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(KST).format(VENDOR_TIME);
        return "{\"timeString\":\"" + kstText + "\",\"level\":" + level + ",\"kind\":\"처리\"}";
    }

    /** 초 단위로 자른 과거 시각 — 업체 시각 문자열이 초까지라 DB 왕복 뒤 equals 비교가 된다. */
    private static LocalDateTime minutesAgo(long minutes) {
        return LocalDateTime.now().withNano(0).minusMinutes(minutes);
    }
}
