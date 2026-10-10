package showroomz.api.admin.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.product.entity.Product;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementClawback;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.service.SettlementClawbackService;
import showroomz.domain.settlement.type.ClawbackSide;
import showroomz.domain.settlement.type.ClawbackStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.support.ContractOptions;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산 차감(44 어드민 설계서 6절 · ST-10) — 이미 생성된 정산의 항목이 운영자 사유 환불(06a B5 → 06c 집행)로 나가면 측별 2행이 생기고,
 * 같은 마켓 · 같은 인플루언서의 다음 정산에서 회수된다. 리워드보다 크면 이월 · 브랜드 측 반영마다 수정세금계산서 · 탈퇴면 미회수.
 */
@DisplayName("정산 차감 — 발생 · 다음 정산 반영 · 이월 · 수정세금계산서 · 미회수 · 06c · 07a 차감 탭 · 파트너 · 스튜디오 D3")
class AdminSettlementClawbackIntegrationTest extends SettlementTestSupport {

    private static final String SETTLEMENTS = "/v1/admin/settlements";
    /** 크림 27,200 · 리워드 12% → 단위 리워드 3,264 · 부가세 326 · 브랜드 측 27,200 − 3,264 − 326. */
    private static final long CREAM_REWARD = 3_264;
    private static final long CREAM_BRAND = 27_200 - 3_264 - 326;

    @Autowired private SettlementClawbackRepository clawbackRepository;
    @Autowired private SettlementTaxDocumentRepository documentRepository;
    @Autowired private SettlementClawbackService clawbackService;
    @Autowired private showroomz.domain.settlement.service.SettlementReader settlementReader;

