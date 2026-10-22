package showroomz.api.admin.creator;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import showroomz.domain.history.entity.LoginHistory;
import showroomz.domain.history.repository.LoginHistoryRepository;
import showroomz.domain.history.type.DeviceType;
import showroomz.domain.history.type.LoginStatus;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.entity.CreatorPrivacyAccessLog;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.repository.CreatorApplicationRepository;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.creator.type.CreatorPrivacyAccessReason;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.SettlementStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 53 테스트 시나리오 2 · 3 · 4 · 7 · 8절 — 권한 · 상단 4카드 + 레일 · 마스킹 해제 세션 · 관리자 메모 · 사유 목록. 기본 흐름은
 * {@link AdminCreatorIntegrationTest}(CR-01 ~ CR-03)가 보고, 여기는 그 테스트가 덮지 않는 경계 · 판정 순서 · 부수 효과를 본다.
 */
@DisplayName("어드민 인플루언서 상세 — 권한 · 상단 카드 · 마스킹 세션 · 메모 경계")
class AdminCreatorDetailIntegrationTest extends AdminCreatorTestSupport {

    @Autowired private LoginHistoryRepository loginHistories;
    @Autowired private CreatorApplicationRepository applications;

    // ------------------------------------------------------------------ 2. 권한 · 접근

    @Test
    @DisplayName("[AU-01] 17개 엔드포인트 — 토큰 없음 401 · 소비자 · 인플루언서 · 브랜드 403 · 부수 효과 없음")
    void everyEndpointIsAdminOnly() throws Exception {
        List<Map.Entry<HttpMethod, String>> calls = List.of(
                Map.entry(HttpMethod.GET, CREATORS + "reason-options"),
                Map.entry(HttpMethod.GET, base),
                Map.entry(HttpMethod.GET, base + "/overview"),
                Map.entry(HttpMethod.GET, base + "/settlements"),
                Map.entry(HttpMethod.GET, base + "/contracts"),
                Map.entry(HttpMethod.GET, base + "/connections"),
                Map.entry(HttpMethod.GET, base + "/histories"),
                Map.entry(HttpMethod.GET, base + "/memos"),
                Map.entry(HttpMethod.POST, base + "/memos"),
                Map.entry(HttpMethod.PATCH, base + "/memos/1"),
                Map.entry(HttpMethod.DELETE, base + "/memos/1"),
                Map.entry(HttpMethod.POST, base + "/privacy-unmask"),
                Map.entry(HttpMethod.POST, base + "/privacy-lock"),
                Map.entry(HttpMethod.POST, base + "/suspend"),
                Map.entry(HttpMethod.POST, base + "/release"),
                Map.entry(HttpMethod.POST, base + "/withdrawal-requests/1/approve"),
                Map.entry(HttpMethod.POST, base + "/withdrawal-requests/1/reject"));

        for (Map.Entry<HttpMethod, String> call : calls) {
            mockMvc.perform(call(call, null)).andExpect(status().isUnauthorized());
            for (String token : List.of(consumerToken, creatorToken, brandToken)) {
                mockMvc.perform(call(call, token)).andExpect(status().isForbidden());
            }
        }
        assertThat(reloadCreator().getStatus()).isEqualTo(CreatorStatus.ACTIVE);
        assertThat(events()).isEmpty();
        assertThat(privacyLogs.findByCreatorId(creator.getId())).isEmpty();
    }

