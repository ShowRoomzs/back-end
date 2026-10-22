package showroomz.api.design1021;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.entity.OrderClaimFreightAdjustment;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.OrderClaimFreightAdjustmentRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.FreightAdjustmentDirection;
import showroomz.domain.order.type.FreightAdjustmentTrigger;
import showroomz.domain.order.type.FreightSettledVia;
import showroomz.domain.order.type.RefundMethod;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.domain.settlement.entity.Settlement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 P4 — 검수 3갈래(통과 · 사유 번복 · 반려) · 귀책 변경 운임 정산 · 자동 입고 · 초과 환급 송금 · 1:1 문의 연결(4절).
 */
@DisplayName("1021 P4 — 자동 입고 · 통과 귀책 변경 · 사유 번복 · 초과분 계좌 송금 · 환불 계좌 안내 문의")
class Design1021P4InspectionIntegrationTest extends SettlementTestSupport {

    private static final String EVIDENCE = "https://cdn.example.com/claims/evidence-1.jpg";

    @Autowired private OrderClaimRepository claimRepository;
    @Autowired private OrderClaimFreightAdjustmentRepository freightRepository;
    @Autowired private OrderRefundTaskRepository refundTaskRepository;

    @Test
    @DisplayName("자동 입고 — 도착 + 3영업일 끝까지는 그대로 · 지나면 시스템 입고 · 검수 기한 발급 · 표식 · 이력")
    void autoReceive() {
        fixHolidays();
        OrderDeliveryGroup group = deliveredFrom(purchase(creamVariant, 1).group());
        Long claimId = returnClaim(group, itemsOf(group).get(0), 1, ClaimReason.CHANGE_OF_MIND);
        LocalDateTime arrivedAt = LocalDateTime.of(2026, 11, 2, 10, 0);   // 월
        jdbc.update("UPDATE order_claim SET status = 'ARRIVED' WHERE claim_id = ?", claimId);
        jdbc.update("UPDATE order_claim_collection SET arrived_at = ? WHERE collection_id = "
                + "(SELECT collection_id FROM order_claim WHERE claim_id = ?)", arrivedAt, claimId);

        LocalDateTime thursdayNoon = LocalDateTime.of(2026, 11, 5, 12, 0);
        assertThat(claimService.findIdsToAutoReceive(thursdayNoon, 100)).doesNotContain(claimId);
        assertThat(claimService.autoReceive(claimId, thursdayNoon)).isFalse();

        LocalDateTime fridayMorning = LocalDateTime.of(2026, 11, 6, 0, 10);
        assertThat(claimService.findIdsToAutoReceive(fridayMorning, 100)).contains(claimId);
        assertThat(claimService.autoReceive(claimId, fridayMorning)).isTrue();
        assertThat(claimService.autoReceive(claimId, fridayMorning)).isFalse();

        var claim = claimRepository.findById(claimId).orElseThrow();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.RECEIVED);
        assertThat(claim.getInspectDueAt()).isNotNull();
        assertThat(collectionOf(claimId).isAutoReceived()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_claim_history WHERE claim_id = ? "
                + "AND event_type = 'AUTO_RECEIVED' AND actor_type = 'SYSTEM'", Long.class, claimId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("통과 + 귀책 변경(일부 반품) — 선불 운임은 PG 환급(별도 큐 행) · 운임 정산 − · 정산 「± 운임 정산」")
    void passWithFaultRefundsPrepaidFreight() throws Exception {
        OrderDeliveryGroup group = deliveredFrom(purchase(creamVariant, 3).group());   // 81,600 · 남는 54,400 ≥ 기준
        Long claimId = returnClaim(group, itemsOf(group).get(0), 1, ClaimReason.CHANGE_OF_MIND);
        assertThat(collectionOf(claimId).getReturnDeduction()).isZero();

        passWithFault(claimId);

        List<OrderClaimFreightAdjustment> rows = freightRows(claimId);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getTrigger()).isEqualTo(FreightAdjustmentTrigger.FAULT_TO_SELLER_ON_PASS);
            assertThat(row.getDirection()).isEqualTo(FreightAdjustmentDirection.REFUND_CONSUMER);
            assertThat(row.getPrepaidFreight()).isEqualTo(DELIVERY_FEE);
            assertThat(row.getSettledVia()).isEqualTo(FreightSettledVia.REFUND_ADDITION);
            assertThat(row.signedAmount()).isEqualTo(-DELIVERY_FEE);
        });
        List<OrderRefundTask> freightRefunds = freightRefundTasks(group);
        assertThat(freightRefunds).singleElement().satisfies(task -> {
            assertThat(task.getMethod()).isEqualTo(RefundMethod.PG_CANCEL);
            assertThat(task.getRefundAmount()).isEqualTo(DELIVERY_FEE);
        });
        assertThat(refundTasks(group)).anySatisfy(task -> {
            assertThat(task.source()).isEqualTo(RefundTaskSource.CLAIM_RETURN_PASSED.name());
            assertThat(task.amount()).isEqualTo(CREAM_PRICE);
        });

        sellerGet("/v1/seller/claims/" + claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.inspection.result").value("PASSED"))
                .andExpect(jsonPath("$.inspection.freight[0].trigger").value("FAULT_TO_SELLER_ON_PASS"))
                .andExpect(jsonPath("$.inspection.freight[0].signedAmount").value(-DELIVERY_FEE));
        appClaim(claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.inspection.state").value("FAULT_CHANGED"))
                .andExpect(jsonPath("$.inspection.freight.prepaidFreightRefund").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.inspection.refundSplit.pgCancel").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.inspection.refundSplit.bankTransfer").value(0));

        endGroupBuy();
        Settlement s = generate(GENERATED_AT);
        assertThat(s.getFreightAdjustmentAmount()).isEqualTo(-DELIVERY_FEE);
        assertThat(freightRows(claimId).get(0).getAppliedSettlementId()).isEqualTo(s.getId());
    }

    @Test
    @DisplayName("통과 + 귀책 변경(전량 · 차감 있던 요청) — 차감 취소로 상품 전액 환불 · 선불 운임은 원래 결제액 초과라 송금 대기 → 송금 완료 기록")
    void passWithFaultBeyondPaymentGoesToBankTransfer() throws Exception {
        OrderDeliveryGroup group = deliveredFrom(purchase(creamVariant, 2).group());   // 54,400 · 전량 반품 → 차감 3,000
        Long claimId = returnClaim(group, itemsOf(group).get(0), 2, ClaimReason.CHANGE_OF_MIND);
        assertThat(collectionOf(claimId).getReturnDeduction()).isEqualTo(DELIVERY_FEE);

        passWithFault(claimId);

        assertThat(collectionOf(claimId).getReturnDeduction()).isZero();
        assertThat(refundTasks(group)).anySatisfy(task -> {
            assertThat(task.source()).isEqualTo(RefundTaskSource.CLAIM_RETURN_PASSED.name());
            assertThat(task.amount()).isEqualTo(CREAM_PRICE * 2);   // 차감 없음 — 환원은 여기서 끝났다(이중 환급 없음)
        });
        OrderRefundTask transfer = freightRefundTasks(group).stream().filter(OrderRefundTask::isBankTransfer)
                .findFirst().orElseThrow();
        assertThat(transfer.getRefundAmount()).isEqualTo(DELIVERY_FEE);
        assertThat(transfer.getStatus()).isEqualTo(RefundTaskStatus.PENDING);
        assertThat(freightRows(claimId).get(0).getSettledVia()).isEqualTo(FreightSettledVia.BANK_TRANSFER);
        assertThat(freightRows(claimId).get(0).getInitialFeeRestored()).isEqualTo(DELIVERY_FEE);

        appClaim(claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.inspection.refundSplit.bankTransfer").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.inspection.refundSplit.bankTransferStatus").value("송금 대기"))
                .andExpect(jsonPath("$.inspection.refundSplit.bankTransferRefundTaskId").value(transfer.getId()))
                .andExpect(jsonPath("$.inspection.refundSplit.inquiryNeeded").value(true));

        // 소비자 [1:1 문의] — 「환불 계좌 안내」로 연결된다.
        JsonNode inquiry = json(mockMvc.perform(post("/v1/user/inquiries")
                        .header(HttpHeaders.AUTHORIZATION, consumerToken).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("type", "CANCEL_EXCHANGE_RETURN", "content", "신한 110-123-456789 김수민",
                                "refundTaskId", transfer.getId()))))
                .andExpect(status().isCreated()));
        long inquiryId = inquiry.get("inquiryId").asLong();
        mockMvc.perform(get("/v1/user/inquiries/" + inquiryId).header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeName").value("환불 계좌 안내"))
                .andExpect(jsonPath("$.refundTaskId").value(transfer.getId()));

        adminGet("/v1/admin/refunds/" + transfer.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.statusLabel").value("송금 대기"))
                .andExpect(jsonPath("$.manuallyCompletable").value(false))
                .andExpect(jsonPath("$.voidable").value(false))
                .andExpect(jsonPath("$.transfer.transferable").value(true))
                .andExpect(jsonPath("$.transfer.linkedInquiryId").value(inquiryId))
                .andExpect(jsonPath("$.transfer.transferred").doesNotExist());
        adminGet("/v1/admin/refunds/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.transferPending").value(1));
        adminGet("/v1/admin/refunds?tab=PENDING&route=FREIGHT").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentLabel").value("계좌 송금"))
                .andExpect(jsonPath("$.content[0].transferable").value(true));

        // 계좌 · 확인 누락 400 · 수동 완료 기록 경로는 막힌다.
        adminPost("/v1/admin/refunds/" + transfer.getId() + "/transfer-complete",
                Map.of("bankCode", "088", "accountNumber", "110-123-456789", "holderName", "김수민", "transferConfirmed", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFUND_TRANSFER_ACCOUNT_REQUIRED"));
        adminPost("/v1/admin/refunds/" + transfer.getId() + "/manual-complete", Map.of("note", "콘솔"))
                .andExpect(status().isConflict());

        adminPost("/v1/admin/refunds/" + transfer.getId() + "/transfer-complete",
                Map.of("bankCode", "088", "accountNumber", "110-123-456789", "holderName", "김수민", "transferConfirmed", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusLabel").value("송금 완료"))
                .andExpect(jsonPath("$.transferable").value(false));
        adminGet("/v1/admin/refunds/" + transfer.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.transfer.transferred.accountNumberMasked").value("********6789"))
                .andExpect(jsonPath("$.transfer.transferred.holderName").value("김수민"));
        assertThat(fulfillmentEvents(group)).contains("REFUND_TRANSFERRED");
        assertThat(jdbc.queryForObject("SELECT transfer_account_enc FROM order_refund_task WHERE refund_task_id = ?",
                String.class, transfer.getId())).doesNotContain("456789");
        adminPost("/v1/admin/refunds/" + transfer.getId() + "/transfer-complete",
                Map.of("bankCode", "088", "accountNumber", "110-123-456789", "holderName", "김수민", "transferConfirmed", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_TRANSFER_NOT_PENDING"));
        appClaim(claimId).andExpect(jsonPath("$.inspection.refundSplit.bankTransferStatus").value("송금 완료"));
    }

    @Test
    @DisplayName("반품 사유 번복 — 착불(브랜드 귀책) 신청만 · 증빙 필수 · 착불 운임 + 최초 배송비 차감 · 소비자 1b 블록")
    void returnOverturn() throws Exception {
        OrderDeliveryGroup group = deliveredFrom(purchase(creamVariant, 2).group());   // 54,400
        Long claimId = returnClaim(group, itemsOf(group).get(0), 2, ClaimReason.DAMAGED_OR_DEFECTIVE);
        sellerPost("/v1/seller/claims/receive", Map.of("claimIds", List.of(claimId))).andExpect(status().isOk());

        sellerPost("/v1/seller/claims/" + claimId + "/inspection/overturn",
                Map.of("explanation", "정상 작동 확인", "evidenceImageUrls", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLAIM_OVERTURN_EVIDENCE_REQUIRED"));
        sellerGet("/v1/seller/claims/" + claimId).andExpect(jsonPath("$.actions.canOverturn").value(true));

        sellerPost("/v1/seller/claims/" + claimId + "/inspection/overturn",
                Map.of("explanation", "외관 손상이 없고 정상 작동합니다.", "evidenceImageUrls", List.of(EVIDENCE)))
                .andExpect(status().isOk());

        assertThat(freightRows(claimId)).singleElement().satisfies(row -> {
            assertThat(row.getTrigger()).isEqualTo(FreightAdjustmentTrigger.REASON_OVERTURNED);
            assertThat(row.getCollectFreight()).isEqualTo(DELIVERY_FEE);
            assertThat(row.getSettledVia()).isEqualTo(FreightSettledVia.REFUND_DEDUCTION);
            assertThat(row.signedAmount()).isEqualTo(DELIVERY_FEE);
        });
        assertThat(refundTasks(group)).anySatisfy(task -> {
            assertThat(task.source()).isEqualTo(RefundTaskSource.CLAIM_RETURN_PASSED.name());
            assertThat(task.amount()).isEqualTo(CREAM_PRICE * 2 - DELIVERY_FEE - DELIVERY_FEE);
        });
        appClaim(claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.inspection.result").value("REASON_OVERTURNED"))
                .andExpect(jsonPath("$.inspection.state").value("REASON_OVERTURNED"))
                .andExpect(jsonPath("$.inspection.explanation").value("외관 손상이 없고 정상 작동합니다."))
                .andExpect(jsonPath("$.inspection.attachments[0]").value(EVIDENCE))
                .andExpect(jsonPath("$.inspection.freight.collectFreight").value(DELIVERY_FEE));
        sellerGet("/v1/seller/claims/" + claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.inspection.result").value("REASON_OVERTURNED"))
                .andExpect(jsonPath("$.inspection.explanation").value("외관 손상이 없고 정상 작동합니다."));
    }

    @Test
    @DisplayName("선불(소비자 귀책) 신청은 사유 번복 409")
    void overturnPrepaidRejected() throws Exception {
        OrderDeliveryGroup group = deliveredFrom(purchase(creamVariant, 1).group());
        Long claimId = returnClaim(group, itemsOf(group).get(0), 1, ClaimReason.CHANGE_OF_MIND);
        sellerPost("/v1/seller/claims/receive", Map.of("claimIds", List.of(claimId))).andExpect(status().isOk());
        sellerGet("/v1/seller/claims/" + claimId).andExpect(jsonPath("$.actions.canOverturn").value(false));

        sellerPost("/v1/seller/claims/" + claimId + "/inspection/overturn",
                Map.of("explanation", "단순 변심", "evidenceImageUrls", List.of(EVIDENCE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_OVERTURN_NOT_APPLICABLE"));
    }

    // ------------------------------------------------------------------ helpers

    private void passWithFault(Long claimId) throws Exception {
        sellerPost("/v1/seller/claims/receive", Map.of("claimIds", List.of(claimId))).andExpect(status().isOk());
        sellerPost("/v1/seller/claims/" + claimId + "/inspection/pass", Map.of("faultToSeller", true))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions appClaim(Long claimId) throws Exception {
        return mockMvc.perform(get("/v1/user/claims/" + claimId).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private OrderClaimCollection collectionOf(Long claimId) {
        return inTransaction(() -> {
            OrderClaimCollection collection = claimRepository.findById(claimId).orElseThrow().getCollection();
            collection.getReturnDeduction();
            return collection;
        });
    }

    private List<OrderClaimFreightAdjustment> freightRows(Long claimId) {
        return freightRepository.findByClaimCollectionIdOrderByIdAsc(collectionOf(claimId).getId());
    }

    private List<OrderRefundTask> freightRefundTasks(OrderDeliveryGroup group) {
        return refundTaskRepository.findByDeliveryGroupIdOrderByIdAsc(group.getId()).stream()
                .filter(task -> task.getSource() == RefundTaskSource.FREIGHT_REFUND).toList();
    }

    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 10, 2, 15, 0);
}