    @Test
    @DisplayName("ST-10 확정 정산 항목 환불 → 이번 정산 불변 · CLW 2행 PENDING → 다음 정산에서 측별 회수 · 리워드 초과분 seq 2 이월 · 수정세금계산서")
    void clawbackRegisteredAndAppliedToNextSettlement() throws Exception {
        OrderDeliveryGroup creamGroup = confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement first = confirm(generate(LocalDateTime.now().withNano(0)));
        Settlement firstBefore = settlement(first.getId());

        Long refundTaskId = operatorRefund(creamGroup, "POST_CONFIRM_DEFECT", 27_200);

        // 이번 정산 금액은 고치지 않는다(스냅샷) — 차감 행만 생긴다.
        Settlement firstAfter = settlement(first.getId());
        assertThat(firstAfter.getBrandPayoutAmount()).isEqualTo(firstBefore.getBrandPayoutAmount());
        assertThat(firstAfter.getRewardAmount()).isEqualTo(firstBefore.getRewardAmount());
        List<SettlementClawback> rows = clawbackRepository.findByRefundTaskIdOrderByIdAsc(refundTaskId);
        assertThat(rows).extracting(SettlementClawback::getSide, SettlementClawback::getAmount,
                        SettlementClawback::getStatus, SettlementClawback::getSeq)
                .containsExactly(tuple(ClawbackSide.BRAND, CREAM_BRAND, ClawbackStatus.PENDING, 1),
                        tuple(ClawbackSide.CREATOR, CREAM_REWARD, ClawbackStatus.PENDING, 1));
        String number = rows.get(0).getClawbackNumber();
        assertThat(number).isEqualTo("CLW-0001");
        assertThat(settlementEvents(first.getId())).contains(SettlementEventType.CLAWBACK_REGISTERED);
        // 06c — 환불 큐 행의 정산 블록 · 06a ④ — 하위주문의 차감 행.
        adminGet(ADMIN_REFUNDS + "/" + refundTaskId).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.state").value("SETTLED"))
                .andExpect(jsonPath("$.settlement.clawback.clawbackNumber").value(number))
                .andExpect(jsonPath("$.settlement.clawback.status").value("PENDING"));
        adminGet(ADMIN_ORDERS + "/" + creamGroup.getOrder().getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].settlement.clawbacks.length()").value(2));

        // 다음 공구 — 같은 브랜드 · 같은 인플루언서 · 세럼 1개(리워드 2,400 < 리워드 차감 3,264).
        Settlement next = nextSettlementWithSerum();
        assertThat(next.getBrandClawbackAmount()).isEqualTo(CREAM_BRAND);
        assertThat(next.getRewardClawbackAmount()).isEqualTo(next.getRewardAmount());
        assertThat(next.getRewardAfterClawback()).isZero();
        assertThat(next.getBrandPayoutAmount()).isEqualTo(next.getBrandPayoutBeforeClawback() - CREAM_BRAND);
        assertThat(clawbackRepository.findAll()).extracting(SettlementClawback::getSide, SettlementClawback::getSeq,
                        SettlementClawback::getStatus, SettlementClawback::getAmount)
                .containsExactlyInAnyOrder(
                        tuple(ClawbackSide.BRAND, 1, ClawbackStatus.APPLIED, CREAM_BRAND),
                        tuple(ClawbackSide.CREATOR, 1, ClawbackStatus.APPLIED, next.getRewardAmount()),
                        tuple(ClawbackSide.CREATOR, 2, ClawbackStatus.PENDING, CREAM_REWARD - next.getRewardAmount()));
        assertThat(settlementEvents(next.getId())).contains(SettlementEventType.CLAWBACK_APPLIED);
        assertThat(documentRepository.findBySettlementIdOrderByIdAsc(next.getId()))
                .filteredOn(d -> d.getType() == TaxDocumentType.BRAND_TAX_INVOICE_CREDIT).singleElement()
                .satisfies(d -> {
                    assertThat(d.getStatus()).isEqualTo(TaxDocumentStatus.PENDING_ISSUE);
                    assertThat(d.getSupplyAmount()).isEqualTo(-CREAM_REWARD);
                    assertThat(d.getVatAmount()).isEqualTo(-326);
                });

        // 07b D3 · 파트너 D3(P6) · 스튜디오 D3(C-05)
        JsonNode admin = json(adminGet(SETTLEMENTS + "/" + next.getId()).andExpect(status().isOk()));
        assertThat(admin.at("/clawbacksApplied/0/clawbackNumber").asText()).isEqualTo(number);
        assertThat(admin.at("/clawbacksApplied/0/brandAmount").asLong()).isEqualTo(CREAM_BRAND);
        assertThat(admin.at("/clawbacksApplied/0/creatorAmount").asLong()).isEqualTo(next.getRewardAmount());
        assertThat(admin.at("/clawbacksApplied/0/originSettlementNumber").asText())
                .isEqualTo(first.getSettlementNumber());
        JsonNode partner = json(sellerGet("/v1/seller/settlements/" + next.getId()).andExpect(status().isOk()));
        assertThat(partner.at("/breakdown/clawbacks/0/amount").asLong()).isEqualTo(CREAM_BRAND);
        assertThat(partner.at("/breakdown/clawbacks/0/creditInvoiceStatus").asText()).isEqualTo("PENDING_ISSUE");
        assertThat(partner.at("/breakdown/clawbacks/0/reasonLabel").asText()).isEqualTo("구매확정 후 하자");
        assertThat(partner.at("/breakdown/brandClawbackAmount").asLong()).isEqualTo(CREAM_BRAND);
        JsonNode studio = json(creatorGet("/v1/creator/settlements/" + next.getId()).andExpect(status().isOk()));
        assertThat(studio.at("/clawbacks/0/clawbackNumber").asText()).isEqualTo(number);
        assertThat(studio.at("/clawbacks/0/amount").asLong()).isEqualTo(next.getRewardAmount());
        assertThat(studio.at("/breakdown/rewardAfterClawback").asLong()).isZero();
    }

