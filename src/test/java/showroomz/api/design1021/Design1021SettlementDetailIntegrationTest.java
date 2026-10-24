package showroomz.api.design1021;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.service.BusinessDayCalculator;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 6절 — 재계산 · 클로백 · 조정 철회 · 분해 줄의 P6 기본 흐름 밖: 확인 기간 재시작 값 · 양측 알림 · 항목 재생성 · 확정 뒤는 클로백 ·
 * 철회의 지급일 · 카드 · 상대 알림 · 재요청 · 어드민 이슈 탭 · 파트너 · 어드민 정산 화면의 분해 줄.
 */
@DisplayName("1021 6절 정산 상세 — 재계산 재시작 · 알림 · 확정 뒤 클로백 · 철회 지급일 · 카드 · 알림 · 이슈 탭 · 분해 줄 API")
class Design1021SettlementDetailIntegrationTest extends SettlementTestSupport {

    @Autowired private SettlementClawbackRepository clawbackRepository;
    @Autowired private SettlementProperties settlementProperties;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private BusinessDayCalculator businessDayCalculator;
    @Autowired private BusinessCalendar businessCalendar;

    @AfterEach
    void disableRecalculation() {
        settlementProperties.getClawback().setRecalculateStatuses(EnumSet.noneOf(SettlementStatus.class));
    }

    // ------------------------------------------------------------------ 6-3 재계산

    @Test
    @DisplayName("재계산 — 확인 기간이 지금부터 다시 3영업일 · 항목 반영액 합 = 확정 거래액 · 수취자 행 금액 갱신 · 양측 알림 · 이력에 환불 번호")
    void recalculationRestartsReviewWindow() throws Exception {
        settlementProperties.getClawback().setRecalculateStatuses(EnumSet.of(SettlementStatus.REVIEWING));
        OrderDeliveryGroup cream = confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().minusDays(6).withNano(0));
        Settlement before = settlement(s.getId());

        Long taskId = operatorRefund(cream, "POST_CONFIRM_DEFECT", CREAM_PRICE);

