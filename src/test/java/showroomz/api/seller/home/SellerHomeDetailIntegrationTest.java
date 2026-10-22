package showroomz.api.seller.home;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 홈 세부(1017 설계서 4절 · 9절) — 검수 지연과 회수 방치 분리 · 배지 = 메뉴 요약 · 진행 중 공구 0 · 2건 · 달력 월 경계 ·
 * 기간이 겹치지 않는 공구의 발송 기한 · 가이드 단계 · 목록 상한 · 정산 카드 = 정산 요약 · 「발송 기한 경과」 필터 경계.
 */
@DisplayName("1017 파트너 홈 세부")
class SellerHomeDetailIntegrationTest extends SettlementTestSupport {

    private static final String HOME = "/v1/seller/home";

    private int contractSeq;

    @Test
    @DisplayName("SD-01 반품 · 교환 — 회수 대기 방치는 「검수 지연」이 아니다 · 검수 기한이 지나야 검수 지연(메뉴 KPI overdue 는 둘 다 센다)")
    void inspectOverdueIsSeparated() throws Exception {
        OrderDeliveryGroup delivered = deliveredFrom(paidGroup(), LocalDateTime.now().minusHours(2));
        Long claimId = returnClaim(delivered, itemsOf(delivered).get(0), 1, ClaimReason.DAMAGED_OR_DEFECTIVE);
        // 회수 대기(소비자 미발송)로 두고 회수 기한을 넘긴다 — 「회수 대기 방치」.
        jdbc.update("UPDATE order_claim SET status = 'REQUESTED', collect_due_at = ? WHERE claim_id = ?",
                LocalDateTime.now().minusHours(1), claimId);

        JsonNode claims = json(sellerGet(HOME).andExpect(status().isOk())).at("/pendingTasks/claims");
        JsonNode kpi = json(sellerGet("/v1/seller/claims/summary").andExpect(status().isOk())).get("kpi");
        assertThat(claims.get("collectWait").asLong()).isEqualTo(kpi.get("collectWait").asLong()).isEqualTo(1);
        assertThat(kpi.get("overdue").asLong()).isEqualTo(1);
        assertThat(claims.get("inspectOverdue").asLong()).isZero();

        jdbc.update("UPDATE order_claim SET status = 'RECEIVED', received_at = ?, inspect_due_at = ? WHERE claim_id = ?",
                LocalDateTime.now().minusDays(3), LocalDateTime.now().minusMinutes(5), claimId);
        claims = json(sellerGet(HOME).andExpect(status().isOk())).at("/pendingTasks/claims");
        assertThat(claims.get("inspection").asLong()).isEqualTo(1);
        assertThat(claims.get("inspectOverdue").asLong()).isEqualTo(1);
        assertThat(claims.get("collectWait").asLong()).isZero();
    }

    @Test
    @DisplayName("SD-02 GNB 배지 — 공구 · 연결·소통 · 계약 배지는 각 메뉴 요약의 배지와 같은 수")
    void badgesEqualMenuSummaries() throws Exception {
        seedIn(GroupBuyStatus.PREPARING); // 최소 물량 확인 대기
        Contract signing = seedSigningContract("서명 공구", 10);
        jdbc.update("UPDATE contract SET creator_signed_at = ? WHERE contract_id = ?", LocalDateTime.now(), signing.getId());

        JsonNode badges = json(sellerGet("/v1/seller/badges").andExpect(status().isOk()));
        assertThat(badges.get("groupBuys").asLong())
                .isEqualTo(json(sellerGet("/v1/seller/group-buys/summary")).get("actionRequiredCount").asLong())
                .isEqualTo(1);
        assertThat(badges.get("contracts").asLong())
                .isEqualTo(json(sellerGet("/v1/seller/contracts/summary")).get("actionRequiredCount").asLong())
                .isEqualTo(1);
        assertThat(badges.get("connections").asLong())
                .isEqualTo(json(sellerGet("/v1/seller/connections/summary")).get("unreadCount").asLong());
        assertThat(badges.get("inquiries").asLong()).isZero();
    }

    @Test
    @DisplayName("SD-03 실시간 — 진행 중 공구가 없으면 빈 목록 · 두 건이면 시작일 순으로 둘 다")
    void liveWithNoneAndTwo() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        assertThat(json(sellerGet(HOME + "/live").andExpect(status().isOk())).get("items")).isEmpty();
        assertThat(json(sellerGet(HOME).andExpect(status().isOk())).at("/live/items")).isEmpty();

