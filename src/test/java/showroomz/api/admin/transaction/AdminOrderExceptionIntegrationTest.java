package showroomz.api.admin.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.utils.BusinessCalendar;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 예외 관리(06d · 40 설계서 6절 AE-01 ~ AE-12) — 처리 지연 3종 · 배송 예외 4종의 진입 · 이탈 조건, 서버 문장, 필터 · 검색 ·
 * 정렬 · 페이지, 요약. 06a 와의 대행 판정 정합(AE-04)은 {@code AdminOrderIntegrationTest} T7 이 본다. 상태는 실제 경로로만 옮기고
 * SQL 은 시각 소급 · 알림 횟수 · 배지 표시에만 쓴다.
 */
@IntegrationTest
class AdminOrderExceptionIntegrationTest extends ClaimTestSupport {

    private static final String EXCEPTIONS = "/v1/admin/order-exceptions";
    private static final Map<String, Object> CARD = Map.of("method", "CARD", "cardIssuer", "SHINHAN");

    @Autowired private BusinessCalendar calendar;
    @Autowired private DeliveryTrackerProperties trackerProperties;
    @Autowired private OrderProperties orderProperties;

    private String admin;
    private boolean trackerEnabled;
    private String badgeScope;

    @BeforeEach
    void setUpAdmin() {
        admin = adminToken(fixture.createAdmin("exceptions-ops@showroomz.test", "운영자"));
        trackerEnabled = trackerProperties.isEnabled();
        badgeScope = orderProperties.getException().getBadgeScope();
    }

    @AfterEach
    void restoreProperties() {
        trackerProperties.setEnabled(trackerEnabled);
        orderProperties.getException().setBadgeScope(badgeScope);
    }

    // ------------------------------------------------------------------ 처리 지연

    @Test
    @DisplayName("[AE-01] 발송 기한 경과 — 주문번호 · 기한 문장 · 경과 영업일(달력 기준) · 열기는 주문 상세")
    void shipOverdue() throws Exception {
        OrderDeliveryGroup group = prepared(paidGroup());
        LocalDateTime due = LocalDateTime.now().minusDays(6).withHour(23).withMinute(59).withSecond(59).withNano(0);
        backdateShipDueAt(group, due);

        JsonNode row = only(rows("?tab=DELAY"));
        assertThat(row.get("kind").asText()).isEqualTo("SHIP_OVERDUE");
        assertThat(row.get("targetNumber").asText()).isEqualTo(orderNumberOf(group));
        assertThat(row.get("dueBasisLabel").asText()).isEqualTo("공구 마감 + " + SHIPPING_LEAD_DAYS + "영업일(주문 시점 값)");
        int expected = calendar.businessDaysBetween(due.toLocalDate(), LocalDateTime.now().toLocalDate());
        assertThat(row.get("elapsedBusinessDays").asInt()).isEqualTo(expected);
        assertThat(row.get("elapsedLabel").asText()).isEqualTo(expected + "영업일");
        assertThat(row.get("overdue").asBoolean()).isTrue();
        assertThat(row.at("/link/type").asText()).isEqualTo("ORDER");
        assertThat(row.at("/link/deliveryGroupId").asLong()).isEqualTo(group.getId());
        assertThat(row.get("invoice").isNull()).isTrue();
    }

    @Test
    @DisplayName("[AE-02] 발송 기한 경과 — 검토 중 취소 요청 · 기한 없음 · 송장 등록은 빠지고 준비 시작으로는 빠지지 않는다")
    void shipOverdueExits() throws Exception {
        OrderDeliveryGroup fresh = paidGroup();
        backdateShipDueAt(fresh, LocalDateTime.now().minusDays(2));
        assertThat(rows("?tab=DELAY")).hasSize(1);
        prepared(fresh);
        assertThat(rows("?tab=DELAY")).hasSize(1);

        registerShipment(fresh.getId(), "CJ", newInvoice()).andExpect(jsonPath("$.succeeded").value(1));
        assertThat(rows("?tab=DELAY")).isEmpty();

        OrderDeliveryGroup requested = prepared(paidGroup());
        backdateShipDueAt(requested, LocalDateTime.now().minusDays(2));
        seedCancelRequest(requested);
        assertThat(rows("?tab=DELAY")).isEmpty();

        OrderDeliveryGroup noDue = prepared(paidGroup());
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = NULL WHERE delivery_group_id = ?", noDue.getId());
        assertThat(rows("?tab=DELAY")).isEmpty();
    }

