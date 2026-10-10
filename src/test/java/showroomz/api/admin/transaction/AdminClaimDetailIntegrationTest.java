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
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.utils.BusinessCalendar;
import showroomz.support.IntegrationTest;

import java.sql.Timestamp;
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
 * 어드민 반품·교환(06b) 세부 — 43 테스트 상세 3절의 검색 · 기간 경계, 반려 보류 열(재발송비 기한 · 고지 · 보관 단계), 검수 기한
 * 영업일 · 무응답 판정 경계, 재발송 시작 뒤 인용 불가, 인용 · 무응답 편입이 남기는 기록(큐 · 이력 · 06c), 교환 무응답의 재고 원복,
 * 요약의 유형 · 브랜드 한정. 대표 경로는 {@code AdminClaimIntegrationTest}.
 */
@IntegrationTest
class AdminClaimDetailIntegrationTest extends ClaimTestSupport {

    private static final String ADMIN_CLAIMS = "/v1/admin/claims";
    private static final Map<String, Object> CARD = Map.of("method", "CARD", "cardIssuer", "SHINHAN");

    @Autowired private BusinessCalendar calendar;

    private String admin;
    private Seller operator;

    @BeforeEach
    void setUpAdmin() {
        operator = fixture.createAdmin("claims-detail@showroomz.test", "운영자");
        admin = adminToken(operator);
    }

    // ------------------------------------------------------------------ 목록 · 요약

