package showroomz.api.admin.brand;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.member.brand.entity.BrandAdminMemo;
import showroomz.domain.member.brand.service.BrandAdminHistoryRecorder;
import showroomz.domain.member.brand.type.BrandAdminEventType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.support.BrandFixture;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 52 테스트 시나리오 8 ~ 12절 — 관리자 메모 · 처리 이력 병합 · 발송 기한 보조줄 · 사유 목록 · 권한 · 개인정보.
 */
@DisplayName("어드민 브랜드 메모 · 이력 · 발송 기한 · 사유 목록 · 권한 · 개인정보(52 시나리오 MM · HS · SL · RO · AU · PV)")
class AdminBrandMemoHistoryIntegrationTest extends AdminBrandTestSupport {

    @Autowired private BrandAdminHistoryRecorder historyRecorder;

    // ------------------------------------------------------------------ 8 메모

    @Test
    @DisplayName("MM-01 · MM-02 길이 · 작성 응답 — 공백 3종 400 · 1 · 300자 200 · 301자 400 · 앞 공백으로 301자면 400(공백 제거 전 길이) · 다듬어 저장 · 200 응답 · 이력")
    void memoLengthAndCreate() throws Exception {
        String memos = base() + "/memos";
        adminPost(memos, Map.of()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        adminPost(memos, Map.of("content", "")).andExpect(status().isBadRequest());
        adminPost(memos, Map.of("content", "   ")).andExpect(status().isBadRequest());
        adminPost(memos, Map.of("content", "가".repeat(301))).andExpect(status().isBadRequest());
        adminPost(memos, Map.of("content", " " + "가".repeat(300))).andExpect(status().isBadRequest());
        assertThat(memoRepository.findAll()).isEmpty();

        adminPost(memos, Map.of("content", "가")).andExpect(status().isOk());
        adminPost(memos, Map.of("content", "가".repeat(300))).andExpect(status().isOk());
        JsonNode created = json(adminPost(memos, Map.of("content", "  미정산액 318만 원 확인  ")).andExpect(status().isOk()));
        assertThat(created.path("content").asText()).isEqualTo("미정산액 318만 원 확인");
        assertThat(created.path("authorName").asText()).isEqualTo("김운영");
        assertThat(created.path("editable").asBoolean()).isTrue();
        assertThat(created.path("modifiedAt").isNull()).isTrue();
        assertThat(created.path("createdAt").isMissingNode()).isFalse();

        JsonNode latest = json(adminGet(base() + "/histories")).path("content").get(0);
        assertThat(latest.path("type").asText()).isEqualTo("MEMO_CREATED");
        assertThat(latest.path("refType").asText()).isEqualTo("BRAND_ADMIN_MEMO");
        assertThat(latest.path("refId").asLong()).isEqualTo(created.path("memoId").asLong());
        assertThat(latest.path("detail").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT admin_memo FROM market WHERE market_id = ?", String.class, brand.marketId()))
                .isNull();
    }

    @Test
    @DisplayName("MM-03 · MM-04 수정 권한 · 24시간 경계 — 작성자 검사가 먼저 · 23시간 59분은 수정 가능 · 24시간 1분은 닫힘 · editable 도 같은 경계")
    void memoEditWindow() throws Exception {
        Seller other = fixture.createAdmin("other-ops@showroomz.test", "박운영");
        String otherToken = adminToken(other);
        Long memoId = createMemo("수정 대상");
        String memo = base() + "/memos/" + memoId;

        patchAs(adminToken, memo, Map.of("content", "수정본")).andExpect(status().isOk())
                .andExpect(jsonPath("$.modifiedAt").exists());
        assertThat(json(adminGet(base() + "/histories")).path("content").get(0).path("type").asText())
                .isEqualTo("MEMO_UPDATED");

        backdateMemo(memoId, LocalDateTime.now().minusHours(23).minusMinutes(59));
        patchAs(adminToken, memo, Map.of("content", "창 안")).andExpect(status().isOk());
        adminGet(base() + "/memos").andExpect(jsonPath("$.content[0].editable").value(true));

        backdateMemo(memoId, LocalDateTime.now().minusHours(24).minusMinutes(1));
        patchAs(adminToken, memo, Map.of("content", "창 밖")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BRAND_MEMO_EDIT_WINDOW_CLOSED"));
        adminGet(base() + "/memos").andExpect(jsonPath("$.content[0].editable").value(false));
        patchAs(otherToken, memo, Map.of("content", "남의 수정")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BRAND_MEMO_NOT_AUTHOR"));
        assertThat(memoRepository.findById(memoId).orElseThrow().getContent()).isEqualTo("창 안");
    }

    @Test
    @DisplayName("MM-05 삭제 — 204 · 목록 · 건수에서 빠짐 · 이력 · 삭제된 메모 · 남의 브랜드 메모 · 없는 id 는 404")
    void memoDelete() throws Exception {
        Long memoId = createMemo("지울 메모");
        createMemo("남길 메모");
        BrandFixture.Brand other = cleanBrand("남의메모");
        Long othersMemo = json(adminPost(base(other.marketId()) + "/memos", Map.of("content", "남의 메모"))).path("memoId").asLong();

        deleteAs(adminToken, base() + "/memos/" + memoId).andExpect(status().isNoContent());
        assertThat(memoRepository.findById(memoId).orElseThrow().getDeletedAt()).isNotNull();
        adminGet(base() + "/memos").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content").value("남길 메모"));
        brandDetail(brand.marketId()).andExpect(jsonPath("$.memos.total").value(1));
        assertThat(json(adminGet(base() + "/histories")).path("content").get(0).path("type").asText())
                .isEqualTo("MEMO_DELETED");

        for (Long id : List.of(memoId, othersMemo, 999_999L)) {
            patchAs(adminToken, base() + "/memos/" + id, Map.of("content", "x")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BRAND_MEMO_NOT_FOUND"));
            deleteAs(adminToken, base() + "/memos/" + id).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("MM-06 목록 — 최신순(작성 시각 · id 역순) · 페이지 · 행마다 요청 운영자 기준 editable")
    void memoList() throws Exception {
        Seller other = fixture.createAdmin("other-ops@showroomz.test", "박운영");
        LocalDateTime base = LocalDateTime.now().withNano(0).minusHours(1);
        for (int i = 0; i < 25; i++) {
            boolean mine = i % 2 == 0;
            memoRepository.save(BrandAdminMemo.write(brand.marketId(), mine ? operator.getId() : other.getId(),
                    mine ? "김운영" : "박운영", "메모 " + i, base.plusSeconds(i)));
        }
        JsonNode first = json(adminGet(base() + "/memos?page=1&size=20"));
        assertThat(first.path("content")).hasSize(20);
        assertThat(first.path("content").get(0).path("content").asText()).isEqualTo("메모 24");
        assertThat(first.path("content").get(0).path("editable").asBoolean()).isTrue();
        assertThat(first.path("content").get(1).path("editable").asBoolean()).isFalse();
        JsonNode second = json(adminGet(base() + "/memos?page=2&size=20"));
        assertThat(textsAt(second.path("content"), "content")).containsExactly("메모 4", "메모 3", "메모 2", "메모 1", "메모 0");
    }

    @Test
    @DisplayName("MM-07 · MM-08 옛 market.admin_memo 는 읽지도 쓰지도 않는다 · 메모는 브랜드 응답 어디에도 없다")
    void legacyColumnAndBrandInvisibility() throws Exception {
        jdbc.update("UPDATE market SET admin_memo = '옛 덮어쓰기 메모' WHERE market_id = ?", brand.marketId());
        createMemo("운영자만 보는 판단 기록");

        assertThat(adminGet(base()).andReturn().getResponse().getContentAsString()).doesNotContain("옛 덮어쓰기 메모");
        assertThat(adminGet(base() + "/memos").andReturn().getResponse().getContentAsString()).doesNotContain("옛 덮어쓰기 메모");
        assertThat(jdbc.queryForObject("SELECT admin_memo FROM market WHERE market_id = ?", String.class, brand.marketId()))
                .isEqualTo("옛 덮어쓰기 메모");
        for (String url : List.of("/v1/seller/basic-info/business", "/v1/seller/basic-info/settlement",
                "/v1/seller/basic-info/manager", "/v1/seller/basic-info/delivery-policy", "/v1/seller/basic-info/account")) {
            String body = getAs(brandToken, url).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).as(url).doesNotContain("운영자만 보는 판단 기록");
        }
    }

    // ------------------------------------------------------------------ 9 처리 이력

    @Test
    @DisplayName("HS-01 이력 14종 라벨 · 톤 — 저장 9종 · 파생 5종이 한 목록에 최신순")
    void historyLabelsAndTones() throws Exception {
        BrandFixture.Brand owner = cleanBrand("이력전수");
        Long id = owner.marketId();
        jdbc.update("UPDATE seller SET created_at = ? WHERE seller_id = ?", LocalDateTime.now().minusHours(2),
                owner.seller().getId());
        onboardingApproved(owner, operator.getId(), LocalDateTime.now().withNano(0).minusMinutes(30));
        changeRequest(owner, ChangeRequestType.SETTLEMENT_ACCOUNT, ChangeRequestStatus.APPROVED);
        changeRequest(owner, ChangeRequestType.BUSINESS_INFO, ChangeRequestStatus.REJECTED);
        suspend(id, "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        release(id, Map.of()).andExpect(status().isOk());
        Long memoId = json(adminPost(base(id) + "/memos", Map.of("content", "메모"))).path("memoId").asLong();
        patchAs(adminToken, base(id) + "/memos/" + memoId, Map.of("content", "메모 수정")).andExpect(status().isOk());
        deleteAs(adminToken, base(id) + "/memos/" + memoId).andExpect(status().isNoContent());
        Long canceled = withdrawalService.request(id, null, null, LocalDateTime.now()).getId();
        withdrawalService.cancel(id, canceled, LocalDateTime.now());
        Long rejected = withdrawalService.request(id, null, null, LocalDateTime.now()).getId();
        reject(id, rejected, Map.of("reason", "UNSETTLED")).andExpect(status().isOk());
        approve(id, withdrawalService.request(id, null, null, LocalDateTime.now()).getId()).andExpect(status().isOk());

        JsonNode items = json(adminGet(base(id) + "/histories?size=50")).path("content");
        Map<String, String> labels = new HashMap<>();
        Map<String, String> tones = new HashMap<>();
        items.forEach(item -> {
            labels.put(item.path("type").asText(), item.path("label").asText());
            tones.put(item.path("type").asText(), item.path("tone").asText());
        });
        Map<String, String[]> expected = Map.ofEntries(
                Map.entry("SUSPENDED", new String[]{"정지", "DANGER"}),
                Map.entry("SUSPENSION_RELEASED", new String[]{"정지 해제", "SUCCESS"}),
                Map.entry("WITHDRAWAL_REQUESTED", new String[]{"탈퇴 신청 접수", "WARNING"}),
                Map.entry("WITHDRAWAL_CANCELED", new String[]{"탈퇴 신청 취소", "NEUTRAL"}),
                Map.entry("WITHDRAWAL_APPROVED", new String[]{"탈퇴 승인 · 계정 해지", "NEUTRAL"}),
                Map.entry("WITHDRAWAL_REJECTED", new String[]{"탈퇴 신청 반려", "INFO"}),
                Map.entry("MEMO_CREATED", new String[]{"관리자 메모 기입", "NEUTRAL"}),
                Map.entry("MEMO_UPDATED", new String[]{"관리자 메모 수정", "NEUTRAL"}),
                Map.entry("MEMO_DELETED", new String[]{"관리자 메모 삭제", "NEUTRAL"}),
                Map.entry("SIGNED_UP", new String[]{"가입 신청", "INFO"}),
                Map.entry("ONBOARDING_APPROVED", new String[]{"입점 승인", "SUCCESS"}),
                Map.entry("CHANGE_REQUEST_RECEIVED", new String[]{"변경 요청 접수", "INFO"}),
                Map.entry("CHANGE_REQUEST_APPROVED", new String[]{"변경 요청 승인", "SUCCESS"}),
                Map.entry("CHANGE_REQUEST_REJECTED", new String[]{"변경 요청 반려", "WARNING"}));
        assertThat(labels.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        expected.forEach((type, pair) -> {
            assertThat(labels.get(type)).as(type).isEqualTo(pair[0]);
            assertThat(tones.get(type)).as(type).isEqualTo(pair[1]);
        });
        assertThat(items.get(0).path("type").asText()).isEqualTo("WITHDRAWAL_APPROVED");
        assertThat(items.get(items.size() - 1).path("type").asText()).isEqualTo("SIGNED_UP");
    }

    @Test
    @DisplayName("HS-02 처리자 이름 — 저장 행은 스냅샷 · 입점 승인 · 변경 요청 처리는 현재 이름(없는 계정은 「운영자」) · 브랜드 행은 「브랜드 본인」/ 담당자명 스냅샷")
    void historyActorNames() throws Exception {
        BrandFixture.Brand owner = cleanBrand("처리자이름");
        Long id = owner.marketId();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        onboardingApproved(owner, operator.getId(), now.minusMinutes(50));
        changeRequest(owner, ChangeRequestType.SETTLEMENT_ACCOUNT, ChangeRequestStatus.APPROVED);
        suspend(id, "REPEATED_SHIPPING_DELAY", null).andExpect(status().isOk());
        withdrawalService.request(id, null, null, LocalDateTime.now());
        jdbc.update("UPDATE seller SET name = '김운영(개명)' WHERE seller_id = ?", operator.getId());

        Map<String, String> names = actorNames(id);
        assertThat(names.get("SUSPENDED")).isEqualTo("김운영");
        assertThat(names.get("ONBOARDING_APPROVED")).isEqualTo("김운영(개명)");
        assertThat(names.get("CHANGE_REQUEST_APPROVED")).isEqualTo("김운영(개명)");
        assertThat(names.get("SIGNED_UP")).isEqualTo("브랜드 본인");
        assertThat(names.get("CHANGE_REQUEST_RECEIVED")).isEqualTo("브랜드 본인");
        assertThat(names.get("WITHDRAWAL_REQUESTED")).isEqualTo("김담당");

        BrandFixture.Brand orphan = cleanBrand("처리자없음");
        onboardingApproved(orphan, 999_999L, now.minusMinutes(10));
        assertThat(actorNames(orphan.marketId()).get("ONBOARDING_APPROVED")).isEqualTo("운영자");
    }

    @Test
    @DisplayName("HS-03 변경 요청 파생 — 접수 3 · 승인 1 · 반려 1 · 철회(취소)는 접수 행까지 없음 · 처리 시각 · 변경 요청 참조")
    void changeRequestDerivedRows() throws Exception {
        BrandFixture.Brand owner = cleanBrand("변경파생");
        changeRequest(owner, ChangeRequestType.SETTLEMENT_ACCOUNT, ChangeRequestStatus.PENDING);
        ChangeRequest approved = changeRequest(owner, ChangeRequestType.SETTLEMENT_ACCOUNT, ChangeRequestStatus.APPROVED);
        changeRequest(owner, ChangeRequestType.BUSINESS_INFO, ChangeRequestStatus.REJECTED);
        ChangeRequest canceled = changeRequest(owner, ChangeRequestType.BUSINESS_INFO, ChangeRequestStatus.CANCELED);

        JsonNode items = json(adminGet(base(owner.marketId()) + "/histories")).path("content");
        List<String> types = textsAt(items, "type");
        assertThat(types).filteredOn("CHANGE_REQUEST_RECEIVED"::equals).hasSize(3);
        assertThat(types).filteredOn("CHANGE_REQUEST_APPROVED"::equals).hasSize(1);
        assertThat(types).filteredOn("CHANGE_REQUEST_REJECTED"::equals).hasSize(1);
        for (JsonNode item : items) {
            if (item.path("type").asText().startsWith("CHANGE_REQUEST")) {
                assertThat(item.path("refType").asText()).isEqualTo("BRAND_CHANGE_REQUEST");
                assertThat(item.path("refId").asLong()).isNotEqualTo(canceled.getId());
                assertThat(item.path("detail").asText()).isIn("정산 계좌 변경 요청", "사업자 정보 변경 요청");
            }
            if (item.path("type").asText().equals("CHANGE_REQUEST_APPROVED")) {
                assertThat(item.path("refId").asLong()).isEqualTo(approved.getId());
                assertThat(at(item.path("occurredAt"))).isEqualTo(approved.getProcessedAt().withNano(0));
            }
        }
    }

    @Test
    @DisplayName("HS-04 · HS-05 페이지 · 저장 대상 — 26건을 20 · 6 · 0 으로 중복 · 누락 없이 · 파생 유형은 기록기가 거부한다")
    void historyPagingAndRecorderGuard() throws Exception {
        for (int i = 0; i < 25; i++) {
            createMemo("메모 " + i);
        }
        JsonNode first = json(adminGet(base() + "/histories?page=1&size=20")).path("content");
        JsonNode second = json(adminGet(base() + "/histories?page=2&size=20")).path("content");
        JsonNode third = json(adminGet(base() + "/histories?page=3&size=20")).path("content");
        assertThat(first).hasSize(20);
        assertThat(second).hasSize(6);
        assertThat(third).isEmpty();
        Set<Long> refIds = new HashSet<>();
        first.forEach(item -> refIds.add(item.path("refId").asLong()));
        second.forEach(item -> refIds.add(item.path("refId").asLong()));
        assertThat(refIds).hasSize(26);
        assertThat(second.get(5).path("type").asText()).isEqualTo("SIGNED_UP");
        brandDetail(brand.marketId()).andExpect(jsonPath("$.rail.histories.total").value(26));

        assertThatThrownBy(() -> inTransaction(() -> {
            historyRecorder.byAdmin(brand.marketId(), BrandAdminEventType.SIGNED_UP, operator.getId(), "김운영", null,
                    null, null, LocalDateTime.now());
            return null;
        })).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ 10 발송 기한 보조줄

    @Test
    @DisplayName("SL-01 · SL-02 최초 설정은 이전 값 null · 배송비만 바꾸면 이력 없음")
    void shipLeadHistoryEdges() throws Exception {
        jdbc.update("UPDATE market SET shipping_lead_days = NULL WHERE market_id = ?", brand.marketId());
        putDeliveryPolicy(4, 3000);
        brandDetail(brand.marketId()).andExpect(jsonPath("$.deliveryPolicy.shipLeadLastChange.previousDays").doesNotExist())
                .andExpect(jsonPath("$.deliveryPolicy.shipLeadLastChange.newDays").value(4));
        putDeliveryPolicy(4, 5000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_ship_lead_history WHERE market_id = ?", Integer.class,
                brand.marketId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 11 사유 목록

    @Test
    @DisplayName("RO-01 모달 사유 목록 전수 — 코드 · 라벨 · 상세 필수 · 지급 정지(정지 사유만)")
    void reasonOptions() throws Exception {
        JsonNode body = json(adminGet(BRANDS + "/reason-options").andExpect(status().isOk()));
        assertThat(textsAt(body.path("suspensionReasons"), "code")).containsExactly("SETTLEMENT_INFO_MISMATCH",
                "BUSINESS_INFO_FALSIFIED", "PRODUCT_INFO_FALSIFIED", "REPEATED_SHIPPING_DELAY", "CONSUMER_RESPONSE_NEGLECT",
                "OTHER");
        assertThat(textsAt(body.path("suspensionReasons"), "holdsPayout"))
                .containsExactly("true", "true", "false", "false", "false", "false");
        assertThat(textsAt(body.path("suspensionReasons"), "detailRequired"))
                .containsExactly("false", "false", "false", "false", "false", "true");
        assertThat(textsAt(body.path("releaseReasons"), "code")).containsExactly("RESOLVED", "MISTAKE");
        assertThat(textsAt(body.path("releaseReasons"), "label")).containsExactly("소명 · 시정 확인", "오정지");
        body.path("releaseReasons").forEach(r -> assertThat(r.path("holdsPayout").isNull()).isTrue());
        assertThat(textsAt(body.path("withdrawalRejectReasons"), "code")).containsExactly("CLAIM_UNRESOLVED",
                "UNSHIPPED_ORDERS", "ACTIVE_GROUP_BUYS", "UNSETTLED", "CONTRACT_IN_PROGRESS", "OTHER");
        assertThat(textsAt(body.path("withdrawalRejectReasons"), "detailRequired"))
                .containsExactly("false", "false", "false", "false", "false", "true");

        JsonNode creatorOptions = json(adminGet("/v1/admin/creators/reason-options").andExpect(status().isOk()));
        Set<String> brandRow = new HashSet<>();
        body.path("suspensionReasons").get(0).fieldNames().forEachRemaining(brandRow::add);
        Set<String> creatorRow = new HashSet<>();
        creatorOptions.path("suspensionReasons").get(0).fieldNames().forEachRemaining(creatorRow::add);
        assertThat(brandRow).isEqualTo(creatorRow);
    }

    // ------------------------------------------------------------------ 12 권한 · 개인정보

    @Test
    @DisplayName("AU-01 역할 — 13개 엔드포인트 전부 토큰 없음 401 · 소비자 · 브랜드 · 인플루언서 403 · 거절된 쓰기는 부수 효과 없음")
    void rolesOnEveryEndpoint() throws Exception {
        Long memoId = createMemo("권한 메모");
        Long requestId = pendingRequestRow(brand.marketId()).getId();
        List<String[]> endpoints = endpoints(brand.marketId(), memoId, requestId);
        for (String token : java.util.Arrays.asList(null, consumerToken, brandToken, creatorToken)) {
            for (String[] endpoint : endpoints) {
                int status = send(token, endpoint).andReturn().getResponse().getStatus();
                assertThat(status).as((token == null ? "없음" : "역할") + " " + endpoint[0] + " " + endpoint[1])
                        .isEqualTo(token == null ? 401 : 403);
            }
        }
        assertThat(sanctionRepository.findAll()).isEmpty();
        assertThat(memoRepository.findAll()).hasSize(1);
        assertThat(memoRepository.findById(memoId).orElseThrow().getDeletedAt()).isNull();
        assertThat(withdrawalRepository.findById(requestId).orElseThrow().isPending()).isTrue();
        for (String[] endpoint : endpoints) {
            int status = send(adminToken, endpoint).andReturn().getResponse().getStatus();
            assertThat(status).as("운영자 " + endpoint[0] + " " + endpoint[1]).isNotIn(401, 403);
        }
    }

    @Test
    @DisplayName("AU-02 운영자 행이 아닌 주체 — ADMIN 역할 토큰이라도 조치 · 메모 쓰기는 401 · 조회는 이름을 확인하지 않아 200")
    void operatorRowRequiredForWrites() throws Exception {
        String impostor = bearerToken(brand.email(), RoleType.ADMIN, brand.seller().getId());
        postAs(impostor, base() + "/suspend", Map.of("reason", "REPEATED_SHIPPING_DELAY"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        postAs(impostor, base() + "/memos", Map.of("content", "메모")).andExpect(status().isUnauthorized());
        getAs(impostor, base()).andExpect(status().isOk());
        getAs(impostor, BRANDS).andExpect(status().isOk());
        assertThat(sanctionRepository.findAll()).isEmpty();
        assertThat(memoRepository.findAll()).isEmpty();
        assertThat(marketStatus(brand.marketId()).name()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("PV-01 · PV-04 계좌 원문은 목록 · 상세 · 레일 · 이력 · 카드 어디에도 없다 · 카드 payload 에 운영자 이름 없음")
    void accountNumberNeverLeaks() throws Exception {
        registerSellerAccount();
        String rail = suspend(brand.marketId(), "SETTLEMENT_INFO_MISMATCH", "예금주 불일치").andReturn().getResponse()
                .getContentAsString();
        release(brand.marketId(), Map.of("note", "확인")).andExpect(status().isOk());
        for (String body : List.of(rail, adminGet(BRANDS).andReturn().getResponse().getContentAsString(),
                adminGet(base()).andReturn().getResponse().getContentAsString(),
                adminGet(base() + "/histories").andReturn().getResponse().getContentAsString())) {
            assertThat(body).doesNotContain(ACCOUNT_RAW);
        }
        List<String> payloads = jdbc.queryForList("SELECT m.card_payload FROM message m JOIN message_thread t "
                + "ON t.thread_id = m.thread_id JOIN connection c ON c.connection_id = t.connection_id "
                + "WHERE c.market_id = ? AND m.card_type IS NOT NULL", String.class, brand.marketId());
        assertThat(payloads).hasSize(2);
        for (String payload : payloads) {
            assertThat(payload).doesNotContain(ACCOUNT_RAW).doesNotContain("김운영");
        }
    }

    @Test
    @DisplayName("PV-02 탈퇴 뒤 파기 대상 원문(로그인 이메일 · 사업자등록번호 · 상호 · 계좌 · 세금계산서 이메일 · 사이트)이 상세 · 목록에 없다")
    void purgedValuesNeverLeak() throws Exception {
        BrandFixture.Brand owner = cleanBrand("파기확인");
        jdbc.update("UPDATE seller SET business_reg_number = '305-81-12345' WHERE seller_id = ?", owner.seller().getId());
        approve(owner.marketId(), withdrawalService.request(owner.marketId(), null, null, LocalDateTime.now()).getId())
                .andExpect(status().isOk());

        String detail = adminGet(base(owner.marketId())).andReturn().getResponse().getContentAsString();
        String list = adminList("keyword", "파기확인").andReturn().getResponse().getContentAsString();
        for (String raw : List.of(owner.email(), "305-81-12345", "주식회사 파기확인", ACCOUNT_RAW, "tax@파기확인.com",
                "https://파기확인.com")) {
            assertThat(detail).as(raw).doesNotContain(raw);
            assertThat(list).as(raw).doesNotContain(raw);
        }
        assertThat(detail).contains("파기확인");
    }

    // ------------------------------------------------------------------ 보조

    private Long createMemo(String content) throws Exception {
        return json(adminPost(base() + "/memos", Map.of("content", content)).andExpect(status().isOk())).path("memoId").asLong();
    }

    private void backdateMemo(Long memoId, LocalDateTime createdAt) {
        jdbc.update("UPDATE brand_admin_memo SET created_at = ? WHERE memo_id = ?", createdAt, memoId);
    }

    private Map<String, String> actorNames(Long marketId) throws Exception {
        Map<String, String> names = new HashMap<>();
        for (JsonNode item : json(adminGet(base(marketId) + "/histories?size=50")).path("content")) {
            names.putIfAbsent(item.path("type").asText(), item.path("actorName").asText());
        }
        return names;
    }

    private void putDeliveryPolicy(int days, int fee) throws Exception {
        mockMvc.perform(put("/v1/seller/basic-info/delivery-policy").header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("shippingLeadDays", days, "defaultDeliveryFee", fee))))
                .andExpect(status().is2xxSuccessful());
    }

    /** {메서드, 경로, 바디 JSON} — 어드민 브랜드 엔드포인트 13개. */
    private List<String[]> endpoints(Long marketId, Long memoId, Long requestId) {
        String b = base(marketId);
        return List.of(
                new String[]{"GET", BRANDS, null},
                new String[]{"GET", BRANDS + "/reason-options", null},
                new String[]{"GET", b, null},
                new String[]{"POST", b + "/suspend", toJson(Map.of("reason", "REPEATED_SHIPPING_DELAY"))},
                new String[]{"POST", b + "/release", toJson(Map.of())},
                new String[]{"POST", b + "/withdrawal-requests/" + requestId + "/approve", toJson(Map.of())},
                new String[]{"POST", b + "/withdrawal-requests/" + requestId + "/reject", toJson(Map.of("reason", "UNSETTLED"))},
                new String[]{"GET", b + "/histories", null},
                new String[]{"GET", b + "/memos", null},
                new String[]{"POST", b + "/memos", toJson(Map.of("content", "권한 밖 메모"))},
                new String[]{"PATCH", b + "/memos/" + memoId, toJson(Map.of("content", "권한 밖 수정"))},
                new String[]{"DELETE", b + "/memos/" + memoId, null},
                new String[]{"GET", b + "/connected-creators", null});
    }

    private ResultActions send(String token, String[] endpoint) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .request(org.springframework.http.HttpMethod.valueOf(endpoint[0]), endpoint[1]);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, token);
        }
        if (endpoint[2] != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(endpoint[2]);
        }
        return mockMvc.perform(request);
    }
}
