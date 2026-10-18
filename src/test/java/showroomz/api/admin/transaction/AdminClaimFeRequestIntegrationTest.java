package showroomz.api.admin.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.utils.BusinessCalendar;
import showroomz.support.IntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 46 수정계획 — 클레임이 걸린 요청. 06b 상세 {@code orderId}(2-1) · 「경과」 영업일(3-1) · 법정 청약철회 기한(3-2) ·
 * 06a ① 추가 결제(4-7) · 앱 재발송비 기한 경과(7-3) · 앱 사유 문구(7-4). 정산 반영(3-3)은 정산 테스트가 본다.
 */
@IntegrationTest
class AdminClaimFeRequestIntegrationTest extends ClaimTestSupport {

    private static final String ADMIN_CLAIMS = "/v1/admin/claims";
    private static final String ADMIN_ORDERS = "/v1/admin/orders";
    private static final Map<String, Object> CARD = Map.of("method", "CARD", "cardIssuer", "SHINHAN");

    @Autowired private BusinessCalendar businessCalendar;

    private String admin;

    @BeforeEach
    void setUpAdmin() {
        admin = adminToken(fixture.createAdmin("fe-request-claims@showroomz.test", "운영자"));
    }

    @Test
    @DisplayName("[46-2-1 · 3-1] 06b — 상세에 orderId · deliveryGroupId(주문 상세 열기) · 목록 「경과」 영업일 = 단계 진입부터 · 파트너 달력일은 그대로")
    void claimDetailOrderIdAndBusinessElapsed() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = returnClaim(group);
        LocalDateTime entered = LocalDateTime.now().minusDays(10).withNano(0);
        jdbc.update("UPDATE order_claim SET stage_entered_at = ? WHERE claim_id = ?", entered, claimId);

        JsonNode detail = json(adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(status().isOk()));
        assertThat(detail.get("orderId").asLong()).isEqualTo(orderIdOf(group));
        assertThat(detail.get("deliveryGroupId").asLong()).isEqualTo(group.getId());
        adminGet(ADMIN_ORDERS + "/" + detail.get("orderId").asLong()).andExpect(status().isOk());
        assertThat(detail.get("legalDeadline").isNull()).isTrue();
        assertThat(detail.get("settlement").isNull()).isTrue();