    @Test
    @DisplayName("[AE-03] 자동 알림 · 다음 단계 — 1회면 「2회차 대기」 · 3회면 대행 가능(다음 회차 없음) · 요약 대행 가능 1건")
    void noticesAndNextStep() throws Exception {
        OrderDeliveryGroup group = prepared(paidGroup());
        backdateShipDueAt(group, LocalDateTime.now().minusDays(3));
        notices(group, 1);

        JsonNode row = only(rows("?tab=DELAY"));
        assertThat(row.get("noticeCount").asInt()).isEqualTo(1);
        assertThat(row.get("actOnBehalfAvailable").asBoolean()).isFalse();
        assertThat(row.get("nextStepLabel").asText()).startsWith("자동 알림 대기 · 2회차 ");
        assertThat(row.get("nextNoticeAt").isNull()).isFalse();
        assertThat(row.get("nextStepNote").isNull()).isTrue();

        notices(group, 3);
        row = only(rows("?tab=DELAY"));
        assertThat(row.get("actOnBehalfAvailable").asBoolean()).isTrue();
        assertThat(row.get("nextStepLabel").asText()).isEqualTo("자동 알림 3회 무응답 · 대행 가능");
        assertThat(row.get("nextStepNote").asText()).isEqualTo("송장 대행 · 직권 취소 — 주문 상세");
        assertThat(row.get("nextNoticeAt").isNull()).isTrue();
        adminGet(EXCEPTIONS + "/summary").andExpect(jsonPath("$.actOnBehalfCount").value(1));
    }

    @Test
    @DisplayName("[AE-05] 검수 지연 — 접수번호 · 「입고 + 2영업일」 · 열기는 클레임 · 3회면 운영자 환불 가능 · 판정하면 빠진다")
    void inspectOverdue() throws Exception {
        Long claimId = received(returnClaim(deliveredGroup(creamVariant, 1)));
        jdbc.update("UPDATE order_claim SET inspect_due_at = ? WHERE claim_id = ?",
                LocalDateTime.now().minusDays(4), claimId);

        JsonNode row = only(rows("?tab=DELAY"));
        assertThat(row.get("kind").asText()).isEqualTo("INSPECT_OVERDUE");
        assertThat(row.get("targetNumber").asText()).isEqualTo("CLM-" + claimId);
        assertThat(row.get("dueBasisLabel").asText()).isEqualTo("입고 + 2영업일");
        assertThat(row.at("/link/type").asText()).isEqualTo("CLAIM");
        assertThat(row.at("/link/claimId").asLong()).isEqualTo(claimId);

        jdbc.update("UPDATE order_claim SET inspect_notice_count = 3 WHERE claim_id = ?", claimId);
        row = only(rows("?tab=DELAY"));
        assertThat(row.get("actOnBehalfAvailable").asBoolean()).isTrue();
        assertThat(row.get("nextStepLabel").asText()).isEqualTo("자동 알림 3회 무응답 · 운영자 환불 가능");
        assertThat(row.get("nextStepNote").asText()).startsWith("운영자 사유 환불 편입 — 클레임 상세");

        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/pass", Map.of()).andExpect(status().isOk());
        assertThat(rows("?tab=DELAY")).isEmpty();
    }

