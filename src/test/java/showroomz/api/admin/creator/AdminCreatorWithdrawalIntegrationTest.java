package showroomz.api.admin.creator;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorSanction;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.service.CreatorWithdrawalEligibility;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.domain.member.creator.type.CreatorWithdrawalCondition;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.ThreadStatus;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 53 테스트 시나리오 12 · 13절 — 탈퇴 승인(파기 · 보존 · 연결 종료 · 계정 축) · 반려 · 조건 판정식 · 운영팀 채널 · 쇼룸 노출 · 브랜드 검색 연동.
 * 기본 흐름은 {@link AdminCreatorIntegrationTest}(CR-06 · CR-06b · CR-08)가 본다.
 */
@DisplayName("어드민 인플루언서 탈퇴 심사 — 승인 · 반려 · 조건식 · 연동")
class AdminCreatorWithdrawalIntegrationTest extends AdminCreatorTestSupport {

    private static final String CDN = "https://cdn.example.com/";
    private static final String REGISTRATION_NUMBER = "128-31-90447";

    // ------------------------------------------------------------------ 12-2. 승인

    @Test
    @DisplayName("[WA-02 ~ WA-07] 승인 — 즉시 파기 집합 NULL · 세무 보존(주민번호 · 등록번호) · 업무 이메일 보존 · 연결 · 1:1 스레드 종료 · 스튜디오 자격만 해지 · 응답에 원값 없음")
    void approvalPurgesAndPreserves() throws Exception {
        registerCreatorResidentNumber();
        LocalDateTime issued = LocalDateTime.now().minusDays(30).withNano(0);
        jdbc.update("UPDATE creator SET business_registration_number = ?, bankbook_image_url = ?, business_license_image_url = ?, "
                        + "instagram_url = ?, showroom_address = ?, connection_code = ?, connection_code_issued_at = ? "
                        + "WHERE creator_id = ?", REGISTRATION_NUMBER, CDN + "bankbook.png", CDN + "license.png",
                "https://instagram.com/jimin", "jimin_room", "JIMIN01", issued, creator.getId());
        BrandFixture.Brand other = fixture.createBrand("other-brand@showroomz.test", "다른랩");
        Connection requested = connectionRepository.save(Connection.requestPair(other.market(), creator));
        MessageThread requestedThread = messageThreadRepository.save(MessageThread.openFor(requested));
        adminPost(base + "/memos", Map.of("content", "탈퇴 전 메모")).andExpect(status().isOk());
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        CreatorWithdrawalRequest request = withdrawalService.request(creator.getId(), "콘텐츠 활동을 중단합니다",
                LocalDateTime.now());
        String businessEmail = jdbc.queryForObject("SELECT business_email FROM creator WHERE creator_id = ?", String.class,
                creator.getId());
        String showroomName = jdbc.queryForObject("SELECT showroom_name FROM creator WHERE creator_id = ?", String.class,
                creator.getId());

        String body = bodyOf(adminPost(approveUrl(request.getId()), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.statusTone").value("NEUTRAL"))
                .andExpect(jsonPath("$.identity.destroyed").value(true))
                .andExpect(jsonPath("$.business.destroyed").value(true))
                .andExpect(jsonPath("$.business.businessType").value(nullValue()))
                .andExpect(jsonPath("$.business.businessRegistrationNumber").value(nullValue()))
                .andExpect(jsonPath("$.business.residentNumberRegistered").value(nullValue()))
                .andExpect(jsonPath("$.settlementAccount.destroyed").value(true))
                .andExpect(jsonPath("$.settlementAccount.accountNumber").value(nullValue()))
                .andExpect(jsonPath("$.basic.showroomUrl").value(nullValue()))
                .andExpect(jsonPath("$.basic.showroomPublic").value(false))
                .andExpect(jsonPath("$.basic.instagramUrl").value(nullValue()))
                .andExpect(jsonPath("$.basic.businessEmail").value("jimin-biz@showroomz.test"))
                .andExpect(jsonPath("$.rail.currentStatus.withdrawal.withdrawnAt").isNotEmpty())
                .andExpect(jsonPath("$.rail.currentStatus.withdrawal.requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.rail.currentStatus.withdrawal.operatorName").value("김운영"))
                .andExpect(jsonPath("$.rail.currentStatus.suspension").value(nullValue()))
                .andExpect(jsonPath("$.rail.withdrawalReview").value(nullValue()))
                .andExpect(jsonPath("$.rail.openAdjustments.length()").value(0))
                .andExpect(jsonPath("$.rail.recentHistories[0].eventType").value("WITHDRAWAL_APPROVED"))
                .andExpect(jsonPath("$.rail.recentHistories[0].detail").value("콘텐츠 활동을 중단합니다")));
        assertThat(body).doesNotContain(REAL_NAME).doesNotContain(PHONE).doesNotContain(ACCOUNT).doesNotContain("900101")
                .doesNotContain(REGISTRATION_NUMBER).doesNotContain("JIMIN01").doesNotContain("bankbook");

        // WA-03 신청 · 상태
        CreatorWithdrawalRequest approved = withdrawalRequests.findById(request.getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(MemberWithdrawalStatus.APPROVED);
        assertThat(approved.getProcessedBy()).isEqualTo(operator.getId());
        assertThat(approved.getProcessedByName()).isEqualTo("김운영");
        assertThat(approved.getProcessedAt()).isNotNull();
        Map<String, Object> counts = jdbc.queryForMap("SELECT approved_group_buy_count, approved_contract_count, "
                + "approved_unsettled_count FROM creator_withdrawal_request WHERE request_id = ?", request.getId());
        assertThat(counts.values()).allMatch(v -> ((Number) v).intValue() == 0);

        // WA-04 파기 · 보존
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM creator WHERE creator_id = ?", creator.getId());
        for (String purged : List.of("real_name", "birthday", "phone_number", "bank_name", "account_number",
                "bankbook_image_url", "business_license_image_url", "instagram_url", "connection_code",
                "connection_code_issued_at")) {
            assertThat(row.get(purged)).as(purged).isNull();
        }
        assertThat(row.get("channel_url")).isEqualTo("");
        assertThat(row.get("status")).isEqualTo("WITHDRAWN");
        assertThat(((Number) row.get("withdrawn_by")).longValue()).isEqualTo(operator.getId());
        assertThat(row.get("withdrawn_at")).isNotNull();
        assertThat(row.get("privacy_purged_at")).isNotNull();
        assertThat(row.get("business_email")).isEqualTo("jimin-biz@showroomz.test");
        assertThat(row.get("resident_registration_number_enc")).isNotNull();
        assertThat(row.get("resident_registration_number_masked")).isEqualTo("900101-1******");
        assertThat(row.get("business_registration_number")).isEqualTo(REGISTRATION_NUMBER);
        assertThat(row.get("business_type")).isEqualTo("INDIVIDUAL");
        assertThat(row.get("showroom_name")).isEqualTo("글로우_지민");
        assertThat(row.get("showroom_address")).isEqualTo("jimin_room");

        // WA-05 연결 · 계정 · 기록 보존
        assertThat(connectionRepository.findById(connection.getId()).orElseThrow().getStatus().name()).isEqualTo("DISCONNECTED");
        assertThat(connectionRepository.findById(requested.getId()).orElseThrow().getStatus().name()).isEqualTo("DISCONNECTED");
        assertThat(messageThreadRepository.findById(pairThreadId).orElseThrow().getStatus()).isEqualTo(ThreadStatus.DORMANT);
        assertThat(messageThreadRepository.findById(requestedThread.getId()).orElseThrow().getStatus())
                .isEqualTo(ThreadStatus.DORMANT);
        assertThat(jdbc.queryForObject("SELECT role_type FROM users WHERE user_id = ?", String.class,
                creator.getUser().getId())).isEqualTo("USER");
        assertThat(userStatus()).isEqualTo(UserStatus.NORMAL);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_admin_memo WHERE creator_id = ? AND deleted_at IS NULL",
                Long.class, creator.getId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contract WHERE creator_id = ? AND deleted_at IS NULL", Long.class,
                creator.getId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT status FROM group_buy WHERE group_buy_id = ?", String.class, groupBuy.getId()))
                .isEqualTo("ENDED");

        // WA-06 이력 순서 · 연결 종료 문장
        assertThat(events()).startsWith(CreatorAdminEventType.WITHDRAWAL_APPROVED, CreatorAdminEventType.CONNECTIONS_ENDED,
                CreatorAdminEventType.WITHDRAWAL_REQUESTED, CreatorAdminEventType.MEMO_CREATED);
        adminGet(base + "/histories").andExpect(jsonPath("$.content[1].detail").value("탈퇴 승인 · 연결 2건 일괄 종료"))
                .andExpect(jsonPath("$.content[1].actorName").value("시스템"));
        verifyApprovedMail(businessEmail, showroomName);
    }

    private void verifyApprovedMail(String businessEmail, String showroomName) {
        // 55 계획서 M10 — 결과 메일은 파기 전에 읽은 업무 이메일 · 쇼룸명으로 간다.
        org.mockito.Mockito.verify(mailService).sendMemberWithdrawalApprovedEmail(
                org.mockito.ArgumentMatchers.eq(businessEmail),
                org.mockito.ArgumentMatchers.eq(showroomz.global.service.MailService.WithdrawalAudience.CREATOR),
                org.mockito.ArgumentMatchers.eq(showroomName), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    @DisplayName("[WA-08 · WA-09] 조건 미충족 — unmet 은 0건 키 없이 선언 순서 · 메시지에 건수 + 「반려로 처리해 주세요」 · 매번 전체 롤백")
    void unmetConditionsRollBack() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        CreatorWithdrawalRequest request = savePendingRequest("본업 집중", LocalDateTime.now().minusDays(2));

        contractIn(ContractStatus.SIGNING);
        approve(request.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_CONDITION_UNMET"))
                .andExpect(jsonPath("$.unmet.UNFINISHED_CONTRACTS").value(1))
                .andExpect(jsonPath("$.unmet.UNFINISHED_GROUP_BUYS").doesNotExist())
                .andExpect(jsonPath("$.unmet.UNSETTLED").doesNotExist());

        seedSettlement("분배 실패 공구", 1_000_000, SettlementStatus.PAYOUT_FAILED);
        seedIn(GroupBuyStatus.READY);
        JsonNode error = json(approve(request.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("탈퇴 조건이 충족되지 않았습니다 — 끝나지 않은 공구 1건 · "
                        + "끝나지 않은 계약 1건 · 미정산 1건. 반려로 처리해 주세요.")));
        List<String> keys = new ArrayList<>();
        error.get("unmet").fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("UNFINISHED_GROUP_BUYS", "UNFINISHED_CONTRACTS", "UNSETTLED");

        assertThat(withdrawalRequests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberWithdrawalStatus.PENDING);
        Creator after = reloadCreator();
        assertThat(after.getStatus()).isEqualTo(CreatorStatus.ACTIVE);
        assertThat(after.getRealName()).isEqualTo(REAL_NAME);
        assertThat(after.isPrivacyPurged()).isFalse();
        assertThat(connectionRepository.findById(connection.getId()).orElseThrow().getStatus().name()).isEqualTo("CONNECTED");
        assertThat(jdbc.queryForObject("SELECT role_type FROM users WHERE user_id = ?", String.class,
                creator.getUser().getId())).isEqualTo("CREATOR");
        assertThat(events()).isEmpty();
    }

    @Test
    @DisplayName("[WA-10] 정지 중 승인 — 열린 정지를 시스템이 종결(탈퇴 승인으로 종결 · 처리자 없음) · 해제 카드 · 해제 알림 없음")
    void approvalClosesOpenSuspension() throws Exception {
        adminPost(base + "/suspend", Map.of("reason", "GROUP_BUY_NONPERFORMANCE")).andExpect(status().isOk());
        approveWithdrawal();

        CreatorSanction closed = inTransaction(() -> sanctions.findAll().stream()
                .filter(s -> s.getCreatorId().equals(creator.getId())).findFirst().orElseThrow());
        assertThat(closed.getReleasedAt()).isNotNull();
        assertThat(closed.getReleaseReason().name()).isEqualTo("WITHDRAWN");
        assertThat(closed.getReleasedBy()).isNull();
        assertThat(closed.getReleaseCardId()).isNull();
        assertThat(cardTypes()).containsExactly(MessageCardType.ACCOUNT_SUSPENDED);
        assertThat(reloadCreator().getStatus()).isEqualTo(CreatorStatus.WITHDRAWN);

        JsonNode released = null;
        for (JsonNode h : json(adminGet(base + "/histories")).get("content")) {
            if (h.get("eventType").asText().equals("SUSPENSION_RELEASED")) {
                released = h;
            }
        }
        assertThat(released).isNotNull();
        assertThat(released.get("actorType").asText()).isEqualTo("SYSTEM");
        assertThat(released.get("detail").asText()).isEqualTo("탈퇴 승인으로 종결");
    }

    @Test
    @DisplayName("[WA-11] 정산 정보 불일치 정지로 지급이 멈춘 채 신청 — 미정산이 남아 승인 409(정지를 피하려는 탈퇴 길이 없다)")
    void payoutHoldBlocksApproval() throws Exception {
        adminPost(base + "/suspend", Map.of("reason", "SETTLEMENT_INFO_MISMATCH")).andExpect(status().isOk());
        seedSettlement("보류 공구", 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        CreatorWithdrawalRequest request = savePendingRequest("본업 집중", LocalDateTime.now().minusDays(1));

        approve(request.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.unmet.UNSETTLED").value(1));
        assertThat(reloadCreator().getStatus()).isEqualTo(CreatorStatus.SUSPENDED);
    }

    @Test
    @DisplayName("[WA-12 · WA-13 · WA-14] 처리된 신청(반려 · 취소 · 승인)은 409 NOT_PENDING · 없는 신청 404")
    void approveNonPendingRequests() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        CreatorWithdrawalRequest rejected = withdrawalService.request(creator.getId(), "첫 신청", LocalDateTime.now());
        reject(rejected.getId(), Map.of("reason", "GROUP_BUY_REMAINING")).andExpect(status().isOk());
        approve(rejected.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_PENDING"));

        CreatorWithdrawalRequest canceled = withdrawalService.request(creator.getId(), "두 번째 신청", LocalDateTime.now());
        withdrawalService.cancel(creator.getId(), canceled.getId(), LocalDateTime.now());
        approve(canceled.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_PENDING"));

        approve(999_999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_FOUND"));
        assertThat(reloadCreator().getStatus()).isEqualTo(CreatorStatus.ACTIVE);
    }

    @Test
    @DisplayName("[WA-16] 남은 연결이 없으면 연결 일괄 종료 이력을 남기지 않는다")
    void approvalWithoutOpenConnections() throws Exception {
        jdbc.update("UPDATE connection SET status = 'DISCONNECTED', disconnected_at = ? WHERE connection_id = ?",
                LocalDateTime.now().minusDays(3), connection.getId());
        approveWithdrawal();
        assertThat(events()).doesNotContain(CreatorAdminEventType.CONNECTIONS_ENDED)
                .startsWith(CreatorAdminEventType.WITHDRAWAL_APPROVED);
    }

    @Test
    @DisplayName("[WA-17 · RL-05] 탈퇴 뒤 — 정지 · 해제 409 CREATOR_WITHDRAWN · 잠금 204 · 조회 7종은 모두 200")
    void withdrawnCreatorIsReadOnly() throws Exception {
        approveWithdrawal();
        adminPost(base + "/suspend", Map.of("reason", "GROUP_BUY_NONPERFORMANCE")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWN"));
        adminPost(base + "/release", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWN"));
        mockMvc.perform(post(base + "/privacy-lock").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        for (String path : List.of("", "/overview", "/settlements", "/contracts", "/connections", "/histories", "/memos")) {
            adminGet(base + path).andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------ 12-3. 반려

    @Test
    @DisplayName("[WR-03 · WR-04 · 55 M10] 사유만으로 반려(안내 NULL · 이력은 라벨만) · 정지 중 반려는 정지 그대로 · 반려 메일(사유 · 안내)")
    void rejectionVariants() throws Exception {
        CreatorWithdrawalRequest first = savePendingRequest("본업 집중", LocalDateTime.now().minusDays(1));
        reject(first.getId(), Map.of("reason", "GROUP_BUY_REMAINING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(jdbc.queryForObject("SELECT reject_note FROM creator_withdrawal_request WHERE request_id = ?", String.class,
                first.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT reject_reason FROM creator_withdrawal_request WHERE request_id = ?",
                String.class, first.getId())).isEqualTo("GROUP_BUY_REMAINING");
        adminGet(base + "/histories").andExpect(jsonPath("$.content[0].eventType").value("WITHDRAWAL_REJECTED"))
                .andExpect(jsonPath("$.content[0].tone").value("INFO"))
                .andExpect(jsonPath("$.content[0].detail").value("끝나지 않은 공구 남음"));

        adminPost(base + "/suspend", Map.of("reason", "CONSUMER_RESPONSE_NEGLECT")).andExpect(status().isOk());
        CreatorWithdrawalRequest second = savePendingRequest("다시 신청", LocalDateTime.now());
        reject(second.getId(), Map.of("reason", "OTHER", "note", "  분쟁 종결 후 다시 신청해 주세요 ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.rail.currentStatus.suspension.reasonLabel").value("소비자 응대 불이행"));
        assertThat(sanctions.existsByCreatorIdAndReleasedAtIsNull(creator.getId())).isTrue();
        adminGet(base + "/histories").andExpect(jsonPath("$.content[0].detail")
                .value("기타(직접 입력) — 분쟁 종결 후 다시 신청해 주세요"));
        String businessEmail = jdbc.queryForObject("SELECT business_email FROM creator WHERE creator_id = ?", String.class,
                creator.getId());
        org.mockito.Mockito.verify(mailService).sendMemberWithdrawalRejectedEmail(
                org.mockito.ArgumentMatchers.eq(businessEmail),
                org.mockito.ArgumentMatchers.eq(showroomz.global.service.MailService.WithdrawalAudience.CREATOR),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("기타(직접 입력)"),
                org.mockito.ArgumentMatchers.eq("분쟁 종결 후 다시 신청해 주세요"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    @DisplayName("[WR-07] 반려 뒤 재신청 — 새 행 · 반려 행은 그대로 · 심사 카드는 새 신청")
    void reRequestAfterRejection() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        CreatorWithdrawalRequest first = withdrawalService.request(creator.getId(), "첫 신청", LocalDateTime.now());
        reject(first.getId(), Map.of("reason", "DISPUTE_IN_PROGRESS", "note", "분쟁 종결 후")).andExpect(status().isOk());

        CreatorWithdrawalRequest second = withdrawalService.request(creator.getId(), "다시 신청", LocalDateTime.now());
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(withdrawalRequests.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(MemberWithdrawalStatus.REJECTED);
        adminGet(base).andExpect(jsonPath("$.rail.withdrawalReview.requestId").value(second.getId()))
                .andExpect(jsonPath("$.rail.withdrawalReview.reasonText").value("다시 신청"));
    }

    @Test
    @DisplayName("[WR-08 · WR-09] 처리된 신청 반려 409 · 없는 신청 404 · 기타 + 안내 없음은 신청 조회보다 먼저 400")
    void rejectGuards() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        CreatorWithdrawalRequest canceled = withdrawalService.request(creator.getId(), "취소할 신청", LocalDateTime.now());
        withdrawalService.cancel(creator.getId(), canceled.getId(), LocalDateTime.now());
        reject(canceled.getId(), Map.of("reason", "GROUP_BUY_REMAINING")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_PENDING"));
        reject(999_999L, Map.of("reason", "GROUP_BUY_REMAINING")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_FOUND"));
        reject(999_999L, Map.of("reason", "OTHER")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_REJECT_DETAIL_REQUIRED"));
        reject(999_999L, Map.of("reason", "OTHER", "note", "가".repeat(501))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ------------------------------------------------------------------ 12-4. 조건 판정식

    @Test
    @DisplayName("[WE-01 · WE-05 · WE-06 · WE-07] 진행 상태 공구 4 · 끝나지 않은 계약 2(체결완료 · 거절 · 삭제 · 브랜드 전용 제외) · 미정산 4(지급 완료만 제외) · 남의 것 제외 — 심사 카드와 판정식이 같다")
    void eligibilityCounts() throws Exception {
        seedIn(GroupBuyStatus.PREPARING);
        seedIn(GroupBuyStatus.READY);
        seedIn(GroupBuyStatus.SUSPENSION_SCHEDULED);
        for (ContractStatus s : List.of(ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING, ContractStatus.DECLINED,
                ContractStatus.DRAFT)) {
            contractIn(s);
        }
        Long deleted = contractIn(ContractStatus.SIGNING);
        jdbc.update("UPDATE contract SET deleted_at = ? WHERE contract_id = ?", LocalDateTime.now(), deleted);
        for (SettlementStatus s : List.of(SettlementStatus.REVIEWING, SettlementStatus.ADJUSTING,
                SettlementStatus.PAYOUT_SCHEDULED, SettlementStatus.PAYOUT_FAILED, SettlementStatus.PAID)) {
            seedSettlement("정산 " + s.name(), 1_000_000, s);
        }
        Creator other = createCreator("다른_쇼룸", "other");
        GroupBuy othersBuy = seed(brand, other, "남의 공구", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(5));
        moveTo(othersBuy.getId(), GroupBuyStatus.IN_PROGRESS);
        inTransaction(() -> contractRepository.saveAndFlush(Contract.createDraft(brand.market(), other, null)).getId());

        CreatorWithdrawalEligibility.Result result = eligibility.evaluate(creator.getId());
        assertThat(result.count(CreatorWithdrawalCondition.UNFINISHED_GROUP_BUYS)).isEqualTo(4);
        assertThat(result.count(CreatorWithdrawalCondition.UNFINISHED_CONTRACTS)).isEqualTo(2);
        assertThat(result.count(CreatorWithdrawalCondition.UNSETTLED)).isEqualTo(4);
        assertThat(eligibility.evaluate(other.getId()).count(CreatorWithdrawalCondition.UNFINISHED_GROUP_BUYS)).isEqualTo(1);

        savePendingRequest("본업 집중", LocalDateTime.now().minusDays(3));
        adminGet(base).andExpect(jsonPath("$.rail.withdrawalReview.elapsedDays").value(3))
                .andExpect(jsonPath("$.rail.withdrawalReview.conditions.unfinishedGroupBuys").value(4))
                .andExpect(jsonPath("$.rail.withdrawalReview.conditions.unfinishedContracts").value(2))
                .andExpect(jsonPath("$.rail.withdrawalReview.conditions.unsettled").value(4));
    }

    @Test
    @DisplayName("[WE-02 · WE-03 · WE-04] 종료 · 중단 공구 — 정산 없음 + 미종결 주문이면 ① · 판매 0건 종료는 0 · 정산이 생기면 ①이 아니라 ③")
    void closedGroupBuyAwaitingOrders() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        assertThat(eligibility.evaluate(creator.getId()).eligible()).isTrue();

        GroupBuy withOrder = groupBuy;
        moveTo(withOrder.getId(), GroupBuyStatus.IN_PROGRESS);
        purchase(creamVariant, 1);
        moveTo(withOrder.getId(), GroupBuyStatus.ENDED);
        assertThat(eligibility.evaluate(creator.getId()).count(CreatorWithdrawalCondition.UNFINISHED_GROUP_BUYS)).isEqualTo(1);
        moveTo(withOrder.getId(), GroupBuyStatus.SUSPENDED);
        assertThat(eligibility.evaluate(creator.getId()).count(CreatorWithdrawalCondition.UNFINISHED_GROUP_BUYS)).isEqualTo(1);
        moveTo(withOrder.getId(), GroupBuyStatus.ENDED);

        seedSettlement("정산 생성 공구", 1_000_000, SettlementStatus.REVIEWING);
        CreatorWithdrawalEligibility.Result result = eligibility.evaluate(creator.getId());
        assertThat(result.count(CreatorWithdrawalCondition.UNFINISHED_GROUP_BUYS)).isEqualTo(1);
        assertThat(result.count(CreatorWithdrawalCondition.UNSETTLED)).isEqualTo(1);
    }

    @Test
    @DisplayName("[WE-08] 심사 카드가 0 · 0 · 0 이어도 승인 트랜잭션이 다시 판정한다 — 그 사이 생긴 준비완료 공구로 409")
    void approvalReevaluates() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        CreatorWithdrawalRequest request = withdrawalService.request(creator.getId(), "본업 집중", LocalDateTime.now());
        adminGet(base).andExpect(jsonPath("$.rail.withdrawalReview.conditions.unfinishedGroupBuys").value(0));

        seedIn(GroupBuyStatus.READY);
        approve(request.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("끝나지 않은 공구 1건")));
    }

    // ------------------------------------------------------------------ 13. 연동

    @Test
    @DisplayName("[CH-01 · CH-02] 운영팀 채널 — 정지 중 인플루언서 소명 · 운영자 답신 가능 · 탈퇴 뒤 운영자 쓰기 409 THREAD_READ_ONLY")
    void operatorChannelWritability() throws Exception {
        long threadId = json(adminPost(base + "/suspend", Map.of("reason", "AD_DISCLOSURE_OMITTED_REPEATED"))
                .andExpect(status().isOk())).get("rail").get("operatorChannelThreadId").asLong();

        creatorPost("/v1/creator/threads/" + threadId + "/messages",
                Map.of("clientMessageId", UUID.randomUUID().toString(), "content", "표기를 모두 수정했습니다."))
                .andExpect(status().is2xxSuccessful());
        adminPost("/v1/admin/threads/" + threadId + "/messages",
                Map.of("clientMessageId", UUID.randomUUID().toString(), "content", "확인 후 해제하겠습니다."))
                .andExpect(status().is2xxSuccessful());

        approveWithdrawal();
        adminPost("/v1/admin/threads/" + threadId + "/messages",
                Map.of("clientMessageId", UUID.randomUUID().toString(), "content", "답변드립니다."))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("THREAD_READ_ONLY"));
        adminGet(base).andExpect(jsonPath("$.rail.operatorChannelThreadId").value(threadId));
    }

    @Test
    @DisplayName("[CH-03 · CH-04 · CH-05] 쇼룸 노출 — 정지 중에는 남고 · 탈퇴하면 사라지며 · role_type 을 되돌려도 creator.status 가 따로 막는다")
    void showroomVisibility() throws Exception {
        jdbc.update("UPDATE creator SET showroom_address = ? WHERE creator_id = ?", "jimin_room", creator.getId());
        assertThat(listedShowroomIds()).contains(creator.getId());

        adminPost(base + "/suspend", Map.of("reason", "AD_DISCLOSURE_OMITTED_REPEATED")).andExpect(status().isOk());
        assertThat(listedShowroomIds()).contains(creator.getId());

        approveWithdrawal();
        assertThat(listedShowroomIds()).doesNotContain(creator.getId());

        jdbc.update("UPDATE users SET role_type = 'CREATOR' WHERE user_id = ?", creator.getUser().getId());
        assertThat(listedShowroomIds()).doesNotContain(creator.getId());
    }

    @Test
    @DisplayName("[CH-09] 브랜드 인플루언서 검색 — 탈퇴한 인플루언서는 결과에서 빠진다")
    void brandSearchExcludesWithdrawn() throws Exception {
        BrandFixture.Brand other = fixture.createBrand("search-brand@showroomz.test", "검색랩");
        String otherToken = sellerToken(other.seller());
        assertThat(searchedCreatorIds(otherToken)).contains(creator.getId());

        approveWithdrawal();
        assertThat(searchedCreatorIds(otherToken)).doesNotContain(creator.getId());
    }

    // ------------------------------------------------------------------ 보조

    private String approveUrl(Long requestId) {
        return base + "/withdrawal-requests/" + requestId + "/approve";
    }

    private ResultActions approve(Long requestId) throws Exception {
        return adminPost(approveUrl(requestId), Map.of());
    }

    private ResultActions reject(Long requestId, Map<String, ?> body) throws Exception {
        return adminPost(base + "/withdrawal-requests/" + requestId + "/reject", body);
    }

    private List<Long> listedShowroomIds() throws Exception {
        List<Long> ids = new ArrayList<>();
        json(getAs(consumerToken, "/v1/user/showrooms?size=50").andExpect(status().isOk())).get("content")
                .forEach(row -> ids.add(row.get("showroomId").asLong()));
        return ids;
    }

    private List<Long> searchedCreatorIds(String token) throws Exception {
        List<Long> ids = new ArrayList<>();
        json(getAs(token, "/v1/seller/connections/creators?keyword=글로우").andExpect(status().isOk())).get("content")
                .forEach(row -> ids.add(row.get("creatorId").asLong()));
        return ids;
    }
}
