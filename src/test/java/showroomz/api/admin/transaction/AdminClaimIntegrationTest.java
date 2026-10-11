package showroomz.api.admin.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.admin.transaction.service.AdminClaimService;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.global.payment.portone.PortOneCancelResult;
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

    @Autowired private AdminClaimService adminClaimService;

    private String admin;
    private Seller operator;

    @BeforeEach
    void setUpAdmin() {
        operator = fixture.createAdmin("claims-ops@showroomz.test", "운영자");
        admin = adminToken(operator);
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

    @Test
    @DisplayName("[AC-13] 어드민 전용 검색 · 정렬 — 브랜드명 · 소비자명 · 정렬 셀렉트 · 전체 탭 검수 지연 상단 고정")
    void adminSearchAndSort() throws Exception {
        Long older = returnClaim(deliveredGroup(creamVariant, 1));
        Long newer = returnClaim(deliveredGroup(creamVariant, 1));
        Long overdue = received(returnClaim(deliveredGroup(creamVariant, 1)));
        jdbc.update("UPDATE order_claim SET inspect_due_at = ? WHERE claim_id = ?", LocalDateTime.now().minusDays(3), overdue);
        jdbc.update("UPDATE order_claim SET requested_at = ?, stage_entered_at = ? WHERE claim_id = ?",
                LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(5), older);
        Long other = otherBrandReshipReadyClaim();
        String myBrand = jdbc.queryForObject("SELECT market_name FROM order_delivery_group WHERE delivery_group_id = "
                + "(SELECT delivery_group_id FROM order_claim WHERE claim_id = ?)", String.class, older);
        String recipient = jdbc.queryForObject("SELECT recipient_name FROM orders WHERE order_id = "
                + "(SELECT order_id FROM order_claim WHERE claim_id = ?)", String.class, older);

        assertThat(ids(adminGet(ADMIN_CLAIMS + "?marketName=" + myBrand))).containsExactlyInAnyOrder(older, newer, overdue);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?marketName=타브랜드"))).containsExactly(other);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?consumerName=" + recipient))).contains(older, newer, overdue);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?consumerName=없는이름"))).isEmpty();

        // 전체 탭 — 검수 지연이 맨 위, 그 뒤는 신청 최신순(탭 기본).
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?marketName=" + myBrand))).startsWith(overdue);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?marketName=" + myBrand + "&sort=ELAPSED_ASC"))).startsWith(overdue, older);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?tab=COLLECTING&marketName=" + myBrand + "&sort=REQUESTED_DESC")))
                .containsExactly(newer, older);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?tab=COLLECTING&marketName=" + myBrand + "&sort=ELAPSED_ASC")))
                .containsExactly(older, newer);
    }

    @Test
    @DisplayName("[AC-14] 06a 주문 상세 — 진행 중 클레임 링크 · 1:1 문의 건수")
    void orderDetailLinksClaimAndInquiries() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = returnClaim(group);
        Long orderId = jdbc.queryForObject("SELECT order_id FROM order_claim WHERE claim_id = ?", Long.class, claimId);
        java.util.Map<String, Object> inquiry = new java.util.HashMap<>();
        inquiry.put("type", "DELIVERY");
        inquiry.put("content", "배송이 늦어요.");
        inquiry.put("imageUrls", java.util.List.of());
        inquiry.put("orderId", orderId);
        userPost("/v1/user/inquiries", inquiry).andExpect(status().isCreated());

        adminGet("/v1/admin/orders/" + orderId).andExpect(status().isOk())
                .andExpect(jsonPath("$.inquiryCount").value(1))
                .andExpect(jsonPath("$.groups[0].activeClaims[0].claimId").value(claimId))
                .andExpect(jsonPath("$.groups[0].activeClaims[0].claimNumber").value("CLM-" + claimId))
                .andExpect(jsonPath("$.groups[0].activeClaims[0].status").value("COLLECTING"));

        sellerPost(SELLER_CLAIMS + "/" + received(claimId) + "/inspection/pass", Map.of()).andExpect(status().isOk());
        adminGet("/v1/admin/orders/" + orderId).andExpect(jsonPath("$.groups[0].activeClaims").isEmpty());
    }

    // ------------------------------------------------------------------ 검수 무응답 운영자 환불(41 보고 4번)

    @Test
    @DisplayName("[AC-15] 검수 무응답 — 알림 3회 전 409 · 3회면 상세에 금액 · 편입하면 클레임 종결 · 반품 수량 · 큐 PENDING · 통과분은 따로 정산 · 철회 409")
    void refundUnansweredInspection() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 2);
        List<Long> claimIds = requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group),
                newInvoice()).claimIds();
        assertThat(claimIds).hasSize(1);
        Long claimId = received(claimIds.get(0));
        adminPost(ADMIN_CLAIMS + "/" + claimId + "/refund-tasks", Map.of("detail", "브랜드 연락 두절")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_STATE_CHANGED"));
        jdbc.update("UPDATE order_claim SET inspect_due_at = ?, inspect_notice_count = 3 WHERE claim_id = ?",
                LocalDateTime.now().minusDays(4), claimId);

        adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(jsonPath("$.canRefundUnanswered").value(true))
                .andExpect(jsonPath("$.unansweredRefundAmount").value(CREAM_PRICE * 2))
                .andExpect(jsonPath("$.canAcceptDispute").value(false));
        JsonNode accepted = json(adminPost(ADMIN_CLAIMS + "/" + claimId + "/refund-tasks",
                Map.of("detail", "자동 알림 3회 무응답 · 소비자 문의 2건")).andExpect(status().isOk()));

        long taskId = accepted.get("refundTaskId").asLong();
        assertThat(accepted.get("amount").asInt()).isEqualTo(CREAM_PRICE * 2);
        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REFUNDED")
                .containsEntry("refunded_amount", CREAM_PRICE * 2).containsEntry("fault_changed_to_seller", false);
        assertThat(jdbc.queryForMap("SELECT reason_code, status, origin FROM order_refund_task WHERE refund_task_id = ?", taskId))
                .containsEntry("reason_code", "INSPECTION_UNANSWERED").containsEntry("status", "PENDING")
                .containsEntry("origin", "OPERATOR");
        assertThat(jdbc.queryForObject("SELECT returned_quantity FROM order_product WHERE order_product_id = ?", Integer.class,
                items(group).get(0).getId())).isEqualTo(2);
        assertThat(events(claimId)).last().isEqualTo("INSPECTION_UNANSWERED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_refund_task WHERE source = 'CLAIM_RETURN_PASSED'",
                Integer.class)).isZero();
        assertThat(fake.partialCancelCalls()).isEmpty();
        adminPost(ADMIN_CLAIMS + "/" + claimId + "/refund-tasks", Map.of("detail", "다시")).andExpect(status().isConflict());
        JsonNode pending = json(adminGet(ADMIN_REFUNDS + "?tab=PENDING")).get("content");
        assertThat(pending).anySatisfy(row -> assertThat(row.get("reasonLabel").asText()).isEqualTo("검수 무응답"));
        // 클레임이 이미 환불로 닫혔으므로 철회하지 않는다 — 철회하면 종결된 클레임에 돈만 안 나간다.
        adminPost(ADMIN_REFUNDS + "/" + taskId + "/void", Map.of("reason", "오편입")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_VOIDABLE"));
        assertThat(jdbc.queryForObject("SELECT status FROM order_refund_task WHERE refund_task_id = ?", String.class, taskId))
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("[AC-15b] 같은 박스의 한 건은 통과 · 한 건은 무응답 — 운영자 환불 뒤 통과분의 PG 자동 환불이 따로 간다(이중 환불 없음)")
    void refundUnansweredThenFinalizeOthers() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        List<Long> claimIds = requestClaim(group, ClaimType.RETURN, ClaimReason.DAMAGED_OR_DEFECTIVE, allItems(group),
                newInvoice()).claimIds();
        assertThat(claimIds).hasSize(2);
        Long passedId = passed(claimIds.get(0));
        Long stuck = received(claimIds.get(1));
        assertThat(claimStatus(passedId)).isEqualTo("REFUND_PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_refund_task", Integer.class)).isZero();
        jdbc.update("UPDATE order_claim SET inspect_due_at = ?, inspect_notice_count = 3 WHERE claim_id = ?",
                LocalDateTime.now().minusDays(4), stuck);
        int stuckGoods = jdbc.queryForObject("SELECT p.price * c.quantity FROM order_claim c JOIN order_product p "
                + "ON p.order_product_id = c.order_product_id WHERE c.claim_id = ?", Integer.class, stuck);
        int passedGoods = jdbc.queryForObject("SELECT p.price * c.quantity FROM order_claim c JOIN order_product p "
                + "ON p.order_product_id = c.order_product_id WHERE c.claim_id = ?", Integer.class, passedId);

        adminPost(ADMIN_CLAIMS + "/" + stuck + "/refund-tasks", Map.of("detail", "무응답")).andExpect(status().isOk());

        // 판정이 다 끝나 통과분이 정산된다 — 무응답 건은 통과분에 섞이지 않는다.
        assertThat(claimStatus(passedId)).isEqualTo("COMPLETED");
        List<Map<String, Object>> tasks = jdbc.queryForList("SELECT source, refund_amount, status FROM order_refund_task "
                + "WHERE delivery_group_id = ? ORDER BY refund_task_id", group.getId());
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0)).containsEntry("source", "OPERATOR_REASON").containsEntry("refund_amount", stuckGoods)
                .containsEntry("status", "PENDING");
        assertThat(tasks.get(1)).containsEntry("source", "CLAIM_RETURN_PASSED").containsEntry("refund_amount", passedGoods)
                .containsEntry("status", "DONE");
        assertThat(fake.partialCancelCalls()).hasSize(1);
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

    // ------------------------------------------------------------------ 동시성 · 커밋 뒤 결제 취소

    @Test
    @DisplayName("[AC-16] 인용 동시 2회 — 하나만 편입되고 다른 하나는 409 · 환불 큐 · 클레임 이력 · 재발송비 청구 처리가 한 번")
    void concurrentAcceptance() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        AdminTransactionDto.DisputeAcceptRequest request = new AdminTransactionDto.DisputeAcceptRequest("동시 인용");

        List<String> results = ConcurrentCalls.race(
                () -> "RFD-" + adminClaimService.acceptDispute(operator.getId(), claimId, request).refundTaskId(),
                () -> "RFD-" + adminClaimService.acceptDispute(operator.getId(), claimId, request).refundTaskId());

        assertThat(results).filteredOn(result -> result.startsWith("RFD-")).hasSize(1);
        assertThat(results).filteredOn(result -> !result.startsWith("RFD-")).singleElement()
                .isEqualTo("CLAIM_STATE_CHANGED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_refund_task WHERE source = 'OPERATOR_REASON' "
                + "AND source_id = ?", Integer.class, claimId)).isEqualTo(1);
        assertThat(events(claimId)).filteredOn("DISPUTE_ACCEPTED"::equals).hasSize(1);
        assertThat(claimRow(claimId)).containsEntry("result", "REFUNDED").containsEntry("refunded_amount", CREAM_PRICE);
        assertThat(chargeStatusOf(claimId)).isEqualTo("VOID");
    }

    @Test
    @DisplayName("[AC-17] 결제된 재발송비 인용 · 포트원 취소 결과 미상 — 인용은 확정 · 결제는 취소 선점으로 남고 정리 배치가 다시 취소해 닫는다")
    void acceptThenCancelRetriedByReconcile() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        String paymentId = json(userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        fake.willFailCancel(paymentId, FakePaymentGateway.Failure.TIMEOUT);

        accept(claimId).andExpect(status().isOk());

        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REFUNDED");
        assertThat(chargeStatusOf(claimId)).isEqualTo("REFUNDED");
        assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCEL_REQUESTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_refund_task WHERE source = 'CLAIM_PAYMENT_CANCELLED'",
                Integer.class)).isZero();

        fake.willAnswerCancel(paymentId, PortOneCancelResult.Outcome.SUCCEEDED);
        claimPaymentService.reconcile(LocalDateTime.now());

        assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCELLED");
        assertThat(fake.cancelCalls()).containsExactly(paymentId, paymentId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_refund_task WHERE source = 'CLAIM_PAYMENT_CANCELLED' "
                + "AND payment_id = ?", Integer.class, paymentId)).isEqualTo(1);
        // 한 번 더 돌아도 다시 취소하지 않는다.
        claimPaymentService.reconcile(LocalDateTime.now());
        assertThat(fake.cancelCalls()).hasSize(2);
    }

    // ------------------------------------------------------------------ 경합 — 운영자 × 당사자(45 보완 시나리오 3절)

    @Test
    @DisplayName("[RC-01] 검수 무응답 환불 × 브랜드 검수 통과 동시 — 클레임 종결 1회 · 환불은 한 길만(운영자 큐 대기 또는 PG 자동 완료) · 반품 수량 1회 · 진 쪽 409")
    void unansweredRefundRacingInspectionPass() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = unansweredClaim(group);

        List<String> results = ConcurrentCalls.race(
                () -> "RFD-" + adminClaimService.refundUnanswered(operator.getId(), claimId,
                        new AdminTransactionDto.ClaimRefundRequest("자동 알림 3회 무응답")).refundTaskId(),
                () -> String.valueOf(sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/pass", Map.of())
                        .andReturn().getResponse().getStatus()));

        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REFUNDED");
        int operatorTasks = operatorTasksOf(claimId);
        int pgTasks = jdbc.queryForObject("SELECT COUNT(*) FROM order_refund_task WHERE source = 'CLAIM_RETURN_PASSED' "
                + "AND delivery_group_id = ?", Integer.class, group.getId());
        assertThat(operatorTasks + pgTasks).as("환불 경로 %s", results).isEqualTo(1);
        if (results.get(0).startsWith("RFD-")) {
            assertThat(results.get(1)).startsWith("4");
            assertThat(taskStatusOf(results.get(0))).isEqualTo("PENDING");
            assertThat(fake.partialCancelCalls()).isEmpty();
            assertThat(events(claimId)).doesNotContain("INSPECTION_PASSED");
        } else {
            assertThat(results.get(0)).isEqualTo("CLAIM_STATE_CHANGED");
            assertThat(results.get(1)).isEqualTo("200");
            assertThat(fake.partialCancelCalls()).hasSize(1);
            assertThat(events(claimId)).doesNotContain("INSPECTION_UNANSWERED");
        }
        assertThat(returnedQuantity(group)).isEqualTo(1);
    }

    @Test
    @DisplayName("[RC-02] 검수 무응답 환불 × 브랜드 전량 반려 동시 — 종결 또는 반려 보류 하나 · 재발송비 청구는 반려가 이긴 경우에만 1건 · 진 쪽 409")
    void unansweredRefundRacingInspectionReject() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = unansweredClaim(group);

        List<String> results = ConcurrentCalls.race(
                () -> "RFD-" + adminClaimService.refundUnanswered(operator.getId(), claimId,
                        new AdminTransactionDto.ClaimRefundRequest("자동 알림 3회 무응답")).refundTaskId(),
                () -> String.valueOf(sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", rejectBody())
                        .andReturn().getResponse().getStatus()));

        int charges = jdbc.queryForObject("SELECT COUNT(*) FROM order_claim_charge WHERE collection_id = ?", Integer.class,
                collectionIdOf(claimId));
        if (results.get(0).startsWith("RFD-")) {
            assertThat(results.get(1)).startsWith("4");
            assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REFUNDED");
            assertThat(operatorTasksOf(claimId)).isEqualTo(1);
            assertThat(charges).isZero();
            assertThat(returnedQuantity(group)).isEqualTo(1);
        } else {
            assertThat(results.get(0)).isEqualTo("CLAIM_STATE_CHANGED");
            assertThat(results.get(1)).isEqualTo("200");
            assertThat(claimStatus(claimId)).isEqualTo("REJECT_HOLD");
            assertThat(operatorTasksOf(claimId)).isZero();
            assertThat(charges).isEqualTo(1);
            assertThat(returnedQuantity(group)).isZero();
        }
    }

    @Test
    @DisplayName("[RC-03] 반려 보류 · 이의 접수 — 인용 × 소비자 재발송비 결제 완료 동시 · 인용은 어느 순서든 성립 · 결제는 자동 취소돼 소비자 돈이 남지 않는다")
    void acceptRacingReshipFeePayment() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        Map<String, Object> inquiry = new HashMap<>();
        inquiry.put("type", "CANCEL_EXCHANGE_RETURN");
        inquiry.put("content", "받았을 때부터 오염이 있었습니다.");
        inquiry.put("imageUrls", List.of());
        inquiry.put("claimId", claimId);
        userPost("/v1/user/inquiries", inquiry).andExpect(status().isCreated());
        String paymentId = json(userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();

        List<String> results = ConcurrentCalls.race(
                () -> "RFD-" + adminClaimService.acceptDispute(operator.getId(), claimId,
                        new AdminTransactionDto.DisputeAcceptRequest("1:1 문의 사진상 배송 시점 오염")).refundTaskId(),
                () -> String.valueOf(completeClaimPayment(paymentId).andReturn().getResponse().getStatus()));
        // 커밋 뒤 취소가 경합으로 남았으면 정리 배치가 닫는다(AC-17 과 같은 길).
        claimPaymentService.cancelRequested();

        assertThat(results.get(0)).as("인용 %s", results).startsWith("RFD-");
        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REFUNDED");
        assertThat(operatorTasksOf(claimId)).isEqualTo(1);
        assertThat(claimPaymentStatus(paymentId)).isNotEqualTo("PAID").isNotEqualTo("CANCEL_REQUESTED");
        assertThat(chargeStatusOf(claimId)).isIn("VOID", "REFUNDED");
        assertThat(fake.cancelCalls()).filteredOn(paymentId::equals).hasSizeLessThanOrEqualTo(1);
        if (chargeStatusOf(claimId).equals("REFUNDED")) {
            // 결제가 먼저였다 — 재발송 대기에서 인용 · 결제 취소 1회.
            assertThat(fake.cancelCalls()).filteredOn(paymentId::equals).hasSize(1);
            assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCELLED");
        }
    }

    @Test
    @DisplayName("[RC-04] 재발송 대기(재발송비 결제됨) — 인용 × 브랜드 재발송 송장 동시 · 하나만 · 결제 취소는 인용이 이긴 경우만 1회")
    void acceptRacingReshipment() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        String paymentId = json(userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        assertThat(claimStatus(claimId)).isEqualTo("RESHIP_READY");
        String invoice = newInvoice();

        List<String> results = ConcurrentCalls.race(
                () -> "RFD-" + adminClaimService.acceptDispute(operator.getId(), claimId,
                        new AdminTransactionDto.DisputeAcceptRequest("배송 시점 오염")).refundTaskId(),
                () -> json(registerReship(claimId, "CJ", invoice)).get("succeeded").asInt() == 1 ? "RESHIPPED" : "SKIPPED");

        assertThat(results).filteredOn(r -> r.startsWith("RFD-") || r.equals("RESHIPPED")).as("%s", results).hasSize(1);
        if (results.get(0).startsWith("RFD-")) {
            assertThat(results.get(1)).isEqualTo("SKIPPED");
            assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REFUNDED")
                    .containsEntry("reship_tracking_number", null);
            assertThat(fake.cancelCalls()).containsExactly(paymentId);
            assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCELLED");
        } else {
            assertThat(results.get(0)).isEqualTo("CLAIM_STATE_CHANGED");
            assertThat(claimStatus(claimId)).isEqualTo("RESHIPPING");
            assertThat(operatorTasksOf(claimId)).isZero();
            assertThat(fake.cancelCalls()).isEmpty();
            assertThat(claimPaymentStatus(paymentId)).isEqualTo("PAID");
        }
    }

    // ------------------------------------------------------------------ 응답 필드(45 보완 시나리오 4-2 · F-A02)

    @Test
    @DisplayName("[F-A02] 06a 상세 — 교환 진행 중은 activeClaims[0] 교환 · 상태 문구 · 구매확정 정지 · 재발송비 결제 대기(접수 전) 교환은 없다")
    void orderDetailShowsExchangeInProgress() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = exchangeClaim(group, addSamePriceVariant(creamVariant, 5));
        Long orderId = group.getOrder().getId();

        adminGet("/v1/admin/orders/" + orderId).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].activeClaims.length()").value(1))
                .andExpect(jsonPath("$.groups[0].activeClaims[0].claimId").value(claimId))
                .andExpect(jsonPath("$.groups[0].activeClaims[0].type").value("EXCHANGE"))
                .andExpect(jsonPath("$.groups[0].activeClaims[0].statusLabel").value("회수 중"))
                .andExpect(jsonPath("$.groups[0].purchaseConfirm.paused").value(true));

        OrderDeliveryGroup drafted = deliveredGroup(creamVariant, 1);
        JsonNode created = json(userPost(USER_CLAIMS, claimBody(drafted, "EXCHANGE", "CHANGE_OF_MIND",
                items(drafted).get(0).getId(), null, addSamePriceVariant(creamVariant, 5).getVariantId()))
                .andExpect(status().isCreated()));
        assertThat(claimStatus(created.get("claimIds").get(0).asLong())).isEqualTo("PAYMENT_PENDING");
        adminGet("/v1/admin/orders/" + drafted.getOrder().getId())
                .andExpect(jsonPath("$.groups[0].activeClaims").isEmpty());
    }

    @Test
    @DisplayName("[F-B06] 전체 탭 검수 지연 상단 고정은 페이지를 넘어서도 — size=1 이면 1쪽 = 검수 지연 · 2쪽 = 그다음(신청 최신순)")
    void overduePinnedAcrossPages() throws Exception {
        Long older = returnClaim(deliveredGroup(creamVariant, 1));
        Long newer = returnClaim(deliveredGroup(creamVariant, 1));
        Long overdue = received(returnClaim(deliveredGroup(creamVariant, 1)));
        jdbc.update("UPDATE order_claim SET requested_at = ? WHERE claim_id = ?", LocalDateTime.now().minusDays(9), overdue);
        jdbc.update("UPDATE order_claim SET inspect_due_at = ? WHERE claim_id = ?", LocalDateTime.now().minusDays(3), overdue);
        jdbc.update("UPDATE order_claim SET requested_at = ? WHERE claim_id = ?", LocalDateTime.now().minusDays(5), older);

        assertThat(ids(adminGet(ADMIN_CLAIMS + "?size=1&page=1"))).containsExactly(overdue);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?size=1&page=2"))).containsExactly(newer);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?size=1&page=3"))).containsExactly(older);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?size=2&page=1"))).containsExactly(overdue, newer);
    }

    @Test
    @DisplayName("[F-B07] types — RETURN + EXCHANGE 둘 다 = 생략과 같은 건수 · 빈 값(types=)도 생략과 같다")
    void bothTypesEqualNoFilter() throws Exception {
        returnClaim(deliveredGroup(creamVariant, 1));
        exchangeClaim(deliveredGroup(creamVariant, 1), addSamePriceVariant(creamVariant, 5));

        List<Long> all = ids(adminGet(ADMIN_CLAIMS));
        assertThat(all).hasSize(2);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?types=RETURN&types=EXCHANGE"))).containsExactlyInAnyOrderElementsOf(all);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?types=RETURN,EXCHANGE"))).containsExactlyInAnyOrderElementsOf(all);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?types="))).containsExactlyInAnyOrderElementsOf(all);
    }

    @Test
    @DisplayName("[F-B08] 인용 뒤 — 상세 인용 불가 · 인용 금액 null · 목록 행 귀책 변경 참 · 종결 단계 · fee_bearer 는 그대로(현재 동작)")
    void afterAcceptanceDetailAndRow() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        accept(claimId).andExpect(status().isOk());

        JsonNode detail = json(adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(status().isOk()));
        assertThat(detail.get("canAcceptDispute").asBoolean()).isFalse();
        assertThat(detail.has("disputeRefundAmount") && !detail.get("disputeRefundAmount").isNull()).isFalse();
        // 인용은 귀책 변경 표시(faultChangedToSeller)만 세우고 fee_bearer 는 그대로다 — 「귀책」 열이 소비자 귀책으로 남는다(현재 동작 고정 · 확인 필요).
        assertThat(detail.get("feeBearer").asText()).isEqualTo("CONSUMER");
        JsonNode row = rowOf(json(adminGet(ADMIN_CLAIMS + "?tab=DONE")).get("content"), claimId);
        assertThat(row.get("faultChangedToSeller").asBoolean()).isTrue();
        assertThat(row.at("/claim/stage").asText()).isEqualTo("DONE");
        assertThat(row.get("feeBearerLabel").asText()).isEqualTo("소비자 귀책");
    }

    // ------------------------------------------------------------------ 도우미

    /** 입고 확인 · 검수 기한 경과 · 자동 알림 3회 — 검수 무응답 운영자 환불 조건(알림 횟수는 배치 몫이라 SQL 로 적는다). */
    private Long unansweredClaim(OrderDeliveryGroup group) throws Exception {
        Long claimId = received(returnClaim(group));
        jdbc.update("UPDATE order_claim SET inspect_due_at = ?, inspect_notice_count = 3 WHERE claim_id = ?",
                LocalDateTime.now().minusDays(4), claimId);
        return claimId;
    }

    private int operatorTasksOf(Long claimId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_refund_task WHERE source = 'OPERATOR_REASON' AND source_id = ?",
                Integer.class, claimId);
    }

    private String taskStatusOf(String refundNo) {
        return jdbc.queryForObject("SELECT status FROM order_refund_task WHERE refund_task_id = ?", String.class,
                Long.parseLong(refundNo.substring("RFD-".length())));
    }

    private int returnedQuantity(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT returned_quantity FROM order_product WHERE order_product_id = ?", Integer.class,
                items(group).get(0).getId());
    }

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
