package showroomz.api.seller.basicinfo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.admin.brand.AdminBrandTestSupport;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.member.brand.type.BrandWithdrawalCondition;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.config.properties.MemberProperties;
import showroomz.global.service.MailService;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 브랜드 탈퇴 신청(51 설계서 10절 WD · 55 계획서 P5 · S11) — 계정 탭 조건 내역 · 대기 중 잠금 · 브랜드 몫 기준 정산 조건 · 정지 중
 * 탈퇴 설정.
 *
 * <p>불변식(WD-04): 조건마다 {@code count} = 내역 합 = {@code BrandWithdrawalEligibility.evaluate} 값이다 — 어긋나면 「N / 4 충족」이 신청 ·
 * 승인 재검증과 다르게 보인다.
 */
@DisplayName("파트너센터 브랜드 탈퇴 신청 — 조건 내역 · 대기 중 잠금 · 브랜드 몫 기준(51 설계서 WD)")
class SellerWithdrawalIntegrationTest extends AdminBrandTestSupport {

    private static final String ACCOUNT = "/v1/seller/basic-info/account";
    private static final String WITHDRAWALS = "/v1/seller/basic-info/withdrawal-requests";
    private static final String CONTRACTS = "/v1/seller/contracts";

    @Autowired private MemberProperties memberProperties;

    @Test
    @DisplayName("WD-01 · WD-04 조건 전부 0 — AVAILABLE · 4/4 · 내역 빈 배열 · 잠금 없음 · 이동 대상 4종")
    void availableWithEmptyBreakdown() throws Exception {
        BrandFixture.Brand owner = cleanBrand("탈퇴가능");

        JsonNode account = json(getAs(sellerToken(owner.seller()), ACCOUNT).andExpect(status().isOk()));
        assertThat(account.at("/withdrawal/state").asText()).isEqualTo("AVAILABLE");
        assertThat(account.at("/withdrawal/satisfiedCount").asInt()).isEqualTo(4);
        assertThat(account.at("/withdrawal/draftContractCount").asLong()).isZero();
        assertThat(account.path("identityLock").isNull()).isTrue();
        assertThat(account.path("passwordChangeable").asBoolean()).isTrue();
        assertThat(textsAt(account.at("/withdrawal/conditions"), "target"))
                .containsExactly("GROUP_BUYS", "CONTRACTS", "ORDERS", "SETTLEMENTS");
        for (JsonNode condition : account.at("/withdrawal/conditions")) {
            assertThat(condition.path("breakdown")).isEmpty();
        }
        assertInvariant(owner, account);
    }

    @Test
    @DisplayName("WD-02 · WD-04 조건 미충족 — 계약 내역 갈래 · 행위 · 지급 예정 예상일 · 조정 협의 items · 작성 중 계약 수 · 합계 불변식")
    void blockedBreakdown() throws Exception {
        BrandFixture.Brand owner = cleanBrand("탈퇴조건");
        Creator partner = createCreator("탈퇴조건쇼룸", "wd02");
        connect(owner, partner);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        seed(owner, partner, "진행 공구", now.plusDays(3), now.plusDays(10));
        contractIn(owner, partner, ContractStatus.REVIEW_PENDING, false);
        contractIn(owner, partner, ContractStatus.SIGNING, false);
        contractIn(owner, partner, ContractStatus.DRAFT, false);
        LocalDate due = LocalDate.now().plusDays(4);
        Settlement scheduled = settlementOf(owner, SettlementStatus.PAYOUT_SCHEDULED);
        setPayout(scheduled.getId(), SettlementPayee.BRAND, PayoutStatus.SCHEDULED, 900_000, null, due);
        Settlement adjusting = settlementOf(owner, SettlementStatus.ADJUSTING);

        JsonNode account = json(getAs(sellerToken(owner.seller()), ACCOUNT).andExpect(status().isOk()));
        assertThat(account.at("/withdrawal/state").asText()).isEqualTo("BLOCKED");
        assertThat(account.at("/withdrawal/draftContractCount").asLong()).isEqualTo(1);

        JsonNode contracts = condition(account, "CONTRACTS_BEFORE_CONCLUSION");
        assertThat(contracts.path("count").asLong()).isEqualTo(2);
        JsonNode review = row(contracts, "REVIEW_PENDING");
        assertThat(review.path("group").asText()).isEqualTo("MY_ACTION");
        assertThat(review.path("action").asText()).isEqualTo("CANCEL_REVIEW");
        JsonNode signing = row(contracts, "SIGNING");
        assertThat(signing.path("group").asText()).isEqualTo("OPERATOR");
        assertThat(signing.path("action").asText()).isEqualTo("CONTACT_OPERATOR");

        JsonNode settlements = condition(account, "UNSETTLED");
        assertThat(settlements.path("count").asLong()).isEqualTo(2);
        JsonNode payout = row(settlements, "PAYOUT_SCHEDULED");
        assertThat(payout.path("group").asText()).isEqualTo("WAIT");
        assertThat(payout.path("expectedDate").asText()).isEqualTo(due.toString());
        JsonNode adjust = row(settlements, "ADJUSTING");
        assertThat(adjust.path("group").asText()).isEqualTo("MY_ACTION");
        assertThat(adjust.at("/items/0/settlementId").asLong()).isEqualTo(adjusting.getId());

        assertThat(condition(account, "ACTIVE_GROUP_BUYS").path("count").asLong()).isEqualTo(1);
        assertInvariant(owner, account);
    }

