package showroomz.api.seller.home;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 홈(1017 설계서 4절) — 처리 대기 = 메뉴 요약 · 배지 합 · 실시간(결제 기준 · 취소 전 포함) · 달력 · 가이드 · 정지 보류.
 * 공구 1건(진행중 · 크림 + 세럼)과 소비자 주문 픽스처는 {@link SettlementTestSupport} 가 깐다.
 */
class SellerHomeIntegrationTest extends SettlementTestSupport {

    private static final String HOME = "/v1/seller/home";
    private static final String BADGES = "/v1/seller/badges";

    private int contractSeq;

    @Test
    @DisplayName("H-01 처리 대기 — 메뉴 요약과 같은 값 · 합계 = 배지(판매 · 계약 · 정산 · 문의) 합 · 기한 경과는 합에 더하지 않는다")
    void pendingTasksMatchMenuSummaries() throws Exception {
        paidGroup();
        OrderDeliveryGroup overdue = paidGroup();
        preparingGroup();
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().minusHours(1), overdue.getId());

        JsonNode home = json(sellerGet(HOME).andExpect(status().isOk()));
        JsonNode orders = json(sellerSummary().andExpect(status().isOk()));
        JsonNode sales = home.at("/pendingTasks/sales");
        assertThat(sales.get("prepareStart").asLong()).isEqualTo(orders.at("/actionBar/prepareStart").asLong()).isEqualTo(2);
        assertThat(sales.get("invoiceRegister").asLong()).isEqualTo(orders.at("/actionBar/invoiceRegister").asLong()).isEqualTo(1);
        assertThat(sales.get("shipOverdue").asLong()).isEqualTo(orders.at("/actionBar/shipOverdue").asLong()).isEqualTo(1);
        assertThat(sales.get("cancelRequests").asLong()).isEqualTo(orders.at("/tabCounts/CANCEL_REQUESTED").asLong());
        assertThat(sales.get("deliveryIssue").asLong()).isEqualTo(orders.at("/actionBar/deliveryIssue").asLong());
        // 기한 경과 1건은 이미 「준비 시작」 2건 안에 있다 — 합계는 2 + 1 이다.
        assertThat(home.at("/pendingTasks/total").asLong()).isEqualTo(3);

        JsonNode badges = json(sellerGet(BADGES).andExpect(status().isOk()));
        assertThat(badges.get("sales").asLong()).isEqualTo(3);
        assertThat(badges.get("sales").asLong() + badges.get("contracts").asLong() + badges.get("settlements").asLong()
                + badges.get("inquiries").asLong()).isEqualTo(home.at("/pendingTasks/total").asLong());

