package showroomz.api.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.repository.CreatorAdminHistoryRepository;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스튜디오 기본정보 관리 — 끝까지 도는 흐름(50 테스트 시나리오 13절 E2E). 단계별 동작은 {@code api/creator/basicinfo/*IntegrationTest} 가 각각
 * 본다. 여기서는 한 흐름 안에서 단계마다 화면 응답(시안 S · T 상태)을 다시 읽어 판정표(시나리오 1-2)대로 넘어가는지만 본다.
 */
@DisplayName("스튜디오 기본정보 — 시안 상태 순회(E2E)")
class CreatorBasicInfoScenarioIntegrationTest extends SettlementTestSupport {

    private static final String BASE = "/v1/creator/basic-info";
    private static final String QUEUE = "/v1/admin/change-requests";

    @Autowired private CreatorAdminHistoryRepository adminHistories;

    @Test
    @DisplayName("[E2E-1] 계정 탭 — S4 → S5 → S6 · S7(연결 수락 막힘) → 취소(풀림) → 재요청 → 반려 S8 → [확인] → S5")
    void accountTabRoundTrip() throws Exception {
        BrandFixture.Brand other = fixture.createBrand("other-brand@showroomz.test", "다른랩");
        Connection first = connectionRepository.save(Connection.requestPair(other.market(), creator));
        state("BLOCKED");

        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        state("AVAILABLE");

        long firstRequest = requestWithdrawal("활동 중단");
        account().andExpect(jsonPath("$.withdrawal.state").value("PENDING"))
                .andExpect(jsonPath("$.withdrawal.pendingRequest.requestId").value(firstRequest));
        creatorPost("/v1/creator/connections/" + first.getId() + "/accept").andExpect(status().isConflict());

        creatorPost(BASE + "/withdrawal-requests/" + firstRequest + "/cancel").andExpect(status().isNoContent());
        state("AVAILABLE");
        creatorPost("/v1/creator/connections/" + first.getId() + "/accept").andExpect(status().isNoContent());

        long secondRequest = requestWithdrawal("다른 채널 집중");
        adminPost("/v1/admin/creators/" + creator.getId() + "/withdrawal-requests/" + secondRequest + "/reject",
                Map.of("reason", "DISPUTE_IN_PROGRESS", "note", "분쟁 종결 후 다시 신청해 주세요")).andExpect(status().isOk());
        account().andExpect(jsonPath("$.withdrawal.state").value("REJECTED"))
                .andExpect(jsonPath("$.withdrawal.rejection.requestId").value(secondRequest))
                .andExpect(jsonPath("$.withdrawal.satisfiedCount").value(3));

        creatorPost(BASE + "/withdrawal-requests/" + secondRequest + "/acknowledge").andExpect(status().isNoContent());
        state("AVAILABLE");
        assertThat(adminEvents()).containsSubsequence(CreatorAdminEventType.WITHDRAWAL_REQUESTED,
                CreatorAdminEventType.WITHDRAWAL_CANCELED, CreatorAdminEventType.WITHDRAWAL_REQUESTED,
                CreatorAdminEventType.WITHDRAWAL_REJECTED);
    }

    @Test
    @DisplayName("[E2E-2] 사업자 왕복 — T1 → T7 → T4 → 반려 T4a → [확인] T1 → 재요청 → 승인 T5 → [확인] → T8 → T9 → 승인 T10 · 요청 코드는 연속")
    void businessTypeRoundTrip() throws Exception {
        registerCreatorAccount();
        registerCreatorResidentNumber();
        settlement().andExpect(jsonPath("$.businessType.value").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_BUSINESS"));

        JsonNode rejected = createChange(toBusiness());
        settlement().andExpect(jsonPath("$.businessTypeChange.status").value("PENDING"))
                .andExpect(jsonPath("$.actions.businessTypeChange").value("CANCEL"));
        adminPost(QUEUE + "/" + rejected.path("requestId").asLong() + "/reject",
                Map.of("reasonType", "BUSINESS_NUMBER_MISMATCH")).andExpect(status().isOk());
        settlement().andExpect(jsonPath("$.businessTypeChange.status").value("REJECTED"))
                .andExpect(jsonPath("$.businessTypeChange.rejectReason.code").value("BUSINESS_NUMBER_MISMATCH"))
                .andExpect(jsonPath("$.businessType.value").value("INDIVIDUAL"));
        creatorPost(BASE + "/business-type-change-requests/" + rejected.path("requestId").asLong() + "/acknowledge")
                .andExpect(status().isNoContent());
        settlement().andExpect(jsonPath("$.businessTypeChange").doesNotExist())
                .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_BUSINESS"));

        JsonNode toBusiness = createChange(toBusiness());
        adminPost(QUEUE + "/" + toBusiness.path("requestId").asLong() + "/approve", Map.of()).andExpect(status().isOk());
        settlement().andExpect(jsonPath("$.businessType.value").value("BUSINESS"))
                .andExpect(jsonPath("$.businessTypeChange.status").value("APPROVED"))
                .andExpect(jsonPath("$.residentNumber").doesNotExist())
                .andExpect(jsonPath("$.business.registrationNumber").value("214-37-55120"))
                .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_INDIVIDUAL"));
        creatorPost(BASE + "/business-type-change-requests/" + toBusiness.path("requestId").asLong() + "/acknowledge")
                .andExpect(status().isNoContent());
        settlement().andExpect(jsonPath("$.businessTypeChange").doesNotExist());

        JsonNode toIndividual = createChange(toIndividual());
        settlement().andExpect(jsonPath("$.businessTypeChange.type").value("TO_INDIVIDUAL"))
                .andExpect(jsonPath("$.businessTypeChange.status").value("PENDING"))
                .andExpect(jsonPath("$.businessTypeChange.maskedResidentNumber").value("950812-*******"))
                .andExpect(jsonPath("$.businessType.value").value("BUSINESS"));
        adminPost(QUEUE + "/" + toIndividual.path("requestId").asLong() + "/approve", Map.of()).andExpect(status().isOk());
        settlement().andExpect(jsonPath("$.businessType.value").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.residentNumber.masked").value("950812-*******"))
                .andExpect(jsonPath("$.business").doesNotExist())
                .andExpect(jsonPath("$.businessTypeChange.status").value("APPROVED"))
                .andExpect(jsonPath("$.account.verification.status").value("UNVERIFIED"));

        int sequence = sequenceOf(rejected.path("requestCode").asText());
        assertThat(sequenceOf(toBusiness.path("requestCode").asText())).isEqualTo(sequence + 1);
        assertThat(sequenceOf(toIndividual.path("requestCode").asText())).isEqualTo(sequence + 2);
        assertThat(adminEvents()).containsSubsequence(CreatorAdminEventType.BUSINESS_TYPE_CHANGE_REJECTED,
                CreatorAdminEventType.BUSINESS_TYPE_CHANGE_APPROVED, CreatorAdminEventType.BUSINESS_TYPE_CHANGE_APPROVED);
    }

    @Test
    @DisplayName("[E2E-3] 계좌 변경과 지급 — 영향 {1건, 금액, 지시 1} → 예금주 불일치(확인 실패 · 예정분 보류 · 지시분 그대로) → 확인 기간 정산 확정도 보류(PG 사유) "
            + "→ 다른 계좌로 확인 → 예정분은 즉시 · 확정분은 다음 지급 배치에서 풀림 → 지급은 새 계좌 · 브랜드 몫은 처음부터 예정대로")
    void accountChangeAcrossPayouts() throws Exception {
        registerCreatorAccount();
        registerCreatorResidentNumber();
        registerSellerAccount();
        if (!bankRepository.existsById("088")) {
            fixture.createBank("088", "신한은행");
        }
        Settlement scheduled = scheduledSettlement("예정 공구");
        Settlement inFlight = scheduledSettlement("지시 공구");
        jdbc.update("UPDATE settlement_payout SET status = 'REQUESTED' WHERE settlement_id = ? AND payee = 'CREATOR'",
                inFlight.getId());
        Settlement reviewing = seedSettlement("확인 중 공구", 800_000, SettlementStatus.REVIEWING);

        creatorGet(BASE + "/settlement-account/change-impact")
                .andExpect(jsonPath("$.pendingPayoutCount").value(1))
                .andExpect(jsonPath("$.pendingPayoutAmount").value(payout(scheduled.getId(), SettlementPayee.CREATOR).getAmount()))
                .andExpect(jsonPath("$.inFlightPayoutCount").value(1));

        partnerGateway.failFor(SettlementPayee.CREATOR, PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name(), "예금주 불일치");
        changeAccount("1101234567");
        settlement().andExpect(jsonPath("$.account.verification.status").value("FAILED"));
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(inFlight.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        confirm(reviewing);
        // 확정 시점의 PG 셀러 동기화가 같은 실패를 만나 PG 사유로 막는다 — 계좌 확인 훅이 아니라 지급 배치 앞단(PG 재등록)이 푸는 행이다.
        SettlementPayout confirmedWhileFailed = payout(reviewing.getId(), SettlementPayee.CREATOR);
        assertThat(confirmedWhileFailed.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(confirmedWhileFailed.getFailCode()).isEqualTo(PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name());

        partnerGateway.approveFor(SettlementPayee.CREATOR);
        changeAccount("1101234568");
        settlement().andExpect(jsonPath("$.account.verification.status").value("VERIFIED"));
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(payout(reviewing.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        payoutService.releaseBlocked(LocalDate.now());
        for (Settlement s : List.of(scheduled, reviewing)) {
            assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
            assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        }
        assertThat(payout(inFlight.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);

        pay(scheduled, payout(scheduled.getId(), SettlementPayee.CREATOR).getDueDate());
        SettlementPayout paid = payout(scheduled.getId(), SettlementPayee.CREATOR);
        assertThat(paid.getBankName()).isEqualTo("신한은행");
        assertThat(personalDataCipher.decrypt(paid.getAccountNumberEnc())).isEqualTo("1101234568");
    }

    // ------------------------------------------------------------------ helpers

    private void state(String expected) throws Exception {
        account().andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawal.state").value(expected))
                .andExpect(jsonPath("$.withdrawal.conditions.length()").value(3));
    }

    private ResultActions account() throws Exception {
        return creatorGet(BASE + "/account");
    }

    private ResultActions settlement() throws Exception {
        return creatorGet(BASE + "/settlement").andExpect(status().isOk());
    }

    private long requestWithdrawal(String reason) throws Exception {
        return json(mockMvc.perform(post(BASE + "/withdrawal-requests").header(HttpHeaders.AUTHORIZATION, creatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("reason", reason))))
                .andExpect(status().isCreated())).path("requestId").asLong();
    }

    private JsonNode createChange(Map<String, Object> body) throws Exception {
        return json(mockMvc.perform(post(BASE + "/business-type-change-requests").header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body))).andExpect(status().isCreated()));
    }

    private static Map<String, Object> toBusiness() {
        Map<String, Object> body = evidence("사업자등록증.pdf");
        body.put("targetType", "BUSINESS");
        body.put("businessRegistrationNumber", "214-37-55120");
        return body;
    }

    private static Map<String, Object> toIndividual() {
        Map<String, Object> body = evidence("폐업사실증명원.pdf");
        body.put("targetType", "INDIVIDUAL");
        body.put("residentRegistrationNumber", "950812-2345678");
        return body;
    }

    private static Map<String, Object> evidence(String fileName) {
        Map<String, Object> body = new HashMap<>();
        body.put("evidenceFileUrl", "https://cdn.showroomz.test/creator-document/" + fileName);
        body.put("evidenceFileName", fileName);
        body.put("evidenceFileSize", 1_258_291);
        return body;
    }

    /** 계좌 변경 = 변경 요청 → 운영자 승인(1021 기-22) — 승인 뒤 PG 실존 확인이 돈다. */
    private void changeAccount(String accountNumber) throws Exception {
        changeCreatorAccountApproved(Map.of("bankCode", "088", "accountNumber", accountNumber,
                "bankbookImageUrl", "https://cdn.showroomz.test/creator-document/bankbook.jpg"))
                .andExpect(status().isOk());
    }

    /** 인플루언서 몫이 지시 전 예정(SCHEDULED)인 확정 정산. */
    private Settlement scheduledSettlement(String title) {
        Settlement s = seedSettlement(title, 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), s.getId());
        return s;
    }

    private ResultActions creatorPost(String url) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    private List<CreatorAdminEventType> adminEvents() {
        return adminHistories.findByCreatorIdOrderByCreatedAtDescIdDesc(creator.getId()).stream()
                .map(h -> h.getEventType()).toList().reversed();
    }

    private static int sequenceOf(String requestCode) {
        return Integer.parseInt(requestCode.substring(requestCode.lastIndexOf('-') + 1));
    }
}
