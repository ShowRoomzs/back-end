package showroomz.api.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.brand.entity.BrandWithdrawalRequest;
import showroomz.domain.member.brand.repository.BrandWithdrawalRequestRepository;
import showroomz.domain.member.brand.service.BrandWithdrawalService;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 52 테스트 시나리오 15절 — 어드민 브랜드 상세의 시안 상태(A1 · A2 · A3 · A4)를 끝까지 도는 흐름. 단계마다 상세를 다시 불러 판정표(1-2)의
 * 필드를 본다.
 */
@DisplayName("어드민 브랜드 상세 E2E — 정지 한 바퀴 · 탈퇴 한 바퀴 · 승인 직전 변동(52 시나리오 E2E)")
class AdminBrandScenarioIntegrationTest extends SettlementTestSupport {

    private static final String BRANDS = "/v1/admin/brands";
    private static final String PARTNER_WITHDRAWALS = "/v1/seller/basic-info/withdrawal-requests";

    @Autowired private OperatorChannelService operatorChannels;
    @Autowired private ChangeRequestRepository memberChangeRequests;
    @Autowired private BrandWithdrawalService withdrawalService;
    @Autowired private BrandWithdrawalRequestRepository withdrawalRepository;

    @Test
    @DisplayName("E2E-1 정지 한 바퀴 — A1 → 정지(정산 정보 불일치) A3 · 지급 보류 → 소명 · 계좌 변경 요청 → 해제 A1 → 다음 영업일 지급 완료")
    void suspensionRoundTrip() throws Exception {
        registerSellerAccount();
        Settlement s = confirm(seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        brandDetail(brand.marketId()).andExpect(jsonPath("$.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.rail.currentStatus.actions.canSuspend").value(true));

        adminPost(base(brand) + "/suspend", Map.of("reason", "SETTLEMENT_INFO_MISMATCH", "reasonDetail", "예금주와 상호가 다릅니다."))
                .andExpect(status().isOk());
        brandDetail(brand.marketId()).andExpect(jsonPath("$.memberStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.rail.currentStatus.sanction.payoutHeld").value(true))
                .andExpect(jsonPath("$.operations.payoutHeld").value(true))
                .andExpect(jsonPath("$.rail.histories.items[0].type").value("SUSPENDED"))
                .andExpect(jsonPath("$.rail.histories.items[0].tone").value("DANGER"));
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        MessageThread channel = inTransaction(() -> operatorChannels.requireMarketChannel(brand.market()));
        inTransaction(() -> messageThreadService.sendMessage(messageThreadRepository.findById(channel.getId()).orElseThrow(),
                ParticipantType.SELLER, brand.seller().getId(), UUID.randomUUID().toString(),
                "예금주를 법인명으로 정정한 통장 사본을 올립니다.", List.of()));
        memberChangeRequests.save(ChangeRequest.create("CHG-2026-0001", brand.market(), ChangeRequestType.SETTLEMENT_ACCOUNT,
                "예금주 정정", "김담당", "https://cdn.showroomz.test/bankbook.pdf", "bankbook.pdf", 1_000L));
        JsonNode suspended = json(brandDetail(brand.marketId()));
        assertThat(chipKeys(suspended)).contains("OPERATOR_THREAD_UNREAD");
        assertThat(suspended.at("/settlementInfo/pendingChangeRequests")).hasSize(1);
        assertThat(types(suspended.at("/rail/histories/items"))).startsWith("CHANGE_REQUEST_RECEIVED", "SUSPENDED");

        adminPost(base(brand) + "/release", Map.of("note", "통장 사본 확인")).andExpect(status().isOk());
        brandDetail(brand.marketId()).andExpect(jsonPath("$.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.rail.currentStatus.sanction").doesNotExist())
                .andExpect(jsonPath("$.rail.histories.items[0].type").value("SUSPENSION_RELEASED"));
        LocalDate due = businessCalendar.addBusinessDays(LocalDate.now(), 1);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getDueDate()).isEqualTo(due);

        pay(s, due);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(operatorCards(brand.marketId())).containsExactly(MessageCardType.ACCOUNT_SUSPENDED,
                MessageCardType.ACCOUNT_SUSPENSION_RELEASED);
    }