    @Test
    @DisplayName("[AE-06] 재발송 지연 — 검수 통과 · 재발송비 결제 + 2영업일 · 알림 없음 · 최근 건 · 송장 등록 건은 없다")
    void reshipDelayed() throws Exception {
        Long exchange = passed(exchangeClaim(deliveredGroup(creamVariant, 1), addSamePriceVariant(creamVariant, 5)));
        Long rejected = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        String paymentId = json(userPost(USER_CLAIMS + "/" + rejected + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(status().isOk());
        assertThat(claimStatus(exchange)).isEqualTo("RESHIP_READY");
        assertThat(claimStatus(rejected)).isEqualTo("RESHIP_READY");
        assertThat(rows("?tab=DELAY")).isEmpty();

        LocalDateTime old = LocalDateTime.now().minusDays(10);
        jdbc.update("UPDATE order_claim SET stage_entered_at = ? WHERE claim_id IN (?, ?)", old, exchange, rejected);
        List<JsonNode> rows = rows("?tab=DELAY&kind=RESHIP_DELAYED");
        assertThat(rows).hasSize(2);
        JsonNode exchangeRow = byClaim(rows, exchange);
        assertThat(exchangeRow.get("dueBasisLabel").asText()).isEqualTo("검수 통과 + 2영업일");
        assertThat(exchangeRow.get("noticeCount").isNull()).isTrue();
        assertThat(exchangeRow.get("nextStepNote").asText()).isEqualTo("자동 알림 없음 — 근거 대기");
        assertThat(byClaim(rows, rejected).get("dueBasisLabel").asText()).isEqualTo("재발송비 결제 + 2영업일");

        registerReship(exchange, "CJ", newInvoice()).andExpect(status().isOk());
        assertThat(rows("?tab=DELAY")).extracting(row -> row.at("/link/claimId").asLong()).containsExactly(rejected);
    }

    // ------------------------------------------------------------------ 배송 예외

    @Test
    @DisplayName("[AE-07] 배송 예외 4종 — 기준 · 경과 문장 · 송장 전체 · 반송 완료 감지와 첫 스캔에 빠진다")
    void deliveryExceptions() throws Exception {
        trackerProperties.setEnabled(true);
        LocalDateTime now = LocalDateTime.now();
        OrderDeliveryGroup pickup = shippingGroup("400070001001");
        backdateShippedAt(pickup, now.minusHours(26).minusMinutes(5));
        alert(pickup, "PICKUP_UNCONFIRMED", null);
        OrderDeliveryGroup stalled = shippingGroup("400070001002");
        alert(stalled, "STALLED", now.minusDays(8).minusHours(1));
        OrderDeliveryGroup returning = returning(shippingGroup("400070001003"));
        jdbc.update("UPDATE order_delivery_group SET return_detected_at = ? WHERE delivery_group_id = ?",
                now.minusDays(2).minusHours(1), returning.getId());
        Long collecting = returnClaim(deliveredGroup(creamVariant, 1));
        jdbc.update("UPDATE order_claim_collection SET invoice_registered_at = ?, last_tracking_at = NULL "
                + "WHERE collection_id = ?", now.minusHours(25).minusMinutes(5), collectionIdOf(collecting));

        List<JsonNode> rows = rows("?tab=DELIVERY");
        assertThat(rows).extracting(row -> row.get("kind").asText()).containsExactlyInAnyOrder(
                "PICKUP_UNCONFIRMED", "TRACKING_STALLED", "RETURNING", "COLLECTION_UNSCANNED");
        JsonNode pickupRow = byKind(rows, "PICKUP_UNCONFIRMED");
        assertThat(pickupRow.get("basisLabel").asText()).isEqualTo("등록 후 24시간");
        assertThat(pickupRow.get("elapsedLabel").asText()).isEqualTo("26시간");
        assertThat(pickupRow.at("/invoice/trackingNumber").asText()).isEqualTo("400070001001");
        assertThat(pickupRow.get("handlerLabel").asText()).isEqualTo("브랜드 확인 · 시스템 알림");
        assertThat(byKind(rows, "TRACKING_STALLED").get("elapsedLabel").asText()).isEqualTo("8일 무갱신");
        assertThat(byKind(rows, "RETURNING").get("elapsedLabel").asText()).isEqualTo("감지 후 2일");
        JsonNode collectionRow = byKind(rows, "COLLECTION_UNSCANNED");
        assertThat(collectionRow.get("targetNumber").asText()).isEqualTo(jdbc.queryForObject(
                "SELECT o.order_number FROM orders o JOIN order_claim c ON c.order_id = o.order_id WHERE c.claim_id = ?",
                String.class, collecting));
        assertThat(collectionRow.at("/link/type").asText()).isEqualTo("CLAIM");
        assertThat(collectionRow.get("elapsedLabel").asText()).isEqualTo("25시간");

        jdbc.update("UPDATE order_delivery_group SET return_completed_at = ? WHERE delivery_group_id = ?", now,
                returning.getId());
        jdbc.update("UPDATE order_claim_collection SET last_tracking_at = ? WHERE collection_id = ?", now,
                collectionIdOf(collecting));
        assertThat(rows("?tab=DELIVERY")).extracting(row -> row.get("kind").asText())
                .containsExactlyInAnyOrder("PICKUP_UNCONFIRMED", "TRACKING_STALLED");
    }

    @Test
    @DisplayName("[AE-08] 추적 연동이 꺼져 있으면 회수 송장 미조회는 비어 있다 — 유형 건수 키는 0 으로 있다")
    void collectionUnscannedHiddenWhenTrackerOff() throws Exception {
        trackerProperties.setEnabled(false);
        Long collecting = returnClaim(deliveredGroup(creamVariant, 1));
        jdbc.update("UPDATE order_claim_collection SET invoice_registered_at = ? WHERE collection_id = ?",
                LocalDateTime.now().minusDays(2), collectionIdOf(collecting));

        assertThat(rows("?tab=DELIVERY")).isEmpty();
        adminGet(EXCEPTIONS + "/summary").andExpect(jsonPath("$.kindCounts.COLLECTION_UNSCANNED").value(0));
    }

    // ------------------------------------------------------------------ 필터 · 검색 · 정렬 · 요약

    @Test
    @DisplayName("[AE-09] 필터 · 검색 — 유형 · 탭에 없는 유형 400 · CLM 정확 · 주문번호 · 브랜드명 부분 · 없는 값 0행")
    void filterAndSearch() throws Exception {
        OrderDeliveryGroup group = prepared(paidGroup());
        backdateShipDueAt(group, LocalDateTime.now().minusDays(2));
        Long claimId = received(returnClaim(deliveredGroup(creamVariant, 1)));
        jdbc.update("UPDATE order_claim SET inspect_due_at = ? WHERE claim_id = ?",
                LocalDateTime.now().minusDays(3), claimId);

        assertThat(rows("?tab=DELAY&kind=INSPECT_OVERDUE")).extracting(row -> row.get("kind").asText())
                .containsExactly("INSPECT_OVERDUE");
        adminGet(EXCEPTIONS + "?tab=DELAY&kind=PICKUP_UNCONFIRMED").andExpect(status().isBadRequest());
        assertThat(rows("?tab=DELAY&keyword=CLM-" + claimId)).hasSize(1);
        String orderNumber = orderNumberOf(group);
        assertThat(rows("?tab=DELAY&keyword=" + orderNumber.substring(0, orderNumber.length() - 1)))
                .extracting(row -> row.get("kind").asText()).contains("SHIP_OVERDUE");
        assertThat(rows("?tab=DELAY&keyword=" + group.getMarketName())).hasSize(2);
        assertThat(rows("?tab=DELAY&keyword=없는검색어")).isEmpty();
    }

    @Test
    @DisplayName("[AE-10] 정렬 · 페이지 — 기한이 오래된 행이 위 · size=1 이면 다음 쪽 · 상한 초과 400 · asOf")
    void sortAndPage() throws Exception {
        OrderDeliveryGroup newer = prepared(paidGroup());
        backdateShipDueAt(newer, LocalDateTime.now().minusDays(1));
        OrderDeliveryGroup older = prepared(paidGroup());
        backdateShipDueAt(older, LocalDateTime.now().minusDays(5));

        assertThat(rows("?tab=DELAY")).extracting(row -> row.get("deliveryGroupId").asLong())
                .containsExactly(older.getId(), newer.getId());
        JsonNode page = json(adminGet(EXCEPTIONS + "?tab=DELAY&size=1").andExpect(status().isOk()));
        assertThat(page.get("asOf").isNull()).isFalse();
        assertThat(page.at("/page/pageInfo/totalResults").asLong()).isEqualTo(2);
        assertThat(page.at("/page/pageInfo/hasNext").asBoolean()).isTrue();
        assertThat(page.at("/page/content/0/deliveryGroupId").asLong()).isEqualTo(older.getId());
        adminGet(EXCEPTIONS + "?size=101").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[AE-11] 요약 — 처리 지연 = 목록 건수 · 유형 7키 · 배지 범위(ALL 합 · DELAY 처리 지연만)")
    void exceptionSummary() throws Exception {
        OrderDeliveryGroup overdue = prepared(paidGroup());
        backdateShipDueAt(overdue, LocalDateTime.now().minusDays(2));
        alert(shippingGroup("400070011001"), "STALLED", LocalDateTime.now().minusDays(9));

        JsonNode summary = json(adminGet(EXCEPTIONS + "/summary").andExpect(status().isOk()));
        assertThat(summary.at("/tabCounts/DELAY").asLong()).isEqualTo(rows("?tab=DELAY").size()).isEqualTo(1);
        assertThat(summary.at("/tabCounts/DELIVERY").asLong()).isEqualTo(1);
        assertThat(summary.get("kindCounts").size()).isEqualTo(7);
        assertThat(summary.get("badge").asLong()).isEqualTo(2);
        assertThat(summary.get("badgeScope").asText()).isEqualTo("ALL");

        orderProperties.getException().setBadgeScope("DELAY");
        adminGet(EXCEPTIONS + "/summary").andExpect(jsonPath("$.badge").value(1))
                .andExpect(jsonPath("$.badgeScope").value("DELAY"));
    }

    @Test
    @DisplayName("[AE-12] 권한 — 셀러 토큰 403 · 토큰 없음 401")
    void auth() throws Exception {
        mockMvc.perform(get(EXCEPTIONS).header(HttpHeaders.AUTHORIZATION, brandToken)).andExpect(status().isForbidden());
        mockMvc.perform(get(EXCEPTIONS)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 도우미

    private void notices(OrderDeliveryGroup group, int count) {
        jdbc.update("UPDATE order_delivery_group SET overdue_notice_count = ?, last_overdue_notice_at = ? "
                + "WHERE delivery_group_id = ?", count, LocalDateTime.now().minusDays(1), group.getId());
    }

    /** 감시 배치가 다는 배지 — 배치 대신 열만 적는다(배지 판정은 배치의 몫이다). */
    private void alert(OrderDeliveryGroup group, String alert, LocalDateTime lastTrackingAt) {
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = ?, last_tracking_at = ? WHERE delivery_group_id = ?",
                alert, lastTrackingAt, group.getId());
    }

    private List<JsonNode> rows(String query) throws Exception {
        JsonNode content = json(adminGet(EXCEPTIONS + query).andExpect(status().isOk())).at("/page/content");
        return StreamSupport.stream(content.spliterator(), false).toList();
    }

    private static JsonNode only(List<JsonNode> rows) {
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private static JsonNode byKind(List<JsonNode> rows, String kind) {
        return rows.stream().filter(row -> row.get("kind").asText().equals(kind)).findFirst().orElseThrow();
    }

    private static JsonNode byClaim(List<JsonNode> rows, Long claimId) {
        return rows.stream().filter(row -> row.at("/link/claimId").asLong() == claimId).findFirst().orElseThrow();
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }
}
