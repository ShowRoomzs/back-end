package showroomz.api.creator.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쇼룸 스튜디오 정산 관리(12) 조회 — 44 스튜디오 설계서 7-2 C-01 · C-02(세금계산서 항목 제외) · C-03 · C-09 · C-08(지급 부분).
 * 산식 · 상태 전이는 정산 도메인 테스트가 소유하고 여기서는 <b>스튜디오 응답</b>만 본다.
 */
@DisplayName("쇼룸 스튜디오 정산 관리(12) — 목록 · 요약 · 상세 · 명세")
class CreatorSettlementIntegrationTest extends SettlementTestSupport {

    private static final String SETTLEMENTS = "/v1/creator/settlements";

    private Settlement reviewing() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        return generate(LocalDateTime.now().withNano(0));
    }

    private String otherCreatorToken() {
        Creator other = createCreator("다른_쇼룸", "other-creator");
        return bearerToken(other.getUser().getUsername(), RoleType.CREATOR, other.getUser().getId());
    }

    @Test
    @DisplayName("C-01 목록 — 남의 정산 404 · PAYOUT_FAILED → PAID · 「상품별」 리워드율 · 사업자 내 행 보류면 payoutDate = null")
    void list() throws Exception {
        makeCreatorBusiness();
        registerSellerAccount();
        Settlement mine = confirm(reviewing());
        pay(mine, mine.getPayoutDueDate());   // 브랜드 먼저 지급 · 내 행은 보류
        Settlement failed = seedSettlement("두 번째 앵콜 공구", 10_000, SettlementStatus.PAYOUT_FAILED);

        JsonNode page = json(creatorGet(SETTLEMENTS).andExpect(status().isOk()));
        assertThat(page.at("/content")).hasSize(2);
        JsonNode seeded = page.at("/content/1");
        assertThat(seeded.at("/settlementId").asLong()).isEqualTo(failed.getId());
        assertThat(seeded.at("/status").asText()).isEqualTo("PAID");
        JsonNode real = page.at("/content/0");
        assertThat(real.at("/rewardRateLabel").asText()).isEqualTo("상품별");
        assertThat(real.at("/brandName").asText()).isEqualTo("글로우랩");
        assertThat(real.at("/payoutDate").isNull()).isTrue();
        assertThat(real.at("/creatorVatAmount").asLong()).isEqualTo(mine.getCreatorVatAmount());

        creatorGet(SETTLEMENTS + "?keyword=글로우랩").andExpect(jsonPath("$.content.length()").value(2));
        creatorGet(SETTLEMENTS + "?status=PAYOUT_FAILED").andExpect(status().isBadRequest());
        String other = otherCreatorToken();
        mockMvc.perform(get(SETTLEMENTS + "/" + mine.getId()).header("Authorization", other))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("C-02 요약 — 빈 상태 amount = null · attentionCount = 요청 가능 창이 열린 정산 확인 중 · 조정 협의는 세지 않음")
    void summaryKpi() throws Exception {
        Settlement s = reviewing();

        creatorGet(SETTLEMENTS + "/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaid.amount").doesNotExist())
                .andExpect(jsonPath("$.totalPaid.count").value(0))
                .andExpect(jsonPath("$.payoutScheduled.amount").doesNotExist())
                .andExpect(jsonPath("$.reviewing.count").value(1))
                .andExpect(jsonPath("$.reviewing.nearestDueAt").exists())
                .andExpect(jsonPath("$.attentionCount").value(1));

        jdbc.update("UPDATE settlement SET status = 'ADJUSTING' WHERE settlement_id = ?", s.getId());
        creatorGet(SETTLEMENTS + "/summary")
                .andExpect(jsonPath("$.attentionCount").value(0))
                .andExpect(jsonPath("$.adjusting.count").value(1))
                .andExpect(jsonPath("$.statusCounts.ADJUSTING").value(1));
    }

    @Test
    @DisplayName("C-03 상세 REVIEWING — 요청 가능 · 상한 · 분배 행 「확인 기간 후 지급」 · 세금계산서 null · 지급 예정일 null · 마감 뒤 요청 불가")
    void reviewingDetail() throws Exception {
        Settlement s = reviewing();
        long upper = settlementCalculator.maxRewardAmount(s);

        JsonNode detail = json(creatorGet(SETTLEMENTS + "/" + s.getId()).andExpect(status().isOk()));
        assertThat(detail.at("/settlement/status").asText()).isEqualTo("REVIEWING");
        assertThat(detail.at("/review/canRequestAdjustment").asBoolean()).isTrue();
        assertThat(detail.at("/review/maxRewardAmount").asLong()).isEqualTo(upper);
        assertThat(upper % 10).isZero();
        assertThat(detail.at("/timeline/payoutDueDate").isNull()).isTrue();
        assertThat(detail.at("/payouts/shownAfterConfirm").asBoolean()).isTrue();
        assertThat(detail.at("/payouts/rows/0/payee").asText()).isEqualTo("CREATOR");
        assertThat(detail.at("/payouts/rows/0/statusLabel").asText()).isEqualTo("확인 기간 후 지급");
        assertThat(detail.at("/taxInvoice").isNull()).isTrue();
        assertThat(detail.at("/withholding/receiptLabel").asText()).isEqualTo("징수 예정액");
        assertThat(detail.at("/breakdown/rewardRateLabel").asText()).isEqualTo("상품별");
        assertThat(detail.at("/breakdown/rewardRates")).hasSize(2);
        assertThat(detail.at("/breakdown/rewardAfterClawback").asLong()).isEqualTo(s.getRewardAmount());
        assertThat(detail.at("/breakdown/pgFee").isMissingNode()).isTrue();
        assertThat(detail.at("/items/downloadAvailable").asBoolean()).isFalse();
        assertThat(detail.at("/items/preview/0/consumerNameMasked").isMissingNode()).isTrue();

        jdbc.update("UPDATE settlement SET review_due_at = ? WHERE settlement_id = ?",
                LocalDateTime.now().minusMinutes(1), s.getId());
        creatorGet(SETTLEMENTS + "/" + s.getId()).andExpect(jsonPath("$.review.canRequestAdjustment").value(false));
    }

    @Test
    @DisplayName("C-08(지급) 인플루언서 몫만 FAILED — 정산 라벨 PAID · 내 행 「지급 확인 중」 · payoutDate = null · 브랜드 행 지급 완료")
    void creatorPayoutFailed() throws Exception {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        Settlement s = confirm(reviewing());
        payoutGateway.failFor(SettlementPayee.CREATOR, "BANK_REJECTED", "예금주 불일치");
        pay(s, s.getPayoutDueDate());

        creatorGet(SETTLEMENTS).andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.content[0].payoutDate").doesNotExist());
        JsonNode detail = json(creatorGet(SETTLEMENTS + "/" + s.getId()));
        assertThat(detail.at("/settlement/status").asText()).isEqualTo("PAID");
        assertThat(detail.at("/payouts/rows/0/status").asText()).isEqualTo("FAILED");
        assertThat(detail.at("/payouts/rows/0/statusLabel").asText()).isEqualTo("지급 확인 중");
        assertThat(detail.at("/payouts/rows/1/status").asText()).isEqualTo("PAID");
        assertThat(detail.at("/payouts/rows/1/statusLabel").asText()).endsWith("지급 완료");
        assertThat(detail.at("/payment/accountNumberMasked").asText()).isEqualTo("******789012");
        assertThat(detail.at("/payment/accountHolder").asText()).isEqualTo("김지민");
        assertThat(detail.at("/timeline/payoutDueNote").asText()).startsWith("자동 확정 ");
    }

    @Test
    @DisplayName("C-09 명세 — 페이지 · 확정 전 다운로드 409 · xlsx 에 소비자 열 없음")
    void items() throws Exception {
        Settlement s = reviewing();

        creatorGet(SETTLEMENTS + "/" + s.getId() + "/items").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].consumerNameMasked").doesNotExist())
                .andExpect(jsonPath("$.content[0].subOrderNumber").doesNotExist());
        creatorGet(SETTLEMENTS + "/" + s.getId() + "/items/download").andExpect(status().isConflict());

        confirm(s);
        byte[] xlsx = creatorGet(SETTLEMENTS + "/" + s.getId() + "/items/download").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        List<List<String>> rows = readSheet(xlsx);
        assertThat(rows.get(0)).contains("주문번호", "상품", "리워드").doesNotContain("주문자", "하위주문번호", "정산번호");
        assertThat(rows.size()).isGreaterThanOrEqualTo(2 + 1 + 3);
    }
}
