package showroomz.api.creator.groupbuy;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.GroupBuyCommandService;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.service.port.GroupBuySettlementReader;
import showroomz.domain.groupbuy.service.port.GroupBuyThreadGateway;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 아직 빈 구현인 판매·정산·3자 스레드 포트에 실제 값이 연결됐을 때의 스튜디오 API 계약을 검증한다. */
@DisplayName("[통합] 쇼룸 공구 관리 외부 포트 계약")
class CreatorGroupBuyPortIntegrationTest extends CreatorGroupBuyTestSupport {

    @MockitoBean GroupBuySalesReader salesReader;
    @MockitoBean GroupBuySettlementReader settlementReader;
    @MockitoBean GroupBuyThreadGateway threadGateway;
    @Autowired GroupBuyCommandService groupBuyCommandService;

    @Test
    @DisplayName("B5·B7·B9: 판매 실적이 연결되면 내 리워드와 LIVE·PROVISIONAL·AT_SUSPENSION을 상태별로 보여준다")
    void salesAndRewardAcrossStates() throws Exception {
        GroupBuy selling = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuy ended = seedIn(GroupBuyStatus.ENDED);
        GroupBuy suspended = seedIn(GroupBuyStatus.SUSPENDED);
        GroupBuySalesReader.GroupBuySales sales = new GroupBuySalesReader.GroupBuySales(5, 129_600,
                List.of(new GroupBuySalesReader.ItemQuantity(cream.getProductId(), 3),
                        new GroupBuySalesReader.ItemQuantity(serum.getProductId(), 2)));
        when(salesReader.readSales(anyLong())).thenReturn(Optional.of(sales));

        // 3 × (27,200 × 12%) + 2 × (24,000 × 10%) = 14,592원.
        studioDetail(selling.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.basis").value("LIVE"))
                .andExpect(jsonPath("$.sales.orderCount").value(5))
                .andExpect(jsonPath("$.sales.amount").value(129_600))
                .andExpect(jsonPath("$.sales.myReward").value(14_592))
                .andExpect(jsonPath("$.payout.salesReward.amount").value(14_592))
                .andExpect(jsonPath("$.payout.platformGuaranteed").value(false));
        studioDetail(ended.getId()).andExpect(jsonPath("$.sales.basis").value("PROVISIONAL"))
                .andExpect(jsonPath("$.sales.myReward").value(14_592))
                .andExpect(jsonPath("$.payout").doesNotExist());
        studioDetail(suspended.getId()).andExpect(jsonPath("$.sales.basis").value("AT_SUSPENSION"))
                .andExpect(jsonPath("$.payout").doesNotExist());
    }

    @Test
    @DisplayName("B5a: 숨김 뒤 주문 건수의 unknown과 확인된 0건은 다르다")
    void hiddenPostOrderCountKeepsUnknownSeparateFromZero() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, true);
        when(salesReader.readSales(groupBuy.getId())).thenReturn(Optional.of(new GroupBuySalesReader.GroupBuySales(
                10, 272_000, List.of(new GroupBuySalesReader.ItemQuantity(cream.getProductId(), 10)))));

