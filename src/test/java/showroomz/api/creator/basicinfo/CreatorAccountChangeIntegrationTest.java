package showroomz.api.creator.basicinfo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.FakeSettlementPartnerGateway;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorSettlementAccountHistory;
import showroomz.domain.member.creator.repository.CreatorAdminHistoryRepository;
import showroomz.domain.member.creator.repository.CreatorSettlementAccountHistoryRepository;
import showroomz.domain.member.creator.type.CreatorAccountVerificationStatus;
import showroomz.domain.member.creator.type.CreatorAdminActorType;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.service.CreatorAccountVerificationService;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스튜디오 정산 계좌 변경 · 실명 확인 · 지급 보류(ui-studio-06 T2 · T3 · T6 · 50 설계서 6절 · 14절 AV). 실명 확인은 PG 셀러 계좌 검증이다 —
 * {@code FakeSettlementPartnerGateway} 로 승인 · 실패 · 장애를 심는다. 확인은 커밋 직후 같은 스레드에서 돈다(테스트에서 바로 보인다).
 */
@DisplayName("스튜디오 정산 계좌 변경 — 실명 확인 · 확인 중 지급 보류 · 해제 · 실패 · 재시도")
class CreatorAccountChangeIntegrationTest extends SettlementTestSupport {

    private static final String BASE = "/v1/creator/basic-info";
    private static final String NEW_ACCOUNT = "1101234567";

    @Autowired private CreatorSettlementAccountHistoryRepository accountHistories;
    @Autowired private CreatorAdminHistoryRepository adminHistories;
    @Autowired private CreatorAccountVerificationService accountVerification;

    @BeforeEach
    void setUpBanks() {
        registerCreatorAccount();
        registerCreatorResidentNumber();
        registerSellerAccount();
        if (!bankRepository.existsById("088")) {
            fixture.createBank("088", "신한은행");
        }
        if (!bankRepository.existsById("004")) {
            fixture.createBank("004", "국민은행");
        }
    }

