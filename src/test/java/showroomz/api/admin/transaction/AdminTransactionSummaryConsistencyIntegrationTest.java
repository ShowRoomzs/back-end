package showroomz.api.admin.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 거래 관리 네 화면의 요약 정합(43 X-10 · 45 보완 시나리오 5절 L-03) — 각 요약은 화면별 테스트가 따로 보지만 <b>한 fixture 에서 넷을
 * 같이</b> 본 적이 없다. 06d 처리 지연 = 06a 툴바 「처리 지연 N건」의 출처 · 06c 배지 = 대기 + 실패 · 06b 전체 = 파트너 A + B 요약 합 ·
 * 06a 배송 이상 = 06d 배송 예외 중 하위주문 유형. 수치 정합의 회귀 방지용이다.
 */
@IntegrationTest
class AdminTransactionSummaryConsistencyIntegrationTest extends ClaimTestSupport {

    private static final String ADMIN_ORDERS = "/v1/admin/orders";
    private static final String ADMIN_CLAIMS = "/v1/admin/claims";
    private static final String ADMIN_REFUNDS = "/v1/admin/refunds";
    private static final String EXCEPTIONS = "/v1/admin/order-exceptions";

    private String admin;

    @BeforeEach
    void setUpAdmin() {
        admin = adminToken(fixture.createAdmin("summary-ops@showroomz.test", "운영자"));
    }

