package showroomz.api.app.claim;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.global.payment.portone.PortOneCancelResult;
import showroomz.support.IntegrationTest;
import showroomz.api.app.auth.entity.RoleType;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 클레임 결제의 비정상 경로(보강 시나리오 5절 PY-01 ~ PY-10). 교환 선결제는 기존 테스트가 정상 · 자동 취소 · 웹훅을 덮었고,
 * 여기는 반려 재발송비 결제의 나머지 경로와 취소 재시도 · 직권 종결을 본다. 포트원은 시나리오 지정 더블이다.
 */
@IntegrationTest
class ClaimPaymentEdgeIntegrationTest extends ClaimTestSupport {

    private static final Map<String, Object> CARD = Map.of("method", "CARD", "cardIssuer", "SHINHAN");

    @Test
    @DisplayName("[PY-01] 반려 재발송비 — 앱 콜백 없이 웹훅만 와도 재발송 대기로 간다")
    void rejectFeeByWebhookOnly() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        String paymentId = openReshipFee(claimId);

        webhook("wh-reject-fee-1", "Transaction.Paid", paymentId).andExpect(status().isOk());

        assertThat(claimStatus(claimId)).isEqualTo("RESHIP_READY");
        assertThat(chargeStatusOf(claimId)).isEqualTo("PAID");
        assertThat(claimPaymentStatus(paymentId)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("[PY-02] 결제창을 두 번 열어 둘 다 결제되면 — 먼저 확정된 것만 받고 나머지는 자동 취소한다")
    void doublePaymentCancelsLater() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        String first = openReshipFee(claimId);
        String second = openReshipFee(claimId);
        assertThat(second).endsWith("-2");

        completeClaimPayment(first).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        completeClaimPayment(second).andExpect(jsonPath("$.paymentStatus").value("CANCELLED"));

        assertThat(jdbc.queryForMap("SELECT * FROM order_claim_charge"))
                .containsEntry("status", "PAID").containsEntry("paid_payment_id", first);
        assertThat(fake.cancelCalls()).containsExactly(second);
        assertThat(events(claimId)).filteredOn("RESHIP_FEE_PAID"::equals).hasSize(1);
    }

    @Test
    @DisplayName("[PY-03] 반려 재발송비 결제 금액이 청구와 다르면 자동 취소 — 거절 보류 그대로")
    void rejectFeeAmountMismatch() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        String paymentId = openReshipFee(claimId);
        fake.willReturnPaid(paymentId, 100);

        completeClaimPayment(paymentId).andExpect(jsonPath("$.paymentStatus").value("CANCELLED"));

        assertThat(claimStatus(claimId)).isEqualTo("REJECT_HOLD");
        assertThat(chargeStatusOf(claimId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("[PY-04] 폐기 기록으로 청구가 소멸한 뒤 도착한 결제는 자동 취소 — 종결된 클레임은 그대로")
    void paymentAfterDisposalCancelled() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        LocalDateTime now = LocalDateTime.now().withNano(0);
        claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L, now.minusMonths(5));
        claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L, now.minusMonths(4));
        String paymentId = openReshipFee(claimId);
        claimService.disposeAfterStorage(claimId, 1L, now);