        // 「발송 기한 경과」 진입 — 같은 1건이 목록에 나온다.
        JsonNode list = json(sellerOrders("tab=ALL&shipOverdue=true").andExpect(status().isOk()));
        assertThat(list.get("content")).hasSize(1);
        assertThat(list.at("/content/0/deliveryGroupId").asLong()).isEqualTo(overdue.getId());
    }

    @Test
    @DisplayName("H-02 계약 · 정산 처리 대기 — 내 서명 필요 · 검토 반려 · 조정 협의 「내 응답 필요」(최신 제안이 상대 것일 때만)")
    void contractAndSettlementTasks() throws Exception {
        Contract awaitingMe = seedSigningContract("서명 대기 공구", LocalDateTime.now().plusDays(3));
        jdbc.update("UPDATE contract SET creator_signed_at = ? WHERE contract_id = ?", LocalDateTime.now().minusHours(2),
                awaitingMe.getId());
        Contract rejected = seedSigningContract("반려 공구", LocalDateTime.now().plusDays(3));
        jdbc.update("UPDATE contract SET status = 'REVIEW_REJECTED' WHERE contract_id = ?", rejected.getId());
        Long adjustmentId = seedAdjustment(9001L);
        seedProposal(adjustmentId, 1, "CREATOR", creator.getId(), "PENDING");

        JsonNode home = json(sellerGet(HOME).andExpect(status().isOk()));
        assertThat(home.at("/pendingTasks/contract/mySignatureNeeded").asLong()).isEqualTo(1);
        assertThat(home.at("/pendingTasks/contract/reviewRejected").asLong()).isEqualTo(1);
        assertThat(home.at("/pendingTasks/settlement/adjustmentMyTurn").asLong()).isEqualTo(1);
        assertThat(home.at("/settlement/adjustmentMyTurn").asLong()).isEqualTo(1);
        JsonNode contracts = json(sellerGet("/v1/seller/contracts/summary").andExpect(status().isOk()));
        assertThat(home.at("/pendingTasks/contract/mySignatureNeeded").asLong()
                + home.at("/pendingTasks/contract/reviewRejected").asLong())
                .isEqualTo(contracts.get("actionRequiredCount").asLong());
        JsonNode settlementSummary = json(sellerGet("/v1/seller/settlements/summary").andExpect(status().isOk()));
        assertThat(settlementSummary.get("adjustmentMyTurnCount").asLong()).isEqualTo(1);

        JsonNode badges = json(sellerGet(BADGES).andExpect(status().isOk()));
        assertThat(badges.get("contracts").asLong()).isEqualTo(2);
        assertThat(badges.get("settlements").asLong()).isEqualTo(1);

        // 내가 다시 제안하면 공이 상대에게 넘어간다.
        jdbc.update("UPDATE settlement_adjustment_proposal SET status = 'REJECTED' WHERE adjustment_id = ?", adjustmentId);
        seedProposal(adjustmentId, 2, "SELLER", brand.marketId(), "PENDING");
        assertThat(json(sellerGet(BADGES).andExpect(status().isOk())).get("settlements").asLong()).isZero();
    }

    @Test
    @DisplayName("H-03 실시간 — 결제 기준 · 취소 전 포함 · 오늘 / 어제 같은 시각 / 누적 · 시간대 24칸(지금 칸 집계 중) · 재고 · 최소 물량")
    void live() throws Exception {
        LocalDateTime before = LocalDateTime.now();
        Purchase today = purchase(creamVariant, 2);
        Purchase cancelledLater = purchase(serumVariant, 1);
        Purchase yesterday = purchase(creamVariant, 1);
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_id = ?",
                before.toLocalDate().minusDays(1).atStartOfDay(), yesterday.orderId());
        // 결제 뒤 취소돼도 실시간에서는 빠지지 않는다.
        jdbc.update("UPDATE orders SET status = 'CANCELLED', cancelled_at = ? WHERE order_id = ?",
                LocalDateTime.now(), cancelledLater.orderId());

        JsonNode live = json(sellerGet(HOME + "/live").andExpect(status().isOk()));
        assertThat(live.get("items")).hasSize(1);
        JsonNode item = live.at("/items/0");
        assertThat(item.get("groupBuyId").asLong()).isEqualTo(groupBuy.getId());
        assertThat(item.get("creatorName").asText()).isEqualTo(creator.getShowroomName());
        assertThat(item.get("ordersToday").asLong()).isEqualTo(2);
        assertThat(item.get("paidToday").asLong()).isEqualTo(CREAM_PRICE * 2L + SERUM_PRICE);
        assertThat(item.get("ordersYesterdaySameTime").asLong()).isEqualTo(1);
        assertThat(item.get("ordersCumulative").asLong()).isEqualTo(3);
        assertThat(item.get("paidCumulative").asLong()).isEqualTo(CREAM_PRICE * 3L + SERUM_PRICE);
        assertThat(item.get("soldQuantity").asLong()).isEqualTo(4);
        assertThat(item.get("progressRatio").asDouble()).isBetween(0.0, 1.0);

        JsonNode hourly = item.get("hourly");
        assertThat(hourly).hasSize(24);
        int hour = Integer.parseInt(live.get("asOf").asText().substring(11, 13)); // 직렬화 형식 「…T08:09:15Z」
        long hourlyOrders = 0;
        for (JsonNode bucket : hourly) {
            hourlyOrders += bucket.get("orders").asLong();
            assertThat(bucket.get("collecting").asBoolean()).isEqualTo(bucket.get("hour").asInt() == hour);
        }
        assertThat(hourlyOrders).isEqualTo(2);

        assertThat(item.at("/stock/remaining").asLong()).isEqualTo(stockOf(creamVariant) + stockOf(serumVariant));
        assertThat(item.at("/stock/minQuantity").asInt()).isEqualTo(500);
        assertThat(item.at("/stock/productId").asLong()).isEqualTo(cream.getProductId());
        assertThat(today.orderId()).isNotNull();

        // 홈 1회 조립에도 같은 블록이 실린다.
        assertThat(json(sellerGet(HOME).andExpect(status().isOk())).at("/live/items/0/ordersToday").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("H-04 달력 — 기간 겹치는 공구 3색 · 발송 기한(공구별 날짜) · 서명 기한(보류 중 제외) · 공휴일 · month 형식 오류 400")
    void calendar() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        OrderDeliveryGroup group = paidGroup();
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ? WHERE delivery_group_id = ?",
                now.plusDays(1), group.getId());
        Contract signing = seedSigningContract("서명 공구", now.plusDays(2));
        Contract held = seedSigningContract("보류 공구", now.plusDays(2));
        jdbc.update("UPDATE contract SET sanction_hold_since = ?, sanction_hold_party = 'CREATOR' WHERE contract_id = ?",
                now, held.getId());
        GroupBuy upcoming = seedIn(GroupBuyStatus.PREPARING);
        LocalDate holiday = now.toLocalDate().plusDays(1);
        businessCalendar.replaceRegisteredHolidays(Set.of(holiday));

        String month = YearMonth.from(now).toString();
        JsonNode calendar = json(sellerGet(HOME + "/calendar?month=" + month).andExpect(status().isOk()));
        assertThat(calendar.get("month").asText()).isEqualTo(month);
        assertThat(calendar.get("from").asText()).isEqualTo(YearMonth.from(now).atDay(1).minusDays(7).toString());

        Map<Long, String> bars = new java.util.HashMap<>();
        calendar.get("groupBuys").forEach(g -> bars.put(g.get("groupBuyId").asLong(), g.get("status").asText()));
        assertThat(bars).containsEntry(groupBuy.getId(), "IN_PROGRESS");
        if (!upcoming.getStartAt().toLocalDate().isAfter(LocalDate.parse(calendar.get("to").asText()))) {
            assertThat(bars).containsEntry(upcoming.getId(), "UPCOMING");
        }

        List<String> deadlines = new ArrayList<>();
        calendar.get("deadlines").forEach(d -> deadlines.add(
                d.get("kind").asText() + "|" + d.get("date").asText() + "|" + d.get("targetId").asLong()));
        assertThat(deadlines).contains(
                "SHIP_DUE|" + now.plusDays(1).toLocalDate() + "|" + groupBuy.getId(),
                "SIGN_DUE|" + now.plusDays(2).toLocalDate() + "|" + signing.getId());
        assertThat(deadlines).noneMatch(d -> d.startsWith("SIGN_DUE") && d.endsWith("|" + held.getId()));
        assertThat(calendar.at("/deadlines/0/label").asText()).startsWith("발송 기한 · ");

        List<String> holidays = new ArrayList<>();
        calendar.get("holidays").forEach(h -> holidays.add(h.get("date").asText()));
        assertThat(holidays).contains(holiday.toString());

        sellerGet(HOME + "/calendar?month=2026-13").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("H-05 첫 공구 가이드 — 자동 판정 · 지금 할 단계 · 연결 1명 · 숨기기 / 다시 표시 · 오픈 이력이 생기면 사라진다")
    void guide() throws Exception {
        BrandFixture.Brand fresh = fixture.createBrand("fresh@showroomz.test", "새싹랩");
        String freshToken = sellerToken(fresh.seller());

        JsonNode guide = json(sellerGet(HOME, freshToken).andExpect(status().isOk())).get("guide");
        assertThat(guide.get("visible").asBoolean()).isTrue();
        assertThat(guide.get("completed").asInt()).isEqualTo(1);
        assertThat(guide.get("currentStep").asText()).isEqualTo("PRODUCT");
        assertThat(guide.get("steps")).hasSize(6);
        assertThat(guide.get("connectedCreatorCount").asLong()).isZero();
        assertThat(guide.get("connectedCreatorName").isNull()).isTrue();

        connect(fresh, creator);
        guide = json(sellerGet(HOME, freshToken).andExpect(status().isOk())).get("guide");
        assertThat(guide.at("/steps/2/key").asText()).isEqualTo("CONNECTION");
        assertThat(guide.at("/steps/2/done").asBoolean()).isTrue();
        assertThat(guide.get("connectedCreatorCount").asLong()).isEqualTo(1);
        assertThat(guide.get("connectedCreatorName").asText()).isEqualTo(creator.getShowroomName());

        guide = json(sellerPatch(HOME + "/guide", Map.of("hidden", true), freshToken).andExpect(status().isOk()));
        assertThat(guide.get("hidden").asBoolean()).isTrue();
        assertThat(guide.get("visible").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT home_guide_hidden_at FROM market WHERE market_id = ?",
                LocalDateTime.class, fresh.marketId())).isNotNull();
        guide = json(sellerPatch(HOME + "/guide", Map.of("hidden", false), freshToken).andExpect(status().isOk()));
        assertThat(guide.get("visible").asBoolean()).isTrue();
        sellerPatch(HOME + "/guide", Map.of(), freshToken).andExpect(status().isBadRequest());

        // 진행 중 공구가 있는(오픈 이력) 브랜드 — 6단계 모두 완료 · 표시하지 않는다.
        JsonNode opened = json(sellerGet(HOME).andExpect(status().isOk())).get("guide");
        assertThat(opened.get("visible").asBoolean()).isFalse();
        assertThat(opened.at("/steps/4/key").asText()).isEqualTo("OPEN");
        assertThat(opened.at("/steps/4/done").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("H-06 계약 진행 · 오픈 예정 — 체결 전 4상태 · 막힌 이유(물량 미확인 · 게시물 반려) · 내 정지면 승인 보류 · 체결 보류")
    void listsAndSanctionHold() throws Exception {
        Contract signing = seedSigningContract("서명 공구", LocalDateTime.now().plusDays(3));
        GroupBuy stockPending = seedIn(GroupBuyStatus.PREPARING);
        GroupBuy rejectedPost = seedIn(GroupBuyStatus.PREPARING);
        confirmStockDirectly(rejectedPost.getId());
        seedPost(rejectedPost.getId(), GroupBuyPostReviewStatus.REJECTED, false);

        JsonNode home = json(sellerGet(HOME).andExpect(status().isOk()));
        assertThat(home.at("/contractsInProgress/count").asLong()).isEqualTo(1);
        assertThat(home.at("/contractsInProgress/items/0/contractId").asLong()).isEqualTo(signing.getId());
        assertThat(home.at("/contractsInProgress/items/0/status").asText()).isEqualTo("SIGNING");
        assertThat(home.at("/contractsInProgress/items/0/hold").isNull()).isTrue();
        assertThat(home.at("/upcomingGroupBuys/count").asLong()).isEqualTo(2);
        Map<Long, String> reasons = new java.util.HashMap<>();
        home.at("/upcomingGroupBuys/items").forEach(i -> reasons.put(i.get("groupBuyId").asLong(),
                i.get("blockedReason").asText()));
        assertThat(reasons).containsEntry(stockPending.getId(), "최소 준비 물량 확인 필요")
                .containsEntry(rejectedPost.getId(), "게시물 반려 — 인플루언서 수정 대기");

        jdbc.update("UPDATE market SET status = 'SUSPENDED' WHERE market_id = ?", brand.marketId());
        home = json(sellerGet(HOME).andExpect(status().isOk()));
        assertThat(home.at("/upcomingGroupBuys/items/0/hold/label").asText()).isEqualTo("승인 보류 · 계정 정지");
        assertThat(home.at("/contractsInProgress/items/0/hold/label").asText()).isEqualTo("체결 보류 · 계정 정지");
        assertThat(home.at("/account/status").asText()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("H-07 인플루언서 토큰은 파트너 홈에 들어오지 못한다(판매자 조회 404)")
    void creatorCannotReadSellerHome() throws Exception {
        String token = bearerToken(creator.getUser().getUsername(), RoleType.CREATOR, creator.getUser().getId());
        sellerGet(HOME, token).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 픽스처

    /** 서명 진행중 계약 — 검토 통과까지(상대 · 나 모두 미서명). */
    private Contract seedSigningContract(String title, LocalDateTime signatureDeadlineAt) {
        return transactionTemplate.execute(tx -> {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            LocalDateTime startAt = now.plusDays(20);
            Contract contract = Contract.createDraft(brand.market(), creator, null);
            contract.updateTerms(title, startAt, startAt.plusDays(7), 300_000, FixedFeeTrigger.POST_REGISTERED, now,
                    1, 1, 3, startAt.toLocalDate().plusDays(10), true, SecondaryUsePeriodType.FIXED, 12, false, null);
            List<ContractItem> items = new ArrayList<>();
            items.add(item(cream, 27_200, "12.0", 300));
            contract.replaceItems(items);
            contract.applyReviewRequested("CTR-HOME-%03d".formatted(++contractSeq), null, null, now.minusDays(2));
            contract.approveReview(now.minusDays(1), signatureDeadlineAt, now.minusDays(1));
            return contractRepository.saveAndFlush(contract);
        });
    }

    private Long seedAdjustment(Long settlementId) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("INSERT INTO settlement_adjustment (settlement_id, settlement_number, group_buy_id, market_id, "
                        + "creator_id, thread_id, requester_type, status, original_reward_amount, max_reward_amount, "
                        + "opened_at, deadline_at, notice_due_at, created_at, modified_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'CREATOR', 'OPEN', 100000, 200000, ?, ?, ?, ?, ?)",
                settlementId, "S-" + settlementId, groupBuy.getId(), brand.marketId(), creator.getId(), pairThreadId,
                now.minusDays(1), now.plusDays(5), now.plusDays(4), now, now);
        return jdbc.queryForObject("SELECT adjustment_id FROM settlement_adjustment WHERE settlement_id = ?",
                Long.class, settlementId);
    }

    private void seedProposal(Long adjustmentId, int seq, String proposer, long proposerId, String status) {
        jdbc.update("INSERT INTO settlement_adjustment_proposal (adjustment_id, seq, proposer_type, proposer_id, "
                        + "reward_amount, reason, status, proposed_at) VALUES (?, ?, ?, ?, 150000, '사유', ?, ?)",
                adjustmentId, seq, proposer, proposerId, status, LocalDateTime.now().withNano(0));
    }
}
