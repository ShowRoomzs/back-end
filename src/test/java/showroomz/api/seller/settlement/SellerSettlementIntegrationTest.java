package showroomz.api.seller.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 정산 관리(13) 조회 — 44 파트너 설계서 7절 P1 · P2 · P3 · P4(증빙 제외) · P7 · P8 · P10. 산식 · 상태 전이는
 * {@code SettlementCalculatorTest} · {@code SettlementLifecycleIntegrationTest}가 소유하고 여기서는 <b>파트너 응답</b>만 본다.
 */
@DisplayName("파트너센터 정산 관리(13) — 목록 · 요약 · 상세 · 명세")
class SellerSettlementIntegrationTest extends SettlementTestSupport {

    private static final String SETTLEMENTS = "/v1/seller/settlements";

    /** 크림 1 · 세럼 1 구매확정 → 공구 종료 → 정산 생성(지금 · 확인 기간 열림). */
    private Settlement reviewing() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        return generate(LocalDateTime.now().withNano(0));
    }

    private String otherBrandToken() {
        BrandFixture.Brand other = fixture.createBrand("other-brand@showroomz.test", "다른브랜드");
        return sellerToken(other.seller());
    }

    @Test
    @DisplayName("P1 목록 — 정렬 2종 · 분배 실패는 PAID 로 접힘 · PAYOUT_FAILED 필터 400 · 키워드 · 일정 열 · 남의 브랜드 404")
    void list() throws Exception {
        Settlement mine = reviewing();
        // 하루 전에 생긴 더 큰 정산 — 생성일순과 수취액순이 갈린다.
        Settlement failed = seedSettlement("두 번째 앵콜 공구", 9_000_000, SettlementStatus.PAYOUT_FAILED);

        sellerGet(SETTLEMENTS).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].settlementId").value(mine.getId()))
                .andExpect(jsonPath("$.content[0].schedule.kind").value("REVIEW_DUE"))
                .andExpect(jsonPath("$.content[0].schedule.date").value(mine.getReviewDueAt().toLocalDate().toString()))
                .andExpect(jsonPath("$.content[0].feeAmount").value(mine.getPgFeeAmount() + mine.getPlatformFeeAmount()))
                .andExpect(jsonPath("$.content[0].influencer.showroomName").value("글로우_지민"))
                .andExpect(jsonPath("$.content[1].settlementId").value(failed.getId()))
                .andExpect(jsonPath("$.content[1].status").value("PAID"))
                .andExpect(jsonPath("$.content[1].statusLabel").value("지급 완료"));
        sellerGet(SETTLEMENTS + "?sort=PAYOUT_DESC")
                .andExpect(jsonPath("$.content[0].settlementId").value(failed.getId()));
        sellerGet(SETTLEMENTS + "?status=PAID")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].settlementId").value(failed.getId()));
        sellerGet(SETTLEMENTS + "?status=PAYOUT_FAILED").andExpect(status().isBadRequest());
        sellerGet(SETTLEMENTS + "?keyword=두 번째")
                .andExpect(jsonPath("$.content.length()").value(1));

        String other = otherBrandToken();
        sellerGet(SETTLEMENTS, other).andExpect(jsonPath("$.content.length()").value(0));
        sellerGet(SETTLEMENTS + "/" + mine.getId(), other).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("P2 요약 — 빈 값 amount = null · attentionCount 는 요청 가능 창이 열린 정산 확인 중만 · 조정 협의는 세지 않음")
    void summaryKpi() throws Exception {
        Settlement mine = reviewing();
        seedSettlement("두 번째 앵콜 공구", 9_000_000, SettlementStatus.PAYOUT_FAILED);

        sellerGet(SETTLEMENTS + "/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.paid.amount").doesNotExist())
                .andExpect(jsonPath("$.paid.count").value(0))
                .andExpect(jsonPath("$.reviewingCount").value(1))
                .andExpect(jsonPath("$.attentionCount").value(1))
                .andExpect(jsonPath("$.statusCounts.REVIEWING").value(1))
                .andExpect(jsonPath("$.statusCounts.PAID").value(1))
                .andExpect(jsonPath("$.reviewingDueAt").exists());

        jdbc.update("UPDATE settlement SET status = 'ADJUSTING' WHERE settlement_id = ?", mine.getId());
        sellerGet(SETTLEMENTS + "/summary")
                .andExpect(jsonPath("$.attentionCount").value(0))
                .andExpect(jsonPath("$.adjustingCount").value(1));

        sellerGet(SETTLEMENTS + "/summary", otherBrandToken())
                .andExpect(jsonPath("$.paid.amount").doesNotExist())
                .andExpect(jsonPath("$.scheduled.amount").doesNotExist())
                .andExpect(jsonPath("$.attentionCount").value(0));
    }

    @Test
    @DisplayName("P3 상세 REVIEWING — 분배 · 지급 · 증빙 블록 null · 조정 요청 가능 · 마감 뒤 불가 · 명세 다운로드 409")
    void reviewingDetail() throws Exception {
        Settlement s = reviewing();

        sellerGet(SETTLEMENTS + "/" + s.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWING"))
                .andExpect(jsonPath("$.statusTone").value("INFO"))
                .andExpect(jsonPath("$.payouts").doesNotExist())
                .andExpect(jsonPath("$.payment").doesNotExist())
                .andExpect(jsonPath("$.taxDocuments").doesNotExist())
                .andExpect(jsonPath("$.adjustment").doesNotExist())
                .andExpect(jsonPath("$.dates.payoutBasis").doesNotExist())
                .andExpect(jsonPath("$.breakdown.confirmedSalesAmount").value(CREAM_PRICE + SERUM_PRICE))
                .andExpect(jsonPath("$.breakdown.return.count").value(0))
                .andExpect(jsonPath("$.breakdown.brandPayoutBeforeClawback").value(s.getBrandPayoutAmount()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.itemTotalCount").value(2))
                .andExpect(jsonPath("$.actions.canRequestAdjustment").value(true))
                .andExpect(jsonPath("$.actions.canDownloadStatement").value(false));
        sellerGet(SETTLEMENTS + "/" + s.getId() + "/items/download").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_STATEMENT_NOT_READY"));

        jdbc.update("UPDATE settlement SET review_due_at = ? WHERE settlement_id = ?",
                LocalDateTime.now().minusMinutes(1), s.getId());
        sellerGet(SETTLEMENTS + "/" + s.getId()).andExpect(jsonPath("$.actions.canRequestAdjustment").value(false));
    }

    @Test
    @DisplayName("P4 상세 PAYOUT_SCHEDULED — 정산 근거 문장 · 브랜드 행 첫 줄 · 사업자 인플루언서 행 「발행 확인 후 지급」 · 현재 계좌 마스킹")
    void scheduledDetail() throws Exception {
        makeCreatorBusiness();
        registerSellerAccount();
        Settlement s = confirm(reviewing());

        JsonNode detail = json(sellerGet(SETTLEMENTS + "/" + s.getId()).andExpect(status().isOk()));
        assertThat(detail.at("/status").asText()).isEqualTo("PAYOUT_SCHEDULED");
        assertThat(detail.at("/dates/payoutBasis").asText()).startsWith("자동 확정 ");
        assertThat(detail.at("/dates/payoutDueDate").asText()).isEqualTo(s.getPayoutDueDate().toString());
        assertThat(detail.at("/payouts/0/payee").asText()).isEqualTo("BRAND");
        assertThat(detail.at("/payouts/0/label").asText()).isEqualTo("우리");
        assertThat(detail.at("/payouts/1/payee").asText()).isEqualTo("CREATOR");
        assertThat(detail.at("/payouts/1/status").asText()).isEqualTo("BLOCKED");
        assertThat(detail.at("/payouts/1/statusLabel").asText()).isEqualTo("발행 확인 후 지급");
        assertThat(detail.at("/payouts/1/note").asText()).startsWith("리워드 ").contains("+ 부가세");
        assertThat(detail.at("/payouts/2/note").asText()).isEqualTo("부가세는 인플루언서에게 지급");
        assertThat(detail.at("/payment/bankName").asText()).isEqualTo("신한은행");
        assertThat(detail.at("/payment/accountMasked").asText()).isEqualTo("******456789");
        assertThat(detail.at("/payment/pgReference").isNull()).isTrue();
        assertThat(detail.at("/taxDocuments").isArray()).isTrue();
        assertThat(detail.at("/influencerTax/businessType").asText()).isEqualTo("BUSINESS");
        assertThat(detail.at("/actions/canDownloadStatement").asBoolean()).isTrue();
        assertThat(detail.at("/actions/canRequestAdjustment").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("P7 브랜드 몫만 FAILED — 목록 라벨 PAID · 상세 브랜드 행 FAILED 「지급 확인 중」 · 인플루언서 행 지급 완료")
    void brandPayoutFailed() throws Exception {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        Settlement s = confirm(reviewing());
        payoutGateway.failFor(SettlementPayee.BRAND, "BANK_REJECTED", "계좌 오류");
        Settlement failed = pay(s, s.getPayoutDueDate());
        assertThat(failed.getStatus()).isEqualTo(SettlementStatus.PAYOUT_FAILED);

        sellerGet(SETTLEMENTS).andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.content[0].schedule.kind").value("NONE"));
        sellerGet(SETTLEMENTS + "/" + s.getId())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payouts[0].status").value("FAILED"))
                .andExpect(jsonPath("$.payouts[0].statusLabel").value("지급 확인 중"))
                .andExpect(jsonPath("$.payouts[1].status").value("PAID"))
                .andExpect(jsonPath("$.payment.pgReference").doesNotExist())
                .andExpect(jsonPath("$.payment.accountMasked").value("******456789"));
    }

    @Test
    @DisplayName("P8 · P10 명세 페이지 · 부분 반품 수량 병기 · 확정 후 xlsx 합계 행 · 반품 · 교환 배송비(소비자 · 브랜드 부담)")
    void itemsAndClaimShipping() throws Exception {
        OrderDeliveryGroup partial = deliveredFrom(purchase(creamVariant, 2).group(), LocalDateTime.now().minusHours(2));
        passClaim(returnClaim(partial, itemsOf(partial).get(0), 1, ClaimReason.CHANGE_OF_MIND));
        confirmLater(partial);
        OrderDeliveryGroup defect = deliveredFrom(purchase(serumVariant, 1).group(), LocalDateTime.now().minusHours(2));
        passClaim(returnClaim(defect, itemsOf(defect).get(0), 1, ClaimReason.DAMAGED_OR_DEFECTIVE));
        confirmLater(defect);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));

        sellerGet(SETTLEMENTS + "/" + s.getId() + "/items?page=1&size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(2));
        JsonNode items = json(sellerGet(SETTLEMENTS + "/" + s.getId() + "/items"));
        JsonNode partialRow = items.at("/content").get(0).at("/productName").asText().equals(cream.getName())
                ? items.at("/content/0") : items.at("/content/1");
        assertThat(partialRow.at("/status").asText()).isEqualTo("PARTIAL_RETURNED");
        assertThat(partialRow.at("/quantity").asInt()).isEqualTo(2);
        assertThat(partialRow.at("/settledQuantity").asInt()).isEqualTo(1);
        assertThat(partialRow.at("/consumerNameMasked").asText()).contains("*");

        Long consumerDeduction = jdbc.queryForObject("SELECT return_deduction FROM order_claim_collection "
                + "WHERE delivery_group_id = ?", Long.class, partial.getId());
        Integer returnFee = jdbc.queryForObject("SELECT return_fee FROM market WHERE market_id = ?", Integer.class,
                brand.marketId());
        sellerGet(SETTLEMENTS + "/" + s.getId())
                .andExpect(jsonPath("$.claimShipping.consumer.count").value(1))
                .andExpect(jsonPath("$.claimShipping.consumer.amount").value(consumerDeduction))
                .andExpect(jsonPath("$.claimShipping.brand.count").value(1))
                .andExpect(jsonPath("$.claimShipping.brand.amount").value(returnFee))
                .andExpect(jsonPath("$.breakdown.return.count").value(2));

        confirm(s);
        byte[] xlsx = sellerGet(SETTLEMENTS + "/" + s.getId() + "/items/download").andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andReturn().getResponse().getContentAsByteArray();
        List<List<String>> rows = readSheet(xlsx);
        assertThat(rows.get(0)).contains("정산번호", "주문자", "정산 반영액", "리워드");
        assertThat(rows).anySatisfy(row -> assertThat(row).startsWith("확정 거래액",
                String.valueOf(s.getConfirmedSalesAmount())));
        assertThat(rows).anySatisfy(row -> assertThat(row).startsWith("합의 후 리워드",
                String.valueOf(s.getRewardAmount())));
    }
}