    @Test
    @DisplayName("[OB-L06] 검색 · 기간 — 접수번호 정확 · 주문번호 정확(일부는 0건) · 기본 30일(신청일시) · 기간 지정 · 역전 400 · 365일 초과 400")
    void searchAndRange() throws Exception {
        Long recent = returnClaim(deliveredGroup(creamVariant, 1));
        Long old = returnClaim(deliveredGroup(creamVariant, 1));
        String orderNumber = jdbc.queryForObject("SELECT o.order_number FROM orders o JOIN order_claim c ON c.order_id = o.order_id "
                + "WHERE c.claim_id = ?", String.class, recent);
        jdbc.update("UPDATE order_claim SET requested_at = ? WHERE claim_id = ?", LocalDateTime.now().minusDays(40), old);

        assertThat(ids(adminGet(ADMIN_CLAIMS + "?keyword=CLM-" + recent))).containsExactly(recent);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?keyword=" + orderNumber))).containsExactly(recent);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?keyword=" + orderNumber.substring(0, orderNumber.length() - 2)))).isEmpty();
        assertThat(ids(adminGet(ADMIN_CLAIMS))).containsExactly(recent);
        LocalDate today = LocalDate.now();
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?from=" + today.minusDays(45) + "&to=" + today)))
                .containsExactlyInAnyOrder(recent, old);
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?from=" + today.minusDays(45) + "&to=" + today.minusDays(35))))
                .containsExactly(old);

        adminGet(ADMIN_CLAIMS + "?from=" + today + "&to=" + today.minusDays(1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        adminGet(ADMIN_CLAIMS + "?from=" + today.minusDays(400) + "&to=" + today).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_SEARCH_RANGE_EXCEEDED"));
    }

    @Test
    @DisplayName("[OB-L04] 반려 보류 열 — 보관 상품 · 반려 사유 · 반려 시각 · 재발송비(결제 대기 · 기한 = 반려 + 14일) · 고지 2회부터 보관 기한(+3개월) · 기한 경과 단계")
    void rejectHoldColumns() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));

        JsonNode row = rowOf(json(adminGet(ADMIN_CLAIMS + "?tab=REJECT_HOLD")), claimId).get("claim");
        assertThat(row.get("status").asText()).isEqualTo("REJECT_HOLD");
        assertThat(row.get("shipLabel").asText()).isNotBlank();
        assertThat(row.get("rejectReasonLabel").asText()).isNotBlank();
        LocalDateTime rejectedAt = at(row.get("rejectedAt").asText());
        assertThat(row.at("/reshipFee/status").asText()).isEqualTo("PENDING");
        assertThat(row.at("/reshipFee/amount").asInt()).isPositive();
        LocalDateTime dueAt = at(row.at("/reshipFee/dueAt").asText());
        assertThat(dueAt).isEqualTo(((Timestamp) jdbc.queryForObject("SELECT due_at FROM order_claim_charge WHERE charge_id = ?",
                Timestamp.class, chargeIdOf(claimId))).toLocalDateTime());
        assertThat(dueAt.toLocalDate()).isEqualTo(rejectedAt.plusDays(14).toLocalDate());
        assertThat(row.at("/storage/noticeCount").asInt()).isZero();
        assertThat(row.at("/storage/storageDueAt").isNull()).isTrue();
        assertThat(row.at("/storage/phase").asText()).isEqualTo("NOTICE_PENDING");

        LocalDateTime lastNotice = LocalDateTime.now().minusDays(10).withNano(0);
        jdbc.update("UPDATE order_claim SET notice_count = 1, last_notice_at = ? WHERE claim_id = ?", lastNotice, claimId);
        assertThat(rowOf(json(adminGet(ADMIN_CLAIMS + "?tab=REJECT_HOLD")), claimId).at("/claim/storage/storageDueAt").isNull())
                .isTrue();
        jdbc.update("UPDATE order_claim SET notice_count = 2 WHERE claim_id = ?", claimId);
        JsonNode storing = rowOf(json(adminGet(ADMIN_CLAIMS + "?tab=REJECT_HOLD")), claimId).at("/claim/storage");
        // 보관 기한 = 최종 고지일 + 3개월의 그날 끝(다른 기한들과 같은 23:59:59 규칙).
        assertThat(at(storing.get("storageDueAt").asText())).isEqualTo(lastNotice.plusMonths(3).toLocalDate().atTime(23, 59, 59));
        assertThat(storing.get("phase").asText()).isEqualTo("STORING");
        jdbc.update("UPDATE order_claim SET last_notice_at = ? WHERE claim_id = ?", LocalDateTime.now().minusMonths(4), claimId);
        assertThat(rowOf(json(adminGet(ADMIN_CLAIMS + "?tab=REJECT_HOLD")), claimId).at("/claim/storage/phase").asText())
                .isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("[OB-L08] 요약 — 유형별 건수 · 탭 합 = 전체 · 브랜드 한정이면 다른 브랜드가 빠진다")
    void summaryByTypeAndBrand() throws Exception {
        returnClaim(deliveredGroup(creamVariant, 1));
        exchangeClaim(deliveredGroup(creamVariant, 1), addSamePriceVariant(creamVariant, 5));
        otherBrandReshipReadyClaim();

        JsonNode all = json(adminGet(ADMIN_CLAIMS + "/summary").andExpect(status().isOk()));
        assertThat(all.at("/typeCounts/RETURN").asLong()).isEqualTo(1);
        assertThat(all.at("/typeCounts/EXCHANGE").asLong()).isEqualTo(2);
        long tabSum = 0;
        for (String tab : List.of("COLLECT_WAIT", "COLLECTING", "INSPECTION", "RESHIP", "REJECT_HOLD", "DONE")) {
            tabSum += all.at("/tabCounts/" + tab).asLong();
        }
        assertThat(all.at("/tabCounts/ALL").asLong()).isEqualTo(tabSum).isEqualTo(3);

        JsonNode mine = json(adminGet(ADMIN_CLAIMS + "/summary?marketId=" + brand.marketId()));
        assertThat(mine.at("/tabCounts/ALL").asLong()).isEqualTo(2);
        assertThat(mine.at("/typeCounts/EXCHANGE").asLong()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 상세

    @Test
    @DisplayName("[OB-D01] 검수 기한 — 기한 전 null · 경과 영업일(달력 기준) · 알림 2회는 무응답 아님 · 3회면 금액 = 단가 × 수량 · 06d 알림 수와 같다")
    void inspectionDeadlineAndUnanswered() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 2);
        Long claimId = received(requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group), newInvoice())
                .claimIds().get(0));
        JsonNode before = json(adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(status().isOk()));
        assertThat(before.has("inspectOverdueBusinessDays") && !before.get("inspectOverdueBusinessDays").isNull()).isFalse();
        assertThat(before.get("canRefundUnanswered").asBoolean()).isFalse();

        LocalDateTime due = LocalDateTime.now().minusDays(6).withHour(23).withMinute(59).withSecond(59).withNano(0);
        jdbc.update("UPDATE order_claim SET inspect_due_at = ?, inspect_notice_count = 2, last_inspect_notice_at = ? "
                + "WHERE claim_id = ?", due, LocalDateTime.now().minusDays(1), claimId);
        JsonNode overdue = json(adminGet(ADMIN_CLAIMS + "/" + claimId));
        assertThat(overdue.get("inspectOverdueBusinessDays").asInt())
                .isEqualTo(calendar.businessDaysBetween(due.toLocalDate(), LocalDate.now()));
        assertThat(overdue.get("canRefundUnanswered").asBoolean()).isFalse();
        assertThat(overdue.has("unansweredRefundAmount") && !overdue.get("unansweredRefundAmount").isNull()).isFalse();
        assertThat(rowOf(json(adminGet(ADMIN_CLAIMS + "?tab=INSPECTION")), claimId).at("/claim/overdue").asBoolean()).isTrue();

        jdbc.update("UPDATE order_claim SET inspect_notice_count = 3 WHERE claim_id = ?", claimId);
        JsonNode unanswered = json(adminGet(ADMIN_CLAIMS + "/" + claimId));
        assertThat(unanswered.get("canRefundUnanswered").asBoolean()).isTrue();
        assertThat(unanswered.get("unansweredRefundAmount").asInt()).isEqualTo(CREAM_PRICE * 2);
        assertThat(unanswered.at("/inspectNotice/count").asInt()).isEqualTo(3);
        JsonNode exceptionRow = StreamSupport.stream(json(adminGet("/v1/admin/order-exceptions?tab=DELAY"))
                .at("/page/content").spliterator(), false)
                .filter(item -> item.at("/link/claimId").asLong() == claimId).findFirst().orElseThrow();
        assertThat(exceptionRow.get("noticeCount").asInt()).isEqualTo(3);
        assertThat(exceptionRow.get("actOnBehalfAvailable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("[OB-D03] 재발송 송장 등록 뒤(반송 시작) — 인용 · 무응답 환불 버튼 없음 · 금액 null · 인용 409")
    void noDisputeAfterReshipping() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        String paymentId = json(userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(status().isOk());
        registerReship(claimId, "CJ", newInvoice()).andExpect(status().isOk());
        assertThat(claimStatus(claimId)).isEqualTo("RESHIPPING");

        JsonNode detail = json(adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(status().isOk()));
        assertThat(detail.get("canAcceptDispute").asBoolean()).isFalse();
        assertThat(detail.has("disputeRefundAmount") && !detail.get("disputeRefundAmount").isNull()).isFalse();
        assertThat(detail.get("canRefundUnanswered").asBoolean()).isFalse();
        accept(claimId, "배송 시점 오염").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_STATE_CHANGED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_refund_task WHERE source = 'OPERATOR_REASON'", Integer.class))
                .isZero();
    }

    // ------------------------------------------------------------------ 쓰기 — 남기는 기록

    @Test
    @DisplayName("[OB-A01 · A06] 인용 기록 — 근거 501자 400 · 500자 통과 · 큐(사유 · 근거 · 편입자 · 결제 · 금액) · 클레임 이력(금액 · 근거) · 종결 시각 · 06c 상세 근거")
    void acceptanceRecords() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        accept(claimId, "가".repeat(501)).andExpect(status().isBadRequest());
        assertThat(claimStatus(claimId)).isEqualTo("REJECT_HOLD");

        String prefix = "1:1 문의 사진상 배송 시점 오염 ";
        String detail = prefix + "가".repeat(500 - prefix.length());
        assertThat(detail).hasSize(500);
        JsonNode accepted = json(accept(claimId, detail).andExpect(status().isOk()));
        long taskId = accepted.get("refundTaskId").asLong();
        assertThat(accepted.get("refundNo").asText()).isEqualTo("RFD-" + taskId);

        Map<String, Object> task = jdbc.queryForMap("SELECT * FROM order_refund_task WHERE refund_task_id = ?", taskId);
        String paidPaymentId = jdbc.queryForObject("SELECT o.paid_payment_id FROM orders o JOIN order_claim c "
                + "ON c.order_id = o.order_id WHERE c.claim_id = ?", String.class, claimId);
        assertThat(task).containsEntry("source", "OPERATOR_REASON").containsEntry("source_id", claimId)
                .containsEntry("reason_code", "DISPUTE_ACCEPTED").containsEntry("reason_detail", detail)
                .containsEntry("requested_by", operator.getId()).containsEntry("payment_id", paidPaymentId)
                .containsEntry("refund_amount", CREAM_PRICE).containsEntry("attempt", 0);
        assertThat(claimRow(claimId).get("completed_at")).isNotNull();
        String history = jdbc.queryForObject("SELECT detail FROM order_claim_history WHERE claim_id = ? "
                + "AND event_type = 'DISPUTE_ACCEPTED'", String.class, claimId);
        assertThat(history).contains(String.format("%,d원", CREAM_PRICE)).contains(detail);
        assertThat(jdbc.queryForObject("SELECT actor_type FROM order_claim_history WHERE claim_id = ? "
                + "AND event_type = 'DISPUTE_ACCEPTED'", String.class, claimId)).isEqualTo("ADMIN");
        adminGet("/v1/admin/refunds/" + taskId).andExpect(status().isOk())
                .andExpect(jsonPath("$.reason.detail").value(detail))
                .andExpect(jsonPath("$.reason.label").value("반려 이의 인용"))
                .andExpect(jsonPath("$.voidable").value(false));
    }

    @Test
    @DisplayName("[OB-A08] 검수 무응답 · 교환 — 잡아 둔 새 옵션 재고 원복 · 반품 수량 · 금액 = 단가 × 수량 · 클레임 이력(금액 · 알림 횟수 · 근거)")
    void unansweredExchangeRestoresStock() throws Exception {
        var newVariant = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = received(exchangeClaim(group, newVariant));
        int reserved = stockOf(newVariant.getVariantId());
        jdbc.update("UPDATE order_claim SET inspect_due_at = ?, inspect_notice_count = 3 WHERE claim_id = ?",
                LocalDateTime.now().minusDays(5), claimId);

        JsonNode refunded = json(adminPost(ADMIN_CLAIMS + "/" + claimId + "/refund-tasks", Map.of("detail", "브랜드 연락 두절"))
                .andExpect(status().isOk()));

        assertThat(refunded.get("amount").asInt()).isEqualTo(CREAM_PRICE);
        assertThat(stockOf(newVariant.getVariantId())).isEqualTo(reserved + 1);
        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REFUNDED");
        assertThat(jdbc.queryForObject("SELECT returned_quantity FROM order_product WHERE order_product_id = ?", Integer.class,
                items(group).get(0).getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT detail FROM order_claim_history WHERE claim_id = ? "
                + "AND event_type = 'INSPECTION_UNANSWERED'", String.class, claimId))
                .contains(String.format("%,d원", CREAM_PRICE)).contains("자동 알림 3회 무응답").contains("브랜드 연락 두절");
        assertThat(jdbc.queryForObject("SELECT reason_code FROM order_refund_task WHERE refund_task_id = ?", String.class,
                refunded.get("refundTaskId").asLong())).isEqualTo("INSPECTION_UNANSWERED");
    }

    @Test
    @DisplayName("[OB-A10] 검수 무응답 가드 — 알림 2회 · 기한 전 · 반려 보류는 409 · 근거 공백 · 501자 400 · 없는 클레임 404 · 큐 0건")
    void unansweredGuards() throws Exception {
        Long twoNotices = received(returnClaim(deliveredGroup(creamVariant, 1)));
        jdbc.update("UPDATE order_claim SET inspect_due_at = ?, inspect_notice_count = 2 WHERE claim_id = ?",
                LocalDateTime.now().minusDays(5), twoNotices);
        Long notDue = received(returnClaim(deliveredGroup(creamVariant, 1)));
        jdbc.update("UPDATE order_claim SET inspect_due_at = ?, inspect_notice_count = 3 WHERE claim_id = ?",
                LocalDateTime.now().plusDays(1), notDue);
        Long rejected = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));

        for (Long claimId : List.of(twoNotices, notDue, rejected)) {
            adminPost(ADMIN_CLAIMS + "/" + claimId + "/refund-tasks", Map.of("detail", "무응답"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_STATE_CHANGED"));
        }
        jdbc.update("UPDATE order_claim SET inspect_notice_count = 3 WHERE claim_id = ?", twoNotices);
        adminPost(ADMIN_CLAIMS + "/" + twoNotices + "/refund-tasks", Map.of("detail", " ")).andExpect(status().isBadRequest());
        adminPost(ADMIN_CLAIMS + "/" + twoNotices + "/refund-tasks", Map.of("detail", "가".repeat(501)))
                .andExpect(status().isBadRequest());
        adminPost(ADMIN_CLAIMS + "/999999/refund-tasks", Map.of("detail", "무응답")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_refund_task", Integer.class)).isZero();
        assertThat(claimStatus(twoNotices)).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("[OB-A07 경계] 검수 무응답 — 근거를 한도(500자)까지 채워도 편입되고 클레임 이력에 근거가 잘리지 않고 남는다")
    void unansweredMaxDetail() throws Exception {
        Long claimId = received(returnClaim(deliveredGroup(creamVariant, 1)));
        jdbc.update("UPDATE order_claim SET inspect_due_at = ?, inspect_notice_count = 3 WHERE claim_id = ?",
                LocalDateTime.now().minusDays(5), claimId);
        String detail = "브랜드 연락 두절 " + "나".repeat(500 - "브랜드 연락 두절 ".length());

        adminPost(ADMIN_CLAIMS + "/" + claimId + "/refund-tasks", Map.of("detail", detail)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT detail FROM order_claim_history WHERE claim_id = ? "
                + "AND event_type = 'INSPECTION_UNANSWERED'", String.class, claimId)).endsWith(detail);
        assertThat(jdbc.queryForObject("SELECT reason_detail FROM order_refund_task WHERE source_id = ?", String.class, claimId))
                .isEqualTo(detail);
    }

    // ------------------------------------------------------------------ 45 보완 시나리오 4-2

    @Test
    @DisplayName("[F-B01] 일부 반려로 갈라진 두 행 — 분할 행 splitFromClaimNumber = 원 행 · 원 행 수량 = 통과분 · 어드민 목록에 둘 다 · 귀책은 행마다 DB 값")
    void partialRejectSplitRows() throws Exception {
        Long claimId = received(returnClaim(deliveredGroup(creamVariant, 3)));
        Map<String, Object> body = new java.util.HashMap<>(rejectBody());
        body.put("rejectedQuantity", 1);
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", body).andExpect(status().isOk());
        Long split = jdbc.queryForObject("SELECT claim_id FROM order_claim WHERE split_from_claim_id = ?", Long.class, claimId);

        JsonNode splitDetail = json(adminGet(ADMIN_CLAIMS + "/" + split).andExpect(status().isOk()));
        assertThat(splitDetail.at("/claim/rejection/splitFromClaimNumber").asText()).isEqualTo("CLM-" + claimId);
        assertThat(splitDetail.at("/claim/summary/quantity").asInt()).isEqualTo(1);
        JsonNode originDetail = json(adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(status().isOk()));
        assertThat(originDetail.at("/claim/summary/quantity").asInt()).isEqualTo(2);
        assertThat(originDetail.at("/claim/rejection/splitFromClaimNumber").textValue()).isNull();

        JsonNode page = json(adminGet(ADMIN_CLAIMS + "?tab=ALL"));
        assertThat(ids(adminGet(ADMIN_CLAIMS + "?tab=ALL"))).contains(claimId, split);
        for (Long id : List.of(claimId, split)) {
            assertThat(rowOf(page, id).get("feeBearer").asText()).as("CLM-%d", id).isEqualTo(claimRow(id).get("fee_bearer"));
        }
    }

    @Test
    @DisplayName("[F-B05] 운영자 개설 클레임 상세 — 운영자 개설 표시 · 사유 「구매확정 후 하자」 · 이력 첫 줄 접수(ADMIN)")
    void operatorOpenedClaimDetail() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        long claimId = json(adminPost("/v1/admin/orders/groups/" + group.getId() + "/defect-claims", Map.of(
                "items", List.of(Map.of("orderProductId", items(group).get(0).getId(), "quantity", 1)),
                "reasonCode", "DAMAGED_OR_DEFECTIVE", "detail", "1:1 문의 — 구매확정 후 용기 파손 발견",
                "evidenceImageUrls", List.of("https://img.test/d.jpg"))).andExpect(status().isOk()))
                .get("claimIds").get(0).asLong();

        JsonNode detail = json(adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(status().isOk()));
        assertThat(detail.at("/claim/summary/openedByOperator").asBoolean()).isTrue();
        assertThat(detail.at("/claim/summary/openReason").asText()).isEqualTo("구매확정 후 하자");
        assertThat(detail.get("feeBearer").asText()).isEqualTo("SELLER");
        JsonNode requested = StreamSupport.stream(detail.at("/claim/history").spliterator(), false)
                .filter(h -> h.get("eventType").asText().equals("REQUESTED")).findFirst().orElseThrow();
        assertThat(requested.get("actorType").asText()).isEqualTo("ADMIN");
        assertThat(requested.get("detail").asText()).contains("운영자 개설");
        assertThat(detail.at("/claim/consumerAttachments/0").asText()).isEqualTo("https://img.test/d.jpg");
    }

    // ------------------------------------------------------------------ 도우미

    private static LocalDateTime at(String text) {
        return LocalDateTime.parse(text.endsWith("Z") ? text.substring(0, text.length() - 1) : text);
    }

    private int stockOf(Long variantId) {
        return jdbc.queryForObject("SELECT stock FROM product_variant WHERE variant_id = ?", Integer.class, variantId);
    }

    private ResultActions accept(Long claimId, String detail) throws Exception {
        return adminPost(ADMIN_CLAIMS + "/" + claimId + "/dispute-acceptance", Map.of("detail", detail));
    }

    private List<Long> ids(ResultActions actions) throws Exception {
        JsonNode rows = json(actions.andExpect(status().isOk())).get("content");
        return StreamSupport.stream(rows.spliterator(), false).map(row -> row.get("claim").get("claimId").asLong()).toList();
    }

    private static JsonNode rowOf(JsonNode page, Long claimId) {
        return StreamSupport.stream(page.get("content").spliterator(), false)
                .filter(row -> row.get("claim").get("claimId").asLong() == claimId).findFirst()
                .orElseThrow(() -> new AssertionError("행 없음: " + claimId));
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}