    @Test
    @DisplayName("WD-03 · 4-G2 지급 정지로 브랜드 몫 보류 — BLOCKED · BRAND_SUSPENDED · 내 응답 필요 · 정산 정보 수정하기 · 정지 사유 · 신청 409")
    void suspendedPayoutBlocked() throws Exception {
        BrandFixture.Brand owner = cleanBrand("지급정지");
        Settlement s = settlementOf(owner, SettlementStatus.PAYOUT_SCHEDULED);
        setPayout(s.getId(), SettlementPayee.BRAND, PayoutStatus.SCHEDULED, 900_000, null, LocalDate.now().plusDays(3));
        suspend(owner.marketId(), "SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());

        JsonNode account = json(getAs(sellerToken(owner.seller()), ACCOUNT).andExpect(status().isOk()));
        JsonNode blocked = row(condition(account, "UNSETTLED"), "BLOCKED");
        assertThat(blocked.path("reasonCode").asText()).isEqualTo("BRAND_SUSPENDED");
        assertThat(blocked.path("group").asText()).isEqualTo("MY_ACTION");
        assertThat(blocked.path("target").asText()).isEqualTo("SETTLEMENT_ACCOUNT");
        assertThat(blocked.path("reasonLabel").asText()).contains("정산 정보 불일치");
        assertThat(blocked.path("dueNote").asText()).isEqualTo("정지 해제 다음 영업일 지급");
        assertInvariant(owner, account);

        postAs(sellerToken(owner.seller()), WITHDRAWALS, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_CONDITION_UNMET"))
                .andExpect(jsonPath("$.unmet.UNSETTLED").value(1));
    }

    @Test
    @DisplayName("[55 P5-04] 사업자 정보 허위 기재 정지로 브랜드 몫 보류 — BLOCKED 행 BRAND_SUSPENDED · 내 응답 필요 · 이동 대상은 정산 정보가 아니라 "
            + "사업자 정보(BUSINESS_INFO) · 정지 사유 라벨")
    void businessInfoSuspensionTargetsBusinessInfo() throws Exception {
        BrandFixture.Brand owner = cleanBrand("사업자허위");
        Settlement s = settlementOf(owner, SettlementStatus.PAYOUT_SCHEDULED);
        setPayout(s.getId(), SettlementPayee.BRAND, PayoutStatus.SCHEDULED, 900_000, null, LocalDate.now().plusDays(3));
        suspend(owner.marketId(), "BUSINESS_INFO_FALSIFIED", null).andExpect(status().isOk());
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        JsonNode account = json(getAs(sellerToken(owner.seller()), ACCOUNT).andExpect(status().isOk()));
        assertThat(account.at("/withdrawal/state").asText()).isEqualTo("BLOCKED");
        JsonNode blocked = row(condition(account, "UNSETTLED"), "BLOCKED");
        assertThat(blocked.path("count").asLong()).isEqualTo(1);
        assertThat(blocked.path("reasonCode").asText()).isEqualTo("BRAND_SUSPENDED");
        assertThat(blocked.path("group").asText()).isEqualTo("MY_ACTION");
        assertThat(blocked.path("target").asText()).isEqualTo("BUSINESS_INFO");
        assertThat(blocked.path("reasonLabel").asText()).contains("사업자 정보 허위 기재");
        assertInvariant(owner, account);
    }

    @Test
    @DisplayName("55 계획서 S11 — 정지 중 탈퇴 설정을 끄면 조건 4/4 여도 BLOCKED + 사유 · 신청 409 BRAND_SUSPENDED · 켜면 AVAILABLE · 신청 201")
    void suspendedWithdrawalFollowsSetting() throws Exception {
        BrandFixture.Brand owner = cleanBrand("정지설정");
        String token = sellerToken(owner.seller());
        suspend(owner.marketId(), "PRODUCT_INFO_FALSIFIED", null).andExpect(status().isOk());
        String message = "계정 이용 정지 중에는 탈퇴를 신청할 수 없습니다. 운영팀 소통 스레드로 문의해 주세요.";

        memberProperties.getSanction().setAllowWithdrawalWhileSuspended(false);
        try {
            JsonNode account = json(getAs(token, ACCOUNT).andExpect(status().isOk()));
            assertThat(account.at("/withdrawal/state").asText()).isEqualTo("BLOCKED");
            assertThat(account.at("/withdrawal/satisfiedCount").asInt()).isEqualTo(4);
            assertThat(account.at("/withdrawal/suspensionBlockedReason").asText()).isEqualTo(message);
            assertInvariant(owner, account);
            postAs(token, WITHDRAWALS, null).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("BRAND_SUSPENDED"))
                    .andExpect(jsonPath("$.message").value(message));
        } finally {
            memberProperties.getSanction().setAllowWithdrawalWhileSuspended(true);
        }
        assertThat(withdrawalRepository.findFirstByMarketIdOrderByRequestedAtDescIdDesc(owner.marketId())).isEmpty();

        JsonNode account = json(getAs(token, ACCOUNT));
        assertThat(account.at("/withdrawal/state").asText()).isEqualTo("AVAILABLE");
        assertThat(account.at("/withdrawal/suspensionBlockedReason").isNull()).isTrue();
        postAs(token, WITHDRAWALS, null).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("[55 S11-05] 대기 중인 신청이 있는 채 정지되고 정지 중 탈퇴 설정이 꺼져 있어도 — 접수된 신청은 그대로 · 계정 탭 PENDING 유지 · "
            + "suspensionBlockedReason null(1021 7-3)")
    void pendingRequestSurvivesSuspensionWhenSettingOff() throws Exception {
        BrandFixture.Brand owner = cleanBrand("대기후정지");
        String token = sellerToken(owner.seller());
        long requestId = json(postAs(token, WITHDRAWALS, null).andExpect(status().isCreated())).path("requestId").asLong();
        suspend(owner.marketId(), "PRODUCT_INFO_FALSIFIED", null).andExpect(status().isOk());

        memberProperties.getSanction().setAllowWithdrawalWhileSuspended(false);
        try {
            JsonNode account = json(getAs(token, ACCOUNT).andExpect(status().isOk()));
            assertThat(account.at("/withdrawal/state").asText()).isEqualTo("PENDING");
            JsonNode reason = account.at("/withdrawal/suspensionBlockedReason");
            assertThat(reason.isNull() || reason.isMissingNode()).as("suspensionBlockedReason = " + reason).isTrue();
            assertThat(account.at("/withdrawal/pendingRequest/requestId").asLong()).isEqualTo(requestId);
            assertThat(account.path("identityLock").asText()).isEqualTo("WITHDRAWAL_PENDING");
            assertInvariant(owner, account);
        } finally {
            memberProperties.getSanction().setAllowWithdrawalWhileSuspended(true);
        }
        assertThat(withdrawalRepository.findById(requestId).orElseThrow().isPending()).isTrue();
    }

    @Test
    @DisplayName("WD-05 · WD-08 · WD-09 대기 중 잠금 — 계정 탭 잠금 · 계정 상태 · 계약 작성 · 복제 · 검토 요청 · 비밀번호(틀려도) · 이메일 409 · "
            + "작성 중 계약 삭제는 된다 · 취소하면 바로 풀린다")
    void pendingLocksAndCancelReleases() throws Exception {
        BrandFixture.Brand owner = cleanBrand("잠금");
        String token = sellerToken(owner.seller());
        Creator partner = createCreator("잠금쇼룸", "wd08");
        Contract draft = contractIn(owner, partner, ContractStatus.DRAFT, false);
        Contract another = contractIn(owner, partner, ContractStatus.DRAFT, false);
        Contract declined = contractIn(owner, partner, ContractStatus.DECLINED, false);

        long requestId = json(postAs(token, WITHDRAWALS, Map.of("reason", "BUSINESS_CLOSED", "message", "감사했습니다"))
                .andExpect(status().isCreated())).path("requestId").asLong();

        JsonNode account = json(getAs(token, ACCOUNT));
        assertThat(account.at("/withdrawal/state").asText()).isEqualTo("PENDING");
        assertThat(account.path("identityLock").asText()).isEqualTo("WITHDRAWAL_PENDING");
        assertThat(account.path("emailChangeable").asBoolean()).isFalse();
        assertThat(account.path("passwordChangeable").asBoolean()).isFalse();
        assertThat(account.at("/withdrawal/pendingRequest/requesterEmail").asText()).isEqualTo(owner.email());
        getAs(token, "/v1/seller/account-status").andExpect(jsonPath("$.withdrawalPending").value(true));

        postAs(token, WITHDRAWALS, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_ALREADY_PENDING"));
        expectPending(postAs(token, CONTRACTS, Map.of()));
        expectPending(postAs(token, CONTRACTS + "/" + declined.getId() + "/duplicate", null));
        expectPending(postAs(token, CONTRACTS + "/" + draft.getId() + "/review-request", Map.of()));
        expectPending(patchAs(token, "/v1/seller/basic-info/account/password", Map.of("currentPassword", "wrong-password",
                "newPassword", "NewPassw0rd!", "newPasswordConfirm", "NewPassw0rd!")));
        expectPending(patchAs(token, "/v1/seller/basic-info/account/email", Map.of("currentPassword", RAW_PASSWORD,
                "newEmail", "new-lock@showroomz.test")));
        deleteAs(token, CONTRACTS + "/" + another.getId()).andExpect(status().isNoContent());

        postAs(token, WITHDRAWALS + "/" + requestId + "/cancel", null).andExpect(status().isNoContent());
        patchAs(token, "/v1/seller/basic-info/account/password", Map.of("currentPassword", RAW_PASSWORD,
                "newPassword", "NewPassw0rd!", "newPasswordConfirm", "NewPassw0rd!")).andExpect(status().isNoContent());
        JsonNode after = json(getAs(token, ACCOUNT));
        assertThat(after.path("identityLock").isNull()).isTrue();
        assertThat(after.path("passwordChangeable").asBoolean()).isTrue();
        getAs(token, "/v1/seller/account-status").andExpect(jsonPath("$.withdrawalPending").value(false));
    }

    @Test
    @DisplayName("WD-11 어드민 반려 뒤 — PENDING 아님 · lastRejection 사유 · 안내 · 잠금 해제")
    void rejectedReleasesLock() throws Exception {
        BrandFixture.Brand owner = cleanBrand("반려잠금");
        String token = sellerToken(owner.seller());
        long requestId = json(postAs(token, WITHDRAWALS, null).andExpect(status().isCreated())).path("requestId").asLong();

        reject(owner.marketId(), requestId, Map.of("reason", "UNCLOSED_ORDERS", "note", "반품 1건을 마친 뒤 다시 신청해 주세요."))
                .andExpect(status().isOk());

        JsonNode account = json(getAs(token, ACCOUNT));
        assertThat(account.at("/withdrawal/state").asText()).isEqualTo("AVAILABLE");
        assertThat(account.at("/withdrawal/lastRejection/reasonLabel").asText()).isEqualTo("미종결 주문 잔존");
        assertThat(account.at("/withdrawal/lastRejection/note").asText()).isEqualTo("반품 1건을 마친 뒤 다시 신청해 주세요.");
        assertThat(account.path("identityLock").isNull()).isTrue();
    }

    @Test
    @DisplayName("WD-12 · WD-13 (D-1) 브랜드 몫 지급 완료 · 인플루언서 몫만 보류 · 분배 실패 — 조건 ④ 0 · 신청 201 · 어드민 승인 200")
    void brandShareOnlyCounts() throws Exception {
        BrandFixture.Brand owner = cleanBrand("몫기준");
        Settlement blocked = settlementOf(owner, SettlementStatus.PAYOUT_SCHEDULED);
        setPayout(blocked.getId(), SettlementPayee.BRAND, PayoutStatus.PAID, 900_000, null, LocalDate.now().minusDays(1));
        setPayout(blocked.getId(), SettlementPayee.CREATOR, PayoutStatus.BLOCKED, 50_000, "TAX_INVOICE_UNVERIFIED", null);
        Settlement failed = settlementOf(owner, SettlementStatus.PAYOUT_FAILED);
        setPayout(failed.getId(), SettlementPayee.BRAND, PayoutStatus.PAID, 900_000, null, LocalDate.now().minusDays(1));
        setPayout(failed.getId(), SettlementPayee.CREATOR, PayoutStatus.FAILED, 50_000, null, null);
        setPayout(failed.getId(), SettlementPayee.PLATFORM, PayoutStatus.PAID, 50_000, null, null);
        setPayout(blocked.getId(), SettlementPayee.PLATFORM, PayoutStatus.PAID, 50_000, null, null);

        assertThat(eligibility.evaluate(owner.marketId()).get(BrandWithdrawalCondition.UNSETTLED)).isZero();
        JsonNode account = json(getAs(sellerToken(owner.seller()), ACCOUNT));
        assertThat(condition(account, "UNSETTLED").path("satisfied").asBoolean()).isTrue();
        assertInvariant(owner, account);

        long requestId = json(postAs(sellerToken(owner.seller()), WITHDRAWALS, null).andExpect(status().isCreated()))
                .path("requestId").asLong();
        approve(owner.marketId(), requestId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("WD-15 (D-3) 결과 메일 — 반려는 로그인 이메일 · 마켓명 · 사유 · 안내 · 승인은 파기 전에 읽은 이메일 · 마켓명")
    void resultMails() throws Exception {
        BrandFixture.Brand owner = cleanBrand("결과메일");
        String token = sellerToken(owner.seller());
        long first = json(postAs(token, WITHDRAWALS, null).andExpect(status().isCreated())).path("requestId").asLong();
        reject(owner.marketId(), first, Map.of("reason", "UNSETTLED", "note", "정산이 끝난 뒤 다시 신청해 주세요."))
                .andExpect(status().isOk());
        verify(mailService).sendMemberWithdrawalRejectedEmail(eq(owner.email()), eq(MailService.WithdrawalAudience.BRAND),
                eq("결과메일"), eq("진행 중 정산 잔존"), eq("정산이 끝난 뒤 다시 신청해 주세요."), any(LocalDateTime.class));

        long second = json(postAs(token, WITHDRAWALS, null).andExpect(status().isCreated())).path("requestId").asLong();
        approve(owner.marketId(), second).andExpect(status().isOk());
        verify(mailService).sendMemberWithdrawalApprovedEmail(eq(owner.email()), eq(MailService.WithdrawalAudience.BRAND),
                eq("결과메일"), any(LocalDateTime.class));
    }

    // ------------------------------------------------------------------ helpers

    private void expectPending(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        actions.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRAND_WITHDRAWAL_PENDING"));
    }

    /** WD-04 — 조건마다 count = 내역 합 = 판정식 · AVAILABLE ⇔ 전부 0 ∧ 정지 중 신청 불가 아님(S11). */
    private void assertInvariant(BrandFixture.Brand owner, JsonNode account) {
        var evaluated = eligibility.evaluate(owner.marketId());
        boolean allZero = true;
        for (JsonNode condition : account.at("/withdrawal/conditions")) {
            BrandWithdrawalCondition key = BrandWithdrawalCondition.valueOf(condition.path("code").asText());
            long sum = 0;
            for (JsonNode row : condition.path("breakdown")) {
                sum += row.path("count").asLong();
            }
            assertThat(condition.path("count").asLong()).as(key.name()).isEqualTo(sum).isEqualTo(evaluated.get(key));
            allZero &= evaluated.get(key) == 0;
        }
        String state = account.at("/withdrawal/state").asText();
        if (!"PENDING".equals(state)) {
            boolean suspensionBlocked = account.at("/withdrawal/suspensionBlockedReason").isTextual();
            assertThat(state).isEqualTo(allZero && !suspensionBlocked ? "AVAILABLE" : "BLOCKED");
        }
    }

    private static JsonNode condition(JsonNode account, String code) {
        for (JsonNode condition : account.at("/withdrawal/conditions")) {
            if (condition.path("code").asText().equals(code)) {
                return condition;
            }
        }
        throw new AssertionError("조건 없음: " + code);
    }

    private static JsonNode row(JsonNode condition, String key) {
        List<String> keys = new ArrayList<>();
        for (JsonNode row : condition.path("breakdown")) {
            if (row.path("key").asText().equals(key)) {
                return row;
            }
            keys.add(row.path("key").asText());
        }
        throw new AssertionError("내역 없음: " + key + " · 있는 것 " + keys);
    }
}