    @Test
    @DisplayName("[AU-02 · AU-03] 없는 인플루언서 — 조회 · 쓰기 전부 404 CREATOR_NOT_FOUND · 소비자 user_id 로도 404")
    void unknownCreatorIs404() throws Exception {
        String missing = CREATORS + 999_999;
        for (String path : List.of("", "/overview", "/settlements", "/contracts", "/connections", "/histories", "/memos")) {
            adminGet(missing + path).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
        }
        adminPost(missing + "/suspend", Map.of("reason", "GROUP_BUY_NONPERFORMANCE")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
        adminPost(missing + "/privacy-unmask", Map.of("reason", "CS_RESPONSE")).andExpect(status().isNotFound());
        adminPost(missing + "/memos", Map.of("content", "메모")).andExpect(status().isNotFound());

        // 경로 변수는 creator_id 다 — 인플루언서가 아닌 소비자 계정의 user_id 는 같은 404.
        Assumptions.assumeFalse(creatorRepository.existsById(consumer.getId()));
        adminGet(CREATORS + consumer.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
    }

    @Test
    @DisplayName("[AU-04 · AU-05] 사유 목록 경로는 상세로 해석되지 않는다 · 남의 메모 · 남의 탈퇴 신청은 다른 인플루언서 경로로 404")
    void crossCreatorResourcesAre404() throws Exception {
        adminGet(CREATORS + "reason-options").andExpect(status().isOk());

        Creator other = createCreator("다른_쇼룸", "other");
        long memoId = json(adminPost(base + "/memos", Map.of("content", "지민 메모")).andExpect(status().isOk()))
                .get("memoId").asLong();
        CreatorWithdrawalRequest request = savePendingRequest("본업 집중", LocalDateTime.now().minusDays(1));
        String otherBase = CREATORS + other.getId();

        adminPatch(otherBase + "/memos/" + memoId, Map.of("content", "남의 메모")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_MEMO_NOT_FOUND"));
        adminPost(otherBase + "/withdrawal-requests/" + request.getId() + "/approve", Map.of())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_FOUND"));
        adminPost(otherBase + "/withdrawal-requests/" + request.getId() + "/reject", Map.of("reason", "GROUP_BUY_REMAINING"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWAL_NOT_FOUND"));

        adminGet(base + "/memos").andExpect(jsonPath("$.content[0].content").value("지민 메모"));
        assertThat(withdrawalRequests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberWithdrawalStatus.PENDING);
    }

    // ------------------------------------------------------------------ 3. 상단 4카드 + 레일

    @Test
    @DisplayName("[DT-02 · DT-03] 기본 정보 — 쇼룸 링크 · 인스타 · 업무 이메일 · 가입일 = creator 생성 · 입점 승인일 = 승인된 신청 시각(반려 건 아님)")
    void basicCard() throws Exception {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 3, 11, 14, 8, 9);
        LocalDateTime lastLogin = LocalDateTime.of(2026, 9, 8, 9, 24, 31);
        jdbc.update("UPDATE creator SET showroom_address = ?, instagram_url = ?, created_at = ? WHERE creator_id = ?",
                "soyeon_showroom", "https://instagram.com/soyeon", joinedAt, creator.getId());
        jdbc.update("UPDATE users SET last_login_at = ? WHERE user_id = ?", lastLogin, creator.getUser().getId());

        adminGet(base).andExpect(jsonPath("$.basic.onboardingApprovedAt").value(nullValue()));

        Long rejected = application(CreatorApplicationStatusOf.REJECTED, LocalDateTime.of(2026, 3, 1, 10, 0, 1));
        Long approved = application(CreatorApplicationStatusOf.APPROVED, LocalDateTime.of(2026, 3, 15, 11, 2, 7));
        assertThat(rejected).isNotEqualTo(approved);

        adminGet(base).andExpect(status().isOk())
                .andExpect(jsonPath("$.basic.showroomName").value("글로우_지민"))
                .andExpect(jsonPath("$.basic.showroomAddress").value("soyeon_showroom"))
                .andExpect(jsonPath("$.basic.showroomUrl").value("https://showroomz.kr/soyeon_showroom"))
                .andExpect(jsonPath("$.basic.showroomPublic").value(true))
                .andExpect(jsonPath("$.basic.instagramUrl").value("https://instagram.com/soyeon"))
                .andExpect(jsonPath("$.basic.businessEmail").value("jimin-biz@showroomz.test"))
                .andExpect(jsonPath("$.basic.joinedAt").value("2026-03-11T14:08:09Z"))
                .andExpect(jsonPath("$.basic.onboardingApprovedAt").value("2026-03-15T11:02:07Z"))
                .andExpect(jsonPath("$.basic.lastLoginAt").value("2026-09-08T09:24:31Z"))
                .andExpect(jsonPath("$.basic.lastLoginDevice").value(nullValue()))
                .andExpect(jsonPath("$.rail.currentStatus.joinedAt").value("2026-03-11T14:08:09Z"))
                .andExpect(jsonPath("$.rail.currentStatus.onboardingApprovedAt").value("2026-03-15T11:02:07Z"));
    }

    @Test
    @DisplayName("[DT-04 · DT-05] 최근 로그인 기기 — 최신 이력의 User-Agent 로 판별(Edg 먼저) · 이력 없음 null · 인스타 미등록 null")
    void lastLoginDevice() throws Exception {
        adminGet(base).andExpect(jsonPath("$.basic.lastLoginDevice").value(nullValue()))
                .andExpect(jsonPath("$.basic.instagramUrl").value(nullValue()));

        Map<String, DeviceType> agents = new java.util.LinkedHashMap<>();
        agents.put("Mozilla/5.0 (Linux; Android 14; SM-S921N) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36",
                DeviceType.ANDROID);
        agents.put("Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
                DeviceType.IPHONE);
        agents.put("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0 Safari/537.36 Edg/128.0",
                DeviceType.DESKTOP_EDGE);
        agents.put("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0 Safari/537.36",
                DeviceType.DESKTOP_CHROME);
        agents.put("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 Version/17.6 Safari/605.1.15",
                DeviceType.UNKNOWN);
        agents.put("", DeviceType.UNKNOWN);
        for (Map.Entry<String, DeviceType> agent : agents.entrySet()) {
            loginHistories.save(LoginHistory.builder().user(creator.getUser()).clientIp("127.0.0.1")
                    .userAgent(agent.getKey()).status(LoginStatus.SUCCESS).build());
            adminGet(base).andExpect(jsonPath("$.basic.lastLoginDevice").value(agent.getValue().name()));
        }
    }

    @Test
    @DisplayName("[DT-06] 사업자 — 등록번호 · 등록증 · 세금계산서 발행 · 주민번호 등록 여부는 null · 상호 · 대표자 · 사업장 주소 필드 없음")
    void businessCard() throws Exception {
        makeCreatorBusiness();
        jdbc.update("UPDATE creator SET business_registration_number = ?, business_license_image_url = ? WHERE creator_id = ?",
                "128-31-90447", "https://cdn.example.com/license.png", creator.getId());

        JsonNode business = json(adminGet(base).andExpect(status().isOk())).get("business");
        assertThat(business.get("destroyed").asBoolean()).isFalse();
        assertThat(business.get("businessType").asText()).isEqualTo("BUSINESS");
        assertThat(business.get("businessTypeLabel").asText()).isEqualTo(CreatorBusinessType.BUSINESS.getDescription());
        assertThat(business.get("businessRegistrationNumber").asText()).isEqualTo("128-31-90447");
        assertThat(business.get("businessLicenseImageUrl").asText()).isEqualTo("https://cdn.example.com/license.png");
        assertThat(business.get("residentNumberRegistered").isNull()).isTrue();
        assertThat(business.get("withholdingType").asText()).isEqualTo("TAX_INVOICE");
        assertThat(business.get("withholdingLabel").asText()).isEqualTo("세금계산서 발행");
        assertThat(business.has("companyName")).isFalse();
        assertThat(business.has("representativeName")).isFalse();
        assertThat(business.has("businessAddress")).isFalse();
        assertThat(business.has("taxationType")).isFalse();
    }

    @Test
    @DisplayName("[DT-07 · DT-08] 비사업자 — 등록번호 · 등록증 null · 주민번호는 등록 여부만(false → true · 값 · 마스킹값 없음) · 구분 미정이면 전부 null")
    void individualCard() throws Exception {
        jdbc.update("UPDATE creator SET business_registration_number = ? WHERE creator_id = ?", "128-31-90447",
                creator.getId());
        adminGet(base).andExpect(jsonPath("$.business.businessType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.business.businessTypeLabel").value(CreatorBusinessType.INDIVIDUAL.getDescription()))
                .andExpect(jsonPath("$.business.businessRegistrationNumber").value(nullValue()))
                .andExpect(jsonPath("$.business.businessLicenseImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.business.residentNumberRegistered").value(false))
                .andExpect(jsonPath("$.business.withholdingType").value("WITHHOLDING_3_3"))
                .andExpect(jsonPath("$.business.withholdingLabel").value("원천징수 3.3%"));

        registerCreatorResidentNumber();
        String body = bodyOf(adminGet(base).andExpect(jsonPath("$.business.residentNumberRegistered").value(true)));
        assertThat(body).doesNotContain("900101").doesNotContain("128-31-90447");

        jdbc.update("UPDATE creator SET business_type = NULL WHERE creator_id = ?", creator.getId());
        adminGet(base).andExpect(status().isOk())
                .andExpect(jsonPath("$.business.businessType").value(nullValue()))
                .andExpect(jsonPath("$.business.businessTypeLabel").value(nullValue()))
                .andExpect(jsonPath("$.business.residentNumberRegistered").value(nullValue()))
                .andExpect(jsonPath("$.business.withholdingType").value(nullValue()))
                .andExpect(jsonPath("$.business.withholdingLabel").value(nullValue()));
    }

    @Test
    @DisplayName("[DT-01 · DT-12 · DT-14] 레일 기본 · 원값은 본문 어디에도 없다 · 운영팀 채널이 없으면 null 이고 조회가 채널을 만들지 않는다")
    void railDefaultsAndNoRawValues() throws Exception {
        long channels = operatorChannelCount();
        String body = bodyOf(adminGet(base).andExpect(status().isOk())
                .andExpect(jsonPath("$.statusLabel").value("활성"))
                .andExpect(jsonPath("$.identity.destroyed").value(false))
                .andExpect(jsonPath("$.identity.age").value(nullValue()))
                .andExpect(jsonPath("$.identity.renewedAt").value(nullValue()))
                .andExpect(jsonPath("$.settlementAccount.bankName").value("카카오뱅크"))
                .andExpect(jsonPath("$.settlementAccount.lastChange").value(nullValue()))
                .andExpect(jsonPath("$.settlementAccount.pendingBusinessTypeChange").value(nullValue()))
                .andExpect(jsonPath("$.unmask.unmasked").value(false))
                .andExpect(jsonPath("$.unmask.lastReason").value(nullValue()))
                .andExpect(jsonPath("$.rail.currentStatus.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rail.currentStatus.label").value("활성"))
                .andExpect(jsonPath("$.rail.currentStatus.tone").value("SUCCESS"))
                .andExpect(jsonPath("$.rail.currentStatus.withdrawal").value(nullValue()))
                .andExpect(jsonPath("$.rail.operatorChannelThreadId").value(nullValue()))
                .andExpect(jsonPath("$.rail.openAdjustments.length()").value(0))
                .andExpect(jsonPath("$.rail.recentHistories.length()").value(0))
                .andExpect(jsonPath("$.rail.historyTotal").value(0)));

        assertThat(body).doesNotContain(REAL_NAME).doesNotContain(PHONE).doesNotContain("06.02")
                .doesNotContain(ACCOUNT);
        assertThat(operatorChannelCount()).isEqualTo(channels);
    }

    @Test
    @DisplayName("[DT-13 · DT-15] 이슈 스레드 — 열린 조정 협의만 · 기한 오름차순 · 닫힌 협의 · 남의 협의 제외")
    void openAdjustmentsOrderedByDeadline() throws Exception {
        Settlement later = seedSettlement("가을 립밤 공구", 1_000_000, SettlementStatus.REVIEWING);
        Settlement sooner = seedSettlement("겨울 크림 공구", 1_000_000, SettlementStatus.REVIEWING);
        Settlement closed = seedSettlement("봄 쿠션 공구", 1_000_000, SettlementStatus.REVIEWING);
        long laterId = openAdjustment(later);
        long soonerId = openAdjustment(sooner);
        long closedId = openAdjustment(closed);
        LocalDateTime base0 = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE settlement_adjustment SET deadline_at = ? WHERE adjustment_id = ?", base0.plusDays(9), laterId);
        jdbc.update("UPDATE settlement_adjustment SET deadline_at = ? WHERE adjustment_id = ?", base0.plusDays(2), soonerId);
        jdbc.update("UPDATE settlement_adjustment SET status = 'AGREED', closed_at = ? WHERE adjustment_id = ?", base0,
                closedId);

        Creator other = createCreator("다른_쇼룸", "other");
        adminGet(CREATORS + other.getId()).andExpect(jsonPath("$.rail.openAdjustments.length()").value(0));

        adminGet(base).andExpect(status().isOk())
                .andExpect(jsonPath("$.rail.openAdjustments.length()").value(2))
                .andExpect(jsonPath("$.rail.openAdjustments[0].adjustmentId").value(soonerId))
                .andExpect(jsonPath("$.rail.openAdjustments[0].settlementId").value(sooner.getId()))
                .andExpect(jsonPath("$.rail.openAdjustments[0].settlementNumber").value(sooner.getSettlementNumber()))
                .andExpect(jsonPath("$.rail.openAdjustments[0].threadId").isNumber())
                .andExpect(jsonPath("$.rail.openAdjustments[1].adjustmentId").value(laterId));
    }

    @Test
    @DisplayName("[DT-10] 스튜디오 계좌 변경 → 정산 정보 카드 보조줄(변경 시각 · 이전 은행 · 뒤 4자리) · 새 계좌 마스킹 · 이력 「인플루언서 본인」")
    void accountChangeSubline() throws Exception {
        registerCreatorResidentNumber();
        if (!bankRepository.existsById("088")) {
            fixture.createBank("088", "신한은행");
        }
        Map<String, Object> change = new java.util.HashMap<>();
        change.put("bankCode", "088");
        change.put("accountNumber", "1101234567");
        change.put("bankbookImageUrl", "https://cdn.showroomz.test/creator-document/bankbook.jpg");
        change.put("bankbookFileName", "통장사본_신한은행.jpg");
        changeCreatorAccountApproved(change).andExpect(status().isOk());   // 변경 요청 → 운영자 승인(1021 기-22)

        String body = bodyOf(adminGet(base).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementAccount.bankName").value("신한은행"))
                .andExpect(jsonPath("$.settlementAccount.accountNumber").value("110***4567"))
                .andExpect(jsonPath("$.settlementAccount.lastChange.changedAt").isNotEmpty())
                .andExpect(jsonPath("$.settlementAccount.lastChange.previousBankName").value("카카오뱅크"))
                .andExpect(jsonPath("$.settlementAccount.lastChange.previousLast4").value("8842")));
        assertThat(body).doesNotContain("1101234567").doesNotContain(ACCOUNT);
        adminGet(base + "/histories").andExpect(jsonPath("$.content[0].eventType").value("SETTLEMENT_ACCOUNT_CHANGED"))
                .andExpect(jsonPath("$.content[0].tone").value("WARNING"))
                .andExpect(jsonPath("$.content[0].detail").value("카카오뱅크 → 신한은행"))
                .andExpect(jsonPath("$.content[0].actorType").value("CREATOR"))
                .andExpect(jsonPath("$.content[0].actorName").value("인플루언서 본인"))
                .andExpect(jsonPath("$.content[0].refType").value("SETTLEMENT_ACCOUNT"));
    }

    // ------------------------------------------------------------------ 4. 마스킹 해제 · 잠금

    @Test
    @DisplayName("[PV-01 · PV-02] 해제 기록 — 로그 1행(필드 3종 · 10분 고정 만료) · 이력 PRIVACY_UNMASKED(사유 라벨 · 경고 · 로그 참조) · 계좌 · 예금주는 그대로 마스킹")
    void unmaskWritesLogAndHistory() throws Exception {
        String body = bodyOf(adminPost(base + "/privacy-unmask", Map.of("reason", "SETTLEMENT_WITHHOLDING_CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.age").isNumber())
                .andExpect(jsonPath("$.unmask.unmasked").value(true))
                .andExpect(jsonPath("$.unmask.unmaskedAt").isNotEmpty())
                .andExpect(jsonPath("$.unmask.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.unmask.remainingSeconds").value(lessThanOrEqualTo(600)))
                .andExpect(jsonPath("$.settlementAccount.accountHolder").value("김*민")));
        assertThat(body).doesNotContain(ACCOUNT);

        List<CreatorPrivacyAccessLog> logs = privacyLogs.findByCreatorId(creator.getId());
        assertThat(logs).hasSize(1);
        CreatorPrivacyAccessLog log = logs.get(0);
        assertThat(log.getOperatorId()).isEqualTo(operator.getId());
        assertThat(log.getReason()).isEqualTo(CreatorPrivacyAccessReason.SETTLEMENT_WITHHOLDING_CHECK);
        assertThat(log.getFields()).isEqualTo("NAME,BIRTHDAY,PHONE");
        assertThat(Duration.between(log.getAccessedAt(), log.getExpiresAt())).isEqualTo(Duration.ofMinutes(10));
        assertThat(log.getLockedAt()).isNull();

        adminGet(base + "/histories").andExpect(jsonPath("$.content[0].eventType").value("PRIVACY_UNMASKED"))
                .andExpect(jsonPath("$.content[0].label").value("본인확인 정보 마스킹 해제"))
                .andExpect(jsonPath("$.content[0].tone").value("WARNING"))
                .andExpect(jsonPath("$.content[0].detail").value("정산·원천징수 확인"))
                .andExpect(jsonPath("$.content[0].actorName").value("김운영"))
                .andExpect(jsonPath("$.content[0].refType").value("PRIVACY_ACCESS"))
                .andExpect(jsonPath("$.content[0].refId").value(log.getId()));
    }

    @Test
    @DisplayName("[PV-06 · PV-09] 세션은 회원 1건 · 운영자 1명 — 다른 인플루언서에 실값이 새지 않고, 잠금은 내 세션만 닫는다")
    void sessionScope() throws Exception {
        Creator other = createCreator("다른_쇼룸", "other");
        jdbc.update("UPDATE creator SET real_name = ?, birthday = ?, phone_number = ? WHERE creator_id = ?", "박소연",
                "1996-01-20", "01077776666", other.getId());

        adminPost(base + "/privacy-unmask", Map.of("reason", "CS_RESPONSE")).andExpect(status().isOk());
        adminGet(CREATORS + other.getId()).andExpect(jsonPath("$.identity.unmasked").value(false))
                .andExpect(jsonPath("$.identity.name").value("박*연"));

        postAs(otherAdminToken, base + "/privacy-unmask", Map.of("reason", "CONTRACT_DISPUTE")).andExpect(status().isOk());
        assertThat(privacyLogs.findByCreatorId(creator.getId())).hasSize(2);

        mockMvc.perform(post(base + "/privacy-lock").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        adminGet(base).andExpect(jsonPath("$.identity.unmasked").value(false));
        getAs(otherAdminToken, base).andExpect(jsonPath("$.identity.unmasked").value(true))
                .andExpect(jsonPath("$.identity.name").value(REAL_NAME))
                .andExpect(jsonPath("$.unmask.operatorName").value("박운영"));
        // M4 프리필은 운영자와 무관한 그 회원의 최신 사유(18절 #11).
        adminGet(base).andExpect(jsonPath("$.unmask.lastReason").value("CONTRACT_DISPUTE"));
    }

    @Test
    @DisplayName("[PV-08 · PV-11] 잠금은 멱등(세션 없음 · 이미 잠김 · 만료 모두 204 · 첫 잠금 시각 유지) · 만료 직전은 실값 · 남은 시간은 음수가 아니다")
    void lockIdempotentAndExpiryBoundary() throws Exception {
        mockMvc.perform(post(base + "/privacy-lock").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());

        adminPost(base + "/privacy-unmask", Map.of("reason", "CS_RESPONSE")).andExpect(status().isOk());
        jdbc.update("UPDATE creator_privacy_access_log SET expires_at = ? WHERE creator_id = ?",
                LocalDateTime.now().plusSeconds(60), creator.getId());
        adminGet(base).andExpect(jsonPath("$.identity.unmasked").value(true))
                .andExpect(jsonPath("$.unmask.remainingSeconds").value(lessThanOrEqualTo(60)));

        mockMvc.perform(post(base + "/privacy-lock").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        LocalDateTime firstLock = privacyLogs.findByCreatorId(creator.getId()).get(0).getLockedAt();
        assertThat(firstLock).isNotNull();
        mockMvc.perform(post(base + "/privacy-lock").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
        assertThat(privacyLogs.findByCreatorId(creator.getId()).get(0).getLockedAt()).isEqualTo(firstLock);
        assertThat(events()).containsExactly(CreatorAdminEventType.PRIVACY_UNMASKED);
    }

    @Test
    @DisplayName("[PV-12] 사유 검증 — 없음 · null · 기타 · 소비자 사유 · 소문자는 400 INVALID_INPUT · 로그 · 이력 0")
    void unmaskReasonValidation() throws Exception {
        java.util.HashMap<String, Object> nullReason = new java.util.HashMap<>();
        nullReason.put("reason", null);
        for (Object body : List.of(Map.of(), nullReason, Map.of("reason", "OTHER"), Map.of("reason", "DELIVERY_INCIDENT"),
                Map.of("reason", "cs_response"))) {
            adminPost(base + "/privacy-unmask", body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        assertThat(privacyLogs.findByCreatorId(creator.getId())).isEmpty();
        assertThat(events()).isEmpty();
    }

    @Test
    @DisplayName("[PV-13 · PV-14] 해제 세션이 살아 있어도 탈퇴 파기가 우선 — 실값 없음 · 해제 버튼 없음 · 잠금은 204")
    void purgeWinsOverOpenSession() throws Exception {
        adminPost(base + "/privacy-unmask", Map.of("reason", "CS_RESPONSE")).andExpect(status().isOk());
        approveWithdrawal();

        String body = bodyOf(adminGet(base).andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.destroyed").value(true))
                .andExpect(jsonPath("$.identity.unmasked").value(false))
                .andExpect(jsonPath("$.identity.name").value(nullValue()))
                .andExpect(jsonPath("$.identity.phone").value(nullValue()))
                .andExpect(jsonPath("$.identity.birthday").value(nullValue()))
                .andExpect(jsonPath("$.unmask.allowed").value(false))
                .andExpect(jsonPath("$.unmask.unmasked").value(false)));
        assertThat(body).doesNotContain(REAL_NAME).doesNotContain(PHONE);
        mockMvc.perform(post(base + "/privacy-lock").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ 7. 관리자 메모

    @Test
    @DisplayName("[MM-03 · MM-04] 길이 — 없음 · null · 빈 값 · 공백 400 · 300자 200 · 301자 400 · 공백 포함 302자는 원문 길이로 400 · 앞뒤 공백은 지운다")
    void memoLength() throws Exception {
        java.util.HashMap<String, Object> nullContent = new java.util.HashMap<>();
        nullContent.put("content", null);
        for (Object body : List.of(Map.of(), nullContent, Map.of("content", ""), Map.of("content", "   "),
                Map.of("content", "가".repeat(301)), Map.of("content", " " + "가".repeat(300) + " "))) {
            adminPost(base + "/memos", body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        assertThat(events()).isEmpty();

        adminPost(base + "/memos", Map.of("content", "가".repeat(300))).andExpect(status().isOk());
        adminPost(base + "/memos", Map.of("content", "  메모  ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("메모"))
                .andExpect(jsonPath("$.modifiedAt").value(nullValue()));
        adminGet(base + "/memos").andExpect(jsonPath("$.total").value(2));
        assertThat(jdbc.queryForObject("SELECT content FROM creator_admin_memo WHERE creator_id = ? ORDER BY memo_id DESC "
                + "LIMIT 1", String.class, creator.getId())).isEqualTo("메모");
    }

    @Test
    @DisplayName("[MM-06 · MM-07 · MM-08] 24시간 경계(안 200 · 밖 403) · 작성자 판정이 먼저 · 수정은 modifiedAt 만 남기고 창을 늘리지 않는다")
    void memoEditWindow() throws Exception {
        long memoId = json(adminPost(base + "/memos", Map.of("content", "처음 메모")).andExpect(status().isOk()))
                .get("memoId").asLong();
        LocalDateTime inside = LocalDateTime.now().minusHours(24).plusMinutes(1).withNano(0);
        jdbc.update("UPDATE creator_admin_memo SET created_at = ? WHERE memo_id = ?", inside, memoId);

        adminGet(base + "/memos").andExpect(jsonPath("$.content[0].editable").value(true));
        adminPatch(base + "/memos/" + memoId, Map.of("content", "고친 메모")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("고친 메모"))
                .andExpect(jsonPath("$.modifiedAt").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT created_at FROM creator_admin_memo WHERE memo_id = ?", LocalDateTime.class,
                memoId)).isEqualTo(inside);

        LocalDateTime outside = LocalDateTime.now().minusHours(24).minusSeconds(1).withNano(0);
        jdbc.update("UPDATE creator_admin_memo SET created_at = ? WHERE memo_id = ?", outside, memoId);
        adminGet(base + "/memos").andExpect(jsonPath("$.content[0].editable").value(false));
        adminPatch(base + "/memos/" + memoId, Map.of("content", "또 고침")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CREATOR_MEMO_EDIT_WINDOW_CLOSED"));
        mockMvc.perform(patch(base + "/memos/" + memoId).header(HttpHeaders.AUTHORIZATION, otherAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("content", "남의 메모"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CREATOR_MEMO_NOT_AUTHOR"));
        adminGet(base + "/memos").andExpect(jsonPath("$.content[0].content").value("고친 메모"));
    }

    @Test
    @DisplayName("[MM-09 · MM-10 · MM-12] 삭제는 soft delete(행 남음) · 이후 수정 · 삭제 404 · 없는 메모 404 · 작성자 이름은 스냅샷")
    void memoDeleteAndSnapshot() throws Exception {
        long memoId = json(adminPost(base + "/memos", Map.of("content", "지울 메모")).andExpect(status().isOk()))
                .get("memoId").asLong();
        jdbc.update("UPDATE seller SET name = ? WHERE seller_id = ?", "김운영2", operator.getId());
        adminGet(base + "/memos").andExpect(jsonPath("$.content[0].authorName").value("김운영"));

        adminDelete(base + "/memos/" + memoId).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creator_admin_memo WHERE memo_id = ? AND deleted_at IS NOT NULL",
                Long.class, memoId)).isEqualTo(1L);
        adminGet(base + "/memos").andExpect(jsonPath("$.total").value(0));
        adminPatch(base + "/memos/" + memoId, Map.of("content", "되살림")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_MEMO_NOT_FOUND"));
        adminDelete(base + "/memos/" + memoId).andExpect(status().isNotFound());
        adminDelete(base + "/memos/999999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_MEMO_NOT_FOUND"));
        assertThat(events()).containsExactly(CreatorAdminEventType.MEMO_DELETED, CreatorAdminEventType.MEMO_CREATED);
    }

    @Test
    @DisplayName("[MM-11] 탈퇴 회원 — 메모 조회는 그대로 · 작성 · 수정 · 삭제는 409 CREATOR_WITHDRAWN(작성자 · 시간 판정보다 먼저)")
    void withdrawnCreatorMemosAreReadOnly() throws Exception {
        long memoId = json(adminPost(base + "/memos", Map.of("content", "탈퇴 전 메모")).andExpect(status().isOk()))
                .get("memoId").asLong();
        approveWithdrawal();

        adminGet(base + "/memos").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].content").value("탈퇴 전 메모"));
        adminPost(base + "/memos", Map.of("content", "탈퇴 후 메모")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWN"));
        adminPatch(base + "/memos/" + memoId, Map.of("content", "고침")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWN"));
        adminDelete(base + "/memos/" + memoId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_WITHDRAWN"));
    }

    @Test
    @DisplayName("[MM-13] 메모는 인플루언서에게 어떤 응답으로도 내려가지 않는다 — 스튜디오 프로필 · 계정 탭 · 정산 정보")
    void memoNeverReachesCreator() throws Exception {
        String secret = "운영 전용 — 고정 지급비 미지급 문의 대응 기록";
        adminPost(base + "/memos", Map.of("content", secret)).andExpect(status().isOk());

        for (String url : List.of("/v1/creator/profile", "/v1/creator/basic-info/account", "/v1/creator/basic-info/settlement")) {
            assertThat(bodyOf(creatorGet(url).andExpect(status().isOk()))).doesNotContain(secret);
        }
    }

    // ------------------------------------------------------------------ 8. 사유 목록

    @Test
    @DisplayName("[RO-01 · RO-02] 사유 목록 — 정지 7(지급 정지 = 정산 정보 불일치만 · 기타만 상세 필수) · 해제 2(시스템 전용 제외) · 반려 5 · 해제 사유 4(기타 없음)")
    void reasonOptions() throws Exception {
        JsonNode options = json(adminGet(CREATORS + "reason-options").andExpect(status().isOk()));

        assertThat(codes(options.get("suspensionReasons"))).containsExactly("AD_DISCLOSURE_OMITTED_REPEATED",
                "AD_LAW_VIOLATION_REPEATED", "GROUP_BUY_NONPERFORMANCE", "CONSUMER_RESPONSE_NEGLECT",
                "SETTLEMENT_INFO_MISMATCH", "FALSE_INFORMATION", "OTHER");   // 허위 기재(1021 기-03 · 지급 정지 아님)
        for (JsonNode option : options.get("suspensionReasons")) {
            String code = option.get("code").asText();
            assertThat(option.get("holdsPayout").asBoolean()).isEqualTo(code.equals("SETTLEMENT_INFO_MISMATCH"));
            assertThat(option.get("detailRequired").asBoolean()).isEqualTo(code.equals("OTHER"));
        }
        assertThat(options.get("suspensionReasons").get(4).get("label").asText()).isEqualTo("정산 정보 불일치");

        assertThat(codes(options.get("releaseReasons"))).containsExactly("RESOLVED", "MISTAKE");
        assertThat(options.get("releaseReasons").get(0).get("label").asText()).isEqualTo("소명 · 시정 확인");

        assertThat(codes(options.get("withdrawalRejectReasons"))).containsExactly("GROUP_BUY_REMAINING",
                "SETTLEMENT_IN_PROGRESS", "UNDELIVERED_ORDER", "DISPUTE_IN_PROGRESS", "OTHER");
        for (JsonNode option : options.get("withdrawalRejectReasons")) {
            assertThat(option.get("detailRequired").asBoolean()).isEqualTo(option.get("code").asText().equals("OTHER"));
            assertThat(option.get("holdsPayout").isNull()).isTrue();
        }

        assertThat(codes(options.get("privacyUnmaskReasons"))).containsExactly("CS_RESPONSE",
                "SETTLEMENT_WITHHOLDING_CHECK", "CONTRACT_DISPUTE", "INVESTIGATION_COOPERATION");
        for (JsonNode option : options.get("privacyUnmaskReasons")) {
            assertThat(option.get("detailRequired").asBoolean()).isFalse();
        }
    }

    // ------------------------------------------------------------------ 보조

    private enum CreatorApplicationStatusOf { APPROVED, REJECTED }

    /** 입점 신청 1건 — 처리 시각은 SQL 로 고정한다(엔티티는 now 를 쓴다). @return 신청 id */
    private Long application(CreatorApplicationStatusOf outcome, LocalDateTime processedAt) {
        CreatorApplication saved = inTransaction(() -> {
            CreatorApplication app = CreatorApplication.createApplication(creator.getUser(), SnsType.INSTAGRAM,
                    "https://instagram.com/jimin", "jimin", 12_000, "jimin-biz@showroomz.test", REAL_NAME, "1994-06-02",
                    PHONE);
            if (outcome == CreatorApplicationStatusOf.APPROVED) {
                app.approve(operator.getEmail());
            } else {
                app.reject("ETC", "자료 보완", "자료 보완", operator.getEmail());
            }
            return applications.save(app);
        });
        jdbc.update("UPDATE creator_application SET processed_at = ?, created_at = ? WHERE creator_application_id = ?",
                processedAt, processedAt.minusDays(4), saved.getId());
        return saved.getId();
    }

    private long openAdjustment(Settlement settlement) throws Exception {
        return json(creatorPost("/v1/creator/settlements/" + settlement.getId() + "/adjustment",
                Map.of("rewardAmount", 130_000, "reason", "추가 콘텐츠 2건 제작")).andExpect(status().isCreated()))
                .get("adjustmentId").asLong();
    }

    private MockHttpServletRequestBuilder call(Map.Entry<HttpMethod, String> call, String token) {
        MockHttpServletRequestBuilder builder = request(call.getKey(), call.getValue());
        if (call.getKey() != HttpMethod.GET && call.getKey() != HttpMethod.DELETE) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return token == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, token);
    }

    private static List<String> codes(JsonNode options) {
        List<String> codes = new java.util.ArrayList<>();
        options.forEach(o -> codes.add(o.get("code").asText()));
        return codes;
    }
}