        Settlement after = settlement(s.getId());
        LocalDateTime expectedDue = businessDayCalculator.dueAt(LocalDateTime.now(), 3);
        assertThat(after.getReviewDueAt().toLocalDate()).isEqualTo(expectedDue.toLocalDate());
        assertThat(after.getReviewDueAt()).isAfter(before.getReviewDueAt());
        assertThat(after.getConfirmedSalesAmount()).isEqualTo(SERUM_PRICE);
        assertThat(itemsOf(after).stream().mapToLong(SettlementItem::getSettledAmount).sum())
                .isEqualTo(after.getConfirmedSalesAmount());
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getAmount()).isEqualTo(after.getBrandPayoutAmount());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getAmount()).isEqualTo(after.getCreatorPayoutAmount());
        assertThat(payout(s.getId(), SettlementPayee.PLATFORM).getAmount()).isEqualTo(after.getPlatformShareAmount());
        assertThat(clawbackRepository.findByRefundTaskIdOrderByIdAsc(taskId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT detail FROM settlement_history WHERE settlement_id = ? "
                + "AND event_type = 'RECALCULATED'", String.class, s.getId())).contains("RFD-" + taskId);

        List<Notification> notices = notices(NotificationType.SETTLEMENT_RECALCULATED);
        assertThat(notices).extracting(Notification::getRecipientType)
                .containsExactlyInAnyOrder(NotificationRecipientType.SELLER, NotificationRecipientType.CREATOR);
    }

    @Test
    @DisplayName("재계산 대상이어도 확정 뒤(지급 예정)는 클로백 — 재계산 이력 없음 · 정산 금액 그대로")
    void confirmedSettlementStaysClawback() {
        settlementProperties.getClawback().setRecalculateStatuses(EnumSet.of(SettlementStatus.REVIEWING));
        OrderDeliveryGroup cream = confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement confirmed = confirm(generate(LocalDateTime.now().withNano(0)));
        long brandBefore = settlement(confirmed.getId()).getBrandPayoutAmount();

        Long taskId = operatorRefund(cream, "POST_CONFIRM_DEFECT", CREAM_PRICE);

        assertThat(clawbackRepository.findByRefundTaskIdOrderByIdAsc(taskId)).hasSize(2);
        assertThat(settlementEvents(confirmed.getId())).doesNotContain(SettlementEventType.RECALCULATED)
                .contains(SettlementEventType.CLAWBACK_REGISTERED);
        assertThat(settlement(confirmed.getId()).getBrandPayoutAmount()).isEqualTo(brandBefore);
    }

    @Test
    @DisplayName("설정이 비면(테스트 기본) 확인 기간 중 환불도 클로백 — 재계산은 설정으로만 켠다")
    void recalculationIsConfigDriven() {
        OrderDeliveryGroup cream = confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement s = generate(LocalDateTime.now().withNano(0));

        Long taskId = operatorRefund(cream, "POST_CONFIRM_DEFECT", CREAM_PRICE);

        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.REVIEWING);
        assertThat(clawbackRepository.findByRefundTaskIdOrderByIdAsc(taskId)).isNotEmpty();
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.RECALCULATED);
    }

    // ------------------------------------------------------------------ 6-4 조정 철회

    @Test
    @DisplayName("인플루언서 요청 철회 — 지급 예정일 = 철회일 + 3영업일 · 이슈 카드 「요청 철회」 · 브랜드 알림 · 재요청 409 · 어드민 이슈 종결 탭")
    void creatorWithdrawConfirmsAndNotifiesBrand() throws Exception {
        Settlement s = seedSettlement("가을 토너 공구", 1_000_000, SettlementStatus.REVIEWING);
        JsonNode requested = json(creatorPost("/v1/creator/settlements/" + s.getId() + "/adjustment",
                Map.of("rewardAmount", 120_000, "reason", "추가 콘텐츠 1건 제작")).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        long threadId = requested.get("threadId").asLong();
        sellerGet("/v1/seller/threads/" + threadId + "/adjustment")
                .andExpect(jsonPath("$.permissions.canWithdraw").value(false));

        creatorPost("/v1/creator/settlement-adjustments/" + adjustmentId + "/withdraw", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.turn").value("CLOSED"))
                .andExpect(jsonPath("$.permissions.canSend").value(false));

        Settlement confirmed = settlement(s.getId());
        assertThat(confirmed.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(confirmed.getPayoutDueDate())
                .isEqualTo(businessCalendar.addBusinessDays(confirmed.getConfirmedAt().toLocalDate(), 3));
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isIn(PayoutStatus.SCHEDULED,
                PayoutStatus.BLOCKED);
        assertThat(messagesOf(threadId).stream().filter(Message::isCard).map(Message::getCardType).toList())
                .contains(MessageCardType.SETTLEMENT_ADJUSTMENT_WITHDRAWN);
        // 철회된 요청 카드 — 닫힘(CLOSED) · 「기한 만료」가 아니라 「요청 철회」(55 S4 · D10). 세 화면이 같은 문구다.
        for (JsonNode messages : List.of(json(sellerGet("/v1/seller/threads/" + threadId + "/messages")),
                json(creatorGet("/v1/creator/threads/" + threadId + "/messages")),
                json(adminGet("/v1/admin/threads/" + threadId + "/messages")))) {
            JsonNode requestCard = proposalCardActions(messages).get(1);
            assertThat(requestCard.get("state").asText()).isEqualTo("CLOSED");
            assertThat(requestCard.get("resultLabel").asText()).isEqualTo("요청 철회");
        }
        assertThat(notices(NotificationType.ADJUSTMENT_WITHDRAWN)).singleElement()
                .satisfies(n -> assertThat(n.getRecipientType()).isEqualTo(NotificationRecipientType.SELLER));

        creatorPost("/v1/creator/settlements/" + s.getId() + "/adjustment",
                Map.of("rewardAmount", 110_000, "reason", "다시 요청")).andExpect(status().isConflict());
        creatorPost("/v1/creator/settlement-adjustments/" + adjustmentId + "/withdraw", Map.of())
                .andExpect(status().isConflict());

        adminGet("/v1/admin/connections/threads?tab=ISSUE&state=CLOSED").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].issue.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.content[0].issue.badgeLabel").value("요청 철회 · 원래 금액"));
    }

    @Test
    @DisplayName("요청자가 다른 금액을 다시 낸 뒤(상대가 반대한 다음)에도 내 최신 제안이 응답 대기면 철회할 수 있다")
    void requesterMayWithdrawOwnLatestPendingProposal() throws Exception {
        Settlement s = seedSettlement("겨울 앰플 공구", 1_000_000, SettlementStatus.REVIEWING);
        JsonNode requested = json(sellerPost("/v1/seller/settlements/" + s.getId() + "/adjustment",
                Map.of("rewardAmount", 80_000, "reason", "샘플 미도착")).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        creatorPost("/v1/creator/settlement-adjustments/" + adjustmentId + "/proposals/"
                + requested.get("proposalId").asLong() + "/reject", Map.of()).andExpect(status().isOk());
        sellerPost("/v1/seller/settlement-adjustments/" + adjustmentId + "/proposals",
                Map.of("rewardAmount", 90_000)).andExpect(status().isCreated());

        sellerPost("/v1/seller/settlement-adjustments/" + adjustmentId + "/withdraw", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));
        assertThat(settlement(s.getId()).getRewardAmount()).isEqualTo(settlement(s.getId()).getOriginalRewardAmount());

        // 카드 결과 — seq 1 은 응답된 그대로 · 철회로 닫힌 seq 2 만 「요청 철회」(55 S4).
        Map<Integer, JsonNode> cards = proposalCardActions(json(creatorGet("/v1/creator/threads/"
                + requested.get("threadId").asLong() + "/messages")));
        assertThat(cards.get(1).get("state").asText()).isEqualTo("DONE");
        assertThat(cards.get(1).get("resultLabel").asText()).isEqualTo("다른 금액 제안으로 응답됨");
        assertThat(cards.get(2).get("state").asText()).isEqualTo("CLOSED");
        assertThat(cards.get(2).get("resultLabel").asText()).isEqualTo("요청 철회");
    }

    // ------------------------------------------------------------------ 6-1 분해 줄 API

    @Test
    @DisplayName("파트너 · 어드민 정산 화면 — 「+ 반품 시 차감한 최초 배송비」 금액 · 건수가 엔티티와 같다 · 검산 맞음")
    void breakdownLinesOnBothSurfaces() throws Exception {
        OrderDeliveryGroup returned = deliveredFrom(purchase(creamVariant, 2).group());   // 54,400 · 무료배송
        confirmedGroup(serumVariant, 1);
        Long claimId = returnClaim(returned, itemsOf(returned).get(0), 2, ClaimReason.CHANGE_OF_MIND);
        passClaim(claimId);
        endGroupBuy();
        Settlement s = settlement(generate(LocalDateTime.now().withNano(0)).getId());
        assertThat(s.getReturnDeductionFeeAmount()).isPositive();

        sellerGet("/v1/seller/settlements/" + s.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown.returnDeductionFee.amount").value(s.getReturnDeductionFeeAmount()))
                .andExpect(jsonPath("$.breakdown.returnDeductionFee.count").value(1))
                .andExpect(jsonPath("$.breakdown.freightAdjustment.amount").value(0));
        adminGet("/v1/admin/settlements/" + s.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown.brand.returnDeductionFee.amount").value(s.getReturnDeductionFeeAmount()))
                .andExpect(jsonPath("$.breakdown.brand.payoutAmount").value(s.getBrandPayoutAmount()))
                .andExpect(jsonPath("$.payouts").doesNotExist());   // 3자 분배는 확정 뒤에 보인다

        registerSellerAccount();
        confirm(s);
        adminGet("/v1/admin/settlements/" + s.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown.brand.returnDeductionFee.amount").value(s.getReturnDeductionFeeAmount()))
                .andExpect(jsonPath("$.payouts.check.balanced").value(true));
    }

    // ------------------------------------------------------------------ helpers

    private List<Notification> notices(NotificationType type) {
        return notificationRepository.findAll().stream().filter(n -> n.getType() == type).toList();
    }

    /** 메시지 목록의 제안 카드(요청 · 다른 금액 제안) — 제안 seq → card.action. */
    private static Map<Integer, JsonNode> proposalCardActions(JsonNode messages) {
        Map<Integer, JsonNode> actions = new java.util.TreeMap<>();
        for (JsonNode m : messages.get("content")) {
            String type = m.path("card").path("cardType").asText();
            if ("SETTLEMENT_ADJUSTMENT_REQUEST".equals(type) || "SETTLEMENT_ADJUSTMENT_COUNTER".equals(type)) {
                actions.put(m.path("card").path("detail").path("adjustment").path("seq").asInt(),
                        m.get("card").get("action"));
            }
        }
        return actions;
    }

    private org.springframework.test.web.servlet.ResultActions creatorPost(String url, Object body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}