        LocalDateTime now = LocalDateTime.now().withNano(0);
        GroupBuy earlier = seed(brand, creator, "먼저 시작한 공구", now.minusDays(5), now.plusDays(2));
        moveTo(earlier.getId(), GroupBuyStatus.IN_PROGRESS);
        GroupBuy later = seed(brand, creator, "나중 시작한 공구", now.minusDays(1), now.plusDays(6));
        moveTo(later.getId(), GroupBuyStatus.SUSPENSION_SCHEDULED);

        JsonNode items = json(sellerGet(HOME + "/live").andExpect(status().isOk())).get("items");
        assertThat(items).hasSize(2);
        assertThat(items.at("/0/groupBuyId").asLong()).isEqualTo(earlier.getId());
        assertThat(items.at("/1/groupBuyId").asLong()).isEqualTo(later.getId());
        assertThat(items.at("/0/progressRatio").asDouble()).isBetween(0.7, 0.72);
    }

    @Test
    @DisplayName("SD-04 달력 — 앞 달에 시작해 넘어온 공구는 막대에 · 구간 밖 공구는 빠진다 · 기간이 겹치지 않아도 발송 기한은 공구명으로")
    void calendarMonthBoundaries() throws Exception {
        YearMonth month = YearMonth.now();
        LocalDateTime first = month.atDay(1).atStartOfDay();
        GroupBuy spanning = seed(brand, creator, "넘어온 공구", first.minusDays(20), first.plusDays(2));
        moveTo(spanning.getId(), GroupBuyStatus.ENDED);
        GroupBuy longAgo = seed(brand, creator, "오래전 공구", first.minusDays(70), first.minusDays(60));
        moveTo(longAgo.getId(), GroupBuyStatus.ENDED);
        OrderDeliveryGroup group = paidGroup();
        jdbc.update("UPDATE order_delivery_group SET group_buy_id = ?, ship_due_at = ? WHERE delivery_group_id = ?",
                longAgo.getId(), first.plusDays(10).withHour(18), group.getId());

        JsonNode calendar = json(sellerGet(HOME + "/calendar?month=" + month).andExpect(status().isOk()));
        List<Long> bars = new ArrayList<>();
        calendar.get("groupBuys").forEach(g -> bars.add(g.get("groupBuyId").asLong()));
        assertThat(bars).contains(spanning.getId()).doesNotContain(longAgo.getId());

        JsonNode shipDue = null;
        for (JsonNode d : calendar.get("deadlines")) {
            if ("SHIP_DUE".equals(d.get("kind").asText()) && d.get("targetId").asLong() == longAgo.getId()) {
                shipDue = d;
            }
        }
        assertThat(shipDue).isNotNull();
        assertThat(shipDue.get("label").asText()).isEqualTo("발송 기한 · 오래전 공구");
        assertThat(shipDue.get("date").asText()).isEqualTo(first.plusDays(10).toLocalDate().toString());
        assertThat(calendar.get("to").asText()).isEqualTo(month.atEndOfMonth().plusDays(7).toString());
    }

    @Test
    @DisplayName("SD-05 가이드 — 상품 등록 · 체결 계약은 자동 완료 · 연결 없음이 지금 할 단계 · 오픈 전이면 계속 보인다")
    void guideStepsForFreshBrand() throws Exception {
        BrandFixture.Brand fresh = fixture.createBrand("guide2@showroomz.test", "새봄랩");
        seed(fresh, creator, "새봄 첫 공구", LocalDateTime.now().plusDays(20), LocalDateTime.now().plusDays(27));

        JsonNode guide = json(sellerGet(HOME, sellerToken(fresh.seller())).andExpect(status().isOk())).get("guide");
        Map<String, Boolean> done = new java.util.LinkedHashMap<>();
        guide.get("steps").forEach(s -> done.put(s.get("key").asText(), s.get("done").asBoolean()));
        assertThat(done).containsExactly(Map.entry("BRAND_INFO", true), Map.entry("PRODUCT", true),
                Map.entry("CONNECTION", false), Map.entry("CONTRACT", true), Map.entry("OPEN", false),
                Map.entry("ORDERS", false));
        assertThat(guide.get("currentStep").asText()).isEqualTo("CONNECTION");
        assertThat(guide.get("completed").asInt()).isEqualTo(3);
        assertThat(guide.get("visible").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("SD-06 계약 진행 — 목록은 공구 시작일 순 5건 · count 는 전체")
    void contractsInProgressCapped() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 6; i >= 1; i--) {
            ids.add(0, seedSigningContract("서명 공구 " + i, 10 + i).getId());
        }

        JsonNode block = json(sellerGet(HOME).andExpect(status().isOk())).get("contractsInProgress");
        assertThat(block.get("count").asLong()).isEqualTo(6);
        assertThat(block.get("items")).hasSize(5);
        List<Long> shown = new ArrayList<>();
        block.get("items").forEach(i -> shown.add(i.get("contractId").asLong()));
        assertThat(shown).containsExactlyElementsOf(ids.subList(0, 5));
    }

    @Test
    @DisplayName("SD-07 정산 카드 — 정산 요약(지급 예정 · 다음 지급일 · 정산 확인 중)과 같은 값")
    void settlementCardMatchesSummary() throws Exception {
        seedSettlement("확인 중 공구", 1_000_000, SettlementStatus.REVIEWING);
        var scheduled = seedSettlement("지급 예정 공구", 2_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ? AND payee = 'BRAND'",
                LocalDate.now().plusDays(3), scheduled.getId());

        JsonNode card = json(sellerGet(HOME).andExpect(status().isOk())).get("settlement");
        JsonNode summary = json(sellerGet("/v1/seller/settlements/summary").andExpect(status().isOk()));
        assertThat(card.get("nextPayoutAmount").asLong()).isEqualTo(summary.at("/scheduled/amount").asLong()).isPositive();
        assertThat(card.get("nextPayoutDate").asText()).isEqualTo(summary.get("nextPayoutDate").asText())
                .isEqualTo(LocalDate.now().plusDays(3).toString());
        assertThat(card.get("reviewingCount").asLong()).isEqualTo(summary.get("reviewingCount").asLong());
    }

    @Test
    @DisplayName("SD-08 「발송 기한 경과」 — 발송된 건 · 기한 전 건은 빠지고 취소 요청이 걸린 건은 든다(요약 숫자 = 목록 건수)")
    void shipOverdueFilterBoundaries() throws Exception {
        LocalDateTime past = LocalDateTime.now().minusHours(3);
        OrderDeliveryGroup overdue = paidGroup();
        OrderDeliveryGroup notYet = paidGroup();
        OrderDeliveryGroup shipped = shippingGroup(nextTrackingNumber());
        OrderDeliveryGroup withCancel = paidGroup();
        seedCancelRequest(withCancel, itemsOf(withCancel));
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ? WHERE delivery_group_id IN (?, ?, ?)", past,
                overdue.getId(), shipped.getId(), withCancel.getId());
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ? WHERE delivery_group_id = ?",
                LocalDateTime.now().plusDays(1), notYet.getId());

        JsonNode list = json(sellerOrders("tab=ALL&shipOverdue=true").andExpect(status().isOk()));
        List<Long> ids = new ArrayList<>();
        list.get("content").forEach(r -> ids.add(r.get("deliveryGroupId").asLong()));
        assertThat(ids).containsExactlyInAnyOrder(overdue.getId(), withCancel.getId());
        assertThat(json(sellerSummary()).at("/actionBar/shipOverdue").asLong()).isEqualTo(ids.size());
        list.get("content").forEach(r -> assertThat(r.at("/overlays/shipOverdue").asBoolean()).isTrue());
    }

    // ------------------------------------------------------------------ 픽스처

    /** 서명 진행중 계약 — 공구 시작은 {@code startInDays} 뒤. */
    private Contract seedSigningContract(String title, int startInDays) {
        return transactionTemplate.execute(tx -> {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            LocalDateTime startAt = now.plusDays(startInDays);
            Contract contract = Contract.createDraft(brand.market(), creator, null);
            contract.updateTerms(title, startAt, startAt.plusDays(7), 300_000, FixedFeeTrigger.POST_REGISTERED, now,
                    1, 1, 3, startAt.toLocalDate().plusDays(10), true, SecondaryUsePeriodType.FIXED, 12, false, null);
            List<ContractItem> items = new ArrayList<>();
            items.add(item(cream, 27_200, "12.0", 300));
            contract.replaceItems(items);
            contract.applyReviewRequested("CTR-SD-%03d".formatted(++contractSeq), null, null, now.minusDays(2));
            contract.approveReview(now.minusDays(1), now.plusDays(3), now.minusDays(1));
            return contractRepository.saveAndFlush(contract);
        });
    }
}