        when(salesReader.countOrdersSince(eq(groupBuy.getId()), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.post.status").value("HIDDEN"))
                .andExpect(jsonPath("$.sales.ordersSinceHidden").doesNotExist());

        when(salesReader.countOrdersSince(eq(groupBuy.getId()), any(LocalDateTime.class)))
                .thenReturn(Optional.of(0L));
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.sales.ordersSinceHidden").value(0));
    }

    @Test
    @DisplayName("B7: 주문 종결 수와 배송·반품 대기 수는 브랜드 처리 건수로 읽기만 한다")
    void endedOrderClosureDoesNotExposeSellerAction() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);
        when(salesReader.readClosure(groupBuy.getId())).thenReturn(Optional.of(
                new GroupBuySalesReader.GroupBuyOrderClosure(312, 288, 24, 18, 6, List.of(), null, null)));

        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.orderClosure.totalCount").value(312))
                .andExpect(jsonPath("$.orderClosure.closedCount").value(288))
                .andExpect(jsonPath("$.orderClosure.unclosed.total").value(24))
                .andExpect(jsonPath("$.orderClosure.unclosed.awaitingShipment").value(18))
                .andExpect(jsonPath("$.orderClosure.unclosed.inReturnOrExchange").value(6));
    }

    @Test
    @DisplayName("B8: 판매 실적의 구매확정·환불 건수는 파트너·어드민 orderClosure와 같은 포트 값이다 — 모르면 null")
    void settledSalesCarryClosurePathCounts() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.SETTLED);
        when(salesReader.readSales(groupBuy.getId())).thenReturn(Optional.of(new GroupBuySalesReader.GroupBuySales(
                312, 8_486_400, List.of(new GroupBuySalesReader.ItemQuantity(cream.getProductId(), 312)))));
        when(salesReader.readClosure(groupBuy.getId())).thenReturn(Optional.of(
                new GroupBuySalesReader.GroupBuyOrderClosure(312, 312, 0, 0, 0, List.of(), 308, 4)));

        studioDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.basis").value("SETTLED"))
                .andExpect(jsonPath("$.sales.orderCount").value(312))
                .andExpect(jsonPath("$.sales.purchaseConfirmedCount").value(308))
                .andExpect(jsonPath("$.sales.refundedCount").value(4))
                // 정산완료에는 미종결 내역 블록이 없다 — 건수는 sales에서만 읽는다.
                .andExpect(jsonPath("$.orderClosure").doesNotExist());

        when(salesReader.readClosure(groupBuy.getId())).thenReturn(Optional.empty());
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.sales.orderCount").value(312))
                .andExpect(jsonPath("$.sales.purchaseConfirmedCount").doesNotExist())
                .andExpect(jsonPath("$.sales.refundedCount").doesNotExist());
    }

    @Test
    @DisplayName("B6: 실적 확정 이력은 기록 시점의 구매확정 건수를 문구로 남긴다 — 모르면 detail을 비운다")
    void salesFinalizedHistoryRecordsPurchaseConfirmedCount() {
        GroupBuy known = seedIn(GroupBuyStatus.ENDED);
        GroupBuy unknown = seedIn(GroupBuyStatus.ENDED);
        when(salesReader.readClosure(known.getId())).thenReturn(Optional.of(
                new GroupBuySalesReader.GroupBuyOrderClosure(1_312, 1_312, 0, 0, 0, List.of(), 1_308, 4)));
        LocalDateTime finalizedAt = LocalDateTime.now().withNano(0);

        groupBuyCommandService.recordSalesFinalized(known.getId(), finalizedAt);
        groupBuyCommandService.recordSalesFinalized(unknown.getId(), finalizedAt);

        assertThat(salesFinalizedDetail(known.getId())).isEqualTo("구매확정 1,308건");
        assertThat(salesFinalizedDetail(unknown.getId())).isNull();
    }

    private String salesFinalizedDetail(long groupBuyId) {
        return groupBuyHistoryRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuyId).stream()
                .filter(entry -> entry.getEventType() == GroupBuyEventType.SALES_FINALIZED)
                .findFirst().orElseThrow().getDetail();
    }

    @Test
    @DisplayName("B8: 정산 블록은 정산 관리의 확정 리워드 · 정산 id 만 — 고정 지급비 합산은 삭제(§41-1 #14 · 44 어드민 설계서 8-7)")
    void settlementAmountUsesConfirmedRewardOnly() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.SETTLED);
        when(settlementReader.readConfirmedReward(groupBuy.getId())).thenReturn(Optional.of(867_672L));
        when(settlementReader.readSettlementId(groupBuy.getId())).thenReturn(Optional.of(41L));

        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.settlement.fixedFeeAmount").doesNotExist())
                .andExpect(jsonPath("$.settlement.confirmedReward").value(867_672))
                .andExpect(jsonPath("$.settlement.settlementId").value(41))
                .andExpect(jsonPath("$.settlement.totalBeforeDeduction").doesNotExist())
                .andExpect(jsonPath("$.settlement.netPaidAmount").doesNotExist())
                .andExpect(jsonPath("$.payout").doesNotExist());

        when(settlementReader.readConfirmedReward(groupBuy.getId())).thenReturn(Optional.empty());
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.settlement.confirmedReward").doesNotExist())
                .andExpect(jsonPath("$.settlement.totalBeforeDeduction").doesNotExist());
    }

    @Test
    @Disabled("[기획 제외] 계약 이행 확인 폐기(2026-10-06 · 1009 기획 수정본 6절) — 기획 복귀 시 되살린다")
    @DisplayName("C6→B12: 스레드 생성과 미이행 기록은 한 거래이고 양측 동의 뒤에만 정산 보류를 풀 수 있다")
    void unfulfilledCreatesThreadAndRequiresAgreementToReleaseHold() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);
        String reason = "배송되지 않은 주문이 18건 있습니다.";
        when(threadGateway.findPairThreadId(any())).thenReturn(Optional.of(pairThreadId));
        when(threadGateway.openFulfillmentDisputeThread(any(), eq(FulfillmentSide.CREATOR), eq(reason)))
                .thenReturn(9_001L);
        when(salesReader.readClosure(groupBuy.getId())).thenReturn(Optional.of(
                new GroupBuySalesReader.GroupBuyOrderClosure(10, 10, 0, 0, 0, List.of(), null, null)));

        studioAction(groupBuy.getId(), "fulfillment-check", Map.of("result", "UNFULFILLED", "reason", reason))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afterEnd.fulfillment.mine.result").value("UNFULFILLED"))
                .andExpect(jsonPath("$.afterEnd.fulfillment.mine.reason").value(reason))
                .andExpect(jsonPath("$.afterEnd.fulfillment.threadId").value(9_001))
                .andExpect(jsonPath("$.afterEnd.fulfillment.onHold").value(true));
        verify(threadGateway).openFulfillmentDisputeThread(any(), eq(FulfillmentSide.CREATOR), eq(reason));
        assertThat(fulfillmentCheckRepository.findByGroupBuyId(groupBuy.getId()))
                .singleElement().satisfies(check -> {
                    assertThat(check.getThreadId()).isEqualTo(9_001L);
                    assertThat(check.getReason()).isEqualTo(reason);
                });

        action(groupBuy.getId(), "fulfillment-check", Map.of("result", "FULFILLED"))
                .andExpect(status().isOk());
        assertThat(groupBuyCommandService.isSettlementReady(groupBuy.getId())).isFalse();

        LocalDateTime now = LocalDateTime.now();
        groupBuyCommandService.recordFulfillmentAgreement(groupBuy.getId(), now, "양측 동의");
        assertThat(groupBuyCommandService.isSettlementReady(groupBuy.getId())).isFalse();
        groupBuyCommandService.releaseFulfillmentHold(groupBuy.getId(), 1L, "운영자", now.plusMinutes(1));
        studioDetail(groupBuy.getId()).andExpect(jsonPath("$.afterEnd.fulfillment.onHold").value(false))
                .andExpect(jsonPath("$.afterEnd.fulfillment.mine.result").value("UNFULFILLED"));
        assertThat(groupBuyCommandService.isSettlementReady(groupBuy.getId())).isTrue();
    }

    @Test
    @DisplayName("판매 포트가 미종결 주문을 모르면 정산 게이트는 열리지 않는다 — 이행 확인은 게이트가 아니다(2026-10-06 폐기)")
    void unknownClosureCannotStartSettlement() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);

        when(salesReader.readClosure(groupBuy.getId())).thenReturn(Optional.empty());
        assertThat(groupBuyCommandService.isSettlementReady(groupBuy.getId())).isFalse();
        when(salesReader.readClosure(groupBuy.getId())).thenReturn(Optional.of(
                new GroupBuySalesReader.GroupBuyOrderClosure(10, 10, 0, 0, 0, List.of(), null, null)));
        assertThat(groupBuyCommandService.isSettlementReady(groupBuy.getId())).isTrue();
    }
}
