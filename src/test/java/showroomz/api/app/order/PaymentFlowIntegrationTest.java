package showroomz.api.app.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.app.order.service.PaymentConfirmService;
import showroomz.api.app.order.service.PaymentHealthService;
import showroomz.api.app.order.service.PaymentReconciliationService;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.type.MismatchReason;
import showroomz.domain.payment.type.PaymentCancelStatus;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.domain.payment.type.ReconciliationIssueKind;
import showroomz.domain.payment.type.WebhookEventResult;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.global.payment.portone.PortOneCancelResult;
import showroomz.global.payment.portone.PortOnePayment;
import showroomz.global.payment.portone.PortOneStatus;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 결제 PR 5·6·7 — 확정 · 웹훅 · 만료 · 결제 후 취소 · 재시도 · 수렴 · 대사 · 지표(결제 계획서 4-2 · 4-4 · 4-5 · 4-8 · 5-4~5-7 · 8절). */
@DisplayName("[통합] 결제 확정 · 취소 · 만료 · 수렴")
class PaymentFlowIntegrationTest extends OrderPaymentTestSupport {

    @Test
    @DisplayName("complete — 포트원 PAID·금액 일치면 주문 PAID · 상품 PAID · 장바구니 삭제 · 반복 호출은 포트원으로 나가지 않는다")
    void completeConfirmsOrder() throws Exception {
        Cart cart = cartItem(creamVariant, 1);
        Created created = created(createOrder(cardOrder(newKey(), cart.getId())).andReturn().getResponse().getContentAsString());

        complete(created.paymentId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.failReason").doesNotExist());

        assertThat(order(created.orderId()).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order(created.orderId()).getPaidPaymentId()).isEqualTo(created.paymentId());
        assertThat(order(created.orderId()).getPaidAt()).isNotNull();
        assertThat(payment(created.paymentId()).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment(created.paymentId()).getRawResponse()).isNull(); // fake 는 원문이 없다
        assertThat(cartRepository.findById(cart.getId())).isEmpty();
        assertThat(stockOf(creamVariant)).isEqualTo(9);

        int lookups = fake.lookupCalls();
        complete(created.paymentId()).andExpect(status().isOk()).andExpect(jsonPath("$.orderStatus").value("PAID"));
        assertThat(fake.lookupCalls()).isEqualTo(lookups);

        detail(created.orderId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.orderNumber").value(order(created.orderId()).getOrderNumber()))
                .andExpect(jsonPath("$.deliveryAddress.recipientName").value("김수민"))
                .andExpect(jsonPath("$.deliveryMemo").value("문 앞에 놓아주세요"))
                .andExpect(jsonPath("$.groups[0].items[0].status").value("PAID"))
                .andExpect(jsonPath("$.summary.totalAmount").value(CREAM_PRICE + DELIVERY_FEE))
                .andExpect(jsonPath("$.payment.methodLabel").value("신한카드"))
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.cancellable").value(true));

        // 결제 후 취소 — 배송 전 전액. 포트원 취소 → 주문 CANCELLED · 재고 복원.
        cancel(created.orderId()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(fake.cancelCalls()).containsExactly(created.paymentId());
        assertThat(order(created.orderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order(created.orderId()).getCancelReason()).isEqualTo("단순 변심");
        assertThat(payment(created.paymentId()).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(paymentCancelRepository.findByPayment_PaymentIdOrderByIdAsc(created.paymentId()))
                .singleElement().satisfies(c -> assertThat(c.getStatus()).isEqualTo(PaymentCancelStatus.SUCCEEDED));
        assertThat(stockOf(creamVariant)).isEqualTo(10);
    }

    @Test
    @DisplayName("complete — 금액 불일치면 자동 취소(CANCELLED_MISMATCH) · 주문은 결제 대기 그대로 · 재시도 가능")
    void amountMismatchAutoCancels() throws Exception {
        Created created = placeCardOrder(creamVariant, 1);
        fake.willReturnPaid(created.paymentId(), created.amount() - 1);

        complete(created.paymentId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("CANCELLED_MISMATCH"))
                .andExpect(jsonPath("$.failReason").value("결제 금액이 주문 금액과 일치하지 않아 결제가 취소되었습니다."));

        assertThat(fake.cancelCalls()).containsExactly(created.paymentId());
        assertThat(payment(created.paymentId()).getMismatchReason()).isEqualTo(MismatchReason.AMOUNT);
        assertThat(order(created.orderId()).getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(stockOf(creamVariant)).isEqualTo(9); // 예약은 그대로 — 주문이 살아 있다
    }

    @Test
    @DisplayName("complete — 포트원 FAILED 면 결제만 FAILED · READY 면 그대로 · 조회 실패는 502 로 상태 불변")
    void failedAndPending() throws Exception {
        Created created = placeCardOrder(creamVariant, 1);
        fake.willReturn(created.paymentId(), PortOnePayment.of(created.paymentId(), PortOneStatus.FAILED,
                FakePaymentGateway.STORE_ID, created.amount()).withFailure("F001", "한도 초과"));
        complete(created.paymentId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.failReason").value("한도 초과"));

        Created second = placeCardOrder(serumVariant, 1);
        fake.willReturnStatus(second.paymentId(), PortOneStatus.READY, second.amount());
        complete(second.paymentId()).andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("READY"));

        fake.willFailLookup(second.paymentId());
        complete(second.paymentId()).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));
        assertThat(payment(second.paymentId()).getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("웹훅 — 서명이 맞으면 confirm · 같은 webhook-id 는 다시 처리하지 않는다 · 위조는 401 · 모르는 결제는 200 IGNORED · 일시 장애는 500 후 재전송 처리")
    void webhookFlow() throws Exception {
        Created created = placeCardOrder(creamVariant, 1);

        webhook("wh-1", "Transaction.Paid", created.paymentId()).andExpect(status().isOk());
        assertThat(order(created.orderId()).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(webhookEventRepository.findByWebhookId("wh-1")).get()
                .satisfies(e -> assertThat(e.getResult()).isEqualTo(WebhookEventResult.PROCESSED));

        int lookups = fake.lookupCalls();
        webhook("wh-1", "Transaction.Paid", created.paymentId()).andExpect(status().isOk());
        assertThat(fake.lookupCalls()).isEqualTo(lookups);
        assertThat(webhookEventRepository.findByWebhookId("wh-1")).get().satisfies(e -> assertThat(e.getAttempts()).isEqualTo(1));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(WEBHOOK)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}")
                        .header("webhook-id", "wh-x").header("webhook-timestamp", "1").header("webhook-signature", "v1,bad"))
                .andExpect(status().isUnauthorized());

        webhook("wh-2", "Transaction.Paid", "unknown-payment").andExpect(status().isOk());
        assertThat(webhookEventRepository.findByWebhookId("wh-2")).get()
                .satisfies(e -> assertThat(e.getResult()).isEqualTo(WebhookEventResult.IGNORED));

        Created second = placeCardOrder(serumVariant, 1);
        fake.willFailLookup(second.paymentId());
        webhook("wh-3", "Transaction.Paid", second.paymentId()).andExpect(status().isInternalServerError());
        assertThat(webhookEventRepository.findByWebhookId("wh-3")).get()
                .satisfies(e -> assertThat(e.getResult()).isEqualTo(WebhookEventResult.FAILED));
        fake.willReturnPaid(second.paymentId(), second.amount());
        webhook("wh-3", "Transaction.Paid", second.paymentId()).andExpect(status().isOk());
        assertThat(webhookEventRepository.findByWebhookId("wh-3")).get().satisfies(e -> {
            assertThat(e.getResult()).isEqualTo(WebhookEventResult.PROCESSED);
            assertThat(e.getAttempts()).isEqualTo(2);
        });
        assertThat(order(second.orderId()).getStatus()).isEqualTo(OrderStatus.PAID);

        // PG 콘솔 취소 — Transaction.Cancelled 웹훅이 PAID 주문을 CANCELLED 로 수렴시키고 재고를 돌려놓는다(4-5 ⑤).
        fake.willReturnStatus(second.paymentId(), PortOneStatus.CANCELLED, second.amount());
        webhook("wh-4", "Transaction.Cancelled", second.paymentId()).andExpect(status().isOk());
        assertThat(order(second.orderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment(second.paymentId()).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(stockOf(serumVariant)).isEqualTo(10);
    }

    @Test
    @DisplayName("만료 — 결제창을 연 적 없는 주문(404)은 즉시 만료 · 재고 복원 · 뒤늦게 결제가 도착하면 자동 취소하고 재고는 두 번 늘지 않는다")
    void expireThenLatePayment() throws Exception {
        Created created = placeCardOrder(creamVariant, 2);
        fake.willReturnNotFound(created.paymentId());
        LocalDateTime later = LocalDateTime.now().plusMinutes(31);

        assertThat(expirationService.findIdsToExpire(later, 10)).containsExactly(created.orderId());
        assertThat(expirationService.expire(created.orderId(), later)).isTrue();
        assertThat(order(created.orderId()).getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(payment(created.paymentId()).getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(stockOf(creamVariant)).isEqualTo(10);
        assertThat(expirationService.expire(created.orderId(), later)).isFalse();

        fake.willReturnPaid(created.paymentId(), created.amount());
        confirmService.confirm(created.paymentId(), PaymentConfirmService.Trigger.WEBHOOK);
        assertThat(payment(created.paymentId()).getStatus()).isEqualTo(PaymentStatus.CANCELLED_MISMATCH);
        assertThat(payment(created.paymentId()).getMismatchReason()).isEqualTo(MismatchReason.ORDER_CLOSED);
        assertThat(order(created.orderId()).getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(stockOf(creamVariant)).isEqualTo(10);
        assertThat(fake.cancelCalls()).containsExactly(created.paymentId());

        // 만료 뒤 같은 키 재요청은 409, 재시도 API 도 409.
        Cart cart = cartItem(serumVariant, 1);
        String key = newKey();
        Created pending = created(createOrder(cardOrder(key, cart.getId())).andReturn().getResponse().getContentAsString());
        fake.willReturnNotFound(pending.paymentId());
        expirationService.expire(pending.orderId(), LocalDateTime.now().plusMinutes(31));
        createOrder(cardOrder(key, cart.getId())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_ALREADY_CLOSED"));
        retry(pending.orderId(), Map.of("method", "CARD", "cardIssuer", "KB")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("만료 전 조회 — PAID 면 만료하지 않고 확정 · PENDING 이면 5분씩 3회 미룬 뒤 만료 · 통신 실패 3회면 조회 없이 만료")
    void expiryChecksPortOneFirst() throws Exception {
        Created paid = placeCardOrder(creamVariant, 1);
        LocalDateTime later = LocalDateTime.now().plusMinutes(31);
        assertThat(expirationService.expire(paid.orderId(), later)).isFalse();
        assertThat(order(paid.orderId()).getStatus()).isEqualTo(OrderStatus.PAID);

        Created pending = placeCardOrder(creamVariant, 1);
        fake.willReturnStatus(pending.paymentId(), PortOneStatus.PENDING, pending.amount());
        LocalDateTime expiresAt = order(pending.orderId()).getExpiresAt();
        for (int i = 1; i <= 3; i++) {
            assertThat(expirationService.expire(pending.orderId(), expiresAt.plusMinutes(5L * (i - 1)))).isFalse();
            assertThat(order(pending.orderId()).getExpiryDeferrals()).isEqualTo(i);
        }
        assertThat(order(pending.orderId()).getExpiresAt()).isAfter(expiresAt);
        assertThat(expirationService.expire(pending.orderId(), order(pending.orderId()).getExpiresAt())).isTrue();
        assertThat(order(pending.orderId()).getStatus()).isEqualTo(OrderStatus.EXPIRED);

        Created unreachable = placeCardOrder(creamVariant, 1);
        fake.willFailLookup(unreachable.paymentId());
        assertThat(expirationService.expire(unreachable.orderId(), later)).isFalse();
        assertThat(expirationService.expire(unreachable.orderId(), later)).isFalse();
        assertThat(order(unreachable.orderId()).getExpiryCheckFailures()).isEqualTo(2);
        assertThat(expirationService.expire(unreachable.orderId(), later)).isTrue();
        assertThat(order(unreachable.orderId()).getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("결제 후 취소 — 타임아웃이면 202 CANCEL_REQUESTED · 동시 취소는 외부 호출 1회 · 수렴 단계가 CANCELLED 로 닫는다 · 거절이면 PAID 복귀")
    void cancelAfterPaymentConverges() throws Exception {
        Created created = placeCardOrder(creamVariant, 1);
        complete(created.paymentId()).andExpect(jsonPath("$.orderStatus").value("PAID"));
        fake.willFailCancel(created.paymentId(), FakePaymentGateway.Failure.TIMEOUT);

        cancel(created.orderId()).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentStatus").value("CANCEL_REQUESTED"));
        cancel(created.orderId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_IN_PROGRESS"));
        assertThat(fake.cancelCalls()).hasSize(1);
        Payment claimed = payment(created.paymentId());
        assertThat(claimed.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        assertThat(claimed.getNextCancelRetryAt()).isNotNull();
        detail(created.orderId()).andExpect(jsonPath("$.cancellable").value(false));

        // 2분 뒤 수렴 — 포트원은 아직 PAID → 취소 재호출(이번엔 성공) → CANCELLED · 주문 CANCELLED · 재고 복원.
        fake.willAnswerCancel(created.paymentId(), PortOneCancelResult.Outcome.SUCCEEDED);
        LocalDateTime later = claimed.getNextCancelRetryAt().plusSeconds(1);
        assertThat(expirationService.findPaymentIdsToConvergeCancel(later, 10)).containsExactly(created.paymentId());
        expirationService.convergeCancel(created.paymentId(), later);
        assertThat(payment(created.paymentId()).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(order(created.orderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(creamVariant)).isEqualTo(10);
        assertThat(expirationService.findPaymentIdsToConvergeCancel(later.plusDays(1), 10)).isEmpty();

        // 명시적 거절 — PAID 복귀 · payment_cancel FAILED · 502.
        Created rejected = placeCardOrder(serumVariant, 1);
        complete(rejected.paymentId());
        fake.willFailCancel(rejected.paymentId(), FakePaymentGateway.Failure.REJECTED);
        cancel(rejected.orderId()).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_FAILED"));
        assertThat(payment(rejected.paymentId()).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order(rejected.orderId()).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentCancelRepository.findByPayment_PaymentIdOrderByIdAsc(rejected.paymentId()))
                .singleElement().satisfies(c -> assertThat(c.getStatus()).isEqualTo(PaymentCancelStatus.FAILED));
    }

    @Test
    @DisplayName("취소 수렴 상한 — 타임아웃이 반복되면 간격을 지수로 늘리다 5회에 CANCEL_FAILED(운영자 처리) · 운영 지표가 경고한다")
    void cancelConvergenceGivesUp() throws Exception {
        Created created = placeCardOrder(creamVariant, 1);
        complete(created.paymentId());
        fake.willFailCancel(created.paymentId(), FakePaymentGateway.Failure.TIMEOUT);
        cancel(created.orderId()).andExpect(status().isAccepted());

        LocalDateTime now = LocalDateTime.now().withNano(0);
        for (int attempt = 1; attempt < 5; attempt++) {
            expirationService.convergeCancel(created.paymentId(), now);
            Payment p = payment(created.paymentId());
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
            assertThat(p.getCancelAttempts()).isEqualTo(attempt);
            assertThat(p.getNextCancelRetryAt()).isEqualTo(now.plusMinutes(2L << (attempt - 1)));
        }
        expirationService.convergeCancel(created.paymentId(), now);
        assertThat(payment(created.paymentId()).getStatus()).isEqualTo(PaymentStatus.CANCEL_FAILED);
        assertThat(order(created.orderId()).getStatus()).isEqualTo(OrderStatus.PAID);
        // 최초 호출 1회 + 수렴 재호출 5회(상한) = 6회 — 그 뒤로는 부르지 않는다.
        assertThat(fake.cancelCalls()).hasSize(6);
        expirationService.convergeCancel(created.paymentId(), now.plusHours(1));
        assertThat(fake.cancelCalls()).hasSize(6);

        assertThat(healthService.check(LocalDateTime.now()))
                .extracting(PaymentHealthService.Metric::name)
                .contains("운영자 처리 대기(CANCEL_FAILED)");
    }

    @Test
    @DisplayName("재시도 — 새 paymentId(시도 2) · 이전 시도 SUPERSEDED · 이전 창이 먼저 결제되면 주문의 결제로 받아들이고 새 시도가 결제되면 자동 취소")
    void retryAndSupersededPayment() throws Exception {
        Created first = placeCardOrder(creamVariant, 1);
        fake.willReturn(first.paymentId(), PortOnePayment.of(first.paymentId(), PortOneStatus.FAILED, FakePaymentGateway.STORE_ID, first.amount()));
        complete(first.paymentId()).andExpect(jsonPath("$.paymentStatus").value("FAILED"));

        String second = retry(first.orderId(), Map.of("method", "EASY_PAY", "easyPayProvider", "TOSSPAY"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(first.orderId()))
                .andExpect(jsonPath("$.payment.payMethod").value("EASY_PAY"))
                .andExpect(jsonPath("$.payment.easyPayProvider").value("TOSSPAY"))
                .andReturn().getResponse().getContentAsString();
        String secondId = objectMapper.readTree(second).get("payment").get("paymentId").asText();
        assertThat(secondId).isEqualTo(first.paymentId().replaceAll("-1$", "-2"));
        assertThat(payment(secondId).getAttempt()).isEqualTo(2);
        assertThat(payment(first.paymentId()).getStatus()).isEqualTo(PaymentStatus.FAILED);

        // 셋째 시도 — 둘째(READY)는 SUPERSEDED 로 내려간다.
        String third = retry(first.orderId(), Map.of("method", "CARD", "cardIssuer", "KB"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String thirdId = objectMapper.readTree(third).get("payment").get("paymentId").asText();
        assertThat(payment(secondId).getStatus()).isEqualTo(PaymentStatus.SUPERSEDED);

        // 둘째 창이 뒤늦게 결제됐다 — 주문이 아직 PAYMENT_PENDING 이면 그 돈은 이 주문의 돈이다(4-2).
        fake.willReturnPaid(secondId, first.amount());
        complete(secondId).andExpect(jsonPath("$.orderStatus").value("PAID")).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        assertThat(order(first.orderId()).getPaidPaymentId()).isEqualTo(secondId);

        // 셋째도 결제됐다 — 주문은 다른 결제로 이미 완료 → 자동 취소(이중 결제 방지).
        fake.willReturnPaid(thirdId, first.amount());
        complete(thirdId).andExpect(jsonPath("$.orderStatus").value("PAID")).andExpect(jsonPath("$.paymentStatus").value("CANCELLED_MISMATCH"));
        assertThat(payment(thirdId).getMismatchReason()).isEqualTo(MismatchReason.NOT_ORDER_PAYMENT);
        assertThat(fake.cancelCalls()).containsExactly(thirdId);
        assertThat(order(first.orderId()).getPaidPaymentId()).isEqualTo(secondId);

        retry(first.orderId(), Map.of("method", "CARD", "cardIssuer", "KB"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_IN_PROGRESS"));
    }

    @Test
    @DisplayName("일일 대사 — 포트원 PAID·우리 EXPIRED 는 이슈 기록 + confirm 으로 자동 취소 수렴 · 우리 PAID·포트원 없음은 이슈만 · 같은 어긋남은 행 1개")
    void reconciliation() throws Exception {
        Created expired = placeCardOrder(creamVariant, 1);
        fake.willReturnNotFound(expired.paymentId());
        expirationService.expire(expired.orderId(), LocalDateTime.now().plusMinutes(31));
        PortOnePayment remotePaid = PortOnePayment.of(expired.paymentId(), PortOneStatus.PAID, FakePaymentGateway.STORE_ID, expired.amount());
        fake.willList(remotePaid);
        fake.willReturn(expired.paymentId(), remotePaid);

        Created oursPaid = placeCardOrder(serumVariant, 1);
        complete(oursPaid.paymentId());

        LocalDateTime now = LocalDateTime.now();
        PaymentReconciliationService.Result result = reconciliationService.reconcile(now.minusDays(1), now.plusDays(1), now);
        assertThat(result.skipped()).isFalse();
        assertThat(result.issues()).containsEntry(ReconciliationIssueKind.PORTONE_PAID_NOT_OURS, 1)
                .containsEntry(ReconciliationIssueKind.OURS_PAID_NOT_PORTONE, 1);
        assertThat(payment(expired.paymentId()).getStatus()).isEqualTo(PaymentStatus.CANCELLED_MISMATCH);
        assertThat(issueRepository.findByPaymentIdAndKind(expired.paymentId(), ReconciliationIssueKind.PORTONE_PAID_NOT_OURS))
                .get().satisfies(issue -> assertThat(issue.isResolved()).isTrue());
        assertThat(issueRepository.findByPaymentIdAndKind(oursPaid.paymentId(), ReconciliationIssueKind.OURS_PAID_NOT_PORTONE))
                .get().satisfies(issue -> assertThat(issue.isResolved()).isFalse());

        reconciliationService.reconcile(now.minusDays(1), now.plusDays(1), now.plusDays(1));
        assertThat(issueRepository.count()).isEqualTo(2);
        assertThat(healthService.measure(now)).filteredOn(m -> m.name().equals("대사 미해결"))
                .singleElement().satisfies(m -> assertThat(m.value()).isEqualTo(1));
    }
}