        JsonNode row = rowOf(json(adminGet(ADMIN_CLAIMS + "?size=100").andExpect(status().isOk())), claimId);
        assertThat(row.at("/claim/elapsedDays").asInt()).isEqualTo(10);
        assertThat(row.get("elapsedBusinessDays").asInt())
                .isEqualTo(businessCalendar.businessDaysBetween(entered.toLocalDate(), LocalDate.now()))
                .isLessThan(10);
    }

    @Test
    @DisplayName("[46-3-2] 법정 청약철회 기한 — 운영자 개설만 · 공급일 + 3개월 · 인지일 + 30일 · 이른 날 · 인지일 없으면 공급 기준 · 범위 밖 인지일 400")
    void legalDeadlineForOperatorOpenedClaim() throws Exception {
        LocalDateTime deliveredAt = LocalDateTime.now().minusDays(40).withNano(0);
        LocalDate supplied = deliveredAt.toLocalDate();
        LocalDate noticed = LocalDate.now().minusDays(5);

        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1, deliveredAt);
        Long claimId = json(defectClaim(group, noticed.toString()).andExpect(status().isOk())).at("/claimIds/0").asLong();
        JsonNode deadline = json(adminGet(ADMIN_CLAIMS + "/" + claimId)).get("legalDeadline");
        assertThat(deadline.get("suppliedOn").asText()).isEqualTo(supplied.toString());
        assertThat(deadline.get("supplyDeadline").asText()).isEqualTo(supplied.plusMonths(3).toString());
        assertThat(deadline.get("noticedOn").asText()).isEqualTo(noticed.toString());
        assertThat(deadline.get("noticeDeadline").asText()).isEqualTo(noticed.plusDays(30).toString());
        assertThat(deadline.get("appliedDeadline").asText()).isEqualTo(noticed.plusDays(30).toString());

        OrderDeliveryGroup noNotice = deliveredGroup(creamVariant, 1, deliveredAt);
        Long plain = json(defectClaim(noNotice, null).andExpect(status().isOk())).at("/claimIds/0").asLong();
        JsonNode supplyOnly = json(adminGet(ADMIN_CLAIMS + "/" + plain)).get("legalDeadline");
        assertThat(supplyOnly.get("noticedOn").isNull()).isTrue();
        assertThat(supplyOnly.get("noticeDeadline").isNull()).isTrue();
        assertThat(supplyOnly.get("appliedDeadline").asText()).isEqualTo(supplied.plusMonths(3).toString());

        OrderDeliveryGroup invalid = deliveredGroup(creamVariant, 1, deliveredAt);
        defectClaim(invalid, supplied.minusDays(1).toString()).andExpect(status().isBadRequest());
        defectClaim(invalid, LocalDate.now().plusDays(1).toString()).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_claim WHERE delivery_group_id = ?", Integer.class,
                invalid.getId())).isZero();
    }

    @Test
    @DisplayName("[46-4-7] 06a ① 추가 결제 — 결제한 반려 재발송비가 원래 결제와 따로 · 06b 링크 · 취소 가능 잔액 · 결제 전에는 없다")
    void orderDetailAdditionalPayment() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = rejectedClaim(returnClaim(group));
        Long orderId = orderIdOf(group);
        assertThat(json(adminGet(ADMIN_ORDERS + "/" + orderId)).get("additionalPayments")).isEmpty();

        String paymentId = json(userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(jsonPath("$.paymentStatus").value("PAID"));

        JsonNode rows = json(adminGet(ADMIN_ORDERS + "/" + orderId)).get("additionalPayments");
        assertThat(rows).hasSize(1);
        JsonNode row = rows.get(0);
        int amount = jdbc.queryForObject("SELECT amount FROM order_claim_charge WHERE charge_id = ?", Integer.class,
                chargeIdOf(claimId));
        assertThat(row.get("claimId").asLong()).isEqualTo(claimId);
        assertThat(row.get("claimNumber").asText()).isEqualTo("CLM-" + claimId);
        assertThat(row.get("deliveryGroupId").asLong()).isEqualTo(group.getId());
        assertThat(row.get("chargeType").asText()).isEqualTo("REJECT_RESHIP");
        assertThat(row.get("typeLabel").asText()).isEqualTo("반려 재발송비");
        assertThat(row.get("amount").asInt()).isEqualTo(amount).isPositive();
        assertThat(row.get("status").asText()).isEqualTo("PAID");
        assertThat(row.get("paymentId").asText()).isEqualTo(paymentId);
        assertThat(row.get("cancellableAmount").asInt()).isEqualTo(amount);
        assertThat(row.get("paidAt").isNull()).isFalse();
    }

    @Test
    @DisplayName("[46-7-3] 앱 재발송비 — 결제 기한이 지나면 overdue = true(날짜는 그대로 · 보관 블록) · 기한 전에는 false")
    void reshipFeeOverdueFlag() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        JsonNode before = json(userGet(USER_CLAIMS + "/" + claimId).andExpect(status().isOk())).get("reshipFee");
        assertThat(before.get("state").asText()).isEqualTo("PAYABLE");
        assertThat(before.get("overdue").asBoolean()).isFalse();
        assertThat(before.get("storage").isNull()).isTrue();

        LocalDateTime due = LocalDateTime.now().minusDays(2).withNano(0);
        jdbc.update("UPDATE order_claim_charge SET due_at = ? WHERE charge_id = ?", due, chargeIdOf(claimId));
        JsonNode after = json(userGet(USER_CLAIMS + "/" + claimId)).get("reshipFee");
        assertThat(after.get("overdue").asBoolean()).isTrue();
        assertThat(after.get("dueDate").asText()).isEqualTo(due.toLocalDate().toString());
        assertThat(after.get("storage").isNull()).isFalse();
    }

    @Test
    @DisplayName("[46-7-4] 앱 사유 문구 — 상세 · 폼은 시안 문구(「상품 파손 · 불량」 · 「오배송」) · 파트너 · 어드민은 원래 라벨")
    void appReasonLabels() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = requestClaim(group, ClaimType.RETURN, ClaimReason.DAMAGED_OR_DEFECTIVE, allItems(group),
                newInvoice()).claimIds().get(0);

        userGet(USER_CLAIMS + "/" + claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.reasonLabel").value("상품 파손 · 불량"));
        JsonNode adminRow = rowOf(json(adminGet(ADMIN_CLAIMS + "?size=100")), claimId);
        assertThat(adminRow.at("/claim/reasonLabel").asText()).isEqualTo("배송 상품 파손 및 불량");

        OrderDeliveryGroup other = deliveredGroup(creamVariant, 1);
        JsonNode reasons = json(userGet(USER_CLAIMS + "/form?orderProductId=" + items(other).get(0).getId()
                + "&type=RETURN").andExpect(status().isOk())).get("reasons");
        Map<String, String> labels = new java.util.HashMap<>();
        reasons.forEach(r -> labels.put(r.get("code").asText(), r.get("label").asText()));
        assertThat(labels).containsEntry("DAMAGED_OR_DEFECTIVE", "상품 파손 · 불량")
                .containsEntry("WRONG_OR_LATE_DELIVERY", "오배송")
                .containsEntry("CHANGE_OF_MIND", "단순 변심");
    }

    // ------------------------------------------------------------------ 도우미

    private ResultActions defectClaim(OrderDeliveryGroup group, String noticedOn) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "items", List.of(Map.of("orderProductId", items(group).get(0).getId(), "quantity", 1)),
                "reasonCode", "DAMAGED_OR_DEFECTIVE", "detail", "구매확정 후 용기 파손",
                "evidenceImageUrls", List.of("https://img.test/d.jpg")));
        if (noticedOn != null) {
            body.put("defectNoticedOn", noticedOn);
        }
        return adminPost(ADMIN_ORDERS + "/groups/" + group.getId() + "/defect-claims", body);
    }

    private Long orderIdOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT order_id FROM order_delivery_group WHERE delivery_group_id = ?", Long.class,
                group.getId());
    }

    private static JsonNode rowOf(JsonNode page, Long claimId) {
        return StreamSupport.stream(page.get("content").spliterator(), false)
                .filter(row -> row.at("/claim/claimId").asLong() == claimId).findFirst().orElseThrow();
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}
