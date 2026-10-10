package showroomz.api.common.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustmentProposal;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentProposalRepository;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentRepository;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentService;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;
import showroomz.domain.settlement.adjustment.type.ProposalStatus;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.scheduler.SettlementAdjustmentDeadlineScheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산 조정 협의(44 이슈 스레드 설계서 9-2 SA-01 ~ SA-23) — 파트너센터 · 스튜디오 · 어드민 세 서피스를 한 시나리오로 돌린다.
 *
 * <p>정산 포트는 실제 어댑터다(단계 5-2 완료) — 요청 → 제안 → 동의/만료 → 정산 {@code PAYOUT_SCHEDULED}가 한 흐름으로 돈다.
 * 보조 정산은 확정 거래액 1,000,000 · 리워드 100,000 · 확인 마감 +3일.
 */
@DisplayName("정산 조정 협의 — 요청 · 제안 · 동의 · 반대 · 만료 · 3서피스(44 이슈 스레드)")
class SettlementAdjustmentIntegrationTest extends SettlementTestSupport {

    private static final String SELLER = "/v1/seller";
    private static final String CREATOR = "/v1/creator";
    private static final String ADMIN_THREADS = "/v1/admin/threads/";
    private static final long ORIGINAL = 100_000;

    @Autowired private SettlementAdjustmentRepository adjustmentRepository;
    @Autowired private SettlementAdjustmentProposalRepository proposalRepository;
    @Autowired private SettlementAdjustmentService adjustmentService;

    private Settlement settlement;

    @BeforeEach
    void seedReviewing() {
        settlement = seedSettlement("여름 수분 세럼 공구", 1_000_000, SettlementStatus.REVIEWING);
    }

    // ------------------------------------------------------------------ 요청(SA-01 ~ SA-05 · SA-23)

