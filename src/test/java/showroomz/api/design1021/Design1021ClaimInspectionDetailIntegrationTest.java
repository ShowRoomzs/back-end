package showroomz.api.design1021;

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
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.order.entity.OrderClaimFreightAdjustment;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.OrderClaimFreightAdjustmentRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.FreightAdjustmentDirection;
import showroomz.domain.order.type.FreightAdjustmentTrigger;
import showroomz.domain.order.type.FreightSettledVia;
import showroomz.domain.order.type.RefundMethod;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.domain.product.entity.ProductVariant;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 4절 — 검수 3갈래 · 귀책 변경 운임의 P4 기본 흐름 밖: 토요일 도착 자동 입고 · 교환 사유 번복 결제 · 착불 신청의 반려(전체 · 일부) ·
 * 반려 이의 인용의 운임 환급(PG · 계좌 송금) · 송금 대기 큐 · 환불 계좌 안내 문의 검증 · 송금 완료 알림.
 */
@DisplayName("1021 4절 검수 상세 — 자동 입고 · 교환 번복 결제 · 착불 반려 · 이의 인용 운임 · 송금 대기 · 문의 연결")
class Design1021ClaimInspectionDetailIntegrationTest extends ClaimTestSupport {

    private static final String ADMIN_CLAIMS = "/v1/admin/claims";
    private static final String ADMIN_REFUNDS = "/v1/admin/refunds";
    private static final Map<String, Object> CARD = Map.of("method", "CARD", "cardIssuer", "SHINHAN");
    private static final String EVIDENCE = "https://img.test/overturn-1.jpg";

    @Autowired private OrderClaimFreightAdjustmentRepository freightRepository;
    @Autowired private OrderRefundTaskRepository refundTaskRepository;
    @Autowired private NotificationRepository notificationRepository;

    private String admin;

    @BeforeEach
    void setUpAdmin() {
        Seller operator = fixture.createAdmin("claims-1021@showroomz.test", "운영자");
        admin = adminToken(operator);
    }

    // ------------------------------------------------------------------ 4-2 자동 입고