    @Test
    @DisplayName("[AV-01] 계좌 변경 — 변경 요청(기존 계좌 유지) → 운영자 승인 → 커밋 뒤 PG 검증 통과로 「확인 완료」 · 계좌 이력 · 셀러 호출은 새 계좌 · 실명 예금주")
    void changeAndVerify() throws Exception {
        mockMvc.perform(put(BASE + "/settlement-account").header(HttpHeaders.AUTHORIZATION, creatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body("088", NEW_ACCOUNT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.bankName").value("국민은행"))   // 승인 전 — 기존 계좌 그대로
                .andExpect(jsonPath("$.pendingAccountChange.bankName").value("신한은행"))
                .andExpect(jsonPath("$.pendingAccountChange.requestCode").isNotEmpty());
        assertThat(accountHistories.findFirstByCreatorIdOrderByChangedAtDescIdDesc(creator.getId())).isEmpty();
        Long requestId = jdbc.queryForObject("SELECT MAX(request_id) FROM brand_change_request WHERE creator_id = ? "
                + "AND type = 'CREATOR_ACCOUNT'", Long.class, creator.getId());
        adminPost("/v1/admin/change-requests/" + requestId + "/approve", Map.of()).andExpect(status().isOk());

        creatorGet(BASE + "/settlement").andExpect(status().isOk())
                .andExpect(jsonPath("$.account.bankName").value("신한은행"))
                .andExpect(jsonPath("$.account.accountNumber").value("****234567"))
                .andExpect(jsonPath("$.pendingAccountChange").doesNotExist())
                .andExpect(jsonPath("$.account.verification.status").value("VERIFIED"))
                .andExpect(jsonPath("$.account.verification.label").value("확인 완료"))
                .andExpect(jsonPath("$.account.verification.verifiedAt").isNotEmpty())
                .andExpect(jsonPath("$.account.bankbook.fileName").value("통장사본_신한은행.jpg"))
                .andExpect(jsonPath("$.account.bankbook.submittedAt").isNotEmpty());
        assertThat(accountHistories.findFirstByCreatorIdOrderByChangedAtDescIdDesc(creator.getId())).isPresent();
        assertThat(partnerGateway.calls(SettlementPayee.CREATOR)).last()
                .satisfies(profile -> {
                    assertThat(profile.account().accountNumber()).isEqualTo(NEW_ACCOUNT);
                    assertThat(profile.account().holder()).isEqualTo("김지민");
                });
    }

    @Test
    @DisplayName("[AV-02 · AV-04] 지급 예정 행 — 예금주 불일치면 보류(정산 계좌 확인 중) 유지 · 배치도 풀지 않음 → 다른 계좌로 확인되면 오늘 이후 예정으로 풀린다")
    void scheduledPayoutHeldThenReleased() throws Exception {
        Settlement s = scheduledSettlement("가을 공구");
        partnerGateway.failFor(SettlementPayee.CREATOR, PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name(), "예금주 불일치");

        change("088", NEW_ACCOUNT).andExpect(status().isOk());

        SettlementPayout held = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(held.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(held.getFailCode()).isEqualTo(PayoutBlockReason.CREATOR_ACCOUNT_UNVERIFIED.name());
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        creatorGet(BASE + "/settlement")
                .andExpect(jsonPath("$.account.verification.status").value("FAILED"))
                .andExpect(jsonPath("$.account.verification.failReason").value("예금주 불일치"));
        // [55 M10-07] 스튜디오 알림함 — 「정산 계좌 확인에 실패했어요 · {사유}」(D11 · 「실명」 없음) · 정산 정보 탭으로 이동
        assertThat(verificationFailedTitles()).containsExactly("정산 계좌 확인에 실패했어요 · 예금주 불일치");
        assertThat(jdbc.queryForObject("SELECT target_sub FROM notification WHERE recipient_type = 'CREATOR' "
                + "AND recipient_id = ? AND type = 'ACCOUNT_VERIFICATION_FAILED'", String.class, creator.getId()))
                .isEqualTo("settlement");

        payoutService.releaseBlocked(LocalDate.now());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        partnerGateway.approveFor(SettlementPayee.CREATOR);
        change("088", "1101234568").andExpect(status().isOk());

        SettlementPayout released = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(released.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(released.getFailCode()).isNull();
        assertThat(released.getDueDate()).isAfterOrEqualTo(LocalDate.now());
        assertThat(settlementEvents(s.getId()))
                .contains(SettlementEventType.PAYOUT_BLOCKED, SettlementEventType.PAYOUT_HOLD_RELEASED);
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("[AV-05] PG 장애 — 「확인 중」으로 남고 보류 유지 → 지급 배치 앞단 재시도로 확인 · 보류 해제")
    void retryOnPartnerOutage() throws Exception {
        Settlement s = scheduledSettlement("가을 공구");
        partnerGateway.failFor(SettlementPayee.CREATOR, PayoutBlockReason.PARTNER_NOT_READY.name(), "PG 호출 실패");

        change("088", NEW_ACCOUNT).andExpect(status().isOk());
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFYING);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        partnerGateway.approveFor(SettlementPayee.CREATOR);
        payoutService.releaseBlocked(LocalDate.now());

        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("[AV-03 · AV-09] 영향 — 예정 · 보류 건수와 합계 · 지시 나간 건은 따로 · 확정 전 제외 · 지시 나간 행은 변경해도 그대로")
    void changeImpact() throws Exception {
        Settlement scheduled = scheduledSettlement("가을 공구");
        Settlement requested = scheduledSettlement("겨울 공구");
        jdbc.update("UPDATE settlement_payout SET status = 'REQUESTED' WHERE settlement_id = ? AND payee = 'CREATOR'",
                requested.getId());
        seedSettlement("봄 공구", 500_000, SettlementStatus.REVIEWING);
        long amount = payout(scheduled.getId(), SettlementPayee.CREATOR).getAmount();

        creatorGet(BASE + "/settlement-account/change-impact").andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingPayoutCount").value(1))
                .andExpect(jsonPath("$.pendingPayoutAmount").value(amount))
                .andExpect(jsonPath("$.inFlightPayoutCount").value(1));

        partnerGateway.failFor(SettlementPayee.CREATOR, PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name(), "예금주 불일치");
        change("088", NEW_ACCOUNT).andExpect(status().isOk());
        assertThat(payout(requested.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
    }

    @Test
    @DisplayName("[AV-07] 사업자 · 세금계산서 확인 전 — 계좌가 확인돼도 그 사유로 보류가 남는다(계좌 사유만 지운다)")
    void otherHoldReasonRemains() throws Exception {
        makeCreatorBusiness();
        Settlement s = scheduledSettlement("가을 공구");
        jdbc.update("UPDATE settlement SET creator_business_type = 'BUSINESS' WHERE settlement_id = ?", s.getId());

        change("088", NEW_ACCOUNT).andExpect(status().isOk());

        SettlementPayout row = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
        assertThat(row.getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(row.getFailCode()).isNull();
    }

    @Test
    @DisplayName("[AV-08] 같은 계좌 400 · 하이픈 400 · 통장 사본 누락 400 · 없는 은행 404")
    void validation() throws Exception {
        change("004", "123456789012").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREATOR_ACCOUNT_UNCHANGED"));
        change("088", "110-123-4567").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("계좌번호는 10~16자리 숫자로 입력해 주세요."));
        Map<String, Object> noBankbook = body("088", NEW_ACCOUNT);
        noBankbook.remove("bankbookImageUrl");
        mockMvc.perform(put(BASE + "/settlement-account").header(HttpHeaders.AUTHORIZATION, creatorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(noBankbook)))
                .andExpect(status().isBadRequest());
        change("999", NEW_ACCOUNT).andExpect(status().isNotFound());
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.UNVERIFIED);
    }

    // ------------------------------------------------------------------ 시나리오 보완(50 테스트 시나리오 14-2) — 변경 API

    @Test
    @DisplayName("[AV-08 ③] 15자리 계좌 — 입력 검증은 통과(10 ~ 16자리) · PG 에 15자리 그대로 간다 · 가짜 PG 는 통과시킨다(토스 사전검사는 14자 초과 거절)")
    void fifteenDigitAccountPassesInputValidation() throws Exception {
        change("088", "123456789012345").andExpect(status().isOk())
                .andExpect(jsonPath("$.account.verification.status").value("VERIFIED"));

        assertThat(partnerGateway.calls(SettlementPayee.CREATOR)).last()
                .satisfies(profile -> assertThat(profile.account().accountNumber()).isEqualTo("123456789012345"));
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("[AX-01] 형식 — 은행 코드 2자리 · 9자리 · 17자리 · 문자 포함 · 파일명 256자 400 / 파일명 생략은 200(표시 파일명 없음)")
    void requestFormats() throws Exception {
        change("08", NEW_ACCOUNT).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("은행 코드는 3자리여야 합니다."));
        for (String invalid : List.of("123456789", "12345678901234567", "11012345a7")) {
            change("088", invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        Map<String, Object> longName = body("088", NEW_ACCOUNT);
        longName.put("bankbookFileName", "가".repeat(252) + ".jpg");
        putBody(longName).andExpect(status().isBadRequest());
        assertThat(creator().getAccountNumber()).isEqualTo("123456789012");

        Map<String, Object> noName = body("088", NEW_ACCOUNT);
        noName.remove("bankbookFileName");
        putBody(noName).andExpect(status().isOk()).andExpect(jsonPath("$.account.bankbook.fileName").doesNotExist());
    }

    @Test
    @DisplayName("[AX-02 · AX-03] 은행 · 번호 중 하나만 달라도 변경 · 바디의 예금주는 무시하고 본인 인증 실명으로 PG 에 보낸다")
    void partialDifferenceAndHolderIgnored() throws Exception {
        Map<String, Object> withHolder = body("088", "123456789012");
        withHolder.put("holderName", "소연컴퍼니");
        withHolder.put("accountHolder", "소연컴퍼니");
        putBody(withHolder).andExpect(status().isOk());
        assertThat(partnerGateway.calls(SettlementPayee.CREATOR)).last()
                .satisfies(profile -> assertThat(profile.account().holder()).isEqualTo("김지민"));

        change("088", "123456789013").andExpect(status().isOk());
        assertThat(creator().getBankName()).isEqualTo("신한은행");
        assertThat(creator().getAccountNumber()).isEqualTo("123456789013");
    }

    @Test
    @DisplayName("[AX-04] 이력 — 계좌 이력 1행(이전 국민 → 이후 신한 · 인플루언서 본인) · 처리 이력 계좌 변경 · 통장 사본 메타 · 실패 사유 초기화")
    void changeHistory() throws Exception {
        jdbc.update("UPDATE creator SET account_verification_status = 'FAILED', account_verification_code = 'ACCOUNT_HOLDER_MISMATCH' "
                + "WHERE creator_id = ?", creator.getId());

        change("088", NEW_ACCOUNT).andExpect(status().isOk());

        CreatorSettlementAccountHistory history = accountHistories
                .findFirstByCreatorIdOrderByChangedAtDescIdDesc(creator.getId()).orElseThrow();
        assertThat(history.getPreviousBankName()).isEqualTo("국민은행");
        assertThat(history.getPreviousLast4()).isEqualTo("9012");
        assertThat(history.getBankName()).isEqualTo("신한은행");
        assertThat(history.getLast4()).isEqualTo("4567");
        assertThat(history.getChangedByType()).isEqualTo(CreatorAdminActorType.CREATOR);
        assertThat(history.getChangedBy()).isEqualTo(creator.getId());
        assertThat(adminEvents()).contains(CreatorAdminEventType.SETTLEMENT_ACCOUNT_CHANGED);

        Creator changed = creator();
        assertThat(changed.getBankbookImageUrl()).isEqualTo("https://cdn.showroomz.test/creator-document/bankbook.jpg");
        assertThat(changed.getBankbookFileName()).isEqualTo("통장사본_신한은행.jpg");
        assertThat(changed.getBankbookSubmittedAt()).isNotNull();
        assertThat(changed.getAccountVerificationCode()).isNull();
    }

    // ------------------------------------------------------------------ 시나리오 보완 — 실명 확인

    @Test
    @DisplayName("[AV-06] 확인 중 계좌가 또 바뀌면 앞 결과는 버린다 — 실패든 통과든 새 계좌에 찍히지 않고, 새 계좌의 확인이 판정한다")
    void staleVerificationResultIsDiscarded() throws Exception {
        partnerGateway.failFor(SettlementPayee.CREATOR, PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name(), "예금주 불일치");
        partnerGateway.beforeNextEnsure(() -> jdbc.update("UPDATE creator SET bank_name = ?, account_number = ? WHERE creator_id = ?",
                "국민은행", "2201234567", creator.getId()));

        change("088", NEW_ACCOUNT).andExpect(status().isOk());

        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFYING);
        assertThat(creator().getAccountVerificationCode()).isNull();

        partnerGateway.approveFor(SettlementPayee.CREATOR);
        partnerGateway.beforeNextEnsure(() -> jdbc.update("UPDATE creator SET account_number = ? WHERE creator_id = ?",
                "2201234568", creator.getId()));
        assertThat(accountVerification.verify(creator.getId())).isEqualTo(CreatorAccountVerificationStatus.VERIFYING);
        assertThat(creator().getAccountVerifiedAt()).isNull();

        assertThat(accountVerification.verify(creator.getId())).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
        assertThat(partnerGateway.calls(SettlementPayee.CREATOR)).last()
                .satisfies(profile -> assertThat(profile.account().accountNumber()).isEqualTo("2201234568"));
    }

    @Test
    @DisplayName("[AX-06 · 55 M10-07] 계좌 사유 실패 전수 — 형식 오류 · 미지원 은행 · 등록 계좌 없음 · 은행 거절(라벨 없음 → 「계좌 확인 실패」) 모두 「확인 실패」 · "
            + "알림 문구 「정산 계좌 확인에 실패했어요 · {사유}」는 화면 사유와 같은 라벨")
    void accountFailureCodes() throws Exception {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(PayoutBlockReason.ACCOUNT_INVALID.name(), "계좌 형식 오류");
        labels.put(PayoutBlockReason.UNSUPPORTED_BANK.name(), "미지원 은행");
        labels.put(PayoutBlockReason.ACCOUNT_MISSING.name(), "등록 계좌 없음");
        labels.put("BANK_REJECTED", "계좌 확인 실패");
        long account = 3301234560L;
        for (Map.Entry<String, String> label : labels.entrySet()) {
            partnerGateway.failFor(SettlementPayee.CREATOR, label.getKey(), "실패");
            change("088", String.valueOf(account++)).andExpect(status().isOk());

            creatorGet(BASE + "/settlement")
                    .andExpect(jsonPath("$.account.verification.status").value("FAILED"))
                    .andExpect(jsonPath("$.account.verification.failReason").value(label.getValue()));
            assertThat(creator().getAccountVerificationCode()).isEqualTo(label.getKey());
            assertThat(verificationFailedTitles()).as(label.getKey()).last()
                    .isEqualTo("정산 계좌 확인에 실패했어요 · " + label.getValue());
        }
        assertThat(verificationFailedTitles()).hasSize(labels.size());
    }

    @Test
    @DisplayName("[AX-07] PG 대기(본인인증 · KYC)는 계좌 검증 통과 — 「실명 확인 완료」")
    void pendingIdentityCountsAsVerified() throws Exception {
        partnerGateway.pendingFor(SettlementPayee.CREATOR, FakeSettlementPartnerGateway.APPROVAL_REQUIRED);
        change("088", NEW_ACCOUNT).andExpect(status().isOk());
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);

        partnerGateway.pendingFor(SettlementPayee.CREATOR, FakeSettlementPartnerGateway.KYC_REQUIRED);
        change("088", "1101234568").andExpect(status().isOk());
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("[AX-08 · AX-11] 계좌 외 실패(셀러 수정 거절) · PG 호출 예외 — 「확인 중」으로 남고 변경 응답은 200")
    void nonAccountFailuresStayVerifying() throws Exception {
        partnerGateway.failFor(SettlementPayee.CREATOR, "ACCOUNT_SYNC_FAILED", "셀러 수정 거절");
        change("088", NEW_ACCOUNT).andExpect(status().isOk());
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFYING);

        partnerGateway.approveFor(SettlementPayee.CREATOR);
        partnerGateway.beforeNextEnsure(() -> {
            throw new IllegalStateException("PG 타임아웃");
        });
        change("088", "1101234568").andExpect(status().isOk());
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFYING);
    }

    @Test
    @DisplayName("[AX-09] 이미 확인된 계좌에 확인이 또 돌아도 확인일은 바뀌지 않는다")
    void verifyIsIdempotent() throws Exception {
        change("088", NEW_ACCOUNT).andExpect(status().isOk());
        LocalDateTime verifiedAt = creator().getAccountVerifiedAt();
        assertThat(verifiedAt).isNotNull();

        assertThat(accountVerification.verify(creator.getId())).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
        assertThat(creator().getAccountVerifiedAt()).isEqualTo(verifiedAt);
    }

    @Test
    @DisplayName("[AX-10] 재시도 배치 대상은 「확인 중」뿐 — 실패 · 확인 전은 건드리지 않는다")
    void retryTargetsOnlyVerifying() {
        Creator second = withAccount(createCreator("둘째_쇼룸", "second"), "VERIFYING");
        Creator failed = withAccount(createCreator("실패_쇼룸", "failed"), "FAILED");
        Creator unverified = withAccount(createCreator("확인전_쇼룸", "unverified"), "UNVERIFIED");
        jdbc.update("UPDATE creator SET account_verification_status = 'VERIFYING' WHERE creator_id = ?", creator.getId());

        assertThat(accountVerification.retryVerifying()).isEqualTo(2);

        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
        assertThat(statusOf(second)).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
        assertThat(statusOf(failed)).isEqualTo(CreatorAccountVerificationStatus.FAILED);
        assertThat(statusOf(unverified)).isEqualTo(CreatorAccountVerificationStatus.UNVERIFIED);
        assertThat(partnerGateway.calls(SettlementPayee.CREATOR)).hasSize(2);
    }

    // ------------------------------------------------------------------ 시나리오 보완 — 지급 보류 · 해제

    @Test
    @DisplayName("[AV-02 후반] 보류가 풀린 행은 지시 때 새 계좌로 스냅샷된다")
    void releasedPayoutUsesNewAccount() throws Exception {
        Settlement s = scheduledSettlement("가을 공구");
        change("088", NEW_ACCOUNT).andExpect(status().isOk());
        SettlementPayout released = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(released.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);

        pay(s, released.getDueDate());

        SettlementPayout paid = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(paid.getBankName()).isEqualTo("신한은행");
        assertThat(personalDataCipher.decrypt(paid.getAccountNumberEnc())).isEqualTo(NEW_ACCOUNT);
        assertThat(paid.getAccountHolder()).isEqualTo("김지민");
    }

    @Test
    @DisplayName("[AV-09 전체] 영향 — 예정 + 보류 건수 · 합계 / 지시 나간 건 / 확정 전 · 조정 협의 · 지급 완료 · 브랜드 몫 제외")
    void changeImpactAllStatuses() throws Exception {
        Settlement scheduled = scheduledSettlement("예정 공구");
        Settlement blocked = scheduledSettlement("보류 공구");
        creatorRowStatus(blocked, "BLOCKED");
        Settlement requested = scheduledSettlement("지시 공구");
        creatorRowStatus(requested, "REQUESTED");
        Settlement held = seedSettlement("조정 공구", 700_000, SettlementStatus.ADJUSTING);
        creatorRowStatus(held, "HELD");
        Settlement paid = scheduledSettlement("완료 공구");
        creatorRowStatus(paid, "PAID");
        seedSettlement("확인 중 공구", 500_000, SettlementStatus.REVIEWING);
        long amount = payout(scheduled.getId(), SettlementPayee.CREATOR).getAmount()
                + payout(blocked.getId(), SettlementPayee.CREATOR).getAmount();

        creatorGet(BASE + "/settlement-account/change-impact").andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingPayoutCount").value(2))
                .andExpect(jsonPath("$.pendingPayoutAmount").value(amount))
                .andExpect(jsonPath("$.inFlightPayoutCount").value(1));
    }

    @Test
    @DisplayName("[PB-01] 확인 중에 확정 — 인플루언서 몫만 보류 · 07b 보류 사유 「정산 계좌 확인 중」 → 확인 완료 시 풀린다")
    void confirmWhileVerifyingBlocksCreatorShare() throws Exception {
        verificationStatus("VERIFYING", null);
        Settlement s = confirm(seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING));

        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        JsonNode detail = json(adminGet("/v1/admin/settlements/" + s.getId()).andExpect(status().isOk()));
        assertThat(detail.at("/rail/blockReasons").findValuesAsText("code")).contains("CREATOR_ACCOUNT_UNVERIFIED");

        assertThat(accountVerification.verify(creator.getId())).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("[PB-02] 확인 이력 없는 기존 회원(확인 전)은 계좌 사유로 막지 않는다 — 오늘과 같은 동작")
    void unverifiedDoesNotBlock() {
        Settlement s = confirm(seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING));

        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.UNVERIFIED);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("[PB-03] 확인 실패 상태로 확정 — 보류 · 배치가 풀지 않는다 → 다른 계좌로 확인되면 풀린다")
    void failedAccountBlocksUntilChanged() throws Exception {
        verificationStatus("FAILED", "ACCOUNT_HOLDER_MISMATCH");
        Settlement s = confirm(seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING));
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        payoutService.releaseBlocked(LocalDate.now());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        change("088", NEW_ACCOUNT).andExpect(status().isOk());
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("[PB-05] 주민번호 없는 비사업자 — 주민번호는 지급 보류 사유가 아니다(1021 기-12) · 계좌가 확인되면 지급 예정으로 돌아간다")
    void residentNumberDoesNotHold() throws Exception {
        jdbc.update("UPDATE creator SET resident_registration_number_enc = NULL, resident_registration_number_masked = NULL "
                + "WHERE creator_id = ?", creator.getId());
        Settlement s = scheduledSettlement("가을 공구");

        change("088", NEW_ACCOUNT).andExpect(status().isOk());

        SettlementPayout row = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(creator().getAccountVerificationStatus()).isEqualTo(CreatorAccountVerificationStatus.VERIFIED);
        assertThat(row.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(row.getFailCode()).isNull();
    }

    @Test
    @DisplayName("[PB-06] 조정 협의 중(HELD) 행은 계좌 변경이 건드리지 않는다")
    void heldRowUntouched() throws Exception {
        Settlement s = seedSettlement("조정 공구", 1_000_000, SettlementStatus.ADJUSTING);
        jdbc.update("UPDATE settlement_payout SET status = 'HELD' WHERE settlement_id = ?", s.getId());
        partnerGateway.failFor(SettlementPayee.CREATOR, PayoutBlockReason.PARTNER_NOT_READY.name(), "PG 호출 실패");

        change("088", NEW_ACCOUNT).andExpect(status().isOk());

        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.HELD);
        assertThat(settlementEvents(s.getId())).doesNotContain(SettlementEventType.PAYOUT_BLOCKED);
    }

    @Test
    @DisplayName("[PB-07] 예정 3건 — 셋 다 보류 · 이력 1건씩 · 브랜드 몫 그대로 → 재시도 배치로 확인되면 셋 다 풀린다")
    void multipleScheduledRows() throws Exception {
        List<Settlement> settlements = List.of(scheduledSettlement("공구 1"), scheduledSettlement("공구 2"),
                scheduledSettlement("공구 3"));
        partnerGateway.failFor(SettlementPayee.CREATOR, PayoutBlockReason.PARTNER_NOT_READY.name(), "PG 호출 실패");

        change("088", NEW_ACCOUNT).andExpect(status().isOk());
        for (Settlement s : settlements) {
            assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
            assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
            assertThat(settlementEvents(s.getId())).containsOnlyOnce(SettlementEventType.PAYOUT_BLOCKED);
        }

        partnerGateway.approveFor(SettlementPayee.CREATOR);
        payoutService.releaseBlocked(LocalDate.now());
        for (Settlement s : settlements) {
            assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
            assertThat(settlementEvents(s.getId())).containsOnlyOnce(SettlementEventType.PAYOUT_HOLD_RELEASED);
        }
    }

    @Test
    @DisplayName("[PB-08] 확인 중 다시 변경 — 두 번째 보류는 0행(이력 중복 없음) · 계좌 이력은 2행")
    void consecutiveChanges() throws Exception {
        Settlement s = scheduledSettlement("가을 공구");
        partnerGateway.failFor(SettlementPayee.CREATOR, PayoutBlockReason.PARTNER_NOT_READY.name(), "PG 호출 실패");

        change("088", NEW_ACCOUNT).andExpect(status().isOk());
        change("088", "1101234568").andExpect(status().isOk());

        assertThat(settlementEvents(s.getId())).containsOnlyOnce(SettlementEventType.PAYOUT_BLOCKED);
        assertThat(accountHistories.findAll().stream().filter(h -> h.getCreatorId().equals(creator.getId()))).hasSize(2);
    }

    // ------------------------------------------------------------------ helpers

    private Creator withAccount(Creator target, String verificationStatus) {
        jdbc.update("UPDATE creator SET real_name = ?, bank_name = ?, account_number = ?, resident_registration_number_enc = ?, "
                        + "resident_registration_number_masked = ?, account_verification_status = ? WHERE creator_id = ?",
                "김둘째", "국민은행", "123456789099", personalDataCipher.encrypt(RESIDENT_NUMBER), "900101-*******",
                verificationStatus, target.getId());
        return target;
    }

    private CreatorAccountVerificationStatus statusOf(Creator target) {
        return creatorRepository.findById(target.getId()).orElseThrow().getAccountVerificationStatus();
    }

    private void verificationStatus(String status, String code) {
        jdbc.update("UPDATE creator SET account_verification_status = ?, account_verification_code = ? WHERE creator_id = ?",
                status, code, creator.getId());
    }

    private void creatorRowStatus(Settlement settlement, String status) {
        jdbc.update("UPDATE settlement_payout SET status = ? WHERE settlement_id = ? AND payee = 'CREATOR'", status,
                settlement.getId());
    }

    private List<CreatorAdminEventType> adminEvents() {
        return adminHistories.findByCreatorIdOrderByCreatedAtDescIdDesc(creator.getId()).stream()
                .map(h -> h.getEventType()).toList();
    }

    /** 계좌 변경 = 변경 요청 → 운영자 승인(1021 기-22). 요청이 실패하면 그 응답. */
    private ResultActions putBody(Map<String, Object> body) throws Exception {
        return changeCreatorAccountApproved(body);
    }

    /** 인플루언서 몫이 지시 전 예정(SCHEDULED)인 확정 정산. */
    private Settlement scheduledSettlement(String title) {
        Settlement s = seedSettlement(title, 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), s.getId());
        return s;
    }

    /** 계좌 변경 = 변경 요청 → 운영자 승인(1021 기-22). 요청이 실패하면 그 응답. */
    private ResultActions change(String bankCode, String accountNumber) throws Exception {
        return changeCreatorAccountApproved(body(bankCode, accountNumber));
    }

    private Map<String, Object> body(String bankCode, String accountNumber) {
        Map<String, Object> body = new HashMap<>();
        body.put("bankCode", bankCode);
        body.put("accountNumber", accountNumber);
        body.put("bankbookImageUrl", "https://cdn.showroomz.test/creator-document/bankbook.jpg");
        body.put("bankbookFileName", "통장사본_신한은행.jpg");
        return body;
    }

    private Creator creator() {
        return creatorRepository.findById(creator.getId()).orElseThrow();
    }

    /** 스튜디오 알림함의 계좌 확인 실패 알림 제목 — 쌓인 순. */
    private List<String> verificationFailedTitles() {
        return jdbc.queryForList("SELECT title FROM notification WHERE recipient_type = 'CREATOR' AND recipient_id = ? "
                + "AND type = 'ACCOUNT_VERIFICATION_FAILED' ORDER BY notification_id", String.class, creator.getId());
    }
}
