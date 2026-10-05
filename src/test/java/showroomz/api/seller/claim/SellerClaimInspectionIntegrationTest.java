package showroomz.api.seller.claim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.seller.order.SellerOrderTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.service.OrderClaimService.Invoice;
import showroomz.domain.order.service.OrderClaimService.Item;
import showroomz.domain.order.service.OrderClaimService.RequestCommand;
import showroomz.domain.order.service.OrderClaimService.RequestResult;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 입고 확인 · 검수 판정 · 판정 종료(환불 큐) · 환불 집행 · 재발송비 결제(35 설계서 6절 #1 · #2 · #10 · #11 · #17 ~ #19 · #24 ·
 * 앱 클레임 설계서 7절 #15 ~ #17). 반품 한 바퀴를 실제 API 로 돈다 — 신청 → 회수 → 입고 확인 → 판정 → 환불 큐.
 * 환불 집행 · 재발송비 결제는 받는 API 가 아직 없어 도메인 진입점을 직접 부른다.
 */
@IntegrationTest
class SellerClaimInspectionIntegrationTest extends SellerOrderTestSupport {

    private static final String CLAIMS = "/v1/seller/claims";
    private static final String USER_CLAIMS = "/v1/user/claims";

    @Autowired private OrderClaimService claimService;
    @Autowired private OrderProperties orderProperties;

    // ------------------------------------------------------------------ 입고 확인

    @Test
    @DisplayName("입고 확인 — 검수 대기로 가고 검수 기한이 발급된다. 묶음 중 한 건만 먼저 확인해도 되고, 남의 건·단계가 아닌 건은 제외된다")
    void receive() throws Exception {
        OrderDeliveryGroup box = deliveredTwoItemGroup();
        RequestResult request = requestAll(box, ClaimReason.CHANGE_OF_MIND, true);
        Long first = request.claimIds().get(0);
        Long second = request.claimIds().get(1);
        Long waiting = requestAll(deliveredGroup(1), ClaimReason.CHANGE_OF_MIND, false).claimIds().get(0);

        sellerPost(CLAIMS + "/receive", Map.of("claimIds", List.of(first, waiting, 999_999L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.skipped", hasSize(2)))
                .andExpect(jsonPath("$.skipped[0].claimId").value(waiting))
                .andExpect(jsonPath("$.skipped[0].code").value("CLAIM_STATE_CHANGED"));

        assertThat(claimRow(first)).containsEntry("status", "RECEIVED");
        assertThat(claimRow(first).get("inspect_due_at")).isNotNull();
        assertThat(claimRow(first).get("received_by")).isNotNull();
        assertThat(claimRow(second)).containsEntry("status", "COLLECTING");
        assertThat(claimRow(waiting)).containsEntry("status", "REQUESTED");
        assertThat(events(first)).contains("RECEIVED");
        // 두 번은 받지 않는다.
        sellerPost(CLAIMS + "/receive", Map.of("claimIds", List.of(first)))
                .andExpect(jsonPath("$.succeeded").value(0));
        sellerGet(CLAIMS + "/" + first).andExpect(jsonPath("$.actions.canPass").value(true))
                .andExpect(jsonPath("$.actions.canReject").value(true))
                .andExpect(jsonPath("$.actions.canConfirmReceipt").value(false));
    }

    @Test
    @DisplayName("추적상 도착 전 입고 확인 — 설정이 켜져 있으면 회수 중에서도 받고, 꺼져 있으면 제외한다(#17)")
    void receiveBeforeArrival() throws Exception {
        Long claimId = requestAll(deliveredGroup(1), ClaimReason.CHANGE_OF_MIND, true).claimIds().get(0);
        orderProperties.getClaim().setReceiveBeforeArrival(false);
        try {
            sellerPost(CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                    .andExpect(jsonPath("$.succeeded").value(0));
            sellerGet(CLAIMS + "?tab=COLLECTING")
                    .andExpect(jsonPath("$.content[0].actions.canConfirmReceipt").value(false));
            jdbc.update("UPDATE order_claim SET status = 'ARRIVED' WHERE claim_id = ?", claimId);
            sellerPost(CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                    .andExpect(jsonPath("$.succeeded").value(1));
        } finally {
            orderProperties.getClaim().setReceiveBeforeArrival(true);
        }
    }

    // ------------------------------------------------------------------ 검수 통과

    @Test
    @DisplayName("수량 2 중 1개 반품 통과 → 남은 1개 반품 통과 — 수량만큼 반영되고 전량이면 항목이 반품으로 종결된다. 환불 큐는 요청마다 1행(#1 · #2)")
    void passPartialThenFull() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(2, LocalDateTime.now().minusDays(8));
        OrderProduct product = items(group).get(0);

        Long first = received(request(group, product, 1, ClaimReason.CHANGE_OF_MIND));
        sellerPost(CLAIMS + "/" + first + "/inspection/pass", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("REFUND_PENDING"))
                .andExpect(jsonPath("$.summary.outcome.code").value("REFUND_PENDING"))
                .andExpect(jsonPath("$.summary.amount").value(CREAM_PRICE))
                .andExpect(jsonPath("$.refund.requestExpectedAmount").value(CREAM_PRICE));

        assertThat(items(group).get(0).getReturnedQuantity()).isEqualTo(1);
        assertThat(items(group).get(0).getStatus()).isEqualTo(OrderProductStatus.PAID);
        assertThat(refundTasks(group)).singleElement().satisfies(task -> {
            assertThat(task.get("source")).isEqualTo("CLAIM_RETURN_PASSED");
            assertThat(((Number) task.get("refund_amount")).intValue()).isEqualTo(CREAM_PRICE);
            assertThat(task.get("status")).isEqualTo("PENDING");
        });

        Long second = received(request(group, product, 1, ClaimReason.CHANGE_OF_MIND));
        sellerPost(CLAIMS + "/" + second + "/inspection/pass", Map.of()).andExpect(status().isOk());

        assertThat(items(group).get(0).getReturnedQuantity()).isEqualTo(2);
        assertThat(items(group).get(0).getStatus()).isEqualTo(OrderProductStatus.RETURNED);
        assertThat(refundTasks(group)).hasSize(2);
        // 환불 대기는 아직 진행 중이라 구매확정이 선다 — 집행으로 닫히면 확정되고, 반품된 항목은 구매확정으로 올라가지 않는다.
        assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now())).isFalse();
        for (Long taskId : refundTaskIds(group)) {
            claimService.completeRefund(taskId, CREAM_PRICE, 1L, LocalDateTime.now());
        }
        assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now())).isTrue();
        assertThat(items(group).get(0).getStatus()).isEqualTo(OrderProductStatus.RETURNED);
    }

    @Test
    @DisplayName("통과한 건은 다시 판정할 수 없다 — 뒤따라온 거절·통과는 409(#11) · 남의 마켓 건은 404")
    void judgedOnce() throws Exception {
        Long claimId = received(requestAll(deliveredGroup(1), ClaimReason.CHANGE_OF_MIND, true).claimIds().get(0));
        BrandFixture.Brand other = otherBrand();

        sellerPost(sellerToken(other.seller()), CLAIMS + "/" + claimId + "/inspection/pass", Map.of())
                .andExpect(status().isNotFound());
        sellerPost(CLAIMS + "/" + claimId + "/inspection/pass", Map.of()).andExpect(status().isOk());
        sellerPost(CLAIMS + "/" + claimId + "/inspection/reject", rejectBody())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_STATE_CHANGED"));
        sellerPost(CLAIMS + "/" + claimId + "/inspection/pass", Map.of()).andExpect(status().isConflict());
        assertThat(refundTasks(groupOf(claimId))).hasSize(1);
    }

    // ------------------------------------------------------------------ 검수 거절

    @Test
    @DisplayName("거절은 사유 · 설명 · 증빙이 전부 있어야 한다 — 하나라도 빠지면 400 이고 상태는 그대로다(#10)")
    void rejectRequiresAllFields() throws Exception {
        Long claimId = received(requestAll(deliveredGroup(1), ClaimReason.CHANGE_OF_MIND, true).claimIds().get(0));
        List<String> photo = List.of("https://img.test/evidence.jpg");
        List<String> sixPhotos = List.of("a", "b", "c", "d", "e", "f");

        for (Map<String, Object> body : List.of(
                Map.<String, Object>of("detail", "사용 흔적", "evidenceImageUrls", photo),
                Map.<String, Object>of("reasonCode", "USED", "detail", " ", "evidenceImageUrls", photo),
                Map.<String, Object>of("reasonCode", "USED", "detail", "사용 흔적", "evidenceImageUrls", List.of()),
                Map.<String, Object>of("reasonCode", "USED", "detail", "사용 흔적", "evidenceImageUrls", sixPhotos))) {
            sellerPost(CLAIMS + "/" + claimId + "/inspection/reject", body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CLAIM_REJECT_INCOMPLETE"));
        }
        assertThat(claimRow(claimId)).containsEntry("status", "RECEIVED");
        assertThat(count("order_claim_charge")).isZero();
    }

    @Test
    @DisplayName("전체 반려 — 환불 큐가 서지 않고 재발송비 결제를 기다린다. 앱에는 반려 사유 · 증빙 · 결제 기한이 그대로 보인다(앱 #16)")
    void rejectAll() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        Long claimId = received(requestAll(group, ClaimReason.CHANGE_OF_MIND, true).claimIds().get(0));
        LocalDate payDue = LocalDate.now().plusDays(14);

        sellerPost(CLAIMS + "/" + claimId + "/inspection/reject", rejectBody()).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("REJECT_HOLD"))
                .andExpect(jsonPath("$.summary.rejectReasonLabel").value("개봉·사용 흔적"))
                .andExpect(jsonPath("$.summary.sellerEvidenceCount").value(2))
                .andExpect(jsonPath("$.summary.storage.phase").value("NOTICE_PENDING"))
                .andExpect(jsonPath("$.rejectDetail").value("용기 입구에 사용 흔적이 있습니다."))
                .andExpect(jsonPath("$.sellerEvidences", hasSize(2)))
                .andExpect(jsonPath("$.refund.requestExpectedAmount").value(0));

        assertThat(refundTasks(group)).isEmpty();
        Map<String, Object> charge = jdbc.queryForMap("SELECT * FROM order_claim_charge");
        assertThat(charge).containsEntry("type", "REJECT_RESHIP").containsEntry("status", "PENDING")
                .containsEntry("amount", DELIVERY_FEE);
        assertThat(time(charge.get("due_at"))).isEqualTo(payDue.atTime(LocalTime.of(23, 59, 59)));
        assertThat(events(claimId)).contains("INSPECTION_REJECTED");

        userGet(USER_CLAIMS + "/" + claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].phase").value("REJECTED_PAY"))
                .andExpect(jsonPath("$.items[0].statusLabel").value("반품 반려"))
                .andExpect(jsonPath("$.items[0].statusSub").value("재발송 배송비 결제 필요"))
                .andExpect(jsonPath("$.items[0].statusSubTone").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].actions[0].type").value("INQUIRY"))
                .andExpect(jsonPath("$.items[0].rejection.reasonLabel").value("개봉·사용 흔적"))
                .andExpect(jsonPath("$.items[0].rejection.sellerMessage").value("용기 입구에 사용 흔적이 있습니다."))
                .andExpect(jsonPath("$.items[0].rejection.evidenceImageUrls", hasSize(2)))
                .andExpect(jsonPath("$.refund.rejectedAmount").value(CREAM_PRICE))
                .andExpect(jsonPath("$.refund.amount").value(0))
                .andExpect(jsonPath("$.reshipFee.state").value("PAYABLE"))
                .andExpect(jsonPath("$.reshipFee.amount").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.reshipFee.dueDate").value(payDue.toString()))
                .andExpect(jsonPath("$.reshipFee.storage").value(nullValue()));
        // 주문 내역에도 할 일이 뜬다.
        userGet("/v1/user/orders").andExpect(jsonPath("$.content[0].items[0].statusSub").value("검수 반려"))
                .andExpect(jsonPath("$.content[0].items[0].todo.type").value("PAY_RESHIP_FEE"))
                .andExpect(jsonPath("$.content[0].items[0].dimmed").value(false));
    }

    @Test
    @DisplayName("일부 반려 — 판정이 다 끝난 순간 환불 큐 1행(통과 금액 − 차감 − 재발송비) · 반려 건은 거절 보류를 거치지 않고 재발송 대기(#24 · 앱 #15)")
    void partialRejection() throws Exception {
        setFreeShippingThreshold(50_000);
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        RequestResult request = requestAll(group, ClaimReason.CHANGE_OF_MIND, true);
        Long cream = received(request.claimIds().get(0));
        Long serum = received(request.claimIds().get(1));

        sellerPost(CLAIMS + "/" + cream + "/inspection/pass", Map.of()).andExpect(status().isOk());
        // 같은 박스에 판정이 남았다 — 환불 큐는 아직 서지 않는다.
        assertThat(refundTasks(group)).isEmpty();
        assertThat(collectionRow(request.collectionId()).get("finalized_at")).isNull();

        sellerPost(CLAIMS + "/" + serum + "/inspection/reject", rejectBody()).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("RESHIP_READY"))
                .andExpect(jsonPath("$.summary.reshipReason").value("REJECT_RETURN"))
                .andExpect(jsonPath("$.summary.actions.canRegisterReshipment").value(true));

        int expected = CREAM_PRICE - DELIVERY_FEE - DELIVERY_FEE;
        assertThat(refundTasks(group)).singleElement()
                .satisfies(task -> assertThat(((Number) task.get("refund_amount")).intValue()).isEqualTo(expected));
        assertThat(jdbc.queryForObject("SELECT status FROM order_claim_charge", String.class)).isEqualTo("DEDUCTED");
        assertThat(collectionRow(request.collectionId()).get("refund_amount")).isEqualTo(expected);
        assertThat(events(serum)).contains("RESHIP_FEE_SETTLED");
        sellerGet(CLAIMS + "/summary").andExpect(jsonPath("$.tabCounts.REJECT_HOLD").value(0))
                .andExpect(jsonPath("$.tabCounts.RESHIP").value(1));

        userGet(USER_CLAIMS + "/" + serum).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].phase").value("APPROVED"))
                .andExpect(jsonPath("$.items[1].phase").value("REJECTED_PREPARING"))
                .andExpect(jsonPath("$.items[1].statusSub").value("환불액에서 재발송비 차감"))
                .andExpect(jsonPath("$.refund.rejectedAmount").value(SERUM_PRICE))
                .andExpect(jsonPath("$.refund.approvedAmount").value(CREAM_PRICE))
                .andExpect(jsonPath("$.refund.returnDeduction").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.refund.reshipDeduction").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.refund.amount").value(expected))
                .andExpect(jsonPath("$.reshipFee.state").value("DEDUCTED"));
    }

    @Test
    @DisplayName("판정 순서가 반대여도 결과가 같다 — 먼저 거절된 건은 나머지 판정을 기다리는 동안 결제를 열지 않는다(앱 #15 · #17)")
    void rejectFirstThenPass() throws Exception {
        setFreeShippingThreshold(50_000);
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        RequestResult request = requestAll(group, ClaimReason.CHANGE_OF_MIND, true);
        Long cream = received(request.claimIds().get(0));
        Long serum = received(request.claimIds().get(1));

        sellerPost(CLAIMS + "/" + serum + "/inspection/reject", rejectBody()).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("REJECT_HOLD"));
        userGet(USER_CLAIMS + "/" + serum)
                .andExpect(jsonPath("$.items[1].phase").value("REJECTED_WAITING"))
                .andExpect(jsonPath("$.items[1].statusSub").value("함께 보낸 상품을 검수하고 있어요"))
                .andExpect(jsonPath("$.reshipFee.state").value("WAITING"));
        assertThat(jdbc.queryForMap("SELECT * FROM order_claim_charge").get("due_at")).isNull();
        assertThatThrownBy(() -> claimService.markReshipFeePaid(chargeId(), "clm-test", consumer.getId(),
                LocalDateTime.now())).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLAIM_PAYMENT_NOT_REQUIRED));

        sellerPost(CLAIMS + "/" + cream + "/inspection/pass", Map.of()).andExpect(status().isOk());

        assertThat(claimRow(serum)).containsEntry("status", "RESHIP_READY");
        assertThat(refundTasks(group)).singleElement().satisfies(task ->
                assertThat(((Number) task.get("refund_amount")).intValue()).isEqualTo(CREAM_PRICE - 2 * DELIVERY_FEE));
        assertThat(jdbc.queryForObject("SELECT status FROM order_claim_charge", String.class)).isEqualTo("DEDUCTED");
    }

    @Test
    @DisplayName("거절은 구매확정 보류를 푼다 — 7일이 지났으면 거절하는 순간 확정되고, 안 지났거나 다른 보류 클레임이 있으면 기다린다(#18 · #19)")
    void rejectionReleasesConfirmHold() throws Exception {
        OrderDeliveryGroup late = deliveredGroup(1, LocalDateTime.now().minusDays(8));
        OrderDeliveryGroup early = deliveredGroup(1, LocalDateTime.now().minusDays(1));
        OrderDeliveryGroup shared = deliveredTwoItemGroup(LocalDateTime.now().minusDays(8));
        Long lateClaim = received(requestAll(late, ClaimReason.CHANGE_OF_MIND, true).claimIds().get(0));
        Long earlyClaim = received(requestAll(early, ClaimReason.CHANGE_OF_MIND, true).claimIds().get(0));
        Long sharedRejected = received(request(shared, items(shared).get(0), 1, ClaimReason.CHANGE_OF_MIND));
        Long sharedOpen = request(shared, items(shared).get(1), 1, ClaimReason.CHANGE_OF_MIND);

        for (Long claimId : List.of(lateClaim, earlyClaim, sharedRejected)) {
            sellerPost(CLAIMS + "/" + claimId + "/inspection/reject", rejectBody()).andExpect(status().isOk());
        }

        assertThat(reload(late).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
        assertThat(claimRow(lateClaim)).containsEntry("status", "REJECT_HOLD");
        assertThat(reload(early).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(reload(shared).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        // 거절 클레임은 막지 않는다 — 주문 관리의 구매확정 D-N 이 다시 나온다.
        sellerGet(SELLER_ORDERS + "/" + early.getId()).andExpect(jsonPath("$.timeline.confirmDueAt").isNotEmpty());

        claimService.closeByAdmin(sharedOpen, 1L, "미발송", LocalDateTime.now());
        assertThat(reload(shared).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
    }

    // ------------------------------------------------------------------ 환불 집행 · 재발송비 결제

    @Test
    @DisplayName("환불 집행 — 요청의 환불 대기 건을 한꺼번에 닫고 집행액을 항목에 나눠 적는다. 앱은 「환불 금액」으로 바뀐다")
    void completeRefund() throws Exception {
        setFreeShippingThreshold(50_000);
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        RequestResult request = requestAll(group, ClaimReason.CHANGE_OF_MIND, true);
        for (Long claimId : request.claimIds()) {
            sellerPost(CLAIMS + "/" + received(claimId) + "/inspection/pass", Map.of()).andExpect(status().isOk());
        }
        Long taskId = refundTaskIds(group).get(0);
        int amount = CREAM_PRICE + SERUM_PRICE - DELIVERY_FEE;
        assertThat(((Number) refundTasks(group).get(0).get("refund_amount")).intValue()).isEqualTo(amount);

        claimService.completeRefund(taskId, amount, 1L, LocalDateTime.now());

        assertThat(request.claimIds()).allSatisfy(id -> assertThat(claimRow(id)).containsEntry("status", "COMPLETED")
                .containsEntry("result", "REFUNDED"));
        // 차감분은 앞 항목에서 뺀다 — 합이 집행액과 같다.
        assertThat(claimRow(request.claimIds().get(0)).get("refunded_amount")).isEqualTo(CREAM_PRICE - DELIVERY_FEE);
        assertThat(claimRow(request.claimIds().get(1)).get("refunded_amount")).isEqualTo(SERUM_PRICE);
        assertThat(refundTasks(group).get(0)).containsEntry("status", "DONE");
        assertThat(events(request.claimIds().get(0))).contains("REFUND_EXECUTED");
        assertThatThrownBy(() -> claimService.completeRefund(taskId, amount, 1L, LocalDateTime.now()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLAIM_STATE_CHANGED));

        sellerGet(CLAIMS + "?tab=DONE").andExpect(jsonPath("$.content[0].outcome.code").value("REFUNDED"))
                .andExpect(jsonPath("$.content[0].outcome.finalized").value(true));
        userGet(USER_CLAIMS + "/" + request.claimIds().get(0))
                .andExpect(jsonPath("$.items[0].phase").value("DONE"))
                .andExpect(jsonPath("$.items[0].statusLabel").value("반품 완료"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.refund.confirmed").value(true))
                .andExpect(jsonPath("$.refund.amount").value(amount));
        userGet("/v1/user/orders").andExpect(jsonPath("$.content[0].items[0].status").value("RETURNED"))
                .andExpect(jsonPath("$.content[0].items[0].statusSub").value("완료"))
                .andExpect(jsonPath("$.content[0].items[0].amount").value(CREAM_PRICE - DELIVERY_FEE));
    }

    @Test
    @DisplayName("재발송비 결제 반영 — 거절 보류가 재발송 대기로 간다. 두 번은 받지 않는다")
    void reshipFeePaid() throws Exception {
        Long claimId = received(requestAll(deliveredGroup(1), ClaimReason.CHANGE_OF_MIND, true).claimIds().get(0));
        sellerPost(CLAIMS + "/" + claimId + "/inspection/reject", rejectBody()).andExpect(status().isOk());

        claimService.markReshipFeePaid(chargeId(), "clm-test-1", consumer.getId(), LocalDateTime.now());

        assertThat(claimRow(claimId)).containsEntry("status", "RESHIP_READY");
        assertThat(jdbc.queryForMap("SELECT * FROM order_claim_charge")).containsEntry("status", "PAID")
                .containsEntry("paid_payment_id", "clm-test-1");
        assertThat(events(claimId)).contains("RESHIP_FEE_PAID");
        assertThatThrownBy(() -> claimService.markReshipFeePaid(chargeId(), "clm-test-2", consumer.getId(),
                LocalDateTime.now())).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLAIM_PAYMENT_NOT_REQUIRED));
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.items[0].phase").value("REJECTED_PREPARING"))
                .andExpect(jsonPath("$.items[0].statusSub").value("반려 상품 발송 준비 중"))
                .andExpect(jsonPath("$.reshipFee.state").value("PAID"));
        sellerGet(CLAIMS + "?tab=REJECT_HOLD").andExpect(jsonPath("$.content", empty()));
    }

    // ------------------------------------------------------------------ 픽스처

    private OrderDeliveryGroup deliveredGroup(int quantity) throws Exception {
        return deliveredGroup(quantity, LocalDateTime.now().minusHours(1));
    }

    private OrderDeliveryGroup deliveredGroup(int quantity, LocalDateTime deliveredAt) throws Exception {
        return delivered(shipped(prepared(paidGroup(creamVariant, quantity)), "CJ", newInvoice()),
                deliveredAt.withNano(0));
    }

    private OrderDeliveryGroup deliveredTwoItemGroup() throws Exception {
        return deliveredTwoItemGroup(LocalDateTime.now().minusHours(1));
    }

    private OrderDeliveryGroup deliveredTwoItemGroup(LocalDateTime deliveredAt) throws Exception {
        return delivered(shipped(prepared(paidTwoItemGroup()), "CJ", newInvoice()), deliveredAt.withNano(0));
    }

    private void setFreeShippingThreshold(int threshold) {
        jdbc.update("UPDATE market SET free_shipping_threshold = ? WHERE market_id = ?", threshold, brand.marketId());
    }

    private static String newInvoice() {
        return String.valueOf(100_000_000_000L + (long) (Math.random() * 899_999_999_999L));
    }

    /** 그 하위주문의 전 항목을 한 박스로 — 송장을 같이 내면 회수 중으로 시작한다. */
    private RequestResult requestAll(OrderDeliveryGroup group, ClaimReason reason, boolean withInvoice) {
        return claimService.request(new RequestCommand(consumer.getId(), group.getId(), ClaimType.RETURN, reason,
                null, List.of(), items(group).stream().map(p -> new Item(p.getId(), p.getQuantity())).toList(),
                withInvoice ? new Invoice(DeliveryCarrier.CJ, newInvoice()) : null, null), LocalDateTime.now());
    }

    /** 한 항목 · 수량 지정 · 회수 중으로 시작. */
    private Long request(OrderDeliveryGroup group, OrderProduct product, int quantity, ClaimReason reason) {
        return claimService.request(new RequestCommand(consumer.getId(), group.getId(), ClaimType.RETURN, reason,
                null, List.of(), List.of(new Item(product.getId(), quantity)),
                new Invoice(DeliveryCarrier.CJ, newInvoice()), null), LocalDateTime.now()).claimIds().get(0);
    }

    /** 입고 확인까지 — 검수 대기. */
    private Long received(Long claimId) throws Exception {
        sellerPost(CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.succeeded").value(1));
        return claimId;
    }

    private static Map<String, Object> rejectBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("reasonCode", "USED");
        body.put("detail", "용기 입구에 사용 흔적이 있습니다.");
        body.put("evidenceImageUrls", List.of("https://img.test/e1.jpg", "https://img.test/e2.jpg"));
        return body;
    }

    private ResultActions userGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private List<Long> refundTaskIds(OrderDeliveryGroup group) {
        return jdbc.queryForList("SELECT refund_task_id FROM order_refund_task WHERE delivery_group_id = ? "
                + "ORDER BY refund_task_id", Long.class, group.getId());
    }

    private Long chargeId() {
        return jdbc.queryForObject("SELECT charge_id FROM order_claim_charge", Long.class);
    }

    private OrderDeliveryGroup groupOf(Long claimId) {
        Long groupId = jdbc.queryForObject("SELECT delivery_group_id FROM order_claim WHERE claim_id = ?", Long.class,
                claimId);
        return deliveryGroupRepository.findOwned(groupId, brand.marketId()).orElseThrow();
    }

    private Map<String, Object> claimRow(Long claimId) {
        return jdbc.queryForMap("SELECT * FROM order_claim WHERE claim_id = ?", claimId);
    }

    private Map<String, Object> collectionRow(Long collectionId) {
        return jdbc.queryForMap("SELECT * FROM order_claim_collection WHERE collection_id = ?", collectionId);
    }

    private List<String> events(Long claimId) {
        return jdbc.queryForList("SELECT event_type FROM order_claim_history WHERE claim_id = ? "
                + "ORDER BY claim_history_id", String.class, claimId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static LocalDateTime time(Object value) {
        return value instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }
}
