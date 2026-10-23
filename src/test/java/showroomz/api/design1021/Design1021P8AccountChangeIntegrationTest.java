package showroomz.api.design1021;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 P8 — 인플루언서 정산 계좌 변경 → 변경 요청(기-22 · 8절).
 */
@DisplayName("1021 P8 — 인플루언서 계좌 변경 요청 · 확인 중 지급 보류 · 반려 · 취소 · 어드민 목록 필터 · 대조표")
class Design1021P8AccountChangeIntegrationTest extends SettlementTestSupport {

    private static final String BASE = "/v1/creator/basic-info";
    private static final String NEW_ACCOUNT = "1101234567";

    @BeforeEach
    void setUpBanks() {
        registerCreatorAccount();
        registerCreatorResidentNumber();
        registerSellerAccount();
        if (!bankRepository.existsById("088")) {
            fixture.createBank("088", "신한은행");
        }
    }

    @Test
    @DisplayName("요청 — 기존 계좌 유지 · 확인 중 블록 · 지급 요청 전 예정은 보류 · 이미 나간 지급은 그대로 · 중복 409 · 반려하면 보류 해제 · 계좌 그대로")
    void requestHoldsThenRejectReleases() throws Exception {
        Settlement scheduled = seedSettlement("예정 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), scheduled.getId());
        Settlement inFlight = seedSettlement("지급 요청 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'REQUESTED' WHERE settlement_id = ? AND payee = 'CREATOR'",
                inFlight.getId());

        requestChange().andExpect(status().isOk())
                .andExpect(jsonPath("$.account.bankName").value("국민은행"))
                .andExpect(jsonPath("$.pendingAccountChange.bankName").value("신한은행"))
                .andExpect(jsonPath("$.pendingAccountChange.maskedAccountNumber").isNotEmpty())
                .andExpect(jsonPath("$.pendingAccountChange.holdingPayoutCount").value(1));
        requestChange().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ALREADY_PENDING"));
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getFailCode()).isEqualTo("CREATOR_ACCOUNT_UNVERIFIED");
        assertThat(payout(inFlight.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);

        long requestId = latestRequestId();
        JsonNode list = json(adminGet("/v1/admin/change-requests?status=PENDING&requesterType=CREATOR&type=CREATOR_ACCOUNT")
                .andExpect(status().isOk()));
        assertThat(list.at("/content/0/requestId").asLong()).isEqualTo(requestId);
        assertThat(list.at("/content/0/typeLabel").asText()).isEqualTo("계좌 변경 (인플루언서)");
        assertThat(json(adminGet("/v1/admin/change-requests?status=PENDING&requesterType=BRAND"))
                .at("/content").size()).isZero();

        adminGet("/v1/admin/change-requests/" + requestId).andExpect(status().isOk())
                .andExpect(jsonPath("$.diff[1].fieldKey").value("ACCOUNT_NUMBER"))
                .andExpect(jsonPath("$.diff[1].requestedValue").value(NEW_ACCOUNT))
                .andExpect(jsonPath("$.evidence.documentLabel").value("통장 사본"))
                .andExpect(jsonPath("$.holderCheck.rule").value("REAL_NAME"));
        JsonNode reasons = json(adminGet("/v1/admin/change-requests/reject-reasons?type=CREATOR_ACCOUNT"));
        assertThat(reasons.findValuesAsText("label")).contains("예금주가 본인 실명과 일치하지 않음", "통장 사본 미첨부");

        adminPost("/v1/admin/change-requests/" + requestId + "/reject",
                Map.of("reasonType", "HOLDER_NAME_MISMATCH")).andExpect(status().isOk());
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        creatorGet(BASE + "/settlement").andExpect(jsonPath("$.account.bankName").value("국민은행"))
                .andExpect(jsonPath("$.pendingAccountChange").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT account_number_enc FROM brand_change_request WHERE request_id = ?",
                String.class, requestId)).isNull();
    }

    @Test
    @DisplayName("요청 중 — 지급 배치가 보류를 다시 판정해도 풀리지 않는다(확인 전 기존 계좌 지급 금지 · 8-0 ②) · 취소하면 풀린다")
    void batchKeepsHoldWhilePending() throws Exception {
        Settlement scheduled = seedSettlement("예정 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), scheduled.getId());
        requestChange().andExpect(status().isOk());

        payoutService.releaseBlocked(LocalDate.now());

        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getFailCode()).isEqualTo("CREATOR_ACCOUNT_UNVERIFIED");

        cancel().andExpect(status().isOk());
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("취소 — 확인 중 요청을 거두면 보류가 풀리고 계좌 그대로 · 열린 요청이 없으면 400(CHANGE_REQUEST_NOT_PENDING)")
    void cancelRequest() throws Exception {
        Settlement scheduled = seedSettlement("예정 정산", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), scheduled.getId());
        requestChange().andExpect(status().isOk());

        cancel().andExpect(status().isOk()).andExpect(jsonPath("$.pendingAccountChange").doesNotExist());
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        cancel().andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));
    }

    @Test
    @DisplayName("승인 — 새 계좌 반영 · PG 실존 확인 통과로 확인 완료 · 보류 해제 · 사업자 구분 변경 배너에는 섞이지 않는다")
    void approveApplies() throws Exception {
        requestChange().andExpect(status().isOk());
        creatorGet(BASE + "/settlement").andExpect(jsonPath("$.businessTypeChange").doesNotExist());

        adminPost("/v1/admin/change-requests/" + latestRequestId() + "/approve", Map.of()).andExpect(status().isOk());

        creatorGet(BASE + "/settlement").andExpect(jsonPath("$.account.bankName").value("신한은행"))
                .andExpect(jsonPath("$.account.verification.status").value("VERIFIED"))
                .andExpect(jsonPath("$.pendingAccountChange").doesNotExist())
                .andExpect(jsonPath("$.businessTypeChange").doesNotExist());
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions requestChange() throws Exception {
        return mockMvc.perform(put(BASE + "/settlement-account").header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("bankCode", "088",
                        "accountNumber", NEW_ACCOUNT,
                        "bankbookImageUrl", "https://cdn.showroomz.test/creator-document/bankbook.jpg",
                        "bankbookFileName", "통장사본_신한은행.jpg"))));
    }

    private ResultActions cancel() throws Exception {
        return mockMvc.perform(delete(BASE + "/settlement-account/change-request")
                .header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    private long latestRequestId() {
        return jdbc.queryForObject("SELECT MAX(request_id) FROM brand_change_request WHERE creator_id = ? "
                + "AND type = 'CREATOR_ACCOUNT'", Long.class, creator.getId());
    }
}