    @Test
    @DisplayName("SA-01 인플루언서 조정 요청 — 3자 스레드 개설 · 카드 2장 · 정산 전액 보류 · 기한 = 개설 + 10영업일 23:59:59 · 구 이슈 행 없음")
    void requestOpensThreadAndHoldsSettlement() throws Exception {
        JsonNode res = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long adjustmentId = res.get("adjustmentId").asLong();
        long threadId = res.get("threadId").asLong();
        LocalDateTime deadline = businessCalendar.addBusinessDays(LocalDate.now(), 10).atTime(23, 59, 59);
        assertThat(res.get("deadlineAt").asText()).startsWith(deadline.toString());

        SettlementAdjustment adjustment = adjustmentRepository.findById(adjustmentId).orElseThrow();
        assertThat(adjustment.getStatus()).isEqualTo(AdjustmentStatus.OPEN);
        assertThat(adjustment.getRequesterType()).isEqualTo(SettlementParty.CREATOR);
        assertThat(adjustment.getOriginalRewardAmount()).isEqualTo(ORIGINAL);
        assertThat(adjustment.getDeadlineAt()).isEqualTo(deadline);
        assertThat(adjustment.getNoticeDueAt()).isEqualTo(
                businessCalendar.previousBusinessDay(deadline.toLocalDate()).atTime(10, 0));

        List<SettlementAdjustmentProposal> proposals = proposalRepository.findByAdjustmentId(adjustmentId);
        assertThat(proposals).singleElement().satisfies(p -> {
            assertThat(p.getSeq()).isEqualTo(1);
            assertThat(p.getStatus()).isEqualTo(ProposalStatus.PENDING);
            assertThat(p.getRewardAmount()).isEqualTo(130_000);
        });

        MessageThread thread = messageThreadRepository.findById(threadId).orElseThrow();
        assertThat(thread.getKind()).isEqualTo(ThreadKind.SETTLEMENT_ADJUSTMENT);
        assertThat(thread.getSubjectId()).isEqualTo(settlement.getGroupBuyId());
        assertThat(thread.getConnection().getId()).isEqualTo(connection.getId());
        assertThat(cardTypes(threadId)).containsExactly(MessageCardType.SETTLEMENT_ADJUSTMENT_OPENED,
                MessageCardType.SETTLEMENT_ADJUSTMENT_REQUEST);

        assertThat(settlement(settlement.getId()).getStatus()).isEqualTo(SettlementStatus.ADJUSTING);
        assertThat(payoutsOf(settlement.getId())).extracting(SettlementPayout::getStatus)
                .containsOnly(PayoutStatus.HELD);
        assertThat(settlementEvents(settlement.getId())).contains(SettlementEventType.ADJUSTMENT_REQUESTED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_buy_issue", Long.class)).isZero();
    }

    @Test
    @DisplayName("SA-02 확인 기간 밖 — 409 WINDOW_CLOSED · 행 없음 · 미리보기 canRequest = false(REVIEW_CLOSED)")
    void requestOutsideReviewWindow() throws Exception {
        jdbc.update("UPDATE settlement SET review_due_at = ? WHERE settlement_id = ?",
                LocalDateTime.now().minusHours(1), settlement.getId());

        creatorRequest(settlement, 130_000).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_WINDOW_CLOSED"));
        assertThat(adjustmentRepository.count()).isZero();
        assertThat(adjustmentThreadCount()).isZero();
        sellerGet(SELLER + "/settlements/" + settlement.getId() + "/adjustment/preview")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRequest").value(false))
                .andExpect(jsonPath("$.cannotRequestReason").value("REVIEW_CLOSED"));
    }

    @Test
    @DisplayName("SA-03 같은 정산에 두 번째 요청 — 409 ALREADY_EXISTS · 미리보기 ALREADY_ADJUSTING")
    void secondRequestConflicts() throws Exception {
        creatorRequest(settlement, 130_000).andExpect(status().isCreated());

        sellerRequest(settlement, 90_000).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_ALREADY_EXISTS"));
        sellerGet(SELLER + "/settlements/" + settlement.getId() + "/adjustment/preview")
                .andExpect(jsonPath("$.canRequest").value(false))
                .andExpect(jsonPath("$.cannotRequestReason").value("ALREADY_ADJUSTING"));
        assertThat(adjustmentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("SA-04 상한 초과 · 원래 금액과 같음 · 사유 공백 — 400 세 종류 · 보류 없음")
    void requestValidation() throws Exception {
        long max = maxRewardAmount();
        sellerRequest(settlement, max + 10).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_AMOUNT_OUT_OF_RANGE"));
        sellerRequest(settlement, ORIGINAL).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_AMOUNT_OUT_OF_RANGE"));
        sellerPost(SELLER + "/settlements/" + settlement.getId() + "/adjustment",
                Map.of("rewardAmount", 90_000, "reason", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_REASON_REQUIRED"));

        assertThat(adjustmentRepository.count()).isZero();
        assertThat(settlement(settlement.getId()).getStatus()).isEqualTo(SettlementStatus.REVIEWING);
        assertThat(payoutsOf(settlement.getId())).extracting(SettlementPayout::getStatus)
                .containsOnly(PayoutStatus.WAITING);
    }

    @Test
    @DisplayName("SA-05 보류 조건부 UPDATE 0행(자동 확정 선행) — 409 WINDOW_CLOSED · 협의 · 스레드 · 카드 전부 롤백")
    void holdLostRollsBackEverything() {
        // 포트의 판정(find)은 지금 시각 · 보류 UPDATE 는 넘겨받은 시각이다 — 확인 마감 뒤 시각을 넘겨 「판정과 보류 사이에 마감이 지났다」를 만든다.
        LocalDateTime afterDue = settlement.getReviewDueAt().plusSeconds(1);

        assertThatThrownBy(() -> adjustmentService.request(settlement.getId(), SettlementParty.CREATOR,
                creator.getId(), 130_000, "추가 콘텐츠 제작", afterDue))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_ADJUSTMENT_WINDOW_CLOSED);

        assertThat(adjustmentRepository.count()).isZero();
        assertThat(proposalRepository.count()).isZero();
        assertThat(adjustmentThreadCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message WHERE card_type LIKE 'SETTLEMENT_ADJUSTMENT_%'",
                Long.class)).isZero();
        assertThat(settlement(settlement.getId()).getStatus()).isEqualTo(SettlementStatus.REVIEWING);
    }

    @Test
    @DisplayName("SA-23 PAIR 연결이 끊긴 쌍 — 503 GROUP_BUY_THREAD_UNAVAILABLE · 행 없음 · 정산 그대로")
    void disconnectedPairCannotOpenThread() throws Exception {
        jdbc.update("UPDATE connection SET status = 'DISCONNECTED' WHERE connection_id = ?", connection.getId());

        creatorRequest(settlement, 130_000).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_THREAD_UNAVAILABLE"));
        assertThat(adjustmentRepository.count()).isZero();
        assertThat(settlement(settlement.getId()).getStatus()).isEqualTo(SettlementStatus.REVIEWING);
    }

    // ------------------------------------------------------------------ 응답(SA-06 ~ SA-11)

    @Test
    @DisplayName("SA-06 · SA-07 브랜드 다른 금액 제안 → 인플루언서 동의 — 기한 불변 · 합의 · 금액 변경 · 정산 지급 예정(AGREED)")
    void counterThenAcceptConfirmsSettlement() throws Exception {
        Settlement before = settlement(settlement.getId());
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        long threadId = requested.get("threadId").asLong();

        JsonNode countered = json(sellerPost(SELLER + "/settlement-adjustments/" + adjustmentId + "/proposals",
                Map.of("rewardAmount", 115_000, "reason", "샘플 반송분 제외")).andExpect(status().isCreated()));
        assertThat(countered.get("latestProposal").get("seq").asInt()).isEqualTo(2);
        assertThat(countered.get("latestProposal").get("status").asText()).isEqualTo("PENDING");
        assertThat(countered.get("latestProposal").get("deltaAmount").asLong()).isEqualTo(15_000);
        assertThat(countered.get("proposals").get(0).get("status").asText()).isEqualTo("COUNTERED");
        assertThat(countered.get("deadlineAt").asText()).isEqualTo(requested.get("deadlineAt").asText());
        assertThat(countered.get("turn").asText()).isEqualTo("THEIR_TURN");

        long p2 = countered.get("latestProposal").get("proposalId").asLong();
        creatorGet(CREATOR + "/threads/" + threadId + "/adjustment").andExpect(status().isOk())
                .andExpect(jsonPath("$.turn").value("MY_TURN"))
                .andExpect(jsonPath("$.permissions.canAccept").value(true))
                .andExpect(jsonPath("$.permissions.canReject").value(true))
                .andExpect(jsonPath("$.permissions.canCounter").value(true));

        JsonNode accepted = json(creatorPost(CREATOR + "/settlement-adjustments/" + adjustmentId + "/proposals/" + p2
                + "/accept", Map.of()).andExpect(status().isOk()));
        assertThat(accepted.get("status").asText()).isEqualTo("AGREED");
        assertThat(accepted.get("agreedRewardAmount").asLong()).isEqualTo(115_000);
        assertThat(accepted.get("finalRewardAmount").asLong()).isEqualTo(115_000);
        assertThat(accepted.get("turn").asText()).isEqualTo("CLOSED");
        assertThat(accepted.get("remainingBusinessDays").isNull()).isTrue();
        assertThat(accepted.get("permissions").get("canSend").asBoolean()).isFalse();

        Settlement after = settlement(settlement.getId());
        assertThat(after.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(after.getConfirmReason()).isEqualTo(SettlementConfirmReason.AGREED);
        assertThat(after.getOriginalRewardAmount()).isEqualTo(ORIGINAL);
        assertThat(after.getRewardAmount()).isEqualTo(115_000);
        assertThat(after.getConfirmedSalesAmount()).isEqualTo(before.getConfirmedSalesAmount());
        // 리워드 +15,000 → 브랜드 수취액은 리워드 + 부가세(10%)만큼 준다.
        assertThat(after.getBrandPayoutAmount()).isEqualTo(before.getBrandPayoutAmount() - 16_500);
        assertThat(payoutsOf(settlement.getId())).extracting(SettlementPayout::getStatus)
                .doesNotContain(PayoutStatus.HELD, PayoutStatus.WAITING);
        assertThat(settlementEvents(settlement.getId())).contains(SettlementEventType.ADJUSTMENT_COUNTERED,
                SettlementEventType.ADJUSTMENT_ACCEPTED, SettlementEventType.CONFIRMED_BY_AGREEMENT);
        assertThat(cardTypes(threadId)).containsExactly(MessageCardType.SETTLEMENT_ADJUSTMENT_OPENED,
                MessageCardType.SETTLEMENT_ADJUSTMENT_REQUEST, MessageCardType.SETTLEMENT_ADJUSTMENT_COUNTER,
                MessageCardType.SETTLEMENT_ADJUSTMENT_ACCEPTED, MessageCardType.SETTLEMENT_ADJUSTMENT_AGREED);
    }

    @Test
    @DisplayName("SA-08 브랜드 반대 → 양측 OPEN_FLOOR → 인플루언서 재제안 → 브랜드 동의 — REJECTED → COUNTERED · seq 2 ACCEPTED")
    void rejectOpensFloorThenCounterAndAccept() throws Exception {
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        long threadId = requested.get("threadId").asLong();
        long p1 = requested.get("proposalId").asLong();

        JsonNode rejected = json(sellerPost(SELLER + "/settlement-adjustments/" + adjustmentId + "/proposals/" + p1
                + "/reject", Map.of()).andExpect(status().isOk()));
        assertThat(rejected.get("turn").asText()).isEqualTo("OPEN_FLOOR");
        assertThat(rejected.get("latestProposal").get("status").asText()).isEqualTo("REJECTED");
        // 반대한 쪽은 그 제안에 뒤늦게 동의할 수 있다(§46 A-9 잠정) · 다시 반대는 없다.
        assertThat(rejected.get("permissions").get("canAccept").asBoolean()).isTrue();
        assertThat(rejected.get("permissions").get("canReject").asBoolean()).isFalse();
        assertThat(rejected.get("permissions").get("canCounter").asBoolean()).isTrue();
        creatorGet(CREATOR + "/threads/" + threadId + "/adjustment")
                .andExpect(jsonPath("$.turn").value("OPEN_FLOOR"))
                .andExpect(jsonPath("$.permissions.canAccept").value(false))
                .andExpect(jsonPath("$.permissions.canCounter").value(true));

        JsonNode recountered = json(creatorPost(CREATOR + "/settlement-adjustments/" + adjustmentId + "/proposals",
                Map.of("rewardAmount", 120_000)).andExpect(status().isCreated()));
        assertThat(recountered.get("proposals").get(0).get("status").asText()).isEqualTo("COUNTERED");
        long p2 = recountered.get("latestProposal").get("proposalId").asLong();

        sellerPost(SELLER + "/settlement-adjustments/" + adjustmentId + "/proposals/" + p2 + "/accept", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGREED"))
                .andExpect(jsonPath("$.proposals[1].seq").value(2))
                .andExpect(jsonPath("$.proposals[1].status").value("ACCEPTED"));
        assertThat(settlement(settlement.getId()).getRewardAmount()).isEqualTo(120_000);
    }

    @Test
    @DisplayName("SA-09 반대한 쪽이 같은 제안에 뒤늦게 동의 — REJECTED → ACCEPTED · 협의 AGREED")
    void rejecterAcceptsLater() throws Exception {
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        long p1 = requested.get("proposalId").asLong();
        sellerPost(SELLER + "/settlement-adjustments/" + adjustmentId + "/proposals/" + p1 + "/reject", Map.of())
                .andExpect(status().isOk());

        sellerPost(SELLER + "/settlement-adjustments/" + adjustmentId + "/proposals/" + p1 + "/accept", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGREED"))
                .andExpect(jsonPath("$.proposals[0].status").value("ACCEPTED"));
        assertThat(adjustmentRepository.findById(adjustmentId).orElseThrow().getFinalRewardAmount())
                .isEqualTo(130_000L);
    }

    @Test
    @DisplayName("SA-10 제안자가 자기 제안에 동의 · 반대 — 409 NOT_YOUR_TURN · 응답 권한 전부 false")
    void proposerCannotRespondToOwnProposal() throws Exception {
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        long p1 = requested.get("proposalId").asLong();

        creatorPost(CREATOR + "/settlement-adjustments/" + adjustmentId + "/proposals/" + p1 + "/accept", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_NOT_YOUR_TURN"));
        creatorPost(CREATOR + "/settlement-adjustments/" + adjustmentId + "/proposals/" + p1 + "/reject", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_NOT_YOUR_TURN"));
        creatorGet(CREATOR + "/threads/" + requested.get("threadId").asLong() + "/adjustment")
                .andExpect(jsonPath("$.turn").value("THEIR_TURN"))
                .andExpect(jsonPath("$.permissions.canAccept").value(false))
                .andExpect(jsonPath("$.permissions.canReject").value(false))
                .andExpect(jsonPath("$.permissions.canCounter").value(false))
                .andExpect(jsonPath("$.permissions.canSend").value(true));
    }

    @Test
    @DisplayName("SA-11 옛 제안 id 로 동의 — 409 STATE_CHANGED")
    void staleProposalConflicts() throws Exception {
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        long p1 = requested.get("proposalId").asLong();
        sellerPost(SELLER + "/settlement-adjustments/" + adjustmentId + "/proposals",
                Map.of("rewardAmount", 110_000)).andExpect(status().isCreated());

        creatorPost(CREATOR + "/settlement-adjustments/" + adjustmentId + "/proposals/" + p1 + "/accept", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_STATE_CHANGED"));
    }

    // ------------------------------------------------------------------ 스레드 · 미리보기 · 목록(SA-12 ~ SA-14)

    @Test
    @DisplayName("SA-12 종결 스레드 — 메시지 · 첨부 presign 409 SETTLEMENT_ADJUSTMENT_CLOSED · 조회는 200")
    void closedThreadIsReadOnly() throws Exception {
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long threadId = requested.get("threadId").asLong();
        creatorSend(threadId, "근거 자료 보내드려요").andExpect(status().isCreated());
        accept(SELLER, brandToken, requested);

        creatorSend(threadId, "종결 뒤 메시지").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_CLOSED"));
        mockMvc.perform(post(SELLER + "/threads/" + threadId + "/attachments/presign")
                        .header(HttpHeaders.AUTHORIZATION, brandToken).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("fileName", "a.pdf", "contentType", "application/pdf",
                                "sizeBytes", 1_000))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_CLOSED"));
        creatorGet(CREATOR + "/threads/" + threadId + "/messages").andExpect(status().isOk());
    }

    @Test
    @DisplayName("SA-13 미리보기 — 금액 없음(preview null) · 금액 있음(자동 계산) · 범위 밖(inRange false)")
    void preview() throws Exception {
        String url = SELLER + "/settlements/" + settlement.getId() + "/adjustment/preview";
        JsonNode blank = json(sellerGet(url).andExpect(status().isOk()));
        assertThat(blank.get("canRequest").asBoolean()).isTrue();
        assertThat(blank.get("input").isNull()).isTrue();
        assertThat(blank.get("preview").isNull()).isTrue();
        assertThat(blank.get("originalRewardAmount").asLong()).isEqualTo(ORIGINAL);
        long brandBefore = blank.get("original").get("brandPayoutAmount").asLong();
        long max = blank.get("maxRewardAmount").asLong();
        assertThat(max % 10).isZero();

        JsonNode typed = json(sellerGet(url + "?rewardAmount=130000").andExpect(status().isOk()));
        assertThat(typed.get("inRange").asBoolean()).isTrue();
        assertThat(typed.get("preview").get("rewardAmount").asLong()).isEqualTo(130_000);
        assertThat(typed.get("preview").get("rewardVatAmount").asLong()).isEqualTo(13_000);
        assertThat(typed.get("preview").get("brandPayoutAmount").asLong()).isEqualTo(brandBefore - 33_000);

        JsonNode over = json(creatorGet(CREATOR + "/settlements/" + settlement.getId()
                + "/adjustment/preview?rewardAmount=" + (max + 10)).andExpect(status().isOk()));
        assertThat(over.get("inRange").asBoolean()).isFalse();
        assertThat(over.get("preview").isNull()).isTrue();
    }

    @Test
    @DisplayName("SA-14 목록 배지 · 카드 respondable — 받는 쪽만 true · 종결 뒤 전부 false")
    void listBadgesAndRespondableCards() throws Exception {
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long threadId = requested.get("threadId").asLong();

        JsonNode sellerRow = threadRow(json(sellerGet(SELLER + "/connections/threads")), threadId);
        assertThat(sellerRow.get("kind").asText()).isEqualTo("SETTLEMENT_ADJUSTMENT");
        assertThat(sellerRow.get("adjustment").get("turn").asText()).isEqualTo("MY_TURN");
        JsonNode creatorRow = threadRow(json(creatorGet(CREATOR + "/connections/threads")), threadId);
        assertThat(creatorRow.get("adjustment").get("turn").asText()).isEqualTo("THEIR_TURN");

        assertThat(requestCardAction(sellerGet(SELLER + "/threads/" + threadId + "/messages"))
                .get("respondable").asBoolean()).isTrue();
        JsonNode creatorAction = requestCardAction(creatorGet(CREATOR + "/threads/" + threadId + "/messages"));
        assertThat(creatorAction.get("type").asText()).isEqualTo("ADJUSTMENT_RESPOND");
        assertThat(creatorAction.get("state").asText()).isEqualTo("PENDING");
        assertThat(creatorAction.get("respondable").asBoolean()).isFalse();

        accept(SELLER, brandToken, requested);

        JsonNode closedAction = requestCardAction(sellerGet(SELLER + "/threads/" + threadId + "/messages"));
        assertThat(closedAction.get("respondable").asBoolean()).isFalse();
        assertThat(closedAction.get("state").asText()).isEqualTo("DONE");
        assertThat(closedAction.get("resultLabel").asText()).isEqualTo("동의");
        JsonNode closedRow = threadRow(json(sellerGet(SELLER + "/connections/threads")), threadId);
        assertThat(closedRow.get("adjustment").get("status").asText()).isEqualTo("AGREED");
        assertThat(closedRow.get("adjustment").get("turn").asText()).isEqualTo("CLOSED");
    }

    // ------------------------------------------------------------------ 어드민 20b(SA-15 ~ SA-19)

    @Test
    @DisplayName("SA-15 어드민 이슈 탭 — state seg · 공구명/브랜드명 검색 · unreadCount 0 · writable false · 요약 open/closed")
    void adminIssueTab() throws Exception {
        creatorRequest(settlement, 130_000).andExpect(status().isCreated());
        Settlement winter = seedSettlement("겨울 리페어 크림 공구", 2_000_000, SettlementStatus.REVIEWING);
        accept(CREATOR, creatorToken, json(sellerRequest(winter, 150_000).andExpect(status().isCreated())));

        adminGet("/v1/admin/connections/threads?tab=ISSUE").andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].tab").value("ISSUE"))
                .andExpect(jsonPath("$.content[0].name").value("여름 수분 세럼 공구"))
                .andExpect(jsonPath("$.content[0].unreadCount").value(0))
                .andExpect(jsonPath("$.content[0].writable").value(false))
                .andExpect(jsonPath("$.content[0].issue.status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].issue.badgeLabel").value("응답 대기"))
                .andExpect(jsonPath("$.content[0].issue.subtitle").value("글로우랩 × 글로우_지민 · 정산 조정 요청"));
        adminGet("/v1/admin/connections/threads?tab=ISSUE&state=CLOSED")
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].name").value("겨울 리페어 크림 공구"))
                .andExpect(jsonPath("$.content[0].issue.badgeLabel").value("합의 · 금액 변경"))
                .andExpect(jsonPath("$.content[0].issue.remainingBusinessDays").doesNotExist());
        adminGet("/v1/admin/connections/threads?tab=ISSUE&state=CLOSED&keyword=여름")
                .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        adminGet("/v1/admin/connections/threads?tab=ISSUE&keyword=글로우랩")
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1));
        adminGet("/v1/admin/connections/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.issue.openCount").value(1))
                .andExpect(jsonPath("$.issue.closedCount").value(1))
                .andExpect(jsonPath("$.issue.unreadCount").value(0));
    }

    @Test
    @DisplayName("IS-01 이슈 탭에는 조정 협의 스레드만 — 운영자가 열람할 수 있게 된 PAIR 스레드도 진행 · 종결 어느 쪽에도 섞이지 않는다")
    void issueTabExcludesPairThreads() throws Exception {
        long threadId = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated())).get("threadId").asLong();
        // 협의가 생긴 쌍이라 운영자 열람(SA-18)은 열린다 — 그래도 목록 축은 다르다.
        adminGet(ADMIN_THREADS + pairThreadId + "/info").andExpect(status().isOk());

        for (String state : List.of("OPEN", "CLOSED")) {
            JsonNode page = json(adminGet("/v1/admin/connections/threads?tab=ISSUE&state=" + state)
                    .andExpect(status().isOk()));
            List<Long> ids = new ArrayList<>();
            page.get("content").forEach(row -> {
                ids.add(row.get("threadId").asLong());
                assertThat(row.get("tab").asText()).isEqualTo("ISSUE");
            });
            assertThat(ids).doesNotContain(pairThreadId);
            if (state.equals("OPEN")) {
                assertThat(ids).containsExactly(threadId);
            }
        }
        adminGet("/v1/admin/connections/threads?tab=ISSUE&keyword=글로우랩")
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].threadId").value(threadId));
    }

    @Test
    @DisplayName("SA-16 어드민 이슈 패널 — 진행 · 합의 · 만료의 steps · holdLabel · 실지급")
    void adminIssuePanel() throws Exception {
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long threadId = requested.get("threadId").asLong();

        JsonNode open = json(adminGet(ADMIN_THREADS + threadId + "/info").andExpect(status().isOk())).get("issue");
        assertThat(open.get("status").asText()).isEqualTo("OPEN");
        assertThat(open.get("settlement").get("holdLabel").asText()).isEqualTo("정산 보류 · 전액");
        assertThat(open.get("settlement").get("currentProposal").get("proposerName").asText()).isEqualTo("글로우_지민");
        assertThat(open.get("settlement").get("currentProposal").get("responderName").asText()).isEqualTo("글로우랩");
        assertThat(open.get("settlement").get("finalCreatorNetAmount").isNull()).isTrue();
        assertThat(stepStates(open)).containsExactly("DONE", "CURRENT", "TODO", "TODO");
        assertThat(open.get("links").get("pairThreadId").asLong()).isEqualTo(pairThreadId);

        accept(SELLER, brandToken, requested);
        JsonNode agreed = json(adminGet(ADMIN_THREADS + threadId + "/info")).get("issue");
        assertThat(agreed.get("settlement").get("holdLabel").asText()).isEqualTo("보류 해제 · 금액 변경");
        assertThat(agreed.get("settlement").get("finalRewardAmount").asLong()).isEqualTo(130_000);
        assertThat(agreed.get("settlement").get("finalCreatorNetAmount").asLong())
                .isEqualTo(settlement(settlement.getId()).getCreatorPayoutAmount());
        assertThat(stepStates(agreed)).containsExactly("DONE", "DONE", "DONE", "DONE");

        Settlement other = seedSettlement("겨울 리페어 크림 공구", 2_000_000, SettlementStatus.REVIEWING);
        JsonNode otherRequested = json(sellerRequest(other, 150_000).andExpect(status().isCreated()));
        expireAll(otherRequested.get("adjustmentId").asLong());
        JsonNode expired = json(adminGet(ADMIN_THREADS + otherRequested.get("threadId").asLong() + "/info"))
                .get("issue");
        assertThat(expired.get("status").asText()).isEqualTo("EXPIRED");
        assertThat(expired.get("settlement").get("holdLabel").asText()).isEqualTo("보류 해제 · 금액 변경 없음");
        assertThat(stepStates(expired)).containsExactly("DONE", "DONE", "SKIPPED", "DONE");
    }

    @Test
    @DisplayName("SA-17 어드민 이슈 스레드 — 전송 · presign 403 THREAD_OPERATOR_READ_ONLY · 메시지 조회 200(발신자 이름 · 조정 카드)")
    void adminIssueThreadIsReadOnly() throws Exception {
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long threadId = requested.get("threadId").asLong();
        creatorSend(threadId, "근거 사진 보내드려요").andExpect(status().isCreated());

        adminPost(ADMIN_THREADS + threadId + "/messages",
                Map.of("clientMessageId", UUID.randomUUID().toString(), "content", "운영팀입니다"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_OPERATOR_READ_ONLY"));
        adminPost(ADMIN_THREADS + threadId + "/attachments/presign",
                Map.of("fileName", "a.pdf", "contentType", "application/pdf", "sizeBytes", 1_000))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_OPERATOR_READ_ONLY"));

        JsonNode messages = json(adminGet(ADMIN_THREADS + threadId + "/messages").andExpect(status().isOk()));
        JsonNode bubble = messages.get("content").get(0);
        assertThat(bubble.get("senderType").asText()).isEqualTo("CREATOR");
        assertThat(bubble.get("senderName").asText()).isEqualTo("글로우_지민");
        JsonNode requestCard = null;
        for (JsonNode m : messages.get("content")) {
            if ("SETTLEMENT_ADJUSTMENT_REQUEST".equals(m.path("card").path("cardType").asText())) {
                requestCard = m.get("card");
            }
        }
        assertThat(requestCard).isNotNull();
        assertThat(requestCard.get("detail").get("adjustment").get("rewardAmount").asLong()).isEqualTo(130_000);
        assertThat(requestCard.get("action").get("type").asText()).isEqualTo("ADJUSTMENT_RESPOND");
        assertThat(requestCard.get("action").get("canExecute").asBoolean()).isFalse();
        assertThat(requestCard.get("action").get("respondable").asBoolean()).isFalse();

        mockMvc.perform(post(ADMIN_THREADS + threadId + "/read").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("SA-18 어드민 쌍 스레드 열람 — 협의 없는 쌍 403 · 있는 쌍 200(읽기 전용) · read 는 참가자 행 없이 no-op")
    void adminPairThreadIsReadableOnlyForIssuePair() throws Exception {
        adminGet(ADMIN_THREADS + pairThreadId + "/info").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_ACCESS_DENIED"));

        creatorRequest(settlement, 130_000).andExpect(status().isCreated());
        adminGet(ADMIN_THREADS + pairThreadId + "/info").andExpect(status().isOk())
                .andExpect(jsonPath("$.tab").doesNotExist())
                .andExpect(jsonPath("$.writable").value(false))
                .andExpect(jsonPath("$.pair.brandName").value("글로우랩"))
                .andExpect(jsonPath("$.pair.showroomName").value("글로우_지민"));
        adminGet(ADMIN_THREADS + pairThreadId + "/messages").andExpect(status().isOk());
        adminPost(ADMIN_THREADS + pairThreadId + "/messages",
                Map.of("clientMessageId", UUID.randomUUID().toString(), "content", "운영팀입니다"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_OPERATOR_READ_ONLY"));
        mockMvc.perform(post(ADMIN_THREADS + pairThreadId + "/read").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thread_participant WHERE thread_id = ? "
                + "AND participant_type = 'ADMIN'", Long.class, pairThreadId)).isZero();
    }

    @Test
    @DisplayName("SA-19 회원 정보 바의 열린 이슈 스레드 — 진행 중만 · 합의 뒤 사라진다")
    void openIssueThreadsListOnlyOpenAdjustments() throws Exception {
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long threadId = requested.get("threadId").asLong();
        assertThat(messageThreadRepository.findOpenGroupBuyThreadsOf(brand.market().getId(), null))
                .extracting(MessageThread::getId).contains(threadId);
        assertThat(messageThreadRepository.findOpenGroupBuyThreadsOf(null, creator.getId()))
                .extracting(MessageThread::getId).contains(threadId);

        accept(SELLER, brandToken, requested);
        assertThat(messageThreadRepository.findOpenGroupBuyThreadsOf(brand.market().getId(), null))
                .extracting(MessageThread::getId).doesNotContain(threadId);
    }

    // ------------------------------------------------------------------ 배치(SA-20 ~ SA-22)

    @Test
    @DisplayName("SA-20 D-1 통지 — 두 번 돌아도 카드 1장 · notice_sent_at 1회 · 이력 1건")
    void deadlineNoticeIsIdempotent() throws Exception {
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        SettlementAdjustment adjustment = adjustmentRepository.findById(adjustmentId).orElseThrow();
        SettlementAdjustmentDeadlineScheduler scheduler = new SettlementAdjustmentDeadlineScheduler(adjustmentService);

        scheduler.run(adjustment.getNoticeDueAt().minusMinutes(1));
        assertThat(adjustmentRepository.findById(adjustmentId).orElseThrow().getNoticeSentAt()).isNull();

        LocalDateTime noticeAt = adjustment.getNoticeDueAt().plusMinutes(1);
        scheduler.run(noticeAt);
        scheduler.run(noticeAt.plusMinutes(10));

        assertThat(adjustmentRepository.findById(adjustmentId).orElseThrow().getNoticeSentAt()).isEqualTo(noticeAt);
        assertThat(cardTypes(requested.get("threadId").asLong()))
                .filteredOn(type -> type == MessageCardType.SETTLEMENT_ADJUSTMENT_DEADLINE_NOTICE).hasSize(1);
        assertThat(settlementEvents(settlement.getId()))
                .filteredOn(type -> type == SettlementEventType.ADJUSTMENT_DEADLINE_NOTICED).hasSize(1);
        assertThat(adjustmentRepository.findById(adjustmentId).orElseThrow().getStatus())
                .isEqualTo(AdjustmentStatus.OPEN);
    }

    @Test
    @DisplayName("SA-21 기한 만료 — 협의 EXPIRED · final = original · 응답 대기 제안 CLOSED · 원래 금액 확정(기한 다음 날 00:00)")
    void expiryConfirmsOriginalAmount() throws Exception {
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        sellerPost(SELLER + "/settlement-adjustments/" + adjustmentId + "/proposals",
                Map.of("rewardAmount", 110_000)).andExpect(status().isCreated());

        LocalDateTime deadline = expireAll(adjustmentId);

        SettlementAdjustment adjustment = adjustmentRepository.findById(adjustmentId).orElseThrow();
        assertThat(adjustment.getStatus()).isEqualTo(AdjustmentStatus.EXPIRED);
        assertThat(adjustment.getFinalRewardAmount()).isEqualTo(ORIGINAL);
        assertThat(proposalRepository.findByAdjustmentId(adjustmentId)).extracting(SettlementAdjustmentProposal::getStatus)
                .containsExactly(ProposalStatus.COUNTERED, ProposalStatus.CLOSED);

        Settlement after = settlement(settlement.getId());
        assertThat(after.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(after.getConfirmReason()).isEqualTo(SettlementConfirmReason.EXPIRED);
        assertThat(after.getRewardAmount()).isEqualTo(ORIGINAL);
        assertThat(after.getConfirmedAt()).isEqualTo(deadline.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT));
        assertThat(settlementEvents(settlement.getId())).contains(SettlementEventType.ADJUSTMENT_EXPIRED,
                SettlementEventType.CONFIRMED_BY_EXPIRY);
        List<MessageCardType> cards = cardTypes(requested.get("threadId").asLong());
        assertThat(cards.get(cards.size() - 1)).isEqualTo(MessageCardType.SETTLEMENT_ADJUSTMENT_EXPIRED);
    }

    @Test
    @DisplayName("SA-22 만료 선행 후 동의 — 409 · 합의 확정 없음(원래 금액 그대로)")
    void acceptAfterExpiryConflicts() throws Exception {
        JsonNode requested = json(creatorRequest(settlement, 130_000).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        expireAll(adjustmentId);

        sellerPost(SELLER + "/settlement-adjustments/" + adjustmentId + "/proposals/"
                + requested.get("proposalId").asLong() + "/accept", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_CLOSED"));
        Settlement after = settlement(settlement.getId());
        assertThat(after.getConfirmReason()).isEqualTo(SettlementConfirmReason.EXPIRED);
        assertThat(after.getRewardAmount()).isEqualTo(ORIGINAL);
        assertThat(settlementEvents(settlement.getId())).doesNotContain(SettlementEventType.CONFIRMED_BY_AGREEMENT);
    }

    // ------------------------------------------------------------------ 보조

    private ResultActions creatorRequest(Settlement target, long amount) throws Exception {
        return creatorPost(CREATOR + "/settlements/" + target.getId() + "/adjustment",
                Map.of("rewardAmount", amount, "reason", "추가 콘텐츠 2건 제작"));
    }

    private ResultActions sellerRequest(Settlement target, long amount) throws Exception {
        return sellerPost(SELLER + "/settlements/" + target.getId() + "/adjustment",
                Map.of("rewardAmount", amount, "reason", "사전 제공 샘플 12개 미도착"));
    }

    private ResultActions creatorPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions creatorSend(long threadId, String content) throws Exception {
        return creatorPost(CREATOR + "/threads/" + threadId + "/messages",
                Map.of("clientMessageId", UUID.randomUUID().toString(), "content", content));
    }

    /** 요청 응답의 최신 제안에 상대가 동의한다. */
    private void accept(String surface, String token, JsonNode requested) throws Exception {
        mockMvc.perform(post(surface + "/settlement-adjustments/" + requested.get("adjustmentId").asLong()
                        + "/proposals/" + requested.get("proposalId").asLong() + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    /** 기한 다음 날 00:05 의 배치 1회 — 반환은 합의 기한. */
    private LocalDateTime expireAll(long adjustmentId) {
        LocalDateTime deadline = adjustmentRepository.findById(adjustmentId).orElseThrow().getDeadlineAt();
        new SettlementAdjustmentDeadlineScheduler(adjustmentService).run(deadline.plusMinutes(6));
        return deadline;
    }

    private long maxRewardAmount() throws Exception {
        return json(sellerGet(SELLER + "/settlements/" + settlement.getId() + "/adjustment/preview"))
                .get("maxRewardAmount").asLong();
    }

    private long adjustmentThreadCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM message_thread WHERE thread_kind = 'SETTLEMENT_ADJUSTMENT'",
                Long.class);
    }

    private List<MessageCardType> cardTypes(long threadId) {
        return messagesOf(threadId).stream().filter(Message::isCard).map(Message::getCardType).toList();
    }

    private static JsonNode threadRow(JsonNode page, long threadId) {
        for (JsonNode row : page.get("content")) {
            if (row.get("threadId").asLong() == threadId) {
                return row;
            }
        }
        throw new AssertionError("스레드 목록에 없음: " + threadId);
    }

    private JsonNode requestCardAction(ResultActions messages) throws Exception {
        for (JsonNode m : json(messages.andExpect(status().isOk())).get("content")) {
            if ("SETTLEMENT_ADJUSTMENT_REQUEST".equals(m.path("card").path("cardType").asText())) {
                return m.get("card").get("action");
            }
        }
        throw new AssertionError("요청 카드 없음");
    }

    private static List<String> stepStates(JsonNode issue) {
        List<String> states = new ArrayList<>();
        issue.get("steps").forEach(step -> states.add(step.get("state").asText()));
        return states;
    }
}
