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
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 반품·교환(06b · 38 설계서 9절 AC-01 ~ AC-12) — 파트너 11 과 같은 행 + 어드민 열, 그리고 운영자의 유일한 쓰기인
 * 반려 이의 인용. 인용은 2026-10-09 확정분(41 보고 1번)을 본다: 환불액 서버 계산 · 귀책 브랜드 · 결제된 재발송비 자동 취소 ·
 * 차감된 재발송비 환원 · 재발송 대기에서도 인용.
 */
@IntegrationTest
class AdminClaimIntegrationTest extends ClaimTestSupport {

    private static final String ADMIN_CLAIMS = "/v1/admin/claims";
    private static final String ADMIN_REFUNDS = "/v1/admin/refunds";
    private static final Map<String, Object> CARD = Map.of("method", "CARD", "cardIssuer", "SHINHAN");

    private String admin;

    @BeforeEach
    void setUpAdmin() {
        admin = adminToken(fixture.createAdmin("claims-ops@showroomz.test", "운영자"));
    }

    // ------------------------------------------------------------------ 조회

    @Test
    @DisplayName("[AC-01] 목록 — 전 브랜드의 클레임이 함께 · 브랜드명 · 귀책 열 · 파트너 행과 같은 claimId")
    void listAllBrands() throws Exception {
        Long mine = returnClaim(deliveredGroup(creamVariant, 1));
        Long other = otherBrandReshipReadyClaim();

        JsonNode rows = json(adminGet(ADMIN_CLAIMS).andExpect(status().isOk())).get("content");

        assertThat(rows).extracting(row -> row.get("claim").get("claimId").asLong()).containsExactlyInAnyOrder(mine, other);
        JsonNode mineRow = rowOf(rows, mine);
        assertThat(mineRow.get("brandName").asText()).isNotBlank();
        assertThat(mineRow.get("feeBearer").asText()).isEqualTo("CONSUMER");
        assertThat(mineRow.get("feeBearerLabel").asText()).isEqualTo("소비자 귀책");
        assertThat(rowOf(rows, other).get("feeBearer").asText()).isEqualTo("SELLER");
        assertThat(rowOf(rows, other).get("brandName").asText()).isNotEqualTo(mineRow.get("brandName").asText());

        JsonNode partner = json(sellerGet(SELLER_CLAIMS + "?tab=ALL")).get("content");
        assertThat(partner).extracting(row -> row.get("claimId").asLong()).containsExactly(mine);
    }