    @Test
    @DisplayName("[L-03] 한 fixture · 네 요약 — 06d 처리 지연 = 목록 건수 = 06a 기한 경과 하위주문 + 06b 검수 지연 · 06c 배지 = 대기 + 실패 · 06b 전체 = 파트너 A + B · 06a 배송 이상 = 06d 하위주문 배송 예외")
    void fourSummariesAgree() throws Exception {
        // 06a · 06d — 발송 기한 경과 2(하나는 알림 3회) · 추적 정지 1 · 반송 중 1
        OrderDeliveryGroup overdue = prepared(paidGroup());
        backdateShipDueAt(overdue, LocalDateTime.now().minusDays(2));
        OrderDeliveryGroup overdueReached = prepared(paidGroup());
        backdateShipDueAt(overdueReached, LocalDateTime.now().minusDays(4));
        jdbc.update("UPDATE order_delivery_group SET overdue_notice_count = 3 WHERE delivery_group_id = ?", overdueReached.getId());
        OrderDeliveryGroup stalled = shippingGroup(newInvoice());
        jdbc.update("UPDATE order_delivery_group SET tracking_alert = 'STALLED', last_tracking_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusDays(9), stalled.getId());
        returning(shippingGroup(newInvoice()));
        // 06b · 06d — 브랜드 A 검수 지연 1 · 회수 중 1 / 브랜드 B 반려 보류 1
        Long inspectOverdue = received(returnClaim(deliveredGroup(creamVariant, 1)));
        jdbc.update("UPDATE order_claim SET inspect_due_at = ? WHERE claim_id = ?", LocalDateTime.now().minusDays(3), inspectOverdue);
        returnClaim(deliveredGroup(creamVariant, 1));
        String otherToken = otherBrandRejectHoldClaim();
        // 06c — 집행 대기 2(운영자 사유) · 실패 1(PG 거절 직권 취소)
        operatorRefund(deliveredGroup(creamVariant, 1), 5_000);
        operatorRefund(deliveredGroup(creamVariant, 1), 7_000);
        OrderDeliveryGroup failing = paidGroup(creamVariant, 1);
        fake.willFailCancel(paymentIdOf(failing), FakePaymentGateway.Failure.REJECTED);
        directCancel(List.of(failing.getId()), "SOLD_OUT", "품절").andExpect(jsonPath("$.succeeded").value(1));

        JsonNode orders = json(adminGet(ADMIN_ORDERS + "/summary").andExpect(status().isOk())).get("tabCounts");
        JsonNode claims = json(adminGet(ADMIN_CLAIMS + "/summary").andExpect(status().isOk()));
        JsonNode refunds = json(adminGet(ADMIN_REFUNDS + "/summary").andExpect(status().isOk()));
        JsonNode exceptions = json(adminGet(EXCEPTIONS + "/summary").andExpect(status().isOk()));

        // 06d 처리 지연(06a 툴바의 출처) = 목록 건수 = 기한 경과 하위주문(06a 행의 shipOverdue) + 검수 지연(06b 행의 overdue)
        long delay = exceptions.at("/tabCounts/DELAY").asLong();
        assertThat(delay).isEqualTo(totalOf(EXCEPTIONS + "?tab=DELAY", "/page/pageInfo/totalResults")).isEqualTo(3);
        long shipOverdueGroups = countTrue(json(adminGet(ADMIN_ORDERS + "?size=100")).get("content"), "/shipOverdue");
        long inspectOverdueRows = countTrue(json(adminGet(ADMIN_CLAIMS + "?tab=INSPECTION&size=100")).get("content"), "/claim/overdue");
        assertThat(delay).isEqualTo(shipOverdueGroups + inspectOverdueRows);
        assertThat(exceptions.at("/kindCounts/SHIP_OVERDUE").asLong()).isEqualTo(shipOverdueGroups).isEqualTo(2);
        assertThat(exceptions.at("/kindCounts/INSPECT_OVERDUE").asLong()).isEqualTo(inspectOverdueRows).isEqualTo(1);
        assertThat(exceptions.get("actOnBehalfCount").asLong()).isEqualTo(1);
        orderDetail(overdueReached).andExpect(jsonPath("$.groups[0].actions.canRegisterShipment").value(true));
        orderDetail(overdue).andExpect(jsonPath("$.groups[0].actions.canRegisterShipment").value(false));

        // 06a 배송 이상(주문 단위 · 하위주문 하나씩) = 06d 배송 예외 중 하위주문 유형(집화 · 추적 정지 · 반송)
        long groupIssues = exceptions.at("/kindCounts/PICKUP_UNCONFIRMED").asLong()
                + exceptions.at("/kindCounts/TRACKING_STALLED").asLong() + exceptions.at("/kindCounts/RETURNING").asLong();
        assertThat(orders.get("DELIVERY_ISSUE").asLong()).isEqualTo(groupIssues).isEqualTo(2);
        assertThat(orders.get("ALL").asLong()).isEqualTo(totalOf(ADMIN_ORDERS, "/pageInfo/totalResults"));

        // 06c 배지 = 집행 대기 + 실패 · 탭 숫자 = 목록 건수
        long pending = refunds.at("/tabs/PENDING/count").asLong();
        long failed = refunds.at("/tabs/FAILED/count").asLong();
        assertThat(refunds.get("badge").asLong()).isEqualTo(pending + failed).isEqualTo(3);
        assertThat(pending).isEqualTo(totalOf(ADMIN_REFUNDS + "?tab=PENDING", "/pageInfo/totalResults")).isEqualTo(2);
        assertThat(failed).isEqualTo(totalOf(ADMIN_REFUNDS + "?tab=FAILED", "/pageInfo/totalResults")).isEqualTo(1);
        assertThat(refunds.at("/tabs/PENDING/amount").asLong()).isEqualTo(12_000);

        // 06b 전체 = 파트너 A 요약 + 파트너 B 요약 · 탭마다 같다
        JsonNode partnerA = json(sellerGet(SELLER_CLAIMS + "/summary").andExpect(status().isOk())).get("tabCounts");
        JsonNode partnerB = json(sellerGet(otherToken, SELLER_CLAIMS + "/summary").andExpect(status().isOk())).get("tabCounts");
        for (String tab : List.of("ALL", "COLLECT_WAIT", "COLLECTING", "INSPECTION", "RESHIP", "REJECT_HOLD", "DONE")) {
            assertThat(claims.at("/tabCounts/" + tab).asLong()).as(tab)
                    .isEqualTo(partnerA.get(tab).asLong() + partnerB.get(tab).asLong());
        }
        assertThat(claims.at("/tabCounts/ALL").asLong()).isEqualTo(3);
        assertThat(claims.at("/tabCounts/ALL").asLong()).isEqualTo(totalOf(ADMIN_CLAIMS + "?tab=ALL", "/pageInfo/totalResults"));
    }

    // ------------------------------------------------------------------ 도우미

    /** 다른 브랜드의 반려 보류 클레임 — 그 브랜드 토큰으로 준비 · 송장 · 입고 · 반려까지. 브랜드 토큰을 돌려준다. */
    private String otherBrandRejectHoldClaim() throws Exception {
        BrandFixture.Brand other = otherBrand();
        String token = sellerToken(other.seller());
        ProductVariant variant = openOtherBrandGroupBuy(other);
        OrderDeliveryGroup group = paidOtherBrandGroup(other, variant);
        sellerPost(token, SELLER_ORDERS + "/prepare-start", Map.of("deliveryGroupIds", List.of(group.getId())))
                .andExpect(jsonPath("$.succeeded").value(1));
        sellerPost(token, SELLER_ORDERS + "/shipments", Map.of("rows", List.of(shipmentRow(group.getId(), "CJ", newInvoice()))))
                .andExpect(jsonPath("$.succeeded").value(1));
        LocalDateTime deliveredAt = LocalDateTime.now().minusHours(1).withNano(0);
        fulfillmentService.applyTracking(deliveryGroupRepository.findOwned(group.getId(), other.marketId()).orElseThrow(),
                Optional.of(new TrackSnapshot(deliveredAt, deliveredAt, false, false)), LocalDateTime.now(), 24, 7);
        OrderProduct product = items(group).get(0);
        Long claimId = requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND,
                List.of(new showroomz.domain.order.service.OrderClaimService.Item(product.getId(), 1)), newInvoice())
                .claimIds().get(0);
        sellerPost(token, SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                .andExpect(jsonPath("$.succeeded").value(1));
        sellerPost(token, SELLER_CLAIMS + "/" + claimId + "/inspection/reject", rejectBody()).andExpect(status().isOk());
        assertThat(claimStatus(claimId)).isEqualTo("REJECT_HOLD");
        return token;
    }

    private void operatorRefund(OrderDeliveryGroup group, int amount) throws Exception {
        mockMvc.perform(post(ADMIN_ORDERS + "/groups/" + group.getId() + "/refund-tasks")
                        .header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("reason", "RECALL", "amount", amount, "detail", "위해성 리콜"))))
                .andExpect(status().isOk());
    }

    private String paymentIdOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT o.paid_payment_id FROM orders o JOIN order_delivery_group g "
                + "ON g.order_id = o.order_id WHERE g.delivery_group_id = ?", String.class, group.getId());
    }

    private long totalOf(String url, String pointer) throws Exception {
        return json(adminGet(url).andExpect(status().isOk())).at(pointer).asLong();
    }

    /** 06a 행은 하위주문 요약 배열을, 06b 행은 클레임을 품는다 — 포인터가 참인 하위주문 · 행 수. */
    private static long countTrue(JsonNode rows, String pointer) {
        long count = 0;
        for (JsonNode row : rows) {
            if (row.has("groups")) {
                for (JsonNode group : row.get("groups")) {
                    count += group.at(pointer).asBoolean() ? 1 : 0;
                }
            } else {
                count += row.at(pointer).asBoolean() ? 1 : 0;
            }
        }
        return count;
    }

    private ResultActions orderDetail(OrderDeliveryGroup group) throws Exception {
        return adminGet(ADMIN_ORDERS + "/" + group.getOrder().getId());
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }
}