    @Test
    @DisplayName("미회수 — 인플루언서 탈퇴면 인플루언서 측 PENDING → UNRECOVERABLE · 07a 차감 탭 · 배지")
    void unrecoverableWhenCreatorWithdraws() throws Exception {
        OrderDeliveryGroup creamGroup = confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement first = confirm(generate(LocalDateTime.now().withNano(0)));
        operatorRefund(creamGroup, "RECALL", 27_200);
        jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE user_id = ?", creator.getUser().getId());

        assertThat(clawbackService.markUnrecoverable(LocalDateTime.now())).isEqualTo(1);
        assertThat(clawbackService.markUnrecoverable(LocalDateTime.now())).isZero();

        JsonNode tab = json(adminGet(SETTLEMENTS + "?tab=CLAWBACK").andExpect(status().isOk()));
        assertThat(tab.at("/pageInfo/totalResults").asLong()).isEqualTo(1);
        JsonNode row = tab.at("/content/0");
        assertThat(row.get("clawbackNumber").asText()).isEqualTo("CLW-0001");
        assertThat(row.get("originSettlementNumber").asText()).isEqualTo(first.getSettlementNumber());
        assertThat(row.get("reasonLabel").asText()).isEqualTo("위해성 리콜");
        assertThat(row.get("brandStatus").asText()).isEqualTo("PENDING");
        assertThat(row.get("creatorStatus").asText()).isEqualTo("UNRECOVERABLE");
        assertThat(row.get("unrecoverableReasonLabel").asText()).isEqualTo("인플루언서 탈퇴");
        assertThat(tab.at("/toolbar/unrecoverableCount").asLong()).isEqualTo(1);
        assertThat(tab.at("/toolbar/unrecoverableAmount").asLong()).isEqualTo(CREAM_REWARD);
        adminGet(SETTLEMENTS + "?tab=CLAWBACK&keyword=clw-0001").andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        adminGet(SETTLEMENTS + "?tab=CLAWBACK&keyword=CLW-9999").andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        adminGet(SETTLEMENTS + "/summary").andExpect(jsonPath("$.tabCounts.CLAWBACK").value(1));
        assertThat(settlementEvents(first.getId())).contains(SettlementEventType.CLAWBACK_UNRECOVERABLE);
    }

    @Test
    @DisplayName("정산 전 환불 · 다른 사유 환불은 차감을 만들지 않는다 — 생성이 배송 예외로 잡는다")
    void noClawbackBeforeSettlement() {
        OrderDeliveryGroup creamGroup = confirmedGroup(creamVariant, 1);
        Long refundTaskId = operatorRefund(creamGroup, "POST_CONFIRM_DEFECT", 1_000);
        assertThat(clawbackRepository.findByRefundTaskIdOrderByIdAsc(refundTaskId)).isEmpty();
        assertThat(clawbackService.register(refundTaskId, LocalDateTime.now())).isEmpty();
    }

    @Test
    @DisplayName("SettlementReader(8-5) — 공구의 정산 요약 · 지급 전 정산 · 회수 대기 차감이 있으면 「정산이 끝나지 않았다」")
    void settlementReader() {
        assertThat(settlementReader.findByGroupBuy(groupBuy.getId())).isEmpty();
        assertThat(settlementReader.hasUnsettledForCreator(creator.getId())).isFalse();

        OrderDeliveryGroup creamGroup = confirmedGroup(creamVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));
        assertThat(settlementReader.findByGroupBuy(groupBuy.getId())).get()
                .satisfies(summary -> {
                    assertThat(summary.settlementId()).isEqualTo(s.getId());
                    assertThat(summary.rewardAmount()).isEqualTo(s.getRewardAmount());
                    assertThat(summary.status()).isEqualTo(showroomz.domain.settlement.type.SettlementStatus.REVIEWING);
                });
        assertThat(settlementReader.hasUnsettledForCreator(creator.getId())).isTrue();
        assertThat(settlementReader.hasUnsettledForMarket(brand.marketId())).isTrue();

        // 지급 완료로 옮겨도 회수 대기 차감이 남으면 아직 끝나지 않았다.
        jdbc.update("UPDATE settlement SET status = 'PAID' WHERE settlement_id = ?", s.getId());
        assertThat(settlementReader.hasUnsettledForCreator(creator.getId())).isFalse();
        operatorRefund(creamGroup, "POST_CONFIRM_DEFECT", 27_200);
        assertThat(settlementReader.hasUnsettledForCreator(creator.getId())).isTrue();
        assertThat(settlementReader.hasUnsettledForMarket(brand.marketId())).isTrue();
    }

    // ------------------------------------------------------------------ 보조

    /** 같은 브랜드 · 같은 인플루언서의 다음 공구 — 세럼 1개 구매확정 → 종료 → 정산 생성. */
    private Settlement nextSettlementWithSerum() {
        groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        for (Product product : List.of(cream, serum)) {
            jdbc.update("UPDATE product SET group_buy_status = 'IN_PROGRESS' WHERE product_id = ?", product.getProductId());
        }
        creamVariant = ContractOptions.variantsOf(productVariantRepository, cream).get(0);
        serumVariant = ContractOptions.variantsOf(productVariantRepository, serum).get(0);
        setStock(serumVariant, 10);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        return generate(LocalDateTime.now().withNano(0));
    }
}
