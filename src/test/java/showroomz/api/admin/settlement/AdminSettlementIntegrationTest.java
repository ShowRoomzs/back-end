package showroomz.api.admin.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 정산 관리(07a · 07b) — 44 어드민 설계서 11-2 ST-08(2/2) · ST-11 · ST-12(2/2) · by-thread. 산식 · 생성 · 상태 전이는
 * 도메인 테스트가 소유하고 여기서는 <b>어드민 응답</b>과 M3 재분배를 본다.
 */
@DisplayName("어드민 정산 관리(07a · 07b) — 목록 · 요약 · 상세 · 명세 · 재분배 · 이슈 스레드 링크")
class AdminSettlementIntegrationTest extends SettlementTestSupport {

    private static final String SETTLEMENTS = "/v1/admin/settlements";
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 9, 30, 10, 0);

    // ------------------------------------------------------------------ ST-11 목록 · 요약

    @Test
    @DisplayName("ST-11 목록 — 탭 6 · 일정 최신순 · 확정 거래액순 · STL- 전방 일치 · 합계 행은 확정 3상태만 · 툴바 · 요약 배지")
    void listTabsAndSummary() throws Exception {
        Settlement r1 = seedSettlement("가을 립밤 공구", 3_000_000, SettlementStatus.REVIEWING);
        Settlement r2 = seedSettlement("겨울 핸드크림 공구", 1_000_000, SettlementStatus.REVIEWING);
        jdbc.update("UPDATE settlement SET review_due_at = ? WHERE settlement_id = ?",
                LocalDateTime.now().plusDays(1).withNano(0), r2.getId());
        Settlement adj = seedSettlement("봄 선크림 공구", 2_000_000, SettlementStatus.REVIEWING);
        creatorAdjustment(adj, 250_000).andExpect(status().isCreated());
        Settlement scheduled = seedSettlement("여름 미스트 공구", 4_000_000, SettlementStatus.REVIEWING);
        confirmService.confirm(scheduled.getId(), SettlementConfirmReason.AUTO, LocalDateTime.now());
        jdbc.update("UPDATE settlement SET payout_due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(10), scheduled.getId());
        Settlement paid = seedSettlement("여름 세럼 공구", 5_000_000, SettlementStatus.PAID);
        jdbc.update("UPDATE settlement SET paid_at = ? WHERE settlement_id = ?",
                LocalDateTime.now().minusDays(1).withNano(0), paid.getId());
        Settlement failed = seedSettlement("가을 토너 공구", 6_000_000, SettlementStatus.PAYOUT_FAILED);
        jdbc.update("UPDATE settlement_payout SET status = 'FAILED', failed_at = ? WHERE settlement_id = ? "
                + "AND payee = 'CREATOR'", LocalDateTime.now().minusDays(2).withNano(0), failed.getId());

        JsonNode all = json(adminGet(SETTLEMENTS).andExpect(status().isOk()));
        assertThat(all.at("/pageInfo/totalResults").asLong()).isEqualTo(6);
        assertThat(ids(all)).containsExactly(adj.getId(), scheduled.getId(), r1.getId(), r2.getId(), paid.getId(),
                failed.getId());
        assertThat(all.at("/content/0/scheduleKind").asText()).isEqualTo("AGREEMENT_DUE");
        assertThat(all.at("/content/5/status").asText()).isEqualTo("PAYOUT_FAILED");
        assertThat(all.at("/content/5/scheduleKind").asText()).isEqualTo("FAILED_AT");
        // 합계 행 — 지급 예정 · 지급 완료 · 분배 실패만(확인 중 · 조정 협의 제외).
        assertThat(all.at("/footer/count").asLong()).isEqualTo(3);
        assertThat(all.at("/footer/confirmedSalesAmount").asLong()).isEqualTo(15_000_000);

        assertThat(ids(json(adminGet(SETTLEMENTS + "?sort=SALES_DESC")))).containsExactly(failed.getId(),
                paid.getId(), scheduled.getId(), r1.getId(), adj.getId(), r2.getId());

        JsonNode reviewing = json(adminGet(SETTLEMENTS + "?tab=REVIEWING"));
        assertThat(ids(reviewing)).containsExactly(r2.getId(), r1.getId());
        assertThat(reviewing.get("footer").isNull()).isTrue();

        JsonNode adjusting = json(adminGet(SETTLEMENTS + "?tab=ADJUSTING"));
        assertThat(ids(adjusting)).containsExactly(adj.getId());
        assertThat(adjusting.at("/toolbar/heldAmount").asLong()).isEqualTo(2_000_000);
        assertThat(adjusting.at("/toolbar/earliestDeadlineAt").isMissingNode()).isFalse();

        JsonNode failedTab = json(adminGet(SETTLEMENTS + "?tab=PAYOUT_FAILED"));
        assertThat(ids(failedTab)).containsExactly(failed.getId());
        assertThat(failedTab.at("/toolbar/failedPayeeLabel").asText()).isEqualTo("인플루언서 1");
        assertThat(failedTab.at("/toolbar/elapsedDays").asLong()).isEqualTo(2);

        String number = settlement(scheduled.getId()).getSettlementNumber();
        assertThat(ids(json(adminGet(SETTLEMENTS + "?keyword=" + number.toLowerCase()))))
                .containsExactly(scheduled.getId());
        assertThat(ids(json(adminGet(SETTLEMENTS + "?keyword=토너")))).containsExactly(failed.getId());
        assertThat(json(adminGet(SETTLEMENTS + "?keyword=글로우랩")).at("/pageInfo/totalResults").asLong()).isEqualTo(6);

        adminGet(SETTLEMENTS + "?tab=EVIDENCE").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.toolbar.count").value(0));
        adminGet(SETTLEMENTS + "?tab=UNKNOWN").andExpect(status().isBadRequest());

        adminGet(SETTLEMENTS + "/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.tabCounts.ALL").value(6))
                .andExpect(jsonPath("$.tabCounts.REVIEWING").value(2))
                .andExpect(jsonPath("$.tabCounts.ADJUSTING").value(1))
                .andExpect(jsonPath("$.tabCounts.PAYOUT_FAILED").value(1))
                .andExpect(jsonPath("$.tabCounts.EVIDENCE").value(0))
                .andExpect(jsonPath("$.gnbBadge").value(1));
    }

    // ------------------------------------------------------------------ 07b 상세 · 명세 · ST-12(2/2)

    @Test
    @DisplayName("07b 상세 — 확정 전 분배 null · 명세 합계 · 이력 · 엑셀 409 / 확정 뒤 3자 분배 · 검산 · 엑셀 · 06a 링크 · 세 서피스 같은 숫자")
    void detailBeforeAndAfterConfirm() throws Exception {
        OrderDeliveryGroup group = confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement s = generate(GENERATED_AT);

        JsonNode reviewing = json(adminGet(SETTLEMENTS + "/" + s.getId()).andExpect(status().isOk()));
        assertThat(reviewing.get("status").asText()).isEqualTo("REVIEWING");
        assertThat(reviewing.at("/stage/current").asText()).isEqualTo("REVIEW");
        assertThat(reviewing.at("/stage/adjustmentSkipped").asBoolean()).isFalse();
        assertThat(reviewing.get("payouts").isNull()).isTrue();
        assertThat(reviewing.get("adjustment").isNull()).isTrue();
        assertThat(reviewing.at("/overview/orderCount").asLong()).isEqualTo(2);
        assertThat(reviewing.at("/items/total").asLong()).isEqualTo(2);
        assertThat(reviewing.at("/items/footer/itemRewardTotal").asLong()).isEqualTo(s.getRewardAmount());
        assertThat(reviewing.at("/breakdown/brand/payoutAmount").asLong()).isEqualTo(s.getBrandPayoutAmount());
        assertThat(reviewing.at("/breakdown/creator/withholding/amount").asLong()).isEqualTo(s.getWithholdingAmount());
        assertThat(reviewing.at("/fixedFee/amount").asInt()).isEqualTo(300_000);
        assertThat(reviewing.at("/actions/canDownloadStatement").asBoolean()).isFalse();
        assertThat(reviewing.at("/history/0/eventType").asText()).isEqualTo("CREATED");
        assertThat(reviewing.at("/history/0/actorLabel").asText()).isEqualTo("시스템");
        adminGet(SETTLEMENTS + "/" + s.getId() + "/statement.xlsx").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_STATEMENT_NOT_READY"));
        adminGet(SETTLEMENTS + "/" + s.getId() + "/items?size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(2));

        registerSellerAccount();
        Settlement confirmed = confirm(s);
        JsonNode detail = json(adminGet(SETTLEMENTS + "/" + s.getId()).andExpect(status().isOk()));
        assertThat(detail.at("/stage/current").asText()).isEqualTo("CONFIRMED");
        assertThat(detail.at("/stage/adjustmentSkipped").asBoolean()).isTrue();
        assertThat(detail.at("/payouts/rows")).hasSize(3);
        assertThat(detail.at("/payouts/rows/0/payee").asText()).isEqualTo("BRAND");
        assertThat(detail.at("/payouts/rows/0/accountNumber").asText()).isEqualTo("110123456789");
        assertThat(detail.at("/payouts/rows/0/accountSource").asText()).isEqualTo("CURRENT_PROFILE");
        assertThat(detail.at("/payouts/check/balanced").asBoolean()).isTrue();
        // 비사업자 · 주민번호 미등록 — 인플루언서 행 보류 사유가 레일에 뜬다.
        assertThat(detail.at("/rail/blockReasons/0/code").asText()).isEqualTo("RESIDENT_NUMBER_MISSING");
        assertThat(detail.at("/rail/payoutDueDate").asText()).isEqualTo(confirmed.getPayoutDueDate().toString());
        assertThat(detail.at("/actions/canDownloadStatement").asBoolean()).isTrue();
        assertThat(detail.at("/actions/canRedistribute").asBoolean()).isFalse();
        adminGet(SETTLEMENTS + "/" + s.getId() + "/statement.xlsx").andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString(".xlsx")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        // 06a ④ — 하위주문의 정산 링크가 07b 를 연다 · 파트너 · 스튜디오 · 어드민이 같은 숫자를 읽는다.
        JsonNode order = json(adminGet(ADMIN_ORDERS + "/" + group.getOrder().getId()).andExpect(status().isOk()));
        assertThat(order.at("/groups/0/settlement/settlementId").asLong()).isEqualTo(s.getId());
        JsonNode partner = json(sellerGet("/v1/seller/settlements/" + s.getId()).andExpect(status().isOk()));
        JsonNode studio = json(creatorGet("/v1/creator/settlements/" + s.getId()).andExpect(status().isOk()));
        assertThat(partner.at("/breakdown/brandPayoutAmount").asLong())
                .isEqualTo(detail.at("/breakdown/brand/payoutAmount").asLong());
        assertThat(studio.at("/breakdown/creatorPayoutAmount").asLong())
                .isEqualTo(detail.at("/breakdown/creator/payoutAmount").asLong());
        assertThat(studio.at("/breakdown/confirmedSalesAmount").asLong())
                .isEqualTo(detail.at("/breakdown/brand/confirmedSalesAmount").asLong());

        adminGet(SETTLEMENTS + "/999999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("by-thread — 조정 스레드의 rail + adjustment(제안 미리보기) · 조정 스레드가 아니면 404")
    void byThread() throws Exception {
        Settlement s = seedSettlement("봄 선크림 공구", 2_000_000, SettlementStatus.REVIEWING);
        long threadId = json(creatorAdjustment(s, 250_000).andExpect(status().isCreated())).get("threadId").asLong();

        JsonNode res = json(adminGet(SETTLEMENTS + "/by-thread/" + threadId).andExpect(status().isOk()));
        assertThat(res.get("settlementId").asLong()).isEqualTo(s.getId());
        assertThat(res.at("/rail/status").asText()).isEqualTo("ADJUSTING");
        assertThat(res.at("/rail/deadlineAt").isNull()).isFalse();
        assertThat(res.at("/adjustment/threadId").asLong()).isEqualTo(threadId);
        assertThat(res.at("/adjustment/proposals/0/rewardAmount").asLong()).isEqualTo(250_000);
        assertThat(res.at("/adjustment/proposals/0/preview/brandPayoutAmount").asLong())
                .isEqualTo(s.getBrandPayoutAmount() - 55_000);

        adminGet(SETTLEMENTS + "/by-thread/" + pairThreadId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ ST-08(2/2) M3 재분배

    @Test
    @DisplayName("ST-08(2/2) 인플루언서만 분배 실패 → 07a 탭 · 배지 1 → 재분배(지난 계좌) 200 → PAID · 공구 SETTLED · 이력 운영자")
    void redistributeSucceeds() throws Exception {
        Settlement s = failedOnCreator();
        Long creatorPayoutId = payout(s.getId(), SettlementPayee.CREATOR).getId();
        adminGet(SETTLEMENTS + "/summary").andExpect(jsonPath("$.gnbBadge").value(1));
        JsonNode before = json(adminGet(SETTLEMENTS + "/" + s.getId()));
        assertThat(before.at("/actions/canRedistribute").asBoolean()).isTrue();
        assertThat(before.at("/rail/failedPayout/payoutId").asLong()).isEqualTo(creatorPayoutId);
        assertThat(before.at("/rail/failedPayout/attempt").asInt()).isZero();
        assertThat(before.at("/rail/failedPayout/retryLimit").asInt()).isEqualTo(3);
        assertThat(before.at("/payouts/rows/1/accountNumber").asText()).isEqualTo("123456789012");
        assertThat(before.at("/payouts/rows/1/accountSource").asText()).isEqualTo("SNAPSHOT");

        payoutGateway.succeedFor(SettlementPayee.CREATOR);
        JsonNode after = json(redistribute(s, creatorPayoutId, "PREVIOUS").andExpect(status().isOk()));

        assertThat(after.get("status").asText()).isEqualTo("PAID");
        assertThat(after.at("/payouts/rows/1/status").asText()).isEqualTo("PAID");
        assertThat(after.at("/payouts/rows/1/attempt").asInt()).isEqualTo(1);
        assertThat(after.at("/rail/failedPayout").isNull()).isTrue();
        assertThat(after.at("/actions/canRedistribute").asBoolean()).isFalse();
        JsonNode retried = null;
        for (JsonNode h : after.get("history")) {
            if ("PAYOUT_RETRIED".equals(h.get("eventType").asText())) {
                retried = h;
            }
        }
        assertThat(retried).isNotNull();
        assertThat(retried.get("actorType").asText()).isEqualTo("ADMIN");
        assertThat(retried.get("actorLabel").asText()).isEqualTo("김운영");
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.SETTLED);
        adminGet(SETTLEMENTS + "/summary").andExpect(jsonPath("$.gnbBadge").value(0));

        redistribute(s, payout(s.getId(), SettlementPayee.BRAND).getId(), "PREVIOUS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_STATE_CHANGED"));
    }

    @Test
    @DisplayName("ST-08(2/2) 계속 실패하면 재분배 3회까지 200(분배 실패로 돌아옴) · 4회째 409 RETRY_EXCEEDED")
    void redistributeRetryLimit() throws Exception {
        Settlement s = failedOnCreator();
        Long creatorPayoutId = payout(s.getId(), SettlementPayee.CREATOR).getId();

        for (int i = 1; i <= 3; i++) {
            JsonNode res = json(redistribute(s, creatorPayoutId, "CURRENT_PROFILE").andExpect(status().isOk()));
            assertThat(res.get("status").asText()).isEqualTo("PAYOUT_FAILED");
            assertThat(res.at("/rail/failedPayout/attempt").asInt()).isEqualTo(i);
        }
        assertThat(json(adminGet(SETTLEMENTS + "/" + s.getId())).at("/actions/canRedistribute").asBoolean()).isFalse();
        redistribute(s, creatorPayoutId, "CURRENT_PROFILE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_PAYOUT_RETRY_EXCEEDED"));
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getAttempt()).isEqualTo(3);
    }

    @Test
    @DisplayName("M3 계좌 없음 — 지난 계좌 · 현재 계좌 모두 없으면 400 ACCOUNT_MISSING · 회원이 계좌를 등록하면 현재 계좌로 200 → PAID")
    void redistributeNeedsAccount() throws Exception {
        registerSellerAccount();
        registerCreatorResidentNumber();
        Settlement s = pay(confirm(generatedSettlement()), LocalDate.of(2026, 10, 12));
        assertThat(s.getStatus()).isEqualTo(SettlementStatus.PAYOUT_FAILED);
        Long creatorPayoutId = payout(s.getId(), SettlementPayee.CREATOR).getId();
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getFailCode()).isEqualTo("ACCOUNT_MISSING");

        redistribute(s, creatorPayoutId, "PREVIOUS").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ACCOUNT_MISSING"));
        redistribute(s, creatorPayoutId, "CURRENT_PROFILE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ACCOUNT_MISSING"));

        registerCreatorAccount();
        redistribute(s, creatorPayoutId, "CURRENT_PROFILE").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payouts.rows[1].accountHolder").value("김지민"));
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.PAID);
    }

    // ------------------------------------------------------------------ 보조

    private Settlement generatedSettlement() {
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        return generate(GENERATED_AT);
    }

    /** 3자 분배에서 인플루언서 행만 PG 가 거절 — 정산 PAYOUT_FAILED · 브랜드 PAID. */
    private Settlement failedOnCreator() {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        Settlement s = confirm(generatedSettlement());
        payoutGateway.failFor(SettlementPayee.CREATOR, "BANK_REJECTED", "예금주 불일치");
        Settlement failed = pay(s, payout(s.getId(), SettlementPayee.BRAND).getDueDate());
        assertThat(failed.getStatus()).isEqualTo(SettlementStatus.PAYOUT_FAILED);
        return failed;
    }

    private ResultActions redistribute(Settlement s, Long payoutId, String source) throws Exception {
        return adminPost(SETTLEMENTS + "/" + s.getId() + "/payouts/" + payoutId + "/redistribute",
                Map.of("accountSource", source));
    }

    private ResultActions creatorAdjustment(Settlement s, long amount) throws Exception {
        return mockMvc.perform(post("/v1/creator/settlements/" + s.getId() + "/adjustment")
                .header(HttpHeaders.AUTHORIZATION, creatorToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("rewardAmount", amount, "reason", "추가 콘텐츠 제작"))));
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(row -> ids.add(row.get("settlementId").asLong()));
        return ids;
    }
}
