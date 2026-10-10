package showroomz.api.admin.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.admin.transaction.dto.AdminOrderDto;
import showroomz.api.admin.transaction.service.AdminOrderCommandService;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.calendar.service.BusinessHolidayLoader;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.event.OrderOverdueNoticeEvent;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.service.OrderOverdueNoticeService;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.global.scheduler.OrderOverdueNoticeScheduler;
import showroomz.global.utils.BusinessCalendar;
import showroomz.support.IntegrationTest;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 처리 지연 자동 알림 배치(1009 기획 수정본 8-4 · 45 보완 시나리오 2-1 NB-01 ~ NB-08 · 3절 RC-08 · 5절 L-07) — 06a 「대행 송장」 ·
 * 06b 「검수 무응답 환불」 · 06d 「대행 가능」이 이 배치가 쌓는 횟수 하나에 걸려 있다. 다른 어드민 테스트는 횟수를 SQL 로 적지만 여기서는
 * <b>배치가 만든 값</b>으로 세 화면을 본다.
 *
 * <p>배치 빈은 테스트 설정에서 꺼져 있어 직접 만든다. 회차 시각은 {@link OrderOverdueNoticeScheduler#run}에 넘긴다 — 오늘이 주말이어도
 * 돌 수 있게 <b>지난 영업일</b>들의 10 · 15시를 회차로 쓰고, 화면은 지금 시각으로 읽는다. 알림 이벤트는 받는 모듈이 없어 발행만 본다.
 */
@IntegrationTest
@RecordApplicationEvents
class OrderOverdueNoticeSchedulerIntegrationTest extends ClaimTestSupport {

    private static final String ADMIN_ORDERS = "/v1/admin/orders";
    private static final String ADMIN_CLAIMS = "/v1/admin/claims";
    private static final String EXCEPTIONS = "/v1/admin/order-exceptions";
    private static final LocalTime FIRST_SLOT = LocalTime.of(10, 0);
    private static final LocalTime SECOND_SLOT = LocalTime.of(15, 0);

    @Autowired private OrderOverdueNoticeService noticeService;
    @Autowired private AdminOrderCommandService commandService;
    @Autowired private BusinessCalendar calendar;
    @Autowired private BusinessHolidayLoader holidayLoader;
    @Autowired private OrderClaimRepository claimRepository;
    @Autowired private ApplicationEvents applicationEvents;

    private OrderOverdueNoticeScheduler scheduler;
    private String admin;
    private Seller operator;

    @BeforeEach
    void setUpScheduler() {
        scheduler = new OrderOverdueNoticeScheduler(noticeService);
        operator = fixture.createAdmin("notice-ops@showroomz.test", "운영자");
        admin = adminToken(operator);
    }

    @AfterEach
    void restoreCalendar() {
        jdbc.update("DELETE FROM business_holiday");
        calendar.replaceRegisteredHolidays(Set.of());
    }

    // ------------------------------------------------------------------ 발송 기한 경과

    @Test
    @DisplayName("[NB-01] 영업일 10시 1회 · 같은 날 15시는 증가 없음 — 횟수 · 마지막 시각 · 이벤트 1건 · 06a · 06d 1회 · 다음 회차는 배치 시각표")
    void countsOncePerBusinessDay() throws Exception {
        LocalDate day = pastBusinessDays(1).get(0);
        OrderDeliveryGroup group = overdueSince(prepared(paidGroup()), day);

        scheduler.run(day.atTime(FIRST_SLOT));
        assertThat(noticeRow(group)).containsEntry("overdue_notice_count", 1);
        assertThat(lastNoticeAt(group)).isEqualTo(day.atTime(FIRST_SLOT));
        assertThat(applicationEvents.stream(OrderOverdueNoticeEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.kind()).isEqualTo(OrderOverdueNoticeEvent.Kind.SHIP_OVERDUE);
            assertThat(event.targetId()).isEqualTo(group.getId());
            assertThat(event.marketId()).isEqualTo(brand.marketId());
            assertThat(event.sequence()).isEqualTo(1);
            assertThat(event.notifiedAt()).isEqualTo(day.atTime(FIRST_SLOT));
        });

        scheduler.run(day.atTime(SECOND_SLOT));
        assertThat(noticeRow(group)).containsEntry("overdue_notice_count", 1);
        assertThat(lastNoticeAt(group)).isEqualTo(day.atTime(FIRST_SLOT));
        assertThat(applicationEvents.stream(OrderOverdueNoticeEvent.class)).hasSize(1);

        orderDetail(group).andExpect(jsonPath("$.groups[0].shipping.overdueNoticeCount").value(1))
                .andExpect(jsonPath("$.groups[0].actions.canRegisterShipment").value(false));
        JsonNode page = json(adminGet(EXCEPTIONS + "?tab=DELAY").andExpect(status().isOk()));
        JsonNode row = rowOfGroup(page, group);
        assertThat(row.get("noticeCount").asInt()).isEqualTo(1);
        assertThat(row.get("lastNoticeAt").asText()).isEqualTo(jsonTime(day.atTime(FIRST_SLOT)));
        assertThat(row.get("nextNoticeAt").asText()).isEqualTo(jsonTime(nextSlotAfterEarlierNotice(at(page.get("asOf").asText()))));
        assertThat(row.get("nextStepLabel").asText()).startsWith("자동 알림 대기 · 2회차 ");
    }

    @Test
    @DisplayName("[NB-02 · NB-08] 영업일 3회 — 06a 대행 송장 · 06d 대행 가능 · 다음 회차 없음 · 4회째도 쌓이고 문장은 「3회 무응답」 유지 · 회차마다 06a = 06d = DB")
    void reachesThresholdAndKeepsCounting() throws Exception {
        List<LocalDate> days = pastBusinessDays(4);
        OrderDeliveryGroup group = overdueSince(prepared(paidGroup()), days.get(0));

        for (int i = 0; i < 4; i++) {
            scheduler.run(days.get(i).atTime(FIRST_SLOT));
            int expected = i + 1;
            assertThat(noticeRow(group)).containsEntry("overdue_notice_count", expected);
            JsonNode row = assertShipNoticeConsistent(group, expected, days.get(i).atTime(FIRST_SLOT));
            boolean reached = expected >= 3;
            assertThat(row.get("actOnBehalfAvailable").asBoolean()).as("%d회", expected).isEqualTo(reached);
            assertThat(row.get("nextNoticeAt").isNull()).as("%d회", expected).isEqualTo(reached);
            orderDetail(group).andExpect(jsonPath("$.groups[0].actions.canRegisterShipment").value(reached));
            if (reached) {
                assertThat(row.get("nextStepLabel").asText()).isEqualTo("자동 알림 3회 무응답 · 대행 가능");
            }
        }
        assertThat(applicationEvents.stream(OrderOverdueNoticeEvent.class))
                .extracting(OrderOverdueNoticeEvent::sequence).containsExactly(1, 2, 3, 4);
        adminGet(EXCEPTIONS + "/summary").andExpect(jsonPath("$.actOnBehalfCount").value(1));
    }

    @Test
    @DisplayName("[NB-03] 토요일 · 등록 공휴일(business_holiday)에는 대상 조회도 하지 않는다 — 증가 0 · 이벤트 0")
    void skipsWeekendsAndHolidays() throws Exception {
        LocalDate saturday = LocalDate.now().minusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        LocalDate holiday = pastBusinessDays(1).get(0);
        OrderDeliveryGroup group = overdueSince(prepared(paidGroup()), saturday.isBefore(holiday) ? saturday : holiday);
        jdbc.update("INSERT INTO business_holiday (holiday_date, name) VALUES (?, ?)", holiday, "테스트 공휴일");
        holidayLoader.refresh();
        assertThat(noticeService.isNoticeDay(holiday.atTime(FIRST_SLOT))).isFalse();
        assertThat(noticeService.isNoticeDay(saturday.atTime(FIRST_SLOT))).isFalse();

        scheduler.run(saturday.atTime(FIRST_SLOT));
        scheduler.run(holiday.atTime(FIRST_SLOT));
        scheduler.run(holiday.atTime(SECOND_SLOT));

        assertThat(noticeRow(group)).containsEntry("overdue_notice_count", 0).containsEntry("last_overdue_notice_at", null);
        assertThat(applicationEvents.stream(OrderOverdueNoticeEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("[NB-04] 대상 — 검토 중 취소 요청 · 기한 없음 · 송장 등록 · 취소는 빠지고 신규(준비 전)는 쌓인다 · 요청 거부 뒤 다음 회차부터 다시 쌓인다")
    void targetConditions() throws Exception {
        List<LocalDate> days = pastBusinessDays(2);
        LocalDate first = days.get(0);
        OrderDeliveryGroup requested = overdueSince(prepared(paidGroup()), first);
        OrderCancelRequest request = seedCancelRequest(requested);
        OrderDeliveryGroup noDue = prepared(paidGroup());
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = NULL WHERE delivery_group_id = ?", noDue.getId());
        OrderDeliveryGroup shipping = overdueSince(shipped(prepared(paidGroup()), "CJ", newInvoice()), first);
        OrderDeliveryGroup cancelled = overdueSince(prepared(paidGroup()), first);
        directCancel(List.of(cancelled.getId()), "SOLD_OUT", "품절").andExpect(jsonPath("$.succeeded").value(1));
        OrderDeliveryGroup fresh = overdueSince(paidGroup(), first);

        scheduler.run(first.atTime(FIRST_SLOT));

        for (OrderDeliveryGroup excluded : List.of(requested, noDue, shipping, cancelled)) {
            assertThat(noticeRow(excluded)).as("하위주문 %d", excluded.getId()).containsEntry("overdue_notice_count", 0);
        }
        assertThat(noticeRow(fresh)).containsEntry("overdue_notice_count", 1);
        assertThat(applicationEvents.stream(OrderOverdueNoticeEvent.class)).extracting(OrderOverdueNoticeEvent::targetId)
                .containsExactly(fresh.getId());

        reject(request.getId(), "이미 출고 준비가 끝났습니다.").andExpect(status().isOk());
        scheduler.run(days.get(1).atTime(FIRST_SLOT));
        assertThat(noticeRow(requested)).containsEntry("overdue_notice_count", 1);
        assertThat(noticeRow(fresh)).containsEntry("overdue_notice_count", 2);
    }

    // ------------------------------------------------------------------ 검수 기한 경과

    @Test
    @DisplayName("[NB-05 · NB-08] 검수 대기 — 영업일마다 1 → 2 → 3 · 같은 날 재실행 증가 없음 · 3회면 06b 무응답 환불 · 06d 대행 가능 · 입고 전 · 판정 뒤는 대상 아님")
    void inspectNotices() throws Exception {
        List<LocalDate> days = pastBusinessDays(4);
        Long claimId = received(returnClaim(deliveredGroup(creamVariant, 1)));
        inspectOverdueSince(claimId, days.get(0));
        Long arrived = arrivedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        inspectOverdueSince(arrived, days.get(0));
        Long judged = received(returnClaim(deliveredGroup(creamVariant, 1)));
        inspectOverdueSince(judged, days.get(0));
        sellerPost(SELLER_CLAIMS + "/" + judged + "/inspection/pass", Map.of()).andExpect(status().isOk());

        for (int i = 0; i < 3; i++) {
            LocalDate day = days.get(i);
            scheduler.run(day.atTime(FIRST_SLOT));
            scheduler.run(day.atTime(SECOND_SLOT));
            int expected = i + 1;
            assertThat(claimRow(claimId)).containsEntry("inspect_notice_count", expected);
            JsonNode detail = json(adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(status().isOk()));
            assertThat(detail.at("/inspectNotice/count").asInt()).isEqualTo(expected);
            assertThat(at(detail.at("/inspectNotice/lastAt").asText())).isEqualTo(day.atTime(FIRST_SLOT));
            assertThat(detail.get("canRefundUnanswered").asBoolean()).as("%d회", expected).isEqualTo(expected >= 3);
            JsonNode row = rowOfClaim(json(adminGet(EXCEPTIONS + "?tab=DELAY")), claimId);
            assertThat(row.get("noticeCount").asInt()).isEqualTo(detail.at("/inspectNotice/count").asInt());
            assertThat(row.get("lastNoticeAt").asText()).isEqualTo(jsonTime(lastInspectNoticeAt(claimId)));
            assertThat(row.get("actOnBehalfAvailable").asBoolean()).isEqualTo(expected >= 3);
        }
        assertThat(applicationEvents.stream(OrderOverdueNoticeEvent.class))
                .allSatisfy(event -> assertThat(event.kind()).isEqualTo(OrderOverdueNoticeEvent.Kind.INSPECT_OVERDUE))
                .extracting(OrderOverdueNoticeEvent::targetId).containsOnly(claimId);
        assertThat(claimRow(arrived)).containsEntry("status", "ARRIVED").containsEntry("inspect_notice_count", 0);
        assertThat(claimRow(judged)).containsEntry("inspect_notice_count", 0);

        // 판정하면 다음 회차부터 대상이 아니다 — 쌓인 횟수는 되돌리지 않는다.
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", rejectBody()).andExpect(status().isOk());
        scheduler.run(days.get(3).atTime(FIRST_SLOT));
        assertThat(claimRow(claimId)).containsEntry("inspect_notice_count", 3);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("[NB-06] 한 회차에 발송 · 검수 둘 다 — 각 1회 · 로그 「발송 1건 · 검수 1건」 · 대상이 없으면 로그 없음")
    void runLogsBothKinds(CapturedOutput output) throws Exception {
        List<LocalDate> days = pastBusinessDays(2);
        OrderDeliveryGroup group = overdueSince(prepared(paidGroup()), days.get(0));
        Long claimId = received(returnClaim(deliveredGroup(creamVariant, 1)));
        inspectOverdueSince(claimId, days.get(0));

        scheduler.run(days.get(0).atTime(FIRST_SLOT));
        assertThat(noticeRow(group)).containsEntry("overdue_notice_count", 1);
        assertThat(claimRow(claimId)).containsEntry("inspect_notice_count", 1);
        assertThat(output).contains("처리 지연 자동 알림 - 발송 1건 · 검수 1건");

        int before = output.getOut().split("처리 지연 자동 알림 - ", -1).length;
        scheduler.run(days.get(0).atTime(SECOND_SLOT));
        assertThat(output.getOut().split("처리 지연 자동 알림 - ", -1).length).isEqualTo(before);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("[NB-07] 한 건의 기록이 실패해도 배치는 멈추지 않는다 — 그 건만 건너뛰고(에러 로그) 나머지는 기록 · 다음 회차에 다시 줍는다")
    void oneFailureDoesNotStopRun(CapturedOutput output) throws Exception {
        List<LocalDate> days = pastBusinessDays(2);
        OrderDeliveryGroup broken = overdueSince(prepared(paidGroup()), days.get(0));
        OrderDeliveryGroup healthy = overdueSince(prepared(paidGroup()), days.get(0));
        Long claimId = received(returnClaim(deliveredGroup(creamVariant, 1)));
        inspectOverdueSince(claimId, days.get(0));
        OrderOverdueNoticeService failing = mock(OrderOverdueNoticeService.class,
                AdditionalAnswers.delegatesTo(noticeService));
        doThrow(new IllegalStateException("알림 기록 실패")).when(failing).noticeShipOverdue(eq(broken.getId()), any());

        new OrderOverdueNoticeScheduler(failing).run(days.get(0).atTime(FIRST_SLOT));

        assertThat(noticeRow(broken)).containsEntry("overdue_notice_count", 0);
        assertThat(noticeRow(healthy)).containsEntry("overdue_notice_count", 1);
        assertThat(claimRow(claimId)).containsEntry("inspect_notice_count", 1);
        assertThat(output).contains("발송 기한 경과 알림 기록 실패 - deliveryGroupId: " + broken.getId())
                .contains("처리 지연 자동 알림 - 발송 1건 · 검수 1건");

        scheduler.run(days.get(1).atTime(FIRST_SLOT));
        assertThat(noticeRow(broken)).containsEntry("overdue_notice_count", 1);
        assertThat(noticeRow(healthy)).containsEntry("overdue_notice_count", 2);
    }

    // ------------------------------------------------------------------ 경합 · 조치까지(RC-08 · L-07)

    @Test
    @DisplayName("[RC-08] 3회째 회차 × 운영자 대행 송장 동시 — 송장은 배치가 먼저 3회로 올린 경우만 성공 · 아니면 409 · 횟수 3 · 송장 이력 ≤ 1")
    void thirdNoticeRacingShipmentOnBehalf() throws Exception {
        List<LocalDate> days = pastBusinessDays(3);
        OrderDeliveryGroup group = overdueSince(prepared(paidGroup()), days.get(0));
        scheduler.run(days.get(0).atTime(FIRST_SLOT));
        scheduler.run(days.get(1).atTime(FIRST_SLOT));
        assertThat(noticeRow(group)).containsEntry("overdue_notice_count", 2);

        List<String> results = ConcurrentCalls.race(
                () -> {
                    scheduler.run(days.get(2).atTime(FIRST_SLOT));
                    return "NOTICED";
                },
                () -> {
                    commandService.registerShipment(operator.getId(), group.getId(),
                            new AdminOrderDto.ShipmentRequest(DeliveryCarrier.CJ, "400090008001", "자동 알림 3회 무응답"));
                    return "SHIPPED";
                });

        assertThat(results.get(0)).isEqualTo("NOTICED");
        assertThat(results.get(1)).isIn("SHIPPED", "ORDER_ACT_ON_BEHALF_NOT_ALLOWED");
        assertThat(noticeRow(group)).containsEntry("overdue_notice_count", 3);
        int invoices = jdbc.queryForObject("SELECT COUNT(*) FROM order_fulfillment_history WHERE delivery_group_id = ? "
                + "AND event_type = 'INVOICE_REGISTERED'", Integer.class, group.getId());
        String status = (String) noticeRow(group).get("fulfillment_status");
        if (results.get(1).equals("SHIPPED")) {
            assertThat(status).isEqualTo("SHIPPING");
            assertThat(invoices).isEqualTo(1);
        } else {
            assertThat(status).isEqualTo("PREPARING");
            assertThat(invoices).isZero();
        }
    }

    @Test
    @DisplayName("[L-07] 배치로 3회 → 06d 「대행 가능」 → 06a 대행 송장 · 06b 검수 무응답 환불 → 06d 처리 지연에서 빠진다")
    void batchThresholdThenOperatorActs() throws Exception {
        List<LocalDate> days = pastBusinessDays(3);
        OrderDeliveryGroup group = overdueSince(prepared(paidGroup()), days.get(0));
        Long claimId = received(returnClaim(deliveredGroup(creamVariant, 1)));
        inspectOverdueSince(claimId, days.get(0));
        for (LocalDate day : days) {
            scheduler.run(day.atTime(FIRST_SLOT));
        }
        JsonNode page = json(adminGet(EXCEPTIONS + "?tab=DELAY"));
        assertThat(rowOfGroup(page, group).get("actOnBehalfAvailable").asBoolean()).isTrue();
        assertThat(rowOfClaim(page, claimId).get("actOnBehalfAvailable").asBoolean()).isTrue();
        adminGet(EXCEPTIONS + "/summary").andExpect(jsonPath("$.actOnBehalfCount").value(2));

        adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/shipment",
                Map.of("carrier", "CJ", "trackingNumber", "400090009001", "note", "자동 알림 3회 무응답"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.groups[0].status").value("SHIPPING"));
        adminPost(ADMIN_CLAIMS + "/" + claimId + "/refund-tasks", Map.of("detail", "자동 알림 3회 무응답"))
                .andExpect(status().isOk());

        assertThat(json(adminGet(EXCEPTIONS + "?tab=DELAY")).at("/page/content")).isEmpty();
        adminGet(EXCEPTIONS + "/summary").andExpect(jsonPath("$.tabCounts.DELAY").value(0))
                .andExpect(jsonPath("$.actOnBehalfCount").value(0));
        // 조치 뒤 회차는 대상이 아니다 — 횟수는 그대로 남는다.
        scheduler.run(LocalDate.now().atTime(SECOND_SLOT));
        assertThat(noticeRow(group)).containsEntry("overdue_notice_count", 3);
        assertThat(claimRow(claimId)).containsEntry("inspect_notice_count", 3);
    }

    // ------------------------------------------------------------------ 도우미

    /** 오늘 이전의 영업일 n 개 — 오래된 순. 회차 시각은 모두 지금보다 앞이다. */
    private List<LocalDate> pastBusinessDays(int n) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate day = LocalDate.now().minusDays(1);
        while (days.size() < n) {
            if (calendar.isBusinessDay(day)) {
                days.add(0, day);
            }
            day = day.minusDays(1);
        }
        return days;
    }

    /** 발송 기한을 첫 회차 전날 끝으로 소급한다 — 시각만 바꾸고 상태는 실제 경로로 만든 그대로다. */
    private OrderDeliveryGroup overdueSince(OrderDeliveryGroup group, LocalDate firstNoticeDay) {
        backdateShipDueAt(group, firstNoticeDay.minusDays(1).atTime(23, 59, 59));
        return group;
    }

    private void inspectOverdueSince(Long claimId, LocalDate firstNoticeDay) {
        jdbc.update("UPDATE order_claim SET inspect_due_at = ? WHERE claim_id = ?",
                firstNoticeDay.minusDays(1).atTime(23, 59, 59), claimId);
    }

    /** 회수 추적상 브랜드 도착(입고 확인 전) — 받는 API 가 없는 구간이라 추적 반영의 도메인 전이를 그대로 탄다. */
    private Long arrivedClaim(Long claimId) {
        Map<String, Object> collection = collectionRow(collectionIdOf(claimId));
        int moved = transactionTemplate.execute(tx -> claimRepository.markArrived(collectionIdOf(claimId),
                DeliveryCarrier.valueOf((String) collection.get("carrier")), (String) collection.get("tracking_number"),
                LocalDateTime.now()));
        assertThat(moved).isEqualTo(1);
        return claimId;
    }

    /** 06a 상세 · 06d 행 · DB 의 발송 알림 횟수와 마지막 시각이 같다(NB-08) — 06d 행을 돌려준다. */
    private JsonNode assertShipNoticeConsistent(OrderDeliveryGroup group, int expected, LocalDateTime noticedAt)
            throws Exception {
        assertThat(lastNoticeAt(group)).isEqualTo(noticedAt);
        JsonNode detail = json(orderDetail(group).andExpect(status().isOk()));
        assertThat(detail.at("/groups/0/shipping/overdueNoticeCount").asInt()).isEqualTo(expected);
        JsonNode row = rowOfGroup(json(adminGet(EXCEPTIONS + "?tab=DELAY")), group);
        assertThat(row.get("noticeCount").asInt()).isEqualTo(detail.at("/groups/0/shipping/overdueNoticeCount").asInt());
        assertThat(row.get("lastNoticeAt").asText()).isEqualTo(jsonTime(noticedAt));
        return row;
    }

    /**
     * 오늘 전에 알린 건의 다음 회차 — 오늘이 영업일이면 지금 이후 가장 이른 10 · 15시, 아니면 다음 영업일 10시. 시각표 조합 자체는
     * {@code AdminOrderExceptionAssemblerTest}가 고정한다 — 여기서는 배치가 남긴 마지막 시각이 그 계산에 들어가는지만 본다.
     */
    private LocalDateTime nextSlotAfterEarlierNotice(LocalDateTime asOf) {
        LocalDate today = asOf.toLocalDate();
        if (calendar.isBusinessDay(today)) {
            for (LocalTime slot : List.of(FIRST_SLOT, SECOND_SLOT)) {
                if (today.atTime(slot).isAfter(asOf)) {
                    return today.atTime(slot);
                }
            }
        }
        return calendar.addBusinessDays(today, 1).atTime(FIRST_SLOT);
    }

    private Map<String, Object> noticeRow(OrderDeliveryGroup group) {
        return jdbc.queryForMap("SELECT overdue_notice_count, last_overdue_notice_at, fulfillment_status "
                + "FROM order_delivery_group WHERE delivery_group_id = ?", group.getId());
    }

    private LocalDateTime lastNoticeAt(OrderDeliveryGroup group) {
        Timestamp value = (Timestamp) noticeRow(group).get("last_overdue_notice_at");
        return value == null ? null : value.toLocalDateTime();
    }

    private LocalDateTime lastInspectNoticeAt(Long claimId) {
        return ((Timestamp) claimRow(claimId).get("last_inspect_notice_at")).toLocalDateTime();
    }

    private static JsonNode rowOfGroup(JsonNode page, OrderDeliveryGroup group) {
        return StreamSupport.stream(page.at("/page/content").spliterator(), false)
                .filter(row -> row.get("kind").asText().equals("SHIP_OVERDUE")
                        && row.get("deliveryGroupId").asLong() == group.getId())
                .findFirst().orElseThrow(() -> new AssertionError("06d 행 없음: 하위주문 " + group.getId()));
    }

    private static JsonNode rowOfClaim(JsonNode page, Long claimId) {
        return StreamSupport.stream(page.at("/page/content").spliterator(), false)
                .filter(row -> row.at("/link/claimId").asLong() == claimId).findFirst()
                .orElseThrow(() -> new AssertionError("06d 행 없음: CLM-" + claimId));
    }

    private static LocalDateTime at(String text) {
        return LocalDateTime.parse(text.endsWith("Z") ? text.substring(0, text.length() - 1) : text);
    }

    private ResultActions orderDetail(OrderDeliveryGroup group) throws Exception {
        return adminGet(ADMIN_ORDERS + "/" + group.getOrder().getId());
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}