        completeClaimPayment(paymentId).andExpect(jsonPath("$.paymentStatus").value("CANCELLED"));

        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REJECTED");
        assertThat(chargeStatusOf(claimId)).isEqualTo("VOID");
    }

    @Test
    @DisplayName("[PY-05] 철회 환불의 PG 취소가 실패하면 취소 대기로 남고 — 정리 배치가 다시 시도해 성공하면 취소로 닫힌다")
    void cancelRetriedUntilSucceeded() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        JsonNode created = json(userPost(USER_CLAIMS, claimBody(group, "EXCHANGE", "CHANGE_OF_MIND",
                items(group).get(0).getId(), null, refill.getVariantId())).andExpect(status().isCreated()));
        Long claimId = created.get("claimIds").get(0).asLong();
        String paymentId = created.get("payment").get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(jsonPath("$.claimStatus").value("REQUESTED"));
        fake.willFailCancel(paymentId, FakePaymentGateway.Failure.TIMEOUT);

        userPost(USER_CLAIMS + "/" + claimId + "/withdraw", Map.of()).andExpect(status().isOk());
        assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCEL_REQUESTED");

        // 거절도 상태를 바꾸지 않는다 — 운영자 확인이 필요하지만 재시도는 계속된다.
        fake.willFailCancel(paymentId, FakePaymentGateway.Failure.REJECTED);
        claimPaymentService.cancelRequested();
        assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCEL_REQUESTED");

        fake.willAnswerCancel(paymentId, PortOneCancelResult.Outcome.SUCCEEDED);
        claimPaymentService.reconcile(LocalDateTime.now());
        assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCELLED");
        assertThat(fake.cancelCalls()).hasSize(3);
    }

    @Test
    @DisplayName("[PY-06] 결제창 준비(사전 등록)가 실패하면 502 — 남은 결제 행은 정리 배치가 실패로 닫고, 다시 열면 새 시도다")
    void preRegisterFailure() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        fake.willFailPreRegisterNext();

        userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));
        String failed = jdbc.queryForObject("SELECT payment_id FROM order_claim_payment", String.class);
        assertThat(claimPaymentStatus(failed)).isEqualTo("READY");

        claimPaymentService.reconcile(LocalDateTime.now().plusMinutes(31));
        assertThat(claimPaymentStatus(failed)).isEqualTo("FAILED");
        assertThat(claimStatus(claimId)).isEqualTo("REJECT_HOLD");

        userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId", endsWith("-2")));
    }

    @Test
    @DisplayName("[PY-07] 남의 클레임 결제 열기 · 남의 결제 확정은 404")
    void othersAreNotFound() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        String paymentId = openReshipFee(claimId);
        Users stranger = createConsumer("stranger", "타인");
        String token = bearerToken(stranger.getEmail(), RoleType.USER, stranger.getId());

        mockMvc.perform(post(USER_CLAIMS + "/" + claimId + "/reship-fee/payments")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(toJson(CARD)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
        mockMvc.perform(post(USER_CLAIMS + "/payments/" + paymentId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        assertThat(claimStatus(claimId)).isEqualTo("REJECT_HOLD");
    }

    @Test
    @DisplayName("[PY-08] 차감(일부 반려) · 충당(교환 거절)으로 정산된 반려에는 낼 배송비가 없다 — 409")
    void settledRejectionNeedsNoPayment() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        var request = requestClaim(group, showroomz.domain.order.type.ClaimType.RETURN,
                showroomz.domain.order.type.ClaimReason.CHANGE_OF_MIND, allItems(group), newInvoice());
        Long serum = request.claimIds().get(1);
        rejectedClaim(serum);
        passed(request.claimIds().get(0));
        assertThat(chargeStatusOf(serum)).isEqualTo("DEDUCTED");
        userPost(USER_CLAIMS + "/" + serum + "/reship-fee/payments", CARD)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_PAYMENT_NOT_REQUIRED"));

        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup exchangeGroup = deliveredGroup(creamVariant, 1);
        Map<String, Object> body = claimBody(exchangeGroup, "EXCHANGE", "CHANGE_OF_MIND",
                items(exchangeGroup).get(0).getId(), null, refill.getVariantId());
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", newInvoice()));
        JsonNode created = json(userPost(USER_CLAIMS, body).andExpect(status().isCreated()));
        Long exchangeId = created.get("claimIds").get(0).asLong();
        completeClaimPayment(created.get("payment").get("paymentId").asText());
        rejectedClaim(exchangeId);
        assertThat(jdbc.queryForObject("SELECT status FROM order_claim_charge WHERE collection_id = ? "
                + "AND type = 'REJECT_RESHIP'", String.class, collectionIdOf(exchangeId))).isEqualTo("COVERED");
        userPost(USER_CLAIMS + "/" + exchangeId + "/reship-fee/payments", CARD)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_PAYMENT_NOT_REQUIRED"));
    }

    @Test
    @DisplayName("[PY-09] 결제 기한이 지난 결제 필요 — 상세에 미결제 고지 · 보관 블록이 붙고, 주문 내역의 할 일은 날짜 없이 남는다")
    void overdueShowsStorage() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = rejectedClaim(returnClaim(group));
        jdbc.update("UPDATE order_claim_charge SET due_at = ? WHERE collection_id = ?",
                LocalDateTime.now().minusDays(2), collectionIdOf(claimId));

        userGet(USER_CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.reshipFee.state").value("PAYABLE"))
                .andExpect(jsonPath("$.reshipFee.storage.noticeCount").value(0))
                .andExpect(jsonPath("$.reshipFee.storage.phase").value("NOTICE_PENDING"))
                .andExpect(jsonPath("$.reshipFee.storage.storageDueAt").value(nullValue()));
        detail(group.getOrder().getId())
                .andExpect(jsonPath("$.items[0].todo.claimId").value(claimId))
                .andExpect(jsonPath("$.items[0].todo.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.items[0].todo.label").value("재발송 배송비 결제 필요"));
        userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("[PY-10] 선결제한 교환이 운영자 직권 종결로 사라지면 — 결제는 취소 대기 → 정리 배치가 취소 · 선점 재고는 원복")
    void adminCloseRefundsExchangePayment() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Map<String, Object> body = claimBody(group, "EXCHANGE", "CHANGE_OF_MIND", items(group).get(0).getId(), null,
                refill.getVariantId());
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", newInvoice()));
        JsonNode created = json(userPost(USER_CLAIMS, body).andExpect(status().isCreated()));
        Long claimId = created.get("claimIds").get(0).asLong();
        String paymentId = created.get("payment").get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(jsonPath("$.claimStatus").value("COLLECTING"));
        assertThat(stockOf(refill)).isEqualTo(4);

        claimService.closeByAdmin(claimId, 1L, "물건이 도착하지 않음", LocalDateTime.now());
        assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCEL_REQUESTED");
        assertThat(stockOf(refill)).isEqualTo(5);

        claimPaymentService.reconcile(LocalDateTime.now());
        assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCELLED");
        assertThat(claimRow(claimId)).containsEntry("result", "CANCELLED").containsEntry("cancel_reason", "ADMIN");
    }

    // ------------------------------------------------------------------ 보조

    private String openReshipFee(Long claimId) throws Exception {
        return json(userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD).andExpect(status().isOk()))
                .get("paymentId").asText();
    }
}
