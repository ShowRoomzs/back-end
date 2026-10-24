package showroomz.api.admin.creator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.member.creator.entity.CreatorSanction;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.domain.member.creator.type.CreatorSuspensionReason;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.config.properties.MemberProperties;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 53 테스트 시나리오 9 · 10 · 11절 — 정지 · 해제의 입력 · 부수 효과 · 지급 보류 연동 · 서버 가드(엔드포인트 경유). 기본 흐름은
 * {@link AdminCreatorIntegrationTest}(CR-04 · CR-05 · CR-05b)가 보고, 여기는 가드를 <b>실제 스튜디오 · 셀러 · 어드민 API</b>로 확인한다.
 */
@DisplayName("어드민 인플루언서 정지 — 입력 · 지급 보류 · 서버 가드")
class AdminCreatorSanctionIntegrationTest extends AdminCreatorTestSupport {

    private static final String CDN = "https://cdn.example.com/";
    private static final String CREATOR_POSTS = "/v1/creator/posts";

    @Autowired private MemberProperties memberProperties;

    // ------------------------------------------------------------------ 9-1. 정지

    @Test
    @DisplayName("[SP-03] 운영팀 채널이 없으면 정지 트랜잭션이 만든다 — 카드 1장 · 상세에 스레드 id")
    void suspendCreatesOperatorChannel() throws Exception {
        assertThat(operatorChannelCount()).isZero();
        suspend("AD_LAW_VIOLATION_REPEATED", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.rail.operatorChannelThreadId").isNumber());
        assertThat(operatorChannelCount()).isEqualTo(1);
        assertThat(cardTypes()).containsExactly(MessageCardType.ACCOUNT_SUSPENDED);
        CreatorSanction sanction = sanctions.findByCreatorIdAndReleasedAtIsNull(creator.getId()).orElseThrow();
        assertThat(sanction.getSuspendCardId()).isNotNull();
    }

    @Test
    @DisplayName("[SP-04] 입력 검증 — 사유 없음 · 모르는 값 400 · 기타 + 상세 없음 · 공백 400 · 상세 501자 400 · 행 · 카드 · 이력 0")
    void suspendValidation() throws Exception {
        adminPost(base + "/suspend", Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        suspend("FAKE", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        suspend("OTHER", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREATOR_SUSPEND_DETAIL_REQUIRED"));
        suspend("OTHER", "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREATOR_SUSPEND_DETAIL_REQUIRED"));
        suspend("GROUP_BUY_NONPERFORMANCE", "가".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(reloadCreator().getStatus()).isEqualTo(CreatorStatus.ACTIVE);
        assertThat(sanctions.existsByCreatorIdAndReleasedAtIsNull(creator.getId())).isFalse();
        assertThat(cardTypes()).isEmpty();
        assertThat(events()).isEmpty();
    }

    @Test
    @DisplayName("[SP-05] 상세는 앞뒤 공백을 지워 저장 · 이력 「라벨 — 상세」 / 상세 없는 정형 사유는 NULL · 이력은 라벨만")
    void suspendDetailNormalized() throws Exception {
        suspend("OTHER", "  허위 후기 유도  ").andExpect(status().isOk())
                .andExpect(jsonPath("$.rail.currentStatus.suspension.reasonDetail").value("허위 후기 유도"));
        adminGet(base + "/histories").andExpect(jsonPath("$.content[0].detail").value("기타(직접 입력) — 허위 후기 유도"));
        adminPost(base + "/release", Map.of()).andExpect(status().isOk());

        suspend("GROUP_BUY_NONPERFORMANCE", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.rail.currentStatus.suspension.reasonDetail").value(nullValue()));
        adminGet(base + "/histories").andExpect(jsonPath("$.content[0].detail").value("공구 진행 불이행"));
    }

    @Test
    @DisplayName("[SP-09] 탈퇴 심사 중에도 정지된다 — 정지와 심사는 직교(심사 카드 유지)")
    void suspendWhileWithdrawalPending() throws Exception {
        savePendingRequest("본업 집중", LocalDateTime.now().minusDays(1));
        suspend("CONSUMER_RESPONSE_NEGLECT", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.rail.withdrawalReview.reasonText").value("본업 집중"))
                .andExpect(jsonPath("$.rail.currentStatus.suspension.reasonLabel").value("소비자 응대 불이행"));
    }

    @Test
    @DisplayName("[SP-10 · RL-06] 사유 7종(1021 기-03 허위 기재 추가) — 지급 정지는 정산 정보 불일치만 · 정지 1건 = 1행(해제해도 남는다) · 이력 · 카드가 정지마다 쌓인다")
    void everyReasonAndRepeatedSuspension() throws Exception {
        int reasons = CreatorSuspensionReason.values().length;
        assertThat(reasons).isEqualTo(7);
        for (CreatorSuspensionReason reason : CreatorSuspensionReason.values()) {
            boolean held = reason == CreatorSuspensionReason.SETTLEMENT_INFO_MISMATCH;
            suspend(reason.name(), reason == CreatorSuspensionReason.OTHER ? "직접 입력 사유" : null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rail.currentStatus.suspension.reasonLabel").value(reason.getLabel()))
                    .andExpect(jsonPath("$.rail.currentStatus.suspension.payoutHeld").value(held))
                    .andExpect(jsonPath("$.rail.currentStatus.suspension.payoutNote").value(held
                            ? "지급 정지 · 정지 해제 다음 영업일 지급" : "유지 · 사유가 정산 정보와 무관"));
            adminPost(base + "/release", Map.of()).andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_sanction WHERE creator_id = ? AND released_at IS NOT NULL",
                Long.class, creator.getId())).isEqualTo((long) reasons);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_sanction WHERE creator_id = ? AND payout_held = TRUE",
                Long.class, creator.getId())).isEqualTo(1L);
        assertThat(events().stream().filter(e -> e == CreatorAdminEventType.SUSPENDED)).hasSize(reasons);
        assertThat(cardTypes()).hasSize(reasons * 2);   // 정지 · 해제 카드
    }

    @Test
    @DisplayName("[SP-11] 상태는 활성인데 열린 정지 행이 남아 있으면 새 정지를 만들지 않는다 — 409 CREATOR_ALREADY_SUSPENDED")
    void openSanctionRowBlocksNewSuspension() throws Exception {
        inTransaction(() -> sanctions.save(CreatorSanction.suspend(creator.getId(),
                CreatorSuspensionReason.GROUP_BUY_NONPERFORMANCE, null, operator.getId(), "김운영", LocalDateTime.now())));
        suspend("AD_DISCLOSURE_OMITTED_REPEATED", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_ALREADY_SUSPENDED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_sanction WHERE creator_id = ?", Long.class,
                creator.getId())).isEqualTo(1L);
        assertThat(reloadCreator().getStatus()).isEqualTo(CreatorStatus.ACTIVE);
    }

    // ------------------------------------------------------------------ 9-2. 해제

    @Test
    @DisplayName("[RL-02 · RL-03] 해제 — 본문 없이 RESOLVED · 오정지 + 메모 · 시스템 전용 사유 · 모르는 값 · 메모 501자는 400(상태 불변)")
    void releaseInputs() throws Exception {
        suspend("GROUP_BUY_NONPERFORMANCE", null).andExpect(status().isOk());
        adminPost(base + "/release", Map.of("reason", "WITHDRAWN")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("탈퇴 종결은 운영자가 고를 수 있는 해제 사유가 아닙니다."));
        adminPost(base + "/release", Map.of("reason", "FAKE")).andExpect(status().isBadRequest());
        adminPost(base + "/release", Map.of("note", "가".repeat(501))).andExpect(status().isBadRequest());
        assertThat(reloadCreator().getStatus()).isEqualTo(CreatorStatus.SUSPENDED);

        mockMvc.perform(post(base + "/release").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        adminGet(base + "/histories").andExpect(jsonPath("$.content[0].detail").value("소명 · 시정 확인"));
        CreatorSanction first = inTransaction(() -> sanctions.findAll().stream()
                .filter(s -> s.getCreatorId().equals(creator.getId())).findFirst().orElseThrow());
        assertThat(first.getReleaseReason().name()).isEqualTo("RESOLVED");
        assertThat(first.getReleasedByName()).isEqualTo("김운영");
        assertThat(first.getReleaseCardId()).isNotNull();

        suspend("GROUP_BUY_NONPERFORMANCE", null).andExpect(status().isOk());
        adminPost(base + "/release", Map.of("reason", "MISTAKE", "note", "  사업자 정보 재확인 ")).andExpect(status().isOk());
        adminGet(base + "/histories").andExpect(jsonPath("$.content[0].detail").value("오정지 — 사업자 정보 재확인"));
    }

    @Test
    @DisplayName("[RL-07] 정지 행 없는 정지(V193 백필) — 상세 정지 블록 null · 해제는 상태만 되돌리고 카드 · 지급 변화 없음 · 이력 참조 없음")
    void releaseBackfilledSuspension() throws Exception {
        jdbc.update("UPDATE creator SET status = 'SUSPENDED' WHERE creator_id = ?", creator.getId());
        adminGet(base).andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.rail.currentStatus.suspension").value(nullValue()));

        adminPost(base + "/release", Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(cardTypes()).isEmpty();
        adminGet(base + "/histories").andExpect(jsonPath("$.content[0].eventType").value("SUSPENSION_RELEASED"))
                .andExpect(jsonPath("$.content[0].refId").value(nullValue()));
    }

    // ------------------------------------------------------------------ 10. 지급 보류

    @Test
    @DisplayName("[PH-04] 이미 지시된(지급 처리 중) · 지급 완료 행은 정지가 건드리지 않는다 — 예정 행만 보류")
    void suspendLeavesRequestedAndPaidRows() throws Exception {
        registerCreatorResidentNumber();
        Settlement requested = scheduled("A 공구");
        Settlement paid = scheduled("B 공구");
        Settlement scheduled = scheduled("C 공구");
        creatorPayoutStatus(requested, PayoutStatus.REQUESTED);
        creatorPayoutStatus(paid, PayoutStatus.PAID);

        suspend("SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());

        assertThat(payout(requested.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(payout(paid.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(payout(scheduled.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        for (Settlement s : List.of(requested, paid, scheduled)) {
            assertThat(payout(s.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
            assertThat(payout(s.getId(), SettlementPayee.PLATFORM).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        }
        assertThat(settlementEvents(scheduled.getId())).contains(SettlementEventType.PAYOUT_BLOCKED);
        assertThat(settlementEvents(requested.getId())).doesNotContain(SettlementEventType.PAYOUT_BLOCKED);
    }

    @Test
    @DisplayName("[PH-07] 해제 다음 영업일 — 목요일(10.08) 해제 · 금요일 공휴일(10.09) · 주말을 건너 월요일(10.12) 지급 예정")
    void releaseDueSkipsHolidayAndWeekend() {
        fixHolidays();
        registerCreatorResidentNumber();
        Settlement s = scheduled("가을 립밤 공구");
        LocalDateTime thursday = LocalDateTime.of(2026, 10, 8, 15, 0);

        sanctionService.suspend(creator.getId(), CreatorSuspensionReason.SETTLEMENT_INFO_MISMATCH, null, operator.getId(),
                "김운영", thursday);
        assertThat(payout(s.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);

        sanctionService.release(creator.getId(), null, null, operator.getId(), "김운영", thursday.plusHours(1));
        SettlementPayout released = payout(s.getId(), SettlementPayee.CREATOR);
        assertThat(released.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(released.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    @DisplayName("[PH-08] 지급과 무관한 사유 — 정지 · 확정 · 해제 내내 지급 행 정상 · PAYOUT_BLOCKED 없음 · 레일 「유지」")
    void unrelatedReasonKeepsPayout() throws Exception {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        Settlement scheduledOne = scheduled("A 공구");
        Settlement reviewing = seedSettlement("B 공구", 1_000_000, SettlementStatus.REVIEWING);

        suspend("CONSUMER_RESPONSE_NEGLECT", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.rail.currentStatus.suspension.payoutHeld").value(false));
        assertThat(payout(scheduledOne.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);

        Settlement confirmed = confirm(reviewing);
        assertThat(payout(confirmed.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);

        adminPost(base + "/release", Map.of()).andExpect(status().isOk());
        assertThat(payout(scheduledOne.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(settlementEvents(scheduledOne.getId())).doesNotContain(SettlementEventType.PAYOUT_BLOCKED,
                SettlementEventType.PAYOUT_HOLD_RELEASED);
        assertThat(settlementEvents(confirmed.getId())).doesNotContain(SettlementEventType.PAYOUT_BLOCKED);
    }

    @Test
    @DisplayName("[PH-10] 지급 판정은 정지 행의 payout_held 스냅샷 — 정지 뒤 사유 값이 바뀌어도 확정은 인플루언서 몫을 막는다")
    void payoutHoldFollowsSnapshot() throws Exception {
        registerSellerAccount();
        registerCreatorAccount();
        registerCreatorResidentNumber();
        Settlement reviewing = seedSettlement("A 공구", 1_000_000, SettlementStatus.REVIEWING);
        suspend("SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());
        jdbc.update("UPDATE creator_sanction SET reason = 'GROUP_BUY_NONPERFORMANCE' WHERE creator_id = ?", creator.getId());

        Settlement confirmed = confirm(reviewing);
        assertThat(payout(confirmed.getId(), SettlementPayee.CREATOR).getStatus()).isEqualTo(PayoutStatus.BLOCKED);
        assertThat(payout(confirmed.getId(), SettlementPayee.BRAND).getStatus()).isEqualTo(PayoutStatus.SCHEDULED);
        adminGet(base).andExpect(jsonPath("$.rail.currentStatus.suspension.payoutHeld").value(true));
    }

    // ------------------------------------------------------------------ 11. 서버 가드 — 엔드포인트 경유

    @Test
    @DisplayName("[GD-01 · GD-02 · GD-09] 정지 중 게시물 — 새 게시 · 첫 게시 전환 409 · 임시저장 · 게시중 수정 · 삭제는 허용 · 해제하면 즉시 게시")
    void postGuardThroughStudio() throws Exception {
        long published = postId(createPost("정지 전 게시물", "PUBLISH").andExpect(status().isCreated()));
        long other = postId(createPost("정지 전 다른 게시물", "PUBLISH").andExpect(status().isCreated()));
        long draft = postId(createPost("정지 전 임시저장", "DRAFT").andExpect(status().isCreated()));
        suspend("AD_DISCLOSURE_OMITTED_REPEATED", null).andExpect(status().isOk());

        createPost("정지 중 새 게시물", "PUBLISH").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_SUSPENDED"))
                .andExpect(jsonPath("$.message").value("계정 이용 정지 중에는 새 게시물을 등록할 수 없습니다."));
        createPost("정지 중 임시저장", "DRAFT").andExpect(status().isCreated());
        mockMvc.perform(post(CREATOR_POSTS + "/" + draft + "/publish").header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREATOR_SUSPENDED"));
        updatePost(draft, "임시저장을 게시로", "PUBLISH").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_SUSPENDED"));
        updatePost(published, "게시중 수정은 된다", "PUBLISH").andExpect(status().isOk());
        mockMvc.perform(delete(CREATOR_POSTS + "/" + other).header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isNoContent());

        adminPost(base + "/release", Map.of()).andExpect(status().isOk());
        mockMvc.perform(post(CREATOR_POSTS + "/" + draft + "/publish").header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[GD-03] 공구 게시물 등록 — 플래그 켜짐(기본 · 1017 결정 9)이면 정지 중 409 CREATOR_SUSPENDED · 끄면 제출된다")
    void groupBuyPostSubmissionFlag() throws Exception {
        GroupBuy blocked = seedPreparing();
        GroupBuy open = seedPreparing();
        suspend("AD_DISCLOSURE_OMITTED_REPEATED", null).andExpect(status().isOk());

        submitGroupBuyPost(blocked).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_SUSPENDED"))
                .andExpect(jsonPath("$.message").value("계정 이용 정지 중에는 공구 게시물을 등록할 수 없습니다."));

        memberProperties.getSanction().setBlockGroupBuyPost(false);
        try {
            submitGroupBuyPost(open).andExpect(status().isOk());
        } finally {
            memberProperties.getSanction().setBlockGroupBuyPost(true);
        }
    }

    @Test
    @DisplayName("[GD-04 · GD-05 · GD-06] 서명 요청 — 브랜드 검토 요청 · 운영자 검토 통과 모두 정지 409 · 심사 중 409 · 둘 다면 정지가 먼저 · 계약 상태 불변")
    void contractSignatureGuardThroughApis() throws Exception {
        Long draft = contractIn(ContractStatus.DRAFT);
        Long reviewing = contractIn(ContractStatus.REVIEW_PENDING);

        savePendingRequest("본업 집중", LocalDateTime.now().minusDays(1));
        reviewRequest(draft).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_COUNTERPARTY_WITHDRAWAL_PENDING"));
        reviewApprove(reviewing).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_COUNTERPARTY_WITHDRAWAL_PENDING"));

        suspend("GROUP_BUY_NONPERFORMANCE", null).andExpect(status().isOk());
        reviewRequest(draft).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_COUNTERPARTY_SUSPENDED"));
        reviewApprove(reviewing).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_COUNTERPARTY_SUSPENDED"));

        assertThat(contractStatus(draft)).isEqualTo("DRAFT");
        assertThat(contractStatus(reviewing)).isEqualTo("REVIEW_PENDING");
    }

    @Test
    @DisplayName("[GD-07] 정지 중에는 새 연결을 수락할 수 없다(1017 결정 9) — 409 CREATOR_SUSPENDED · 해제하면 204")
    void suspendedCreatorCannotAcceptConnection() throws Exception {
        BrandFixture.Brand other = fixture.createBrand("other-brand@showroomz.test", "다른랩");
        Connection requested = connectionRepository.save(Connection.requestPair(other.market(), creator));
        suspend("AD_DISCLOSURE_OMITTED_REPEATED", null).andExpect(status().isOk());

        mockMvc.perform(post("/v1/creator/connections/" + requested.getId() + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, creatorToken)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_SUSPENDED"))
                .andExpect(jsonPath("$.message").value("계정 이용 정지 중에는 새 연결을 수락할 수 없습니다."));

        adminPost(base + "/release", Map.of()).andExpect(status().isOk());
        mockMvc.perform(post("/v1/creator/connections/" + requested.getId() + "/accept")
                .header(HttpHeaders.AUTHORIZATION, creatorToken)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("[GD-10 · PH-11] 스튜디오 프로필 sanction — 활성 · 정지(사유 · 시각 · 지급 정지) · 심사 중 표시 재료")
    void profileSanctionBlock() throws Exception {
        creatorGet("/v1/creator/profile").andExpect(status().isOk())
                .andExpect(jsonPath("$.sanction.status").value("ACTIVE"))
                .andExpect(jsonPath("$.sanction.reasonLabel").value(nullValue()))
                .andExpect(jsonPath("$.sanction.suspendedAt").value(nullValue()))
                .andExpect(jsonPath("$.sanction.payoutHeld").value(false))
                .andExpect(jsonPath("$.sanction.withdrawalPending").value(false));

        suspend("SETTLEMENT_INFO_MISMATCH", null).andExpect(status().isOk());
        creatorGet("/v1/creator/profile")
                .andExpect(jsonPath("$.sanction.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.sanction.reasonLabel").value("정산 정보 불일치"))
                .andExpect(jsonPath("$.sanction.suspendedAt").isNotEmpty())
                .andExpect(jsonPath("$.sanction.payoutHeld").value(true));

        adminPost(base + "/release", Map.of()).andExpect(status().isOk());
        savePendingRequest("본업 집중", LocalDateTime.now().minusDays(1));
        creatorGet("/v1/creator/profile")
                .andExpect(jsonPath("$.sanction.status").value("ACTIVE"))
                .andExpect(jsonPath("$.sanction.payoutHeld").value(false))
                .andExpect(jsonPath("$.sanction.withdrawalPending").value(true));
    }

    // ------------------------------------------------------------------ 보조

    private ResultActions suspend(String reason, String detail) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        if (detail != null) {
            body.put("reasonDetail", detail);
        }
        return adminPost(base + "/suspend", body);
    }

    /** 지급 예정 정산 — 3행 SCHEDULED · 예정일 D+2. */
    private Settlement scheduled(String title) {
        Settlement s = seedSettlement(title, 1_000_000, SettlementStatus.PAYOUT_SCHEDULED);
        jdbc.update("UPDATE settlement_payout SET status = 'SCHEDULED', due_date = ? WHERE settlement_id = ?",
                LocalDate.now().plusDays(2), s.getId());
        return s;
    }

    private void creatorPayoutStatus(Settlement s, PayoutStatus status) {
        jdbc.update("UPDATE settlement_payout SET status = ? WHERE settlement_id = ? AND payee = 'CREATOR'", status.name(),
                s.getId());
    }

    private ResultActions createPost(String content, String action) throws Exception {
        return mockMvc.perform(post(CREATOR_POSTS).header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(postPayload(content, action))));
    }

    private ResultActions updatePost(long postId, String content, String action) throws Exception {
        return mockMvc.perform(put(CREATOR_POSTS + "/" + postId).header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(postPayload(content, action))));
    }

    private long postId(ResultActions actions) throws Exception {
        return json(actions).get("postId").asLong();
    }

    private static Map<String, Object> postPayload(String content, String action) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("imageUrl", CDN + "cover.jpg");
        image.put("originalUrl", CDN + "origin-cover.jpg");
        image.put("width", 1080);
        image.put("height", 1350);
        image.put("fileSize", 2_048_000);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        payload.put("images", List.of(image));
        payload.put("action", action);
        return payload;
    }

    private ResultActions submitGroupBuyPost(GroupBuy target) throws Exception {
        return creatorPost("/v1/creator/group-buys/" + target.getId() + "/post/submission",
                Map.of("title", "여름 수분 세럼, 제가 쓰던 그 조합", "content", "건조한 여름에도 속까지 촉촉하게 — 크림과 세럼을 같이 쓰면 좋아요."));
    }

    private ResultActions reviewRequest(Long contractId) throws Exception {
        return mockMvc.perform(post("/v1/seller/contracts/" + contractId + "/review-request")
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    private ResultActions reviewApprove(Long contractId) throws Exception {
        return adminPost("/v1/admin/contracts/" + contractId + "/review/approve", Map.of());
    }

    private String contractStatus(Long contractId) {
        return jdbc.queryForObject("SELECT status FROM contract WHERE contract_id = ?", String.class, contractId);
    }
}