    @Test
    @DisplayName("E2E-2 탈퇴 한 바퀴 — A1 → 신청 A2 → 심사 중 정지 A3+심사 → 반려 → 재신청 → 승인 A4 · 이력 순서")
    void withdrawalRoundTrip() throws Exception {
        BrandFixture.Brand owner = fixture.createBrand("e2e-wd@showroomz.test", "한바퀴브랜드");
        String token = sellerToken(owner.seller());
        brandDetail(owner.marketId()).andExpect(jsonPath("$.withdrawalPending").value(false));

        long first = json(partner(token, Map.of("reason", "OTHER_CHANNEL_FOCUS", "message", "자사몰 운영에 집중합니다."))
                .andExpect(status().isCreated())).path("requestId").asLong();
        brandDetail(owner.marketId()).andExpect(jsonPath("$.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.withdrawalPending").value(true))
                .andExpect(jsonPath("$.rail.withdrawalReview.requestId").value(first))
                .andExpect(jsonPath("$.rail.currentStatus.actions.canReviewWithdrawal").value(true));

        adminPost(base(owner) + "/suspend", Map.of("reason", "CONSUMER_RESPONSE_NEGLECT")).andExpect(status().isOk());
        brandDetail(owner.marketId()).andExpect(jsonPath("$.memberStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.rail.withdrawalReview.requestId").value(first))
                .andExpect(jsonPath("$.rail.currentStatus.actions.canSuspend").value(false))
                .andExpect(jsonPath("$.rail.currentStatus.actions.canRelease").value(true))
                .andExpect(jsonPath("$.rail.currentStatus.actions.canReviewWithdrawal").value(true));

        adminPost(base(owner) + "/withdrawal-requests/" + first + "/reject",
                Map.of("reason", "UNSETTLED", "note", "정산 지급 후 다시 신청해 주세요.")).andExpect(status().isOk());
        brandDetail(owner.marketId()).andExpect(jsonPath("$.memberStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.rail.withdrawalReview").doesNotExist());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/v1/seller/basic-info/account").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.withdrawal.lastRejection.requestId").value(first))
                .andExpect(jsonPath("$.withdrawal.lastRejection.reasonCode").value("UNSETTLED"));

        long second = json(partner(token, null).andExpect(status().isCreated())).path("requestId").asLong();
        adminPost(base(owner) + "/withdrawal-requests/" + second + "/approve", Map.of()).andExpect(status().isOk());

        JsonNode withdrawn = json(brandDetail(owner.marketId()));
        assertThat(withdrawn.path("memberStatus").asText()).isEqualTo("WITHDRAWN");
        assertThat(withdrawn.path("purged").asBoolean()).isTrue();
        assertThat(withdrawn.at("/rail/currentStatus/withdrawal/approvedByName").asText()).isEqualTo("김운영");
        assertThat(withdrawn.at("/rail/pendingChips")).isEmpty();
        List<String> history = types(json(adminGet(base(owner) + "/histories")).path("content"));
        assertThat(history).containsSubsequence("WITHDRAWAL_APPROVED", "SUSPENSION_RELEASED", "WITHDRAWAL_REQUESTED",
                "WITHDRAWAL_REJECTED", "SUSPENDED", "WITHDRAWAL_REQUESTED", "SIGNED_UP");
        assertThat(withdrawalRepository.findAll()).extracting(BrandWithdrawalRequest::getId).containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("E2E-3 승인 직전 변동 — 공구 종료 뒤 남은 결제 주문이 승인을 막는다(409 UNCLOSED_ORDERS) → 반려 → 배송 · 구매확정 → 재신청 → 승인")
    void lateOrderBlocksApproval() throws Exception {
        OrderDeliveryGroup order = paidGroup();
        endGroupBuy();
        Long requestId = withdrawalRepository.save(BrandWithdrawalRequest.open(brand.marketId(), null, null, "김담당",
                LocalDateTime.now())).getId();

        JsonNode unmet = json(adminPost(base(brand) + "/withdrawal-requests/" + requestId + "/approve", Map.of())
                .andExpect(status().isConflict()));
        List<String> keys = new ArrayList<>();
        unmet.path("unmet").fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("UNCLOSED_ORDERS");
        assertThat(unmet.at("/unmet/UNCLOSED_ORDERS").asLong()).isEqualTo(1);

        adminPost(base(brand) + "/withdrawal-requests/" + requestId + "/reject", Map.of("reason", "UNSHIPPED_ORDERS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus.memberStatus").value("ACTIVE"));

        OrderDeliveryGroup delivered = deliveredFrom(order);
        LocalDateTime now = LocalDateTime.now();
        assertThat(fulfillmentService.confirmPurchase(delivered.getId(), now, now.minusDays(7))).isTrue();

        Long again = withdrawalService.request(brand.marketId(), null, null, LocalDateTime.now()).getId();
        adminPost(base(brand) + "/withdrawal-requests/" + again + "/approve", Map.of()).andExpect(status().isOk());
        assertThat(MarketStatus.valueOf(jdbc.queryForObject("SELECT status FROM market WHERE market_id = ?", String.class,
                brand.marketId()))).isEqualTo(MarketStatus.WITHDRAWN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE market_id = ? AND display_status <> 'HIDDEN'",
                Integer.class, brand.marketId())).isZero();
    }

    // ------------------------------------------------------------------ 보조

    private String base(BrandFixture.Brand owner) {
        return BRANDS + "/" + owner.marketId();
    }

    private ResultActions brandDetail(Long marketId) throws Exception {
        return adminGet(BRANDS + "/" + marketId).andExpect(status().isOk());
    }

    private ResultActions partner(String token, Object body) throws Exception {
        var request = post(PARTNER_WITHDRAWALS).header(HttpHeaders.AUTHORIZATION, token);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        }
        return mockMvc.perform(request);
    }

    private static List<String> types(JsonNode items) {
        List<String> types = new ArrayList<>();
        items.forEach(item -> types.add(item.path("type").asText()));
        return types;
    }

    private static List<String> chipKeys(JsonNode detail) {
        List<String> keys = new ArrayList<>();
        detail.at("/rail/pendingChips").forEach(chip -> keys.add(chip.path("key").asText()));
        return keys;
    }

    private List<MessageCardType> operatorCards(Long marketId) {
        Long threadId = jdbc.queryForObject("SELECT t.thread_id FROM message_thread t JOIN connection c "
                + "ON c.connection_id = t.connection_id WHERE c.type = 'OPERATOR_MARKET' AND c.market_id = ?",
                Long.class, marketId);
        return messagesOf(threadId).stream().map(m -> m.getCardType()).filter(Objects::nonNull).toList();
    }
}