    @Test
    @DisplayName("토요일 도착 — 다음 영업일(월)부터 3영업일(목) 끝까지는 그대로 · 금요일에 시스템 입고 · 검수 기한 발급 · 회수 중은 대상 아님")
    void autoReceiveCountsFromNextBusinessDay() throws Exception {
        Long claimId = returnClaim(deliveredGroup(creamVariant, 1));
        Long collecting = returnClaim(deliveredGroup(creamVariant, 1));
        LocalDateTime saturday = LocalDateTime.of(2026, 11, 7, 10, 0);
        markArrived(claimId, saturday);
        jdbc.update("UPDATE order_claim_collection SET arrived_at = ? WHERE collection_id = "
                + "(SELECT collection_id FROM order_claim WHERE claim_id = ?)", saturday, collecting);

        LocalDateTime thursdayNight = LocalDateTime.of(2026, 11, 12, 23, 0);
        assertThat(claimService.autoReceive(claimId, thursdayNight)).isFalse();
        assertThat(claimStatus(claimId)).isEqualTo("ARRIVED");

        LocalDateTime friday = LocalDateTime.of(2026, 11, 13, 0, 10);
        assertThat(claimService.findIdsToAutoReceive(friday, 100)).contains(claimId).doesNotContain(collecting);
        assertThat(claimService.autoReceive(collecting, friday)).isFalse();
        assertThat(claimService.autoReceive(claimId, friday)).isTrue();

        Map<String, Object> row = jdbc.queryForMap("SELECT status, received_by, inspect_due_at FROM order_claim "
                + "WHERE claim_id = ?", claimId);
        assertThat(row.get("status")).isEqualTo("RECEIVED");
        assertThat(row.get("received_by")).isNull();
        assertThat(row.get("inspect_due_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT detail FROM order_claim_history WHERE claim_id = ? "
                + "AND event_type = 'AUTO_RECEIVED'", String.class, claimId)).contains("3영업일 경과");
        sellerGet(SELLER_CLAIMS + "/" + claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.inspection.autoReceived").value(true));
    }

    // ------------------------------------------------------------------ 4-3 교환 사유 번복

    @Test
    @DisplayName("교환 사유 번복 — 재발송 배송비 + 착불 운임 청구 · 소비자 1c 「결제 필요」 · 결제하면 재발송 대기 · 운임은 재발송비 줄로(운임 정산 제외)")
    void exchangeOverturnPaymentFlow() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        Long claimId = received(exchangeClaim(group, refill));
        int base = baseFee(group);

        overturn(claimId).andExpect(status().isOk());

        assertThat(claimStatus(claimId)).isEqualTo("REJECT_HOLD");
        assertThat(feeBearerOf(claimId)).isEqualTo("CONSUMER");
        Map<String, Object> charge = jdbc.queryForMap("SELECT amount, status, due_at FROM order_claim_charge "
                + "WHERE collection_id = ? AND type = 'OVERTURN_RESHIP'", collectionIdOf(claimId));
        assertThat(((Number) charge.get("amount")).intValue()).isEqualTo(base * 2);
        assertThat(charge.get("status")).isEqualTo("PENDING");
        assertThat(charge.get("due_at")).isNotNull();
        assertThat(freightRows(claimId)).singleElement().satisfies(row -> {
            assertThat(row.getTrigger()).isEqualTo(FreightAdjustmentTrigger.REASON_OVERTURNED);
            assertThat(row.getSettledVia()).isEqualTo(FreightSettledVia.CHARGE);
        });
        assertThat(freightRepository.findByGroupBuyIdAndAppliedSettlementIdIsNullAndSettledViaNot(
                group.getGroupBuyId(), FreightSettledVia.CHARGE)).isEmpty();

        userGet(USER_CLAIMS + "/" + claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].phase").value("OVERTURN_PAYMENT_PENDING"))
                .andExpect(jsonPath("$.items[0].statusLabel").value("교환 사유 변경"))
                .andExpect(jsonPath("$.reshipFee.state").value("PAYABLE"))
                .andExpect(jsonPath("$.reshipFee.amount").value(base * 2))
                .andExpect(jsonPath("$.inspection.state").value("OVERTURN_PAYMENT_PENDING"))
                .andExpect(jsonPath("$.inspection.payment.reshipFee").value(base))
                .andExpect(jsonPath("$.inspection.payment.collectFreight").value(base))
                .andExpect(jsonPath("$.inspection.payment.total").value(base * 2))
                .andExpect(jsonPath("$.inspection.attachments[0]").value(EVIDENCE));
        sellerGet(SELLER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.inspection.overturnFee").value(base * 2))
                .andExpect(jsonPath("$.actions.canOverturn").value(false))
                // 목록 행도 번복 청구를 재발송비로 · 판정 요약(58 계획 3-3)
                .andExpect(jsonPath("$.summary.reshipFee.amount").value(base * 2))
                .andExpect(jsonPath("$.summary.inspectionResult").value("REASON_OVERTURNED"));
        userGet(USER_CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.reshipFee.reshipFee").value(base))
                .andExpect(jsonPath("$.reshipFee.collectFreight").value(base))
                .andExpect(jsonPath("$.reshipFee.storage").doesNotExist());

        // 58 계획 5-6 — 결제 기한이 지나면 번복 보류도 반려 보류처럼 미결제 고지 · 보관 기한 블록이 붙는다
        Object dueAt = charge.get("due_at");
        jdbc.update("UPDATE order_claim_charge SET due_at = ? WHERE collection_id = ? AND type = 'OVERTURN_RESHIP'",
                LocalDateTime.now().minusDays(2), collectionIdOf(claimId));
        userGet(USER_CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.reshipFee.overdue").value(true))
                .andExpect(jsonPath("$.reshipFee.storage.noticeCount").value(0))
                .andExpect(jsonPath("$.reshipFee.storage.phase").value("NOTICE_PENDING"));
        jdbc.update("UPDATE order_claim_charge SET due_at = ? WHERE collection_id = ? AND type = 'OVERTURN_RESHIP'",
                dueAt, collectionIdOf(claimId));

        String paymentId = json(userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(jsonPath("$.paymentStatus").value("PAID"));

        assertThat(claimStatus(claimId)).isEqualTo("RESHIP_READY");
        userGet(USER_CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.items[0].phase").value("RESHIP_PREPARING"))
                .andExpect(jsonPath("$.inspection.state").value("REASON_OVERTURNED"))
                .andExpect(jsonPath("$.inspection.payment.status").value("PAID"));
    }

    @Test
    @DisplayName("사유 번복 입력 검증 — 입고 확인 전 409 · 설명 공백 400 · 증빙 6장 400 · 상태는 그대로")
    void overturnValidation() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = requestClaim(group, ClaimType.RETURN, ClaimReason.DAMAGED_OR_DEFECTIVE, allItems(group),
                newInvoice()).claimIds().get(0);
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/overturn",
                Map.of("explanation", "정상", "evidenceImageUrls", List.of(EVIDENCE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_STATE_CHANGED"));

        received(claimId);
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/overturn",
                Map.of("explanation", "  ", "evidenceImageUrls", List.of(EVIDENCE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLAIM_OVERTURN_EVIDENCE_REQUIRED"));
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/overturn",
                Map.of("explanation", "정상 작동", "evidenceImageUrls", List.of(EVIDENCE, EVIDENCE, EVIDENCE, EVIDENCE,
                        EVIDENCE, EVIDENCE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLAIM_OVERTURN_EVIDENCE_REQUIRED"));
        assertThat(claimStatus(claimId)).isEqualTo("RECEIVED");
    }

    // ------------------------------------------------------------------ 4-3 착불 신청의 반려

    @Test
    @DisplayName("착불(브랜드 귀책) 반품 전체 반려 — 재발송 배송비 + 착불 운임 합산 결제 · 운임 행 CHARGE · 소비자 결제 블록")
    void fullRejectOfCollectClaimChargesFreight() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Long claimId = requestClaim(group, ClaimType.RETURN, ClaimReason.DAMAGED_OR_DEFECTIVE, allItems(group),
                newInvoice()).claimIds().get(0);
        int base = baseFee(group);

        rejectedClaim(claimId);

        Map<String, Object> charge = jdbc.queryForMap("SELECT amount, status FROM order_claim_charge "
                + "WHERE collection_id = ? AND type = 'REJECT_RESHIP'", collectionIdOf(claimId));
        assertThat(((Number) charge.get("amount")).intValue()).isEqualTo(base * 2);
        assertThat(charge.get("status")).isEqualTo("PENDING");
        assertThat(freightRows(claimId)).singleElement().satisfies(row -> {
            assertThat(row.getTrigger()).isEqualTo(FreightAdjustmentTrigger.REJECT_WITH_COLLECT);
            assertThat(row.getDirection()).isEqualTo(FreightAdjustmentDirection.CHARGE_CONSUMER);
            assertThat(row.getCollectFreight()).isEqualTo(base);
            assertThat(row.getSettledVia()).isEqualTo(FreightSettledVia.CHARGE);
        });
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.reshipFee.state").value("PAYABLE"))
                .andExpect(jsonPath("$.reshipFee.amount").value(base * 2))
                // 합계를 재발송 배송비 · 착불 운임 두 줄로(58 계획 2-4)
                .andExpect(jsonPath("$.reshipFee.reshipFee").value(base))
                .andExpect(jsonPath("$.reshipFee.collectFreight").value(base));
    }

    @Test
    @DisplayName("착불 반품 일부 반려 — 통과분 환불에서 재발송 배송비 + 착불 운임을 뺀다(청구 DEDUCTED) · 결제 없음")
    void partialRejectOfCollectClaimDeductsFreight() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 2);
        Long claimId = received(requestClaim(group, ClaimType.RETURN, ClaimReason.DAMAGED_OR_DEFECTIVE,
                allItems(group), newInvoice()).claimIds().get(0));
        int base = baseFee(group);
        // 판정 전 운임 미리보기 — 기본 배송비 · 재발송 배송비 · 원래 결제액 · PG 환급 한도(58 계획 5-4)
        sellerGet(SELLER_CLAIMS + "/" + claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.freightPreview.baseDeliveryFee").value(base))
                .andExpect(jsonPath("$.freightPreview.reshipFee").value(base))
                .andExpect(jsonPath("$.freightPreview.originalPaidAmount").isNumber())
                .andExpect(jsonPath("$.freightPreview.pgRefundableAmount").isNumber());

        Map<String, Object> body = new HashMap<>(rejectBody());
        body.put("rejectedQuantity", 1);
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", body).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM order_claim_charge WHERE collection_id = ? "
                + "AND type = 'REJECT_RESHIP'", String.class, collectionIdOf(claimId))).isEqualTo("DEDUCTED");
        assertThat(refundTaskRows(group)).anySatisfy(task -> {
            assertThat(task.getSource()).isEqualTo(RefundTaskSource.CLAIM_RETURN_PASSED);
            assertThat(task.getRefundAmount()).isEqualTo(CREAM_PRICE - base * 2);
        });
        // 차감은 재발송 배송비 · 착불 운임 두 줄로(58 계획 2-4) · 판정 뒤에는 미리보기가 없다
        userGet(USER_CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.refund.reshipDeduction").value(base))
                .andExpect(jsonPath("$.refund.collectFreightDeduction").value(base));
        sellerGet(SELLER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.freightPreview").value(nullValue()));
    }

    // ------------------------------------------------------------------ 4-4 이의 인용

    @Test
    @DisplayName("선불 반품 반려 → 이의 인용 — 귀책 브랜드 · 선불 운임 운임 정산(REFUND) · 원래 결제 잔액 안이면 PG 운임 환급 행")
    void disputeAcceptedRefundsPrepaidFreightByPg() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);   // 27,200 + 배송비(기준 100,000 미만) — 잔액이 남는다
        Long claimId = rejectedClaim(returnClaim(group));
        int base = baseFee(group);

        accept(claimId).andExpect(status().isOk());

        assertThat(feeBearerOf(claimId)).isEqualTo("SELLER");
        assertThat(freightRows(claimId)).singleElement().satisfies(row -> {
            assertThat(row.getTrigger()).isEqualTo(FreightAdjustmentTrigger.DISPUTE_ACCEPTED);
            assertThat(row.getDirection()).isEqualTo(FreightAdjustmentDirection.REFUND_CONSUMER);
            assertThat(row.getPrepaidFreight()).isEqualTo(base);
            assertThat(row.getInitialFeeRestored()).isZero();   // 전체 반려 — 환불에서 뺀 최초 배송비가 없다
            assertThat(row.getSettledVia()).isEqualTo(FreightSettledVia.REFUND_ADDITION);
        });
        assertThat(refundTaskRows(group)).anySatisfy(task -> {
            assertThat(task.getSource()).isEqualTo(RefundTaskSource.FREIGHT_REFUND);
            assertThat(task.getMethod()).isEqualTo(RefundMethod.PG_CANCEL);
            assertThat(task.getRefundAmount()).isEqualTo(base);
        });
    }

    @Test
    @DisplayName("무료배송 전량 반려 → 이의 인용 — 상품 환불이 잔액을 먼저 쓰고 선불 운임은 계좌 송금 대기 · 경로 「운임 환급」 · 어드민 홈 「송금 대기」")
    void disputeAcceptedBeyondPaymentGoesToTransfer() throws Exception {
        freeShippingFrom(50_000);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 2);   // 54,400 · 무료배송
        Long claimId = rejectedClaim(returnClaim(group));
        int base = baseFee(group);

        // 58 계획 5-13 — 인용 전 예정액: 선불 운임 · 통과분 환불이 없어 최초 배송비 환원은 0 (인용 뒤 송금 대기 금액과 같다)
        JsonNode before = json(adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(status().isOk()));
        assertThat(before.at("/disputePreview/prepaidFreightRefund").asInt()).isEqualTo(base);
        assertThat(before.at("/disputePreview/initialFeeRestored").asInt()).isZero();
        assertThat(before.at("/disputePreview/total").asInt()).isEqualTo(base);

        accept(claimId).andExpect(status().isOk());

        OrderRefundTask transfer = freightTransfer(group);
        assertThat(transfer.getRefundAmount()).isEqualTo(base);
        assertThat(transfer.getStatus()).isEqualTo(RefundTaskStatus.PENDING);
        assertThat(refundTaskRows(group)).anySatisfy(task -> {
            assertThat(task.getSource()).isEqualTo(RefundTaskSource.OPERATOR_REASON);
            assertThat(task.getRefundAmount()).isEqualTo(CREAM_PRICE * 2);
        });
        assertThat(freightRows(claimId).get(0).getSettledVia()).isEqualTo(FreightSettledVia.BANK_TRANSFER);
        assertThat(jdbc.queryForObject("SELECT return_deduction FROM order_claim_collection WHERE collection_id = ?",
                Integer.class, collectionIdOf(claimId))).isZero();

        JsonNode freightRoute = json(adminGet(ADMIN_REFUNDS + "?tab=PENDING&route=FREIGHT").andExpect(status().isOk()));
        assertThat(freightRoute.at("/content").size()).isEqualTo(1);
        assertThat(freightRoute.at("/content/0/route").asText()).isEqualTo("FREIGHT");
        assertThat(freightRoute.at("/content/0/statusLabel").asText()).isEqualTo("송금 대기");
        assertThat(freightRoute.at("/content/0/executable").asBoolean()).isFalse();

        JsonNode todo = json(adminGet("/v1/admin/home").andExpect(status().isOk())).get("todo");
        assertThat(queues(todo)).contains("REFUND_TRANSFER", "REFUND_EXECUTION");

        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.inspection.state").value("FAULT_CHANGED"))
                .andExpect(jsonPath("$.inspection.refundSplit.bankTransfer").value(base))
                .andExpect(jsonPath("$.inspection.refundSplit.inquiryNeeded").value(true));
    }

    // ------------------------------------------------------------------ 4-5 문의 연결 · 송금 완료

    @Test
    @DisplayName("환불 계좌 안내 문의 — 유형 · 송금 대기 아님 · 다른 주문 검증 · 어드민 문의 상세 연결 · 송금 완료 → 소비자 알림 · 다시 문의 409")
    void refundAccountInquiryAndTransferNotice() throws Exception {
        freeShippingFrom(50_000);
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 2);
        Long claimId = rejectedClaim(returnClaim(group));
        accept(claimId).andExpect(status().isOk());
        OrderRefundTask transfer = freightTransfer(group);
        Long goodsRefund = refundTaskRows(group).stream()
                .filter(t -> t.getSource() == RefundTaskSource.OPERATOR_REASON).findFirst().orElseThrow().getId();
        Long orderId = group.getOrder().getId();

        inquiry("DELIVERY", transfer.getId(), null).andExpect(status().isBadRequest());
        inquiry("CANCEL_EXCHANGE_RETURN", goodsRefund, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_TRANSFER_NOT_PENDING"));
        inquiry("CANCEL_EXCHANGE_RETURN", transfer.getId(), orderId + 1_000).andExpect(status().isBadRequest());
        inquiry("CANCEL_EXCHANGE_RETURN", 999_999L, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFUND_TASK_NOT_FOUND"));

        long inquiryId = json(inquiry("CANCEL_EXCHANGE_RETURN", transfer.getId(), orderId)
                .andExpect(status().isCreated())).get("inquiryId").asLong();
        adminGet("/v1/admin/inquiries/" + inquiryId).andExpect(status().isOk())
                .andExpect(jsonPath("$.refundTaskId").value(transfer.getId()))
                .andExpect(jsonPath("$.typeName").value("환불 계좌 안내"))
                .andExpect(jsonPath("$.orderId").value(orderId));
        adminGet(ADMIN_REFUNDS + "/" + transfer.getId()).andExpect(jsonPath("$.transfer.linkedInquiryId").value(inquiryId));

        adminPost(ADMIN_REFUNDS + "/" + transfer.getId() + "/transfer-complete",
                Map.of("bankCode", "088", "accountNumber", "110123456789", "holderName", "김수민", "transferConfirmed", true))
                .andExpect(status().isOk());

        List<Notification> notices = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.CLAIM_REFUND_TRANSFERRED).toList();
        assertThat(notices).singleElement().satisfies(n -> {
            assertThat(n.getRecipientType()).isEqualTo(NotificationRecipientType.USER);
            assertThat(n.getRecipientId()).isEqualTo(consumer.getId());
            assertThat(n.getTitle()).contains(String.format("%,d원", transfer.getRefundAmount()));
        });
        inquiry("CANCEL_EXCHANGE_RETURN", transfer.getId(), null).andExpect(status().isConflict());
        adminGet(ADMIN_REFUNDS + "/summary").andExpect(jsonPath("$.transferPending").value(0));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions overturn(Long claimId) throws Exception {
        return sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/overturn",
                Map.of("explanation", "외관 손상이 없고 정상 작동합니다.", "evidenceImageUrls", List.of(EVIDENCE)));
    }

    private ResultActions accept(Long claimId) throws Exception {
        return adminPost(ADMIN_CLAIMS + "/" + claimId + "/dispute-acceptance",
                Map.of("detail", "1:1 문의 사진상 배송 시점 오염으로 판단"));
    }

    private ResultActions inquiry(String type, Long refundTaskId, Long orderId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("content", "환불 받을 계좌는 신한 110-123-456789 김수민입니다.");
        body.put("refundTaskId", refundTaskId);
        body.put("orderId", orderId);
        return userPost("/v1/user/inquiries", body);
    }

    private void markArrived(Long claimId, LocalDateTime arrivedAt) {
        jdbc.update("UPDATE order_claim SET status = 'ARRIVED' WHERE claim_id = ?", claimId);
        jdbc.update("UPDATE order_claim_collection SET arrived_at = ? WHERE collection_id = "
                + "(SELECT collection_id FROM order_claim WHERE claim_id = ?)", arrivedAt, claimId);
    }

    private int baseFee(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT base_delivery_fee FROM order_delivery_group WHERE delivery_group_id = ?",
                Integer.class, group.getId());
    }

    private String feeBearerOf(Long claimId) {
        return jdbc.queryForObject("SELECT fee_bearer FROM order_claim_collection WHERE collection_id = ?",
                String.class, collectionIdOf(claimId));
    }

    private List<OrderClaimFreightAdjustment> freightRows(Long claimId) {
        return freightRepository.findByClaimCollectionIdOrderByIdAsc(collectionIdOf(claimId));
    }

    private List<OrderRefundTask> refundTaskRows(OrderDeliveryGroup group) {
        return refundTaskRepository.findByDeliveryGroupIdOrderByIdAsc(group.getId());
    }

    private OrderRefundTask freightTransfer(OrderDeliveryGroup group) {
        return refundTaskRows(group).stream()
                .filter(t -> t.getSource() == RefundTaskSource.FREIGHT_REFUND && t.getMethod() == RefundMethod.BANK_TRANSFER)
                .findFirst().orElseThrow(() -> new AssertionError("송금 대기 행 없음"));
    }

    private static List<String> queues(JsonNode todo) {
        List<String> queues = new java.util.ArrayList<>();
        todo.get("rows").forEach(row -> queues.add(row.get("queue").asText()));
        return queues;
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}