    @Test
    @DisplayName("[AC-02] 목록 — marketId · tab · types 로 좁힌다")
    void listFilters() throws Exception {
        Long rejected = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        Long exchange = exchangeClaim(deliveredGroup(creamVariant, 1), addSamePriceVariant(creamVariant, 5));
        Long other = otherBrandReshipReadyClaim();

        assertThat(ids(adminGet(ADMIN_CLAIMS + "?marketId=" + brand.marketId()))).containsExactlyInAnyOrder(rejected, exchange);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?tab=REJECT_HOLD"))).containsExactly(rejected);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?types=EXCHANGE"))).containsExactlyInAnyOrder(exchange, other);
    }

    @Test
    @DisplayName("[AC-03] 목록 — 검수에서 브랜드 귀책으로 바꾼 반려는 faultChangedToSeller 가 참")
    void faultChangedToSellerColumn() throws Exception {
        Long claimId = received(returnClaim(deliveredGroup(creamVariant, 1)));
        Map<String, Object> body = new HashMap<>(rejectBody());
        body.put("faultChangedToSeller", true);
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", body).andExpect(status().isOk());

        JsonNode row = rowOf(json(adminGet(ADMIN_CLAIMS)).get("content"), claimId);
        assertThat(row.get("faultChangedToSeller").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("[AC-04] 요약 — 탭 건수는 파트너 요약과 같고 검수 기한 경과가 지연에 잡힌다")
    void claimSummary() throws Exception {
        Long receivedId = received(returnClaim(deliveredGroup(creamVariant, 1)));
        returnClaim(deliveredGroup(creamVariant, 1));
        jdbc.update("UPDATE order_claim SET inspect_due_at = ? WHERE claim_id = ?",
                LocalDateTime.now().minusDays(3), receivedId);

        JsonNode partner = json(sellerGet(SELLER_CLAIMS + "/summary"));
        adminGet(ADMIN_CLAIMS + "/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.tabCounts.ALL").value(partner.get("tabCounts").get("ALL").asLong()))
                .andExpect(jsonPath("$.tabCounts.ALL").value(2))
                .andExpect(jsonPath("$.kpi.overdue").value(1));
    }

    @Test
    @DisplayName("[AC-05] 상세 — 인용 가능 여부 · 서버 계산 환불액 · 검수 알림")
    void detailFlags() throws Exception {
        Long rejected = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        Long exchangeRejected = rejectedClaim(exchangeClaim(deliveredGroup(creamVariant, 1),
                addSamePriceVariant(creamVariant, 5)));
        Long receivedId = received(returnClaim(deliveredGroup(creamVariant, 1)));
        LocalDateTime noticedAt = LocalDateTime.now().minusHours(2).withNano(0);
        jdbc.update("UPDATE order_claim SET inspect_due_at = ?, inspect_notice_count = 2, last_inspect_notice_at = ? "
                + "WHERE claim_id = ?", LocalDateTime.now().minusDays(7), noticedAt, receivedId);

        adminGet(ADMIN_CLAIMS + "/" + rejected).andExpect(status().isOk())
                .andExpect(jsonPath("$.canAcceptDispute").value(true))
                .andExpect(jsonPath("$.disputeRefundAmount").value(CREAM_PRICE))
                .andExpect(jsonPath("$.inspectOverdueBusinessDays").doesNotExist());
        adminGet(ADMIN_CLAIMS + "/" + exchangeRejected)
                .andExpect(jsonPath("$.canAcceptDispute").value(false))
                .andExpect(jsonPath("$.disputeRefundAmount").doesNotExist());
        JsonNode detail = json(adminGet(ADMIN_CLAIMS + "/" + receivedId));
        assertThat(detail.get("canAcceptDispute").asBoolean()).isFalse();
        assertThat(detail.get("inspectNotice").get("count").asInt()).isEqualTo(2);
        assertThat(detail.get("inspectNotice").get("lastAt").isNull()).isFalse();
        assertThat(detail.get("inspectOverdueBusinessDays").asInt()).isPositive();
    }

    @Test
    @DisplayName("[AC-06] 상세 — 없는 id · 결제 대기(접수 전)는 404")
    void detailNotFound() throws Exception {
        adminGet(ADMIN_CLAIMS + "/999999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));

        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        JsonNode created = json(userPost(USER_CLAIMS, claimBody(group, "EXCHANGE", "CHANGE_OF_MIND",
                items(group).get(0).getId(), null, addSamePriceVariant(creamVariant, 5).getVariantId()))
                .andExpect(status().isCreated()));
        long draftId = created.get("claimIds").get(0).asLong();
        assertThat(claimStatus(draftId)).isEqualTo("PAYMENT_PENDING");
        adminGet(ADMIN_CLAIMS + "/" + draftId).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 인용

    @Test
    @DisplayName("[AC-07] 인용 — 서버 계산 금액 · 귀책 브랜드 · 재발송비 요청 소멸 · 환불 큐 편입 · 반품 수량 · 이력")
    void acceptDispute() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = rejectedClaim(returnClaim(group));
        assertThat(chargeStatusOf(claimId)).isEqualTo("PENDING");

        JsonNode accepted = json(accept(claimId).andExpect(status().isOk()));

        long taskId = accepted.get("refundTaskId").asLong();
        assertThat(accepted.get("refundNo").asText()).isEqualTo("RFD-" + taskId);
        assertThat(accepted.get("amount").asInt()).isEqualTo(CREAM_PRICE);
        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REFUNDED")
                .containsEntry("refunded_amount", CREAM_PRICE).containsEntry("fault_changed_to_seller", true);
        assertThat(chargeStatusOf(claimId)).isEqualTo("VOID");
        assertThat(jdbc.queryForMap("SELECT * FROM order_refund_task WHERE refund_task_id = ?", taskId))
                .containsEntry("source", "OPERATOR_REASON").containsEntry("origin", "OPERATOR")
                .containsEntry("reason_code", "DISPUTE_ACCEPTED").containsEntry("status", "PENDING")
                .containsEntry("refund_amount", CREAM_PRICE);
        assertThat(((Number) jdbc.queryForObject("SELECT returned_quantity FROM order_product WHERE order_product_id = ?",
                Integer.class, items(group).get(0).getId())).intValue()).isEqualTo(1);
        assertThat(events(claimId)).last().isEqualTo("DISPUTE_ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT detail FROM order_claim_history WHERE claim_id = ? "
                + "AND event_type = 'DISPUTE_ACCEPTED'", String.class, claimId)).contains("재발송비 결제 요청 취소");
        assertThat(jdbc.queryForList("SELECT event_type FROM order_fulfillment_history WHERE delivery_group_id = ?",
                String.class, group.getId())).contains("REFUND_ENQUEUED_BY_OPERATOR");
        // 편입만 — 돈은 나가지 않았다.
        assertThat(fake.partialCancelCalls()).isEmpty();
    }

    @Test
    @DisplayName("[AC-07b] 재발송비를 이미 결제한 반려(재발송 대기) — 인용되고 그 결제는 자동 취소된다")
    void acceptAfterReshipFeePaid() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        String paymentId = json(userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        assertThat(claimStatus(claimId)).isEqualTo("RESHIP_READY");

        adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(jsonPath("$.canAcceptDispute").value(true))
                .andExpect(jsonPath("$.disputeRefundAmount").value(CREAM_PRICE));
        JsonNode accepted = json(accept(claimId).andExpect(status().isOk()));

        assertThat(accepted.get("amount").asInt()).isEqualTo(CREAM_PRICE);
        assertThat(claimRow(claimId)).containsEntry("result", "REFUNDED").containsEntry("fault_changed_to_seller", true);
        assertThat(chargeStatusOf(claimId)).isEqualTo("REFUNDED");
        assertThat(fake.cancelCalls()).containsExactly(paymentId);
        assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT detail FROM order_claim_history WHERE claim_id = ? "
                + "AND event_type = 'DISPUTE_ACCEPTED'", String.class, claimId)).contains("재발송비 결제 취소");
    }

    @Test
    @DisplayName("[AC-07c] 일부 반려 · 재발송비를 통과분 환불에서 차감 — 인용하면 차감분을 환원해 환불액에 더한다")
    void acceptRestoresDeductedFee() throws Exception {
        Long claimId = received(returnClaim(deliveredGroup(creamVariant, 2)));
        Map<String, Object> body = new HashMap<>(rejectBody());
        body.put("rejectedQuantity", 1);
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", body).andExpect(status().isOk());
        Long split = jdbc.queryForObject("SELECT claim_id FROM order_claim WHERE split_from_claim_id = ?", Long.class,
                claimId);
        assertThat(claimStatus(split)).isEqualTo("RESHIP_READY");
        assertThat(chargeStatusOf(split)).isEqualTo("DEDUCTED");
        int fee = jdbc.queryForObject("SELECT amount FROM order_claim_charge WHERE collection_id = ?", Integer.class,
                collectionIdOf(split));
        assertThat(fee).isPositive();

        adminGet(ADMIN_CLAIMS + "/" + split).andExpect(jsonPath("$.disputeRefundAmount").value(CREAM_PRICE + fee));
        JsonNode accepted = json(accept(split).andExpect(status().isOk()));

        assertThat(accepted.get("amount").asInt()).isEqualTo(CREAM_PRICE + fee);
        assertThat(chargeStatusOf(split)).isEqualTo("VOID");
        assertThat(claimRow(split)).containsEntry("refunded_amount", CREAM_PRICE + fee);
        assertThat(jdbc.queryForObject("SELECT detail FROM order_claim_history WHERE claim_id = ? "
                + "AND event_type = 'DISPUTE_ACCEPTED'", String.class, split)).contains("차감 환원");
    }

    @Test
    @DisplayName("[AC-08] 같은 박스에 다른 반려 보류가 남아 있으면 재발송비 청구는 그 건의 것 — 건드리지 않는다")
    void otherRejectKeepsCharge() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        List<Long> claimIds = requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group),
                newInvoice()).claimIds();
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", claimIds)).andExpect(status().isOk());
        for (Long id : claimIds) {
            sellerPost(SELLER_CLAIMS + "/" + id + "/inspection/reject", rejectBody()).andExpect(status().isOk());
        }
        Long first = claimIds.get(0);
        int firstPrice = jdbc.queryForObject("SELECT p.price FROM order_claim c JOIN order_product p "
                + "ON p.order_product_id = c.order_product_id WHERE c.claim_id = ?", Integer.class, first);

        JsonNode accepted = json(accept(first).andExpect(status().isOk()));

        assertThat(accepted.get("amount").asInt()).isEqualTo(firstPrice);
        assertThat(chargeStatusOf(first)).isEqualTo("PENDING");
        assertThat(claimStatus(claimIds.get(1))).isEqualTo("REJECT_HOLD");
    }

    @Test
    @DisplayName("[AC-09] 인용 가드 — 교환 반려 · 검수 대기 · 재발송 송장 등록 뒤 · 두 번째 호출은 409")
    void acceptGuards() throws Exception {
        Long exchangeRejected = rejectedClaim(exchangeClaim(deliveredGroup(creamVariant, 1),
                addSamePriceVariant(creamVariant, 5)));
        accept(exchangeRejected).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_STATE_CHANGED"));

        Long receivedId = received(returnClaim(deliveredGroup(creamVariant, 1)));
        accept(receivedId).andExpect(status().isConflict());

        Long reshipping = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        String paymentId = json(userPost(USER_CLAIMS + "/" + reshipping + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(status().isOk());
        registerReship(reshipping, "CJ", newInvoice()).andExpect(status().isOk());
        assertThat(claimStatus(reshipping)).isEqualTo("RESHIPPING");
        accept(reshipping).andExpect(status().isConflict());

        Long once = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        accept(once).andExpect(status().isOk());
        accept(once).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_refund_task WHERE source = 'OPERATOR_REASON'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("[AC-10] 인용 → 환불 관리 집행 대기에 뜨고, 집행해도 클레임 결과는 환불로 유지된다")
    void acceptThenExecute() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        long taskId = json(accept(claimId)).get("refundTaskId").asLong();

        JsonNode pending = json(adminGet(ADMIN_REFUNDS + "?tab=PENDING").andExpect(status().isOk())).get("content");
        assertThat(pending).anySatisfy(row -> {
            assertThat(row.get("refundTaskId").asLong()).isEqualTo(taskId);
            assertThat(row.get("reasonLabel").asText()).isEqualTo("반려 이의 인용");
        });

        adminPost(ADMIN_REFUNDS + "/" + taskId + "/execute", Map.of()).andExpect(jsonPath("$.outcome").value("DONE"));
        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REFUNDED");
        assertThat(fake.partialCancelCalls()).hasSize(1);
    }

    @Test
    @DisplayName("[AC-11] 권한 — 셀러 토큰 403")
    void sellerForbidden() throws Exception {
        mockMvc.perform(get(ADMIN_CLAIMS).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[AC-12] 입력 검증 — 근거가 비면 400 · 금액은 받지 않는다(보내도 무시 · 서버 계산)")
    void acceptValidation() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        adminPost(ADMIN_CLAIMS + "/" + claimId + "/dispute-acceptance", Map.of("detail", " "))
                .andExpect(status().isBadRequest());

        adminPost(ADMIN_CLAIMS + "/" + claimId + "/dispute-acceptance", Map.of("amount", 1, "detail", "근거"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(CREAM_PRICE));
    }

    // ------------------------------------------------------------------ 도우미

    private ResultActions accept(Long claimId) throws Exception {
        return adminPost(ADMIN_CLAIMS + "/" + claimId + "/dispute-acceptance",
                Map.of("detail", "1:1 문의 사진상 배송 시점 오염으로 판단"));
    }

    private List<Long> ids(ResultActions actions) throws Exception {
        JsonNode rows = json(actions.andExpect(status().isOk())).get("content");
        return java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .map(row -> row.get("claim").get("claimId").asLong()).toList();
    }

    private static JsonNode rowOf(JsonNode rows, Long claimId) {
        for (JsonNode row : rows) {
            if (row.get("claim").get("claimId").asLong() == claimId) {
                return row;
            }
        }
        throw new AssertionError("행 없음: " + claimId);
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}
