package showroomz.api.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentRepository;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentService;
import showroomz.domain.settlement.adjustment.type.AdjustmentStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementClawback;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.ClawbackSide;
import showroomz.domain.settlement.type.ClawbackStatus;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementConfirmReason;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.global.scheduler.SettlementAdjustmentDeadlineScheduler;
import showroomz.global.scheduler.SettlementAutoConfirmScheduler;
import showroomz.global.scheduler.SettlementGenerationScheduler;
import showroomz.global.scheduler.SettlementPayoutScheduler;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산 관리 수명주기 E2E(dev/44_정산관리_테스트_시나리오.md 11절 E2E-1 ~ 6) — 흐름 하나를 한 테스트에서 끝까지 돌리고, 쓰기가 일어날
 * 때마다 <b>세 서피스(07b · 파트너 · 스튜디오)가 같은 정산 행을 읽는지</b>와 연계 화면(공구 상세 · 06a · 06c · 20b)을 다시 본다.
 *
 * <p>단계별 세부 판정은 각 서피스 테스트가 소유한다 — 여기서는 단계 사이의 이음새(앞 단계의 쓰기가 다음 화면에 그대로 보이는가)를 본다.
 * 배치는 꺼져 있어 진입점(tick · run)이나 서비스를 직접 부른다. 확인 기간 · 합의 기한처럼 「지금」과 비교하는 흐름은 지금 시각으로,
 * 공휴일이 끼는 지급 흐름은 기준 시각표(09.30 생성 · 10.09 한글날만)로 돈다.
 */
@DisplayName("[시나리오 E2E-1~6] 정산 관리 — 생성부터 지급 · 조정 · 증빙 · 재분배 · 차감까지")
class SettlementScenarioIntegrationTest extends SettlementTestSupport {

