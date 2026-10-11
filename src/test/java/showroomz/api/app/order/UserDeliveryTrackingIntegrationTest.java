package showroomz.api.app.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.order.SellerOrderTestSupport;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.DeliveryTrackingEvent;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.repository.DeliveryTrackingEventRepository;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C10-2 배송 조회 · 스캔 이력 저장(앱 클레임 설계서 7절 #22 · #24). 하위주문은 실제 결제 경로로 만들고 추적 결과는
 * 감시 배치가 부르는 {@code applyTracking}으로 태운다 — 화면은 저장된 이력만 읽는다.
 */
@IntegrationTest
class UserDeliveryTrackingIntegrationTest extends SellerOrderTestSupport {

    private static final String INVOICE = "684922013378";

    @Autowired private DeliveryTrackingEventRepository trackingEventRepository;

    @Test
    @DisplayName("송장이 없으면 NOT_SHIPPED — 바는 전부 빈 칸이고 발송 기한을 내린다 · 공구 진행 중이면 기한 대신 「마감 후 N영업일」(#22)")
    void notShipped() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        tracking(group).andExpect(jsonPath("$.shipDueAt").value(nullValue()))
                .andExpect(jsonPath("$.headline.sub").value("공구 마감 후 2영업일 이내 발송 (주말·공휴일 제외)"));
        closeGroupBuy(LocalDateTime.now().minusDays(1));

        tracking(group).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_SHIPPED"))
                .andExpect(jsonPath("$.context").value("ORDER"))
                .andExpect(jsonPath("$.stageIndex").value(-1))
                .andExpect(jsonPath("$.headline.text").value("배송 준비 중이에요"))
                .andExpect(jsonPath("$.carrier").value(nullValue()))
                .andExpect(jsonPath("$.trackingNumber").value(nullValue()))
                .andExpect(jsonPath("$.scans", empty()))
                .andExpect(jsonPath("$.shipDueAt").value(appTime(reload(group).getShipDueAt())))
                .andExpect(jsonPath("$.item.productName").value(items(group).get(0).getProductName()));
    }

    @Test
    @DisplayName("송장은 있고 이력이 0건이면 IN_TRANSIT 의 첫 칸 — 택배사 전화·조회 주소는 서버가 내린다(#22)")
    void inTransitWithoutScans() throws Exception {
        OrderDeliveryGroup group = shippingGroup(INVOICE);

        tracking(group).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.stageIndex").value(0))
                .andExpect(jsonPath("$.headline.text").value("상품이 배송중이에요"))
                .andExpect(jsonPath("$.headline.sub").value(nullValue()))
                .andExpect(jsonPath("$.carrier.code").value("CJ"))
                .andExpect(jsonPath("$.carrier.label").value(DeliveryCarrier.CJ.getLabel()))
                .andExpect(jsonPath("$.carrier.tel").value(DeliveryCarrier.CJ.getTel()))
                .andExpect(jsonPath("$.carrier.trackingUrl").value(DeliveryCarrier.CJ.trackingUrl(INVOICE)))
                .andExpect(jsonPath("$.trackingNumber").value(INVOICE))
                .andExpect(jsonPath("$.scans", empty()))
                .andExpect(jsonPath("$.shipDueAt").value(nullValue()));
    }

    @Test
    @DisplayName("이력이 쌓이면 둘째 칸 — 최신순으로 원문 그대로 내리고 보조 문구는 최종 위치다(#22)")
    void inTransitWithScans() throws Exception {
        OrderDeliveryGroup group = shippingGroup(INVOICE);
        LocalDateTime pickedUp = LocalDateTime.now().minusHours(6).withNano(0);
        track(group, snapshot(null,
                new TrackEvent(pickedUp, "서울강남", "집화처리", 2),
                new TrackEvent(pickedUp.plusHours(3), "곤지암Hub", "간선하차", 3)), batchNow());

        tracking(group).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.stageIndex").value(1))
                .andExpect(jsonPath("$.scans", hasSize(2)))
                .andExpect(jsonPath("$.scans[0].location").value("곤지암Hub"))
                .andExpect(jsonPath("$.scans[0].description").value("간선하차"))
                .andExpect(jsonPath("$.scans[0].occurredAt").value(appTime(pickedUp.plusHours(3))))
                .andExpect(jsonPath("$.scans[1].location").value("서울강남"));
    }

    @Test
    @DisplayName("배송완료면 DELIVERED — 마지막 칸이고 날짜는 배송완료일이다(#22)")
    void delivered() throws Exception {
        OrderDeliveryGroup group = shippingGroup(INVOICE);
        LocalDateTime deliveredAt = LocalDateTime.now().minusHours(1).withNano(0);
        track(group, snapshot(deliveredAt,
                new TrackEvent(deliveredAt.minusDays(1), "서울강남", "집화처리", 2),
                new TrackEvent(deliveredAt, "성남분당", "배송완료", 6)), batchNow());

        tracking(group).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DELIVERED"))
                .andExpect(jsonPath("$.stageIndex").value(2))
                .andExpect(jsonPath("$.headline.date").value(deliveredAt.toLocalDate().toString()))
                .andExpect(jsonPath("$.headline.text").value("상품 배송이 완료되었어요"))
                .andExpect(jsonPath("$.scans", hasSize(2)));
    }

    @Test
    @DisplayName("남의 주문은 403, 그 주문의 항목이 아니면 404(#22)")
    void ownership() throws Exception {
        OrderDeliveryGroup mine = shippingGroup(INVOICE);
        OrderDeliveryGroup other = paidGroup();
        Users stranger = createConsumer("stranger", "박타인");
        String strangerToken = bearerToken(stranger.getUsername(), RoleType.USER, stranger.getId());

        mockMvc.perform(get(trackingUrl(mine.getOrder().getId(), items(mine).get(0).getId()))
                        .header(HttpHeaders.AUTHORIZATION, strangerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(trackingUrl(mine.getOrder().getId(), items(other).get(0).getId()))
                        .header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("폴링 2회 — 이력 3건 뒤 5건이 오면 5행만 남는다(중복 없음 · #24)")
    void pollingAppendsOnlyNewEvents() throws Exception {
        OrderDeliveryGroup group = shippingGroup(INVOICE);
        LocalDateTime base = LocalDateTime.now().minusDays(1).withNano(0);
        TrackEvent[] five = {
                new TrackEvent(base, "서울강남", "집화처리", 2),
                new TrackEvent(base.plusHours(2), "서울강남", "간선상차", 3),
                new TrackEvent(base.plusHours(5), "곤지암Hub", "간선하차", 3),
                new TrackEvent(base.plusHours(9), "성남분당", "간선하차", 4),
                new TrackEvent(base.plusHours(12), "성남분당", "배송출발", 5)};

        track(group, snapshot(null, five[0], five[1], five[2]), batchNow());
        track(group, snapshot(null, five), batchNow());
        track(group, snapshot(null, five), batchNow());

        List<DeliveryTrackingEvent> stored = trackingEventRepository
                .findByCarrierAndTrackingNumberOrderBySeqDesc(DeliveryCarrier.CJ, INVOICE);
        assertThat(stored).extracting(DeliveryTrackingEvent::getSeq).containsExactly(4, 3, 2, 1, 0);
        assertThat(stored.get(0).getDescription()).isEqualTo("배송출발");
    }

    private static TrackSnapshot snapshot(LocalDateTime deliveredAt, TrackEvent... events) {
        LocalDateTime last = events[events.length - 1].occurredAt();
        return new TrackSnapshot(last, deliveredAt, false, false, List.of(events), null);
    }

    /** 앱 주문 API 의 시각 규약 — {@code OrderDto.TIME_PATTERN}(오프셋 없음). */
    private static String appTime(LocalDateTime value) {
        return value.withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private ResultActions tracking(OrderDeliveryGroup group) throws Exception {
        return mockMvc.perform(get(trackingUrl(group.getOrder().getId(), items(group).get(0).getId()))
                .header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private static String trackingUrl(Long orderId, Long orderProductId) {
        return ORDERS + "/" + orderId + "/items/" + orderProductId + "/tracking";
    }
}
