package showroomz.api.design1021;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentRepository;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementClawback;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.type.ClawbackSide;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.global.config.properties.SettlementProperties;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 P6 — 정산: 주민번호 보류 삭제(6-2) · 클로백 역산 · 합의 비율 · 확인 기간 재계산(6-3) · 조정 요청 철회(6-4).
 */
@DisplayName("1021 P6 — 주민번호 보류 삭제 · 클로백 역산 · 재계산 · 조정 요청 철회")
class Design1021P6SettlementIntegrationTest extends SettlementTestSupport {

    @Autowired private SettlementClawbackRepository clawbackRepository;
    @Autowired private SettlementAdjustmentRepository adjustmentRepository;
    @Autowired private SettlementProperties settlementProperties;

    @AfterEach
    void restoreRecalculation() {
        settlementProperties.getClawback().setRecalculateStatuses(EnumSet.noneOf(SettlementStatus.class));
    }

    @Test
    @DisplayName("기-12 — 비사업자 주민번호 미등록이어도 지급 보류 없이 지급 · 원천징수영수증은 생성 보류(이력 1회)")
    void residentNumberDoesNotBlockPayout() {
        registerSellerAccount();
        registerCreatorAccount();
        confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement confirmed = confirm(generate(LocalDateTime.now().withNano(0)));
        assertThat(payout(confirmed.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);

        Settlement paid = pay(confirmed, payout(confirmed.getId(), SettlementPayee.BRAND).getDueDate());

        assertThat(payout(paid.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(jdbc.queryForObject("SELECT status FROM settlement_tax_document WHERE settlement_id = ? AND type = ?",
                String.class, paid.getId(), TaxDocumentType.WITHHOLDING_RECEIPT.name()))
                .isEqualTo(TaxDocumentStatus.PENDING_ISSUE.name());
        assertThat(settlementEvents(paid.getId()))
                .containsOnlyOnce(SettlementEventType.WITHHOLDING_RECEIPT_SKIPPED)
                .doesNotContain(SettlementEventType.WITHHOLDING_RECEIPT_GENERATED);
    }

    @Test
    @DisplayName("기-13 클로백 — 브랜드 몫은 PG 수수료를 뺀 실수령분만 · 합의로 바뀐 리워드는 그 비율로 회수")
    void clawbackReverseAndAgreementRatio() {
        OrderDeliveryGroup cream = confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement first = confirm(generate(LocalDateTime.now().withNano(0)));
        // 합의로 리워드가 원래의 절반이 됐다고 둔다 — 인플루언서가 실제로 받은 비율.
        long original = settlement(first.getId()).getOriginalRewardAmount();
        jdbc.update("UPDATE settlement SET reward_amount = ? WHERE settlement_id = ?", original / 2, first.getId());

        Long refundTaskId = operatorRefund(cream, "POST_CONFIRM_DEFECT", CREAM_PRICE);

        List<SettlementClawback> rows = clawbackRepository.findByRefundTaskIdOrderByIdAsc(refundTaskId);
        long unitReward = 3_264;   // 크림 27,200 × 12%
        long creator = unitReward * (original / 2) / original;
        long creatorVat = creator / 10;
        long brand = CREAM_PRICE - CREAM_PRICE * 3 / 100 - creator - creatorVat;
        assertThat(rows).extracting(SettlementClawback::getSide, SettlementClawback::getAmount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ClawbackSide.BRAND, brand),
                        org.assertj.core.groups.Tuple.tuple(ClawbackSide.CREATOR, creator));
    }

    @Test
    @DisplayName("기-13 재계산 — 확인 기간 중 환불은 클로백 대신 금액 · 항목 재계산 · 확인 기간 재시작 · 이력 · 07a 차감 탭 가상 행")
    void recalculateDuringReview() throws Exception {
        settlementProperties.getClawback().setRecalculateStatuses(EnumSet.of(SettlementStatus.REVIEWING));
        OrderDeliveryGroup cream = confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        Settlement before = settlement(s.getId());
        assertThat(before.getStatus()).isEqualTo(SettlementStatus.REVIEWING);

        adminGet("/v1/admin/refunds/" + enqueueOperatorRefund(cream, CREAM_PRICE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.expectedClawback").doesNotExist());
        Long refundTaskId = executeLatestOperatorRefund(cream);

        assertThat(clawbackRepository.findByRefundTaskIdOrderByIdAsc(refundTaskId)).isEmpty();
        Settlement after = settlement(s.getId());
        assertThat(after.getStatus()).isEqualTo(SettlementStatus.REVIEWING);
        assertThat(after.getConfirmedSalesAmount()).isEqualTo(before.getConfirmedSalesAmount() - CREAM_PRICE);
        assertThat(after.getRewardAmount()).isLessThan(before.getRewardAmount());
        assertThat(after.getOriginalRewardAmount()).isEqualTo(after.getRewardAmount());
        assertThat(after.getReviewDueAt()).isAfterOrEqualTo(before.getReviewDueAt());
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getAmount()).isEqualTo(after.getBrandPayoutAmount());
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.RECALCULATED);

        JsonNode tab = json(adminGet("/v1/admin/settlements?tab=CLAWBACK").andExpect(status().isOk()));
        assertThat(tab.findValuesAsText("clawbackNumber")).contains("—");
        assertThat(tab.findValuesAsText("brandStatus")).contains("RECALCULATED");
    }

    @Test
    @DisplayName("기-14 요청 철회 — 요청자 · 응답 전만 · 원래 금액 확정(WITHDRAWN) · 상대가 반대한 뒤면 409")
    void withdrawAdjustment() throws Exception {
        Settlement s = seedSettlement("가을 크림 공구", 1_000_000, SettlementStatus.REVIEWING);
        JsonNode requested = json(sellerPost("/v1/seller/settlements/" + s.getId() + "/adjustment",
                Map.of("rewardAmount", 80_000, "reason", "샘플 미도착")).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();

        creatorPost("/v1/creator/settlement-adjustments/" + adjustmentId + "/withdraw")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_WITHDRAW_NOT_ALLOWED"));
        sellerPost("/v1/seller/settlement-adjustments/" + adjustmentId + "/withdraw", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.permissions.canWithdraw").value(false));

        assertThat(adjustmentRepository.findById(adjustmentId).orElseThrow().getStatus())
                .isEqualTo(AdjustmentStatus.WITHDRAWN);
        Settlement confirmed = settlement(s.getId());
        assertThat(confirmed.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(confirmed.getConfirmReason()).isEqualTo(SettlementConfirmReason.WITHDRAWN);
        assertThat(confirmed.getRewardAmount()).isEqualTo(confirmed.getOriginalRewardAmount());
        assertThat(settlementEvents(s.getId())).contains(SettlementEventType.ADJUSTMENT_WITHDRAWN,
                SettlementEventType.CONFIRMED_BY_WITHDRAWAL);

        // 상대가 반대한 뒤에는 철회가 아니라 협의다.
        Settlement other = seedSettlement("겨울 세럼 공구", 1_000_000, SettlementStatus.REVIEWING);
        JsonNode second = json(sellerPost("/v1/seller/settlements/" + other.getId() + "/adjustment",
                Map.of("rewardAmount", 80_000, "reason", "샘플 미도착")).andExpect(status().isCreated()));
        long secondId = second.get("adjustmentId").asLong();
        long proposalId = second.get("proposalId").asLong();
        sellerGet("/v1/seller/threads/" + second.get("threadId").asLong() + "/adjustment")
                .andExpect(jsonPath("$.permissions.canWithdraw").value(true));
        creatorPost("/v1/creator/settlement-adjustments/" + secondId + "/proposals/" + proposalId + "/reject")
                .andExpect(status().isOk());
        sellerPost("/v1/seller/settlement-adjustments/" + secondId + "/withdraw", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_WITHDRAW_NOT_ALLOWED"));
    }

    // ------------------------------------------------------------------ helpers

    private Long enqueueOperatorRefund(OrderDeliveryGroup group, int amount) throws Exception {
        adminPost("/v1/admin/orders/groups/" + group.getId() + "/refund-tasks",
                Map.of("reason", "POST_CONFIRM_DEFECT", "amount", amount, "detail", "구매확정 후 하자 확인"))
                .andExpect(status().isOk());
        return jdbc.queryForObject("SELECT MAX(refund_task_id) FROM order_refund_task "
                + "WHERE delivery_group_id = ? AND source = 'OPERATOR_REASON'", Long.class, group.getId());
    }

    private Long executeLatestOperatorRefund(OrderDeliveryGroup group) throws Exception {
        Long taskId = jdbc.queryForObject("SELECT MAX(refund_task_id) FROM order_refund_task "
                + "WHERE delivery_group_id = ? AND source = 'OPERATOR_REASON'", Long.class, group.getId());
        adminPost("/v1/admin/refunds/" + taskId + "/execute", Map.of()).andExpect(status().isOk());
        return taskId;
    }

    private ResultActions creatorPost(String url) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }
}