    private static final String ADMIN = "/v1/admin/settlements";
    private static final String PARTNER = "/v1/seller/settlements";
    private static final String STUDIO = "/v1/creator/settlements";
    /** 기준 시각표 — 09.30(수) 생성 → 확정 10.06 → 지급 예정 10.12(10.09 한글날 제외). */
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 9, 30, 10, 0);
    private static final LocalDate PAYOUT_DUE = LocalDate.of(2026, 10, 12);
    private static final String APPROVAL = "20261007-41000012-38475920";
    private static final byte[] PDF = "%PDF-1.4 scenario".getBytes(StandardCharsets.US_ASCII);
    /** 크림 27,200 · 리워드 12% → 단위 리워드 3,264 · 부가세 326 · 브랜드 측 27,200 − 3,264 − 326. */
    private static final long CREAM_REWARD = 3_264;
    private static final long CREAM_BRAND = 27_200 - 3_264 - 326;

    @Autowired private SettlementTaxDocumentRepository documentRepository;
    @Autowired private SettlementClawbackRepository clawbackRepository;
    @Autowired private SettlementAdjustmentRepository adjustmentRepository;
    @Autowired private SettlementAdjustmentService adjustmentService;

    // ================================================================== E2E-1

    @Test
    @DisplayName("E2E-1 기본 — 구매확정 2건 → 공구 종료 → 생성 배치 → 자동 확정 배치 → 지급 배치 → 원천징수영수증 · 공구 SETTLED")
    void basicLifecycleThroughBatches() throws Exception {
        readyToPay();
        doReturn(PDF).when(renderer).render(anyString(), anyString());
        OrderDeliveryGroup creamGroup = confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();

        // ① 생성 배치 — 공구 상세 「정산 대기(포트)」 · 06a 정산 블록.
        new SettlementGenerationScheduler(generationService).tick();
        Settlement s = settlementRepository.findByGroupBuyId(groupBuy.getId()).orElseThrow();
        assertThat(s.getStatus()).isEqualTo(SettlementStatus.REVIEWING);
        adminGet("/v1/admin/group-buys/" + groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.afterEnd.settlement.stage").value("WAITING"))
                .andExpect(jsonPath("$.afterEnd.settlement.stageSource").value("PORT"));
        adminGet(ADMIN_ORDERS + "/" + creamGroup.getOrder().getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].settlement.settlementNumber").value(s.getSettlementNumber()))
                .andExpect(jsonPath("$.groups[0].settlement.status").value("REVIEWING"));

        // ② 확인 기간 — 세 서피스 같은 숫자 · 파트너 · 스튜디오 모두 조정 요청 가능 · 분배 블록은 확정 후.
        Surfaces reviewing = surfaces(s);
        reviewing.assertSameNumbers(s);
        assertThat(reviewing.partner().at("/actions/canRequestAdjustment").asBoolean()).isTrue();
        assertThat(reviewing.partner().at("/payouts").isNull()).isTrue();
        assertThat(reviewing.studio().at("/review/canRequestAdjustment").asBoolean()).isTrue();
        assertThat(reviewing.admin().at("/payouts").isNull()).isTrue();

        // ③ 자동 확정 배치 — 마감이 지난 정산만 · 확정 시각은 규칙값(오늘 00:00) · 파트너 분배 · 증빙 블록 등장 · 07a 증빙 탭.
        jdbc.update("UPDATE settlement SET review_due_at = ? WHERE settlement_id = ?",
                LocalDate.now().minusDays(1).atTime(23, 59, 59), s.getId());
        new SettlementAutoConfirmScheduler(confirmService).tick();
        Settlement confirmed = settlement(s.getId());
        assertThat(confirmed.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(confirmed.getConfirmReason()).isEqualTo(SettlementConfirmReason.AUTO);
        assertThat(confirmed.getConfirmedAt()).isEqualTo(LocalDate.now().atStartOfDay());
        assertThat(confirmed.getPayoutDueDate()).isEqualTo(businessCalendar.addBusinessDays(LocalDate.now(), 3));
        Surfaces scheduled = surfaces(confirmed);
        scheduled.assertSameNumbers(confirmed);
        assertThat(scheduled.admin().at("/payouts/check/balanced").asBoolean()).isTrue();
        assertThat(scheduled.partner().at("/payouts/0/payee").asText()).isEqualTo("BRAND");
        assertThat(scheduled.partner().at("/taxDocuments/0/type").asText()).isEqualTo("BRAND_TAX_INVOICE");
        assertThat(scheduled.partner().at("/taxDocuments/0/status").asText()).isEqualTo("PENDING_ISSUE");
        assertThat(scheduled.partner().at("/actions/canRequestAdjustment").asBoolean()).isFalse();
        assertThat(scheduled.studio().at("/taxInvoice").isNull()).isTrue();   // 비사업자 — 세금계산서 없음
        JsonNode evidence = json(adminGet(ADMIN + "?tab=EVIDENCE").andExpect(status().isOk()));
        assertThat(evidence.findValuesAsText("type")).containsExactly("BRAND_TAX_INVOICE");

        // ④ 지급 배치(예정일 10:00) — 정산 PAID · 공구 SETTLED · 공구 상세 확정 리워드 · 영수증 · 07a 배지는 발행 대기만 남는다.
        new SettlementPayoutScheduler(payoutService, businessCalendar).run(confirmed.getPayoutDueDate().atTime(10, 0));
        Settlement paid = settlement(s.getId());
        assertThat(paid.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getStatus).containsOnly(PayoutStatus.PAID);
        assertThat(payoutGateway.calls()).hasSize(1);
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.SETTLED);
        adminGet("/v1/admin/group-buys/" + groupBuy.getId())
                .andExpect(jsonPath("$.afterEnd.settlement.stage").value("TRANSFERRED"));
        studioGroupBuy().andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.settlementId").value(s.getId()))
                .andExpect(jsonPath("$.settlement.confirmedReward").value(paid.getRewardAmount()));
        Surfaces done = surfaces(paid);
        done.assertSameNumbers(paid);
        assertThat(done.studio().at("/withholding/receiptAvailable").asBoolean()).isTrue();
        assertThat(document(s, TaxDocumentType.WITHHOLDING_RECEIPT).getStatus()).isEqualTo(TaxDocumentStatus.GENERATED);
        adminGet(ADMIN + "/summary").andExpect(jsonPath("$.gnbBadge").value(1));   // 브랜드 세금계산서 발행 대기

        issue(s, document(s, TaxDocumentType.BRAND_TAX_INVOICE).getId()).andExpect(status().isOk());
        adminGet(ADMIN + "/summary").andExpect(jsonPath("$.gnbBadge").value(0));
    }

    // ================================================================== E2E-2

    @Test
    @DisplayName("E2E-2 조정 합의 — 인플루언서 요청 → 브랜드 다른 금액 제안 → 인플루언서 동의 → 지급 · 세금계산서 · 영수증은 합의 후 리워드")
    void adjustmentAgreedThenPaid() throws Exception {
        readyToPay();
        doReturn(PDF).when(renderer).render(anyString(), anyString());
        Settlement s = generatedNow();
        long original = s.getRewardAmount();

        // ① 요청 — 전액 보류 · 07a 조정 탭 · 20b 「응답 대기」 · 스튜디오 THEIR_TURN · 파트너 MY_TURN.
        JsonNode requested = json(creatorPost(STUDIO + "/" + s.getId() + "/adjustment",
                Map.of("rewardAmount", original + 2_000, "reason", "추가 콘텐츠 2건 제작")).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        long threadId = requested.get("threadId").asLong();
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.ADJUSTING);
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getStatus).containsOnly(PayoutStatus.HELD);
        JsonNode adjustingTab = json(adminGet(ADMIN + "?tab=ADJUSTING").andExpect(status().isOk()));
        assertThat(adjustingTab.at("/content/0/settlementId").asLong()).isEqualTo(s.getId());
        assertThat(adjustingTab.at("/toolbar/heldAmount").asLong()).isEqualTo(s.getConfirmedSalesAmount());
        adminGet("/v1/admin/connections/threads?tab=ISSUE").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].threadId").value(threadId))
                .andExpect(jsonPath("$.content[0].issue.badgeLabel").value("응답 대기"));
        Surfaces adjusting = surfaces(s);
        assertThat(adjusting.studio().at("/adjustment/turn").asText()).isEqualTo("THEIR_TURN");
        assertThat(adjusting.studio().at("/review").isNull()).isTrue();
        assertThat(adjusting.partner().at("/adjustment/turn").asText()).isEqualTo("MY_TURN");
        assertThat(adjusting.partner().at("/actions/canRespondAdjustment").asBoolean()).isTrue();

        // ② 브랜드 다른 금액 제안 — 차례가 바뀐다 · 카드.
        JsonNode countered = json(sellerPost("/v1/seller/settlement-adjustments/" + adjustmentId + "/proposals",
                Map.of("rewardAmount", original + 1_000, "reason", "샘플 반송분 제외")).andExpect(status().isCreated()));
        long p2 = countered.get("latestProposal").get("proposalId").asLong();
        assertThat(json(creatorGet("/v1/creator/threads/" + threadId + "/adjustment")).get("turn").asText())
                .isEqualTo("MY_TURN");
        assertThat(json(sellerGet(PARTNER + "/" + s.getId())).at("/adjustment/turn").asText()).isEqualTo("THEIR_TURN");
        assertThat(cardTypes(threadId)).contains(MessageCardType.SETTLEMENT_ADJUSTMENT_COUNTER);

        // ③ 동의 — 합의 확정 · 리워드 변경 · 20b 「보류 해제 · 금액 변경」 · 세금계산서 공급가 = 합의 후 리워드 · 세 서피스 같은 숫자.
        creatorPost("/v1/creator/settlement-adjustments/" + adjustmentId + "/proposals/" + p2 + "/accept", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AGREED"));
        Settlement agreed = settlement(s.getId());
        assertThat(agreed.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(agreed.getConfirmReason()).isEqualTo(SettlementConfirmReason.AGREED);
        assertThat(agreed.getOriginalRewardAmount()).isEqualTo(original);
        assertThat(agreed.getRewardAmount()).isEqualTo(original + 1_000);
        assertThat(agreed.getPayoutDueDate())
                .isEqualTo(businessCalendar.addBusinessDays(agreed.getConfirmedAt().toLocalDate(), 3));
        assertThat(json(adminGet("/v1/admin/threads/" + threadId + "/info")).at("/issue/settlement/holdLabel").asText())
                .isEqualTo("보류 해제 · 금액 변경");
        assertThat(document(s, TaxDocumentType.BRAND_TAX_INVOICE).getSupplyAmount()).isEqualTo(original + 1_000);
        Surfaces confirmed = surfaces(agreed);
        confirmed.assertSameNumbers(agreed);
        assertThat(confirmed.partner().at("/breakdown/originalRewardAmount").asLong()).isEqualTo(original);
        assertThat(confirmed.partner().at("/breakdown/rewardAmount").asLong()).isEqualTo(original + 1_000);
        assertThat(confirmed.partner().at("/adjustment/status").asText()).isEqualTo("AGREED");

        // ④ 지급 — PAID · 영수증 금액도 합의 후 리워드.
        Settlement paid = pay(agreed, agreed.getPayoutDueDate());
        assertThat(paid.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.SETTLED);
        SettlementTaxDocument receipt = document(s, TaxDocumentType.WITHHOLDING_RECEIPT);
        assertThat(receipt.getStatus()).isEqualTo(TaxDocumentStatus.GENERATED);
        assertThat(receipt.getSupplyAmount()).isEqualTo(original + 1_000);
        surfaces(paid).assertSameNumbers(paid);
    }

    // ================================================================== E2E-3

    @Test
    @DisplayName("E2E-3 조정 만료 — 요청 → 반대(OPEN_FLOOR) → D-1 통지 1장 → 만료 배치 → 원래 금액 확정(기한 다음 날 00:00) → 만료 뒤 동의 409 → 지급")
    void adjustmentExpiresThenPaid() throws Exception {
        readyToPay();
        Settlement s = generatedNow();
        long original = s.getRewardAmount();
        JsonNode requested = json(creatorPost(STUDIO + "/" + s.getId() + "/adjustment",
                Map.of("rewardAmount", original + 2_000, "reason", "추가 콘텐츠 2건 제작")).andExpect(status().isCreated()));
        long adjustmentId = requested.get("adjustmentId").asLong();
        long threadId = requested.get("threadId").asLong();
        long p1 = requested.get("proposalId").asLong();

        // ① 브랜드 반대 — 양측 OPEN_FLOOR.
        sellerPost("/v1/seller/settlement-adjustments/" + adjustmentId + "/proposals/" + p1 + "/reject", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.turn").value("OPEN_FLOOR"));
        assertThat(json(creatorGet("/v1/creator/threads/" + threadId + "/adjustment")).get("turn").asText())
                .isEqualTo("OPEN_FLOOR");

        // ② D-1 통지 — 배치가 두 번 돌아도 카드 1장.
        SettlementAdjustment adjustment = adjustmentRepository.findById(adjustmentId).orElseThrow();
        SettlementAdjustmentDeadlineScheduler scheduler = new SettlementAdjustmentDeadlineScheduler(adjustmentService);
        scheduler.run(adjustment.getNoticeDueAt().plusMinutes(1));
        scheduler.run(adjustment.getNoticeDueAt().plusMinutes(11));
        assertThat(cardTypes(threadId)).filteredOn(MessageCardType.SETTLEMENT_ADJUSTMENT_DEADLINE_NOTICE::equals)
                .hasSize(1);
        assertThat(settlement(s.getId()).getStatus()).isEqualTo(SettlementStatus.ADJUSTING);

        // ③ 만료 — 원래 금액 · 확정 시각 = 기한 다음 날 00:00 · 20b 합의 단계 SKIPPED.
        LocalDateTime deadline = adjustment.getDeadlineAt();
        scheduler.run(deadline.plusMinutes(6));
        assertThat(adjustmentRepository.findById(adjustmentId).orElseThrow().getStatus()).isEqualTo(AdjustmentStatus.EXPIRED);
        Settlement expired = settlement(s.getId());
        assertThat(expired.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(expired.getConfirmReason()).isEqualTo(SettlementConfirmReason.EXPIRED);
        assertThat(expired.getRewardAmount()).isEqualTo(original);
        assertThat(expired.getConfirmedAt()).isEqualTo(deadline.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT));
        JsonNode issue = json(adminGet("/v1/admin/threads/" + threadId + "/info")).get("issue");
        assertThat(issue.get("status").asText()).isEqualTo("EXPIRED");
        assertThat(stepStates(issue)).containsExactly("DONE", "DONE", "SKIPPED", "DONE");
        surfaces(expired).assertSameNumbers(expired);

        // ④ 만료 뒤 동의 — 409 · 금액 그대로.
        sellerPost("/v1/seller/settlement-adjustments/" + adjustmentId + "/proposals/" + p1 + "/accept", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_ADJUSTMENT_CLOSED"));
        assertThat(settlement(s.getId()).getRewardAmount()).isEqualTo(original);

        // ⑤ 지급 완료.
        Settlement paid = pay(expired, expired.getPayoutDueDate());
        assertThat(paid.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(paid.getRewardAmount()).isEqualTo(original);
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.SETTLED);
    }

    // ================================================================== E2E-4

    @Test
    @DisplayName("E2E-4 사업자 증빙 — 확정 → 브랜드 먼저 지급 → 승인번호 제출 → 반려 → 재제출 → 확인(+3영업일) → 인플루언서 지급 · 영수증 없음 → M5 → 파트너 다운로드")
    void businessCreatorEvidenceFlow() throws Exception {
        hangulDayOnly();
        makeCreatorBusiness();
        registerSellerAccount();
        registerCreatorAccount();
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement s = confirm(generate(GENERATED_AT));
        assertThat(s.getPayoutDueDate()).isEqualTo(PAYOUT_DUE);

        // ① 예정일 — 브랜드 PAID · 인플루언서 BLOCKED · 플랫폼 몫 없음 · 정산은 지급 예정에 머문다 · 스튜디오 D5.
        Settlement partlyPaid = pay(s, PAYOUT_DUE);
        assertThat(partlyPaid.getStatus()).isEqualTo(SettlementStatus.PAYOUT_SCHEDULED);
        assertThat(payoutsOf(s.getId())).extracting(SettlementPayout::getPayee, SettlementPayout::getStatus)
                .containsExactly(tuple(SettlementPayee.BRAND, PayoutStatus.PAID),
                        tuple(SettlementPayee.CREATOR, PayoutStatus.BLOCKED),
                        tuple(SettlementPayee.PLATFORM, PayoutStatus.NOT_APPLICABLE));
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.ENDED);
        JsonNode d5 = json(creatorGet(STUDIO + "/" + s.getId()).andExpect(status().isOk()));
        assertThat(d5.at("/taxInvoice/cardStatus").asText()).isEqualTo("PENDING_INPUT");
        assertThat(d5.at("/payouts/rows/0/statusLabel").asText()).isEqualTo("발행 필요");
        assertThat(json(sellerGet(PARTNER + "/" + s.getId())).at("/payouts/0/status").asText()).isEqualTo("PAID");
        int badge = json(adminGet(ADMIN + "/summary")).get("gnbBadge").asInt();

        // ② 제출 → 07a 배지 +1.
        Long invoiceId = document(s, TaxDocumentType.CREATOR_TAX_INVOICE).getId();
        submitInvoice(s, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.taxInvoice.cardStatus").value("SUBMITTED"));
        adminGet(ADMIN + "/summary").andExpect(jsonPath("$.gnbBadge").value(badge + 1));

        // ③ 반려 → 스튜디오 D5b(재제출).
        adminPost(ADMIN + "/" + s.getId() + "/tax-documents/" + invoiceId + "/verify", Map.of("result", "AMOUNT_MISMATCH"))
                .andExpect(status().isOk());
        creatorGet(STUDIO + "/" + s.getId()).andExpect(jsonPath("$.taxInvoice.cardStatus").value("REJECTED"))
                .andExpect(jsonPath("$.timeline.payoutDueNote").value("재제출 확인 후 + 3영업일"));
        adminGet(ADMIN + "/summary").andExpect(jsonPath("$.gnbBadge").value(badge));

        // ④ 재제출(같은 행) → 확인 → 인플루언서 행 SCHEDULED(확인일 + 3영업일).
        submitInvoice(s, false).andExpect(status().isOk());
        adminPost(ADMIN + "/" + s.getId() + "/tax-documents/" + invoiceId + "/verify", Map.of("result", "MATCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.canVerifyInvoice").value(false));
        SettlementPayout creatorRow = payout(s.getId(), SettlementPayee.CREATOR);
        LocalDate creatorDue = businessCalendar.addBusinessDays(LocalDate.now(), 3);
        assertThat(creatorRow.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(creatorRow.getDueDate()).isEqualTo(creatorDue);

        // ⑤ 인플루언서 지급 → 정산 PAID · 공구 SETTLED · 사업자라 영수증 행이 없다.
        Settlement paid = pay(s, creatorDue);
        assertThat(paid.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.SETTLED);
        assertThat(documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(s.getId(),
                TaxDocumentType.WITHHOLDING_RECEIPT)).isEmpty();
        creatorGet(STUDIO + "/" + s.getId() + "/withholding-receipt").andExpect(status().isConflict());
        surfaces(paid).assertSameNumbers(paid);

        // ⑥ M5 → 파트너 다운로드 = 업로드 바이트.
        Long brandInvoiceId = document(s, TaxDocumentType.BRAND_TAX_INVOICE).getId();
        sellerGet(PARTNER + "/" + s.getId() + "/tax-documents/" + brandInvoiceId + "/download")
                .andExpect(status().isConflict());
        issue(s, brandInvoiceId).andExpect(status().isOk());
        assertThat(sellerGet(PARTNER + "/" + s.getId() + "/tax-documents/" + brandInvoiceId + "/download")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).isEqualTo(PDF);
    }

    // ================================================================== E2E-5

    @Test
    @DisplayName("E2E-5 분배 실패 · 재분배 — 인플루언서 행 거절 → 07a 분배 실패 탭 · 파트너 · 스튜디오는 PAID + 자기 행만 「지급 확인 중」 → M3 → PAID · 공구 SETTLED")
    void payoutFailedThenRedistributed() throws Exception {
        hangulDayOnly();
        readyToPay();
        confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement s = confirm(generate(GENERATED_AT));
        payoutGateway.failFor(SettlementPayee.CREATOR, "BANK_REJECTED", "예금주 불일치");

        // ① 분배 실패.
        Settlement failed = pay(s, PAYOUT_DUE);
        assertThat(failed.getStatus()).isEqualTo(SettlementStatus.PAYOUT_FAILED);
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.ENDED);
        JsonNode failedTab = json(adminGet(ADMIN + "?tab=PAYOUT_FAILED").andExpect(status().isOk()));
        assertThat(failedTab.at("/content/0/settlementId").asLong()).isEqualTo(s.getId());
        assertThat(failedTab.at("/toolbar/failedPayeeLabel").asText()).isEqualTo("인플루언서 1");
        adminGet(ADMIN + "/summary").andExpect(jsonPath("$.tabCounts.PAYOUT_FAILED").value(1))
                .andExpect(jsonPath("$.gnbBadge").value(2));   // 분배 실패 1 + 브랜드 세금계산서 발행 대기 1
        Surfaces broken = surfaces(failed);
        broken.assertSameNumbers(failed);
        assertThat(broken.admin().at("/actions/canRedistribute").asBoolean()).isTrue();
        // 파트너 — 정산 라벨 PAID · 자기(브랜드) 행 지급 완료.
        assertThat(broken.partner().at("/status").asText()).isEqualTo("PAID");
        assertThat(broken.partner().at("/payouts/0/status").asText()).isEqualTo("PAID");
        sellerGet(PARTNER).andExpect(jsonPath("$.content[0].status").value("PAID"));
        // 스튜디오 — 정산 라벨 PAID · 자기(인플루언서) 행만 「지급 확인 중」.
        assertThat(broken.studio().at("/settlement/status").asText()).isEqualTo("PAID");
        assertThat(broken.studio().at("/payouts/rows/0/status").asText()).isEqualTo("FAILED");
        assertThat(broken.studio().at("/payouts/rows/0/statusLabel").asText()).isEqualTo("지급 확인 중");
        assertThat(broken.studio().at("/withholding/receiptAvailable").asBoolean()).isFalse();

        // ② 재분배(지난 계좌) — PAID · 공구 SETTLED · 이력 운영자.
        payoutGateway.succeedFor(SettlementPayee.CREATOR);
        Long creatorPayoutId = payout(s.getId(), SettlementPayee.CREATOR).getId();
        JsonNode after = json(adminPost(ADMIN + "/" + s.getId() + "/payouts/" + creatorPayoutId + "/redistribute",
                Map.of("accountSource", "PREVIOUS")).andExpect(status().isOk()));
        assertThat(after.get("status").asText()).isEqualTo("PAID");
        List<String> retriedBy = new ArrayList<>();
        after.get("history").forEach(h -> {
            if ("PAYOUT_RETRIED".equals(h.get("eventType").asText())) {
                retriedBy.add(h.get("actorType").asText() + ":" + h.get("actorLabel").asText());
            }
        });
        assertThat(retriedBy).containsExactly("ADMIN:김운영");
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.SETTLED);
        assertThat(payoutGateway.calls()).hasSize(2);
        Surfaces recovered = surfaces(settlement(s.getId()));
        recovered.assertSameNumbers(settlement(s.getId()));
        assertThat(recovered.studio().at("/payouts/rows/0/status").asText()).isEqualTo("PAID");
        adminGet(ADMIN + "/summary").andExpect(jsonPath("$.tabCounts.PAYOUT_FAILED").value(0))
                .andExpect(jsonPath("$.gnbBadge").value(1));
    }

    // ================================================================== E2E-6

    @Test
    @DisplayName("E2E-6 차감 — 지급 완료 정산 → 확정 후 하자 환불(06a → 06c) → 다음 공구 정산에 측별 반영 · 수정세금계산서 → M5 · 파트너 다운로드")
    void clawbackAcrossSettlements() throws Exception {
        readyToPay();
        OrderDeliveryGroup creamGroup = confirmedGroup(creamVariant, 1);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();
        Settlement first = confirm(generatedNow());
        first = pay(first, first.getPayoutDueDate());
        assertThat(first.getStatus()).isEqualTo(SettlementStatus.PAID);
        long firstBrandPayout = first.getBrandPayoutAmount();

        // ① 06a B5 → 06c 집행 — 지급 끝난 정산은 고치지 않고 CLW-0001 2행 · 06a / 06c 정산 블록.
        Long taskId = operatorRefund(creamGroup, "POST_CONFIRM_DEFECT", 27_200);
        assertThat(settlement(first.getId()).getBrandPayoutAmount()).isEqualTo(firstBrandPayout);
        assertThat(clawbackRepository.findByRefundTaskIdOrderByIdAsc(taskId))
                .extracting(SettlementClawback::getClawbackNumber, SettlementClawback::getSide, SettlementClawback::getStatus)
                .containsExactly(tuple("CLW-0001", ClawbackSide.BRAND, ClawbackStatus.PENDING),
                        tuple("CLW-0001", ClawbackSide.CREATOR, ClawbackStatus.PENDING));
        adminGet(ADMIN_ORDERS + "/" + creamGroup.getOrder().getId())
                .andExpect(jsonPath("$.groups[0].settlement.clawbacks.length()").value(2));
        adminGet(ADMIN_REFUNDS + "/" + taskId).andExpect(jsonPath("$.settlement.clawback.clawbackNumber").value("CLW-0001"))
                .andExpect(jsonPath("$.settlement.clawback.status").value("PENDING"));

        // ② 다음 공구(같은 브랜드 · 같은 인플루언서 · 크림 2) — 측별 전액 반영 · 수정세금계산서 발행 대기 · 세 서피스 차감 블록.
        openNextGroupBuy(creator);
        confirmedGroup(creamVariant, 2);
        endGroupBuy();
        Settlement next = confirm(generatedNow());
        assertThat(next.getBrandClawbackAmount()).isEqualTo(CREAM_BRAND);
        assertThat(next.getRewardClawbackAmount()).isEqualTo(CREAM_REWARD);
        assertThat(next.getRewardAfterClawback()).isEqualTo(next.getRewardAmount() - CREAM_REWARD);
        assertThat(clawbackRepository.findByRefundTaskIdOrderByIdAsc(taskId)).extracting(SettlementClawback::getStatus)
                .containsOnly(ClawbackStatus.APPLIED);
        SettlementTaxDocument credit = documentRepository.findBySettlementIdOrderByIdAsc(next.getId()).stream()
                .filter(d -> d.getType() == TaxDocumentType.BRAND_TAX_INVOICE_CREDIT).findFirst().orElseThrow();
        assertThat(credit.getStatus()).isEqualTo(TaxDocumentStatus.PENDING_ISSUE);
        assertThat(credit.getSupplyAmount()).isEqualTo(-CREAM_REWARD);
        Surfaces nextSurfaces = surfaces(next);
        nextSurfaces.assertSameNumbers(next);
        assertThat(nextSurfaces.admin().at("/clawbacksApplied/0/clawbackNumber").asText()).isEqualTo("CLW-0001");
        assertThat(nextSurfaces.admin().at("/clawbacksApplied/0/originSettlementNumber").asText())
                .isEqualTo(first.getSettlementNumber());
        assertThat(nextSurfaces.partner().at("/breakdown/clawbacks/0/amount").asLong()).isEqualTo(CREAM_BRAND);
        assertThat(nextSurfaces.partner().at("/breakdown/clawbacks/0/creditInvoiceStatus").asText())
                .isEqualTo("PENDING_ISSUE");
        assertThat(nextSurfaces.studio().at("/clawbacks/0/amount").asLong()).isEqualTo(CREAM_REWARD);
        assertThat(nextSurfaces.studio().at("/breakdown/rewardAfterClawback").asLong())
                .isEqualTo(next.getRewardAmount() - CREAM_REWARD);
        adminGet(ADMIN_REFUNDS + "/" + taskId).andExpect(jsonPath("$.settlement.clawback.status").value("APPLIED"));

        // ③ M5(수정세금계산서) → ISSUED · 파트너 다운로드 = 업로드 바이트.
        issue(next, credit.getId()).andExpect(status().isOk());
        assertThat(documentRepository.findById(credit.getId()).orElseThrow().getStatus())
                .isEqualTo(TaxDocumentStatus.ISSUED);
        assertThat(sellerGet(PARTNER + "/" + next.getId() + "/tax-documents/" + credit.getId() + "/download")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).isEqualTo(PDF);
        assertThat(json(sellerGet(PARTNER + "/" + next.getId())).at("/breakdown/clawbacks/0/creditInvoiceStatus").asText())
                .isEqualTo("ISSUED");
    }

    // ------------------------------------------------------------------ 보조

    /** 07b · 파트너 · 스튜디오 상세 — 같은 정산 행을 세 서피스가 읽은 결과. */
    private record Surfaces(JsonNode admin, JsonNode partner, JsonNode studio) {

        /** 공통 확인 방법 2 — 브랜드 수취액 · 인플루언서 실지급 · 확정 거래액이 세 서피스 · DB 행에서 같다. */
        void assertSameNumbers(Settlement row) {
            assertThat(admin.at("/breakdown/brand/payoutAmount").asLong()).isEqualTo(row.getBrandPayoutAmount());
            assertThat(partner.at("/breakdown/brandPayoutAmount").asLong()).isEqualTo(row.getBrandPayoutAmount());
            assertThat(admin.at("/breakdown/creator/payoutAmount").asLong()).isEqualTo(row.getCreatorPayoutAmount());
            assertThat(studio.at("/breakdown/creatorPayoutAmount").asLong()).isEqualTo(row.getCreatorPayoutAmount());
            assertThat(admin.at("/breakdown/brand/confirmedSalesAmount").asLong()).isEqualTo(row.getConfirmedSalesAmount());
            assertThat(partner.at("/breakdown/confirmedSalesAmount").asLong()).isEqualTo(row.getConfirmedSalesAmount());
            assertThat(studio.at("/breakdown/confirmedSalesAmount").asLong()).isEqualTo(row.getConfirmedSalesAmount());
        }
    }

    private Surfaces surfaces(Settlement s) throws Exception {
        return new Surfaces(json(adminGet(ADMIN + "/" + s.getId()).andExpect(status().isOk())),
                json(sellerGet(PARTNER + "/" + s.getId()).andExpect(status().isOk())),
                json(creatorGet(STUDIO + "/" + s.getId()).andExpect(status().isOk())));
    }

    /** 확인 기간이 열린 정산 — 지금 시각 생성(조정 요청 · 만료처럼 「지금」과 비교하는 흐름용). 기존 구매확정 주문을 쓴다. */
    private Settlement generatedNow() {
        if (settlementRepository.findByGroupBuyId(groupBuy.getId()).isEmpty()
                && reload(groupBuy.getId()).getStatus() != GroupBuyStatus.ENDED) {
            confirmedGroup(creamVariant, 1);
            confirmedGroup(serumVariant, 1);
            endGroupBuy();
        }
        return generate(LocalDateTime.now().withNano(0));
    }

    /** 세 수취자 모두 지시 가능 — 계좌 · 주민등록번호. */
    private void readyToPay() {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
    }

    /** 기준 시각표 — 한글날만 공휴일(지급 예정일 10.12). */
    private void hangulDayOnly() {
        jdbc.update("INSERT INTO business_holiday (holiday_date, name) VALUES (?, ?)", LocalDate.of(2026, 10, 9), "한글날");
        businessCalendar.replaceRegisteredHolidays(Set.of(LocalDate.of(2026, 10, 9)));
    }

    private SettlementTaxDocument document(Settlement s, TaxDocumentType type) {
        return documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(s.getId(), type).orElseThrow();
    }

    private ResultActions submitInvoice(Settlement s, boolean withAttachment) throws Exception {
        var request = multipart(STUDIO + "/" + s.getId() + "/tax-invoice");
        request.param("approvalNumber", APPROVAL);
        if (withAttachment) {
            request.file(new MockMultipartFile("attachment", "invoice.pdf", "application/pdf", PDF));
        }
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    /** M5 — 브랜드 세금계산서 · 수정세금계산서 발행본 등록. */
    private ResultActions issue(Settlement s, Long documentId) throws Exception {
        return mockMvc.perform(multipart(ADMIN + "/" + s.getId() + "/tax-documents/" + documentId + "/issue")
                .file(new MockMultipartFile("file", "brand-invoice.pdf", "application/pdf", PDF))
                .param("approvalNumber", "20261010-41000099-00001234")
                .param("issuedDate", "2026-10-10")
                .header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    private ResultActions creatorPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions studioGroupBuy() throws Exception {
        return mockMvc.perform(get("/v1/creator/group-buys/" + groupBuy.getId())
                .header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    private List<MessageCardType> cardTypes(long threadId) {
        return messagesOf(threadId).stream().filter(Message::isCard).map(Message::getCardType).toList();
    }

    private static List<String> stepStates(JsonNode issue) {
        List<String> states = new ArrayList<>();
        issue.get("steps").forEach(step -> states.add(step.get("state").asText()));
        return states;
    }
}
