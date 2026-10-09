package showroomz.domain.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.entity.GroupBuyHistory;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementItemStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.scheduler.SettlementGenerationScheduler;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산 생성(44 어드민 설계서 2절 · 구현 계획서 4-4) — ST-03 · ST-04 · ST-12(1/2). 배치는 꺼져 있고 생성 서비스를 직접 부른다.
 */
@DisplayName("정산 생성 — 주문 전부 종결 · 스냅샷 · 공구 포트")
class SettlementGenerationIntegrationTest extends SettlementTestSupport {

    /** 2026-10-02(금) 15:00 — 10.05 개천절 대체 · 10.09 한글날이 끼는 주. */
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 10, 2, 15, 0);

    @Autowired(required = false)
    private SettlementGenerationScheduler scheduler;

    @Test
    @DisplayName("ST-03 미종결 1건이면 정산 없음 → 종결 뒤 생성 · 확인 마감 = 다음 영업일부터 3영업일째 23:59:59(공휴일 건너뜀)")
    void generatesOnlyAfterAllOrdersClosed() {
        fixHolidays();
        OrderDeliveryGroup confirmed = confirmedGroup(creamVariant, 1);
        OrderDeliveryGroup open = preparing(paidGroup());
        endGroupBuy();

        assertThat(generationService.generate(groupBuy.getId(), GENERATED_AT)).isEmpty();
        assertThat(settlementRepository.findByGroupBuyId(groupBuy.getId())).isEmpty();

        cancelledBySeller(open);
        Settlement settlement = generate(GENERATED_AT);

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.REVIEWING);
        assertThat(settlement.getSettlementNumber()).isEqualTo("STL-2610-001");
        // 10.02(금) 생성 → 10.05 공휴일 · 10.06 · 10.07 · 10.08 → 10.08 23:59:59
        assertThat(settlement.getReviewDueAt()).isEqualTo(LocalDateTime.of(2026, 10, 8, 23, 59, 59));
        assertThat(settlement.getCreatedAt()).isEqualTo(GENERATED_AT);
        assertThat(settlement.getGrossOrderAmount()).isEqualTo(2L * CREAM_PRICE);
        assertThat(settlement.getCancelDeduction()).isEqualTo(CREAM_PRICE);
        assertThat(settlement.getCancelCount()).isEqualTo(1);
        assertThat(settlement.getConfirmedSalesAmount()).isEqualTo(CREAM_PRICE);
        assertThat(settlement.getOrdersClosedAt()).isNotNull();
        assertThat(itemsOf(settlement)).extracting(SettlementItem::getDeliveryGroupId, SettlementItem::getStatus)
                .containsExactlyInAnyOrder(tuple(confirmed.getId(), SettlementItemStatus.CONFIRMED),
                        tuple(open.getId(), SettlementItemStatus.CANCELLED));
        // 두 번째 생성은 없다 — 공구 1건 = 정산 1건.
        assertThat(generationService.generate(groupBuy.getId(), GENERATED_AT)).isEmpty();
    }

    @Test
    @DisplayName("금액 스냅샷 — 리워드는 항목별 단가 × 반영 수량 · 소비자 배송비 가산 · 검산 · 명세 행 = 항목 수 · payout 3행 WAITING · 이력")
    void snapshotAmounts() {
        OrderDeliveryGroup creams = confirmedGroup(creamVariant, 2);   // 54,400 · 무료배송
        OrderDeliveryGroup serum = confirmedGroup(serumVariant, 1);    // 24,000 + 배송비
        endGroupBuy();

        Settlement s = generate(GENERATED_AT);

        long deliveryFees = creams.getDeliveryFee() + serum.getDeliveryFee();
        long confirmedSales = 2L * CREAM_PRICE + SERUM_PRICE;
        long reward = 2L * (CREAM_PRICE * 12 / 100) + SERUM_PRICE * 10 / 100;   // 3,264 × 2 + 2,400
        long rewardVat = reward / 10;
        long pgFee = confirmedSales * 3 / 100;
        long withholding = reward * 3 / 100 + reward * 3 / 1000;
        assertThat(s.getConfirmedSalesAmount()).isEqualTo(confirmedSales);
        assertThat(s.getConsumerDeliveryFeeAmount()).isEqualTo(deliveryFees);
        assertThat(s.getOriginalRewardAmount()).isEqualTo(reward);
        assertThat(s.getRewardAmount()).isEqualTo(reward);
        assertThat(s.getRewardVatAmount()).isEqualTo(rewardVat);
        assertThat(s.getPgFeeAmount()).isEqualTo(pgFee);
        assertThat(s.getPlatformFeeAmount()).isZero();
        assertThat(s.getWithholdingAmount()).isEqualTo(withholding);
        assertThat(s.getCreatorPayoutAmount()).isEqualTo(reward - withholding);
        assertThat(s.getBrandPayoutAmount()).isEqualTo(confirmedSales - pgFee - reward - rewardVat + deliveryFees);
        assertThat(s.getPlatformShareAmount()).isEqualTo(rewardVat);
        assertThat(s.getBrandPayoutAmount() + s.getCreatorPayoutAmount() + s.getWithholdingAmount()
                + s.getPlatformShareAmount() + s.getPgFeeAmount()).isEqualTo(confirmedSales + deliveryFees);

        List<SettlementItem> items = itemsOf(s);
        assertThat(items).hasSize(2);
        SettlementItem creamItem = items.stream().filter(i -> i.getDeliveryGroupId().equals(creams.getId()))
                .findFirst().orElseThrow();
        assertThat(creamItem.getUnitReward()).isEqualTo(3_264);
        assertThat(creamItem.getRewardAmount()).isEqualTo(6_528);
        assertThat(creamItem.getSettledQuantity()).isEqualTo(2);
        assertThat(creamItem.getConsumerNameMasked()).isNotBlank().contains("*");

        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getPayee, SettlementPayout::getStatus,
                        SettlementPayout::getAmount)
                .containsExactly(tuple(SettlementPayee.BRAND, PayoutStatus.WAITING, s.getBrandPayoutAmount()),
                        tuple(SettlementPayee.CREATOR, PayoutStatus.WAITING, s.getCreatorPayoutAmount()),
                        tuple(SettlementPayee.PLATFORM, PayoutStatus.WAITING, s.getPlatformShareAmount()));
        assertThat(settlementEvents(s.getId())).containsExactly(SettlementEventType.CREATED);
        assertThat(groupBuyHistoryRepository.findAll()).extracting(GroupBuyHistory::getEventType)
                .contains(GroupBuyEventType.SALES_FINALIZED);
    }

    @Test
    @DisplayName("ST-03 판매 0건 공구는 대상에서 빠지고 영구 미생성")
    void zeroSalesNeverGenerates() {
        endGroupBuy();

        assertThat(generationService.findGroupBuyIdsToGenerate(200)).doesNotContain(groupBuy.getId());
        assertThat(generationService.generate(groupBuy.getId(), GENERATED_AT)).isEmpty();
        assertThat(scheduler).as("통합 테스트는 생성 배치가 꺼져 있다").isNull();
    }

    @Test
    @DisplayName("ST-04 계약 항목 없는 상품의 매출 → 생성 실패 · 롤백 · 계약을 고치면 다음 회차에 생성")
    void missingContractItemFailsAndRetries() {
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        jdbc.update("UPDATE contract_item SET product_id = NULL WHERE product_id = ?", serum.getProductId());

        assertThatThrownBy(() -> generationService.generate(groupBuy.getId(), GENERATED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_GENERATION_INCONSISTENT);
        assertThat(settlementRepository.count()).isZero();
        assertThat(settlementItemRepository.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement_number_sequence", Integer.class)).isZero();

        jdbc.update("UPDATE contract_item SET product_id = ? WHERE product_id IS NULL", serum.getProductId());
        assertThat(generationService.findGroupBuyIdsToGenerate(200)).contains(groupBuy.getId());
        assertThat(generate(GENERATED_AT).getConfirmedSalesAmount()).isEqualTo(SERUM_PRICE);
    }

    @Test
    @DisplayName("정산 전 운영자 사유 환불(06a B5) → 배송 예외 차감 · 전액이면 배송 예외 · 부분이면 반영액만 줄고 수량 · 리워드는 그대로")
    void operatorRefundBeforeSettlement() {
        OrderDeliveryGroup full = confirmedGroup(creamVariant, 1);
        OrderDeliveryGroup partial = confirmedGroup(serumVariant, 1);
        operatorRefund(full, "POST_CONFIRM_DEFECT", CREAM_PRICE);
        operatorRefund(partial, "POST_CONFIRM_DEFECT", 4_000);
        endGroupBuy();

        Settlement s = generate(GENERATED_AT);

        assertThat(s.getDeliveryExceptionDeduction()).isEqualTo(CREAM_PRICE + 4_000);
        assertThat(s.getDeliveryExceptionCount()).isEqualTo(2);
        assertThat(s.getConfirmedSalesAmount()).isEqualTo(SERUM_PRICE - 4_000);
        assertThat(s.getRewardAmount()).isEqualTo(SERUM_PRICE / 10);
        List<SettlementItem> items = itemsOf(s);
        SettlementItem fullItem = items.stream().filter(i -> i.getDeliveryGroupId().equals(full.getId()))
                .findFirst().orElseThrow();
        SettlementItem partialItem = items.stream().filter(i -> i.getDeliveryGroupId().equals(partial.getId()))
                .findFirst().orElseThrow();
        assertThat(fullItem.getStatus()).isEqualTo(SettlementItemStatus.DELIVERY_EXCEPTION);
        assertThat(fullItem.getSettledQuantity()).isZero();
        assertThat(fullItem.getRewardAmount()).isZero();
        assertThat(partialItem.getStatus()).isEqualTo(SettlementItemStatus.CONFIRMED);
        assertThat(partialItem.getSettledAmount()).isEqualTo(SERUM_PRICE - 4_000);
        assertThat(partialItem.getSettledQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("ST-12(1/2) 공구 상세 settlement.stage 는 포트 값 · 정산 확인 409 · 06a ④ · 06c settlement")
    void portsFilled() throws Exception {
        OrderDeliveryGroup group = confirmedGroup(creamVariant, 1);
        Long refundTaskId = operatorRefund(confirmedGroup(serumVariant, 1), "POST_CONFIRM_DEFECT", 1_000);
        adminGet(ADMIN_REFUNDS + "/" + refundTaskId).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.state").value("BEFORE_SETTLEMENT"))
                .andExpect(jsonPath("$.settlement.settlementNumber").doesNotExist());
        adminGet(ADMIN_ORDERS + "/" + group.getOrder().getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].settlement").doesNotExist());
        endGroupBuy();

        Settlement s = generate(GENERATED_AT);

        adminGet("/v1/admin/group-buys/" + groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.afterEnd.settlement.stage").value("WAITING"))
                .andExpect(jsonPath("$.afterEnd.settlement.stageSource").value("PORT"))
                .andExpect(jsonPath("$.permissions.canConfirmSettlement").value(false));
        adminPost("/v1/admin/group-buys/" + groupBuy.getId() + "/settlement/confirm", java.util.Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_SETTLEMENT_NOT_READY"));

        JsonNode order = json(adminGet(ADMIN_ORDERS + "/" + group.getOrder().getId()).andExpect(status().isOk()));
        JsonNode settlement = order.at("/groups/0/settlement");
        assertThat(settlement.get("settlementNumber").asText()).isEqualTo(s.getSettlementNumber());
        assertThat(settlement.get("status").asText()).isEqualTo("REVIEWING");
        assertThat(settlement.get("settledAmount").asLong()).isEqualTo(CREAM_PRICE);
        assertThat(settlement.get("rewardAmount").asLong()).isEqualTo(CREAM_PRICE * 12 / 100);
        assertThat(settlement.get("clawbacks")).isEmpty();

        adminGet(ADMIN_REFUNDS + "/" + refundTaskId).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.state").value("SETTLED"))
                .andExpect(jsonPath("$.settlement.settlementNumber").value(s.getSettlementNumber()));
    }
}
