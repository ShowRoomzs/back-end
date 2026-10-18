package showroomz.api.creator.basicinfo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorAdminHistoryRepository;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.SettlementStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인플루언서 사업자 구분 변경 — 스튜디오 요청(T4 ~ T10) → 어드민 변경 요청 큐(rev.7 C1 · C3a · C3b · C5b · C7b · C8b) → 승인 · 반려 반영
 * (16 설계서 8절 · 50 설계서 14절 BT · 구현 계획서 S5). 브랜드 경로 회귀 차단(셀러 API 의 인플루언서 유형 · 인플루언서 요청 id)도 여기서 본다.
 */
@DisplayName("사업자 구분 변경 — 스튜디오 요청 · 어드민 큐 · 대조 · 승인 반영 · 반려 · 브랜드 경로 회귀 차단")
class CreatorBusinessTypeChangeIntegrationTest extends SettlementTestSupport {

    private static final String STUDIO = "/v1/creator/basic-info";
    private static final String QUEUE = "/v1/admin/change-requests";
    private static final String RESIDENT = "950812-2345678";

    @Autowired private CreatorAdminHistoryRepository adminHistories;
    @Autowired private ChangeRequestRepository brandChangeRequests;

    @Test
    @DisplayName("[BT-01 · BT-09] 사업자 전환 요청 — 큐 행(요청자 = 인플루언서 · 쇼룸명) · 배지 합산 · 쇼룸명 검색 · 스튜디오 배너 · 인플루언서 상세 연계")
    void requestToBusinessShowsInQueue() throws Exception {
        long requestId = toBusiness("123-45-67890").andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TO_BUSINESS"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestCode").isNotEmpty())
                .andReturn().getResponse().getContentAsString().transform(this::idOf);

        adminGet(QUEUE + "?status=PENDING").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requester.type").value("CREATOR"))
                .andExpect(jsonPath("$.content[0].requester.id").value(creator.getId()))
                .andExpect(jsonPath("$.content[0].requester.name").value("글로우_지민"))
                .andExpect(jsonPath("$.content[0].brandName").doesNotExist())
                .andExpect(jsonPath("$.content[0].type").value("TO_BUSINESS"))
                .andExpect(jsonPath("$.statusCounts.pending").value(1));
        adminGet(QUEUE + "/summary").andExpect(jsonPath("$.pendingCount").value(1));
        adminGet(QUEUE + "?status=ALL&keyword=지민").andExpect(jsonPath("$.content.length()").value(1));

        creatorGet(STUDIO + "/settlement").andExpect(status().isOk())
                .andExpect(jsonPath("$.businessTypeChange.requestId").value(requestId))
                .andExpect(jsonPath("$.businessTypeChange.status").value("PENDING"))
                .andExpect(jsonPath("$.businessTypeChange.businessRegistrationNumber").value("123-45-67890"))
                .andExpect(jsonPath("$.businessTypeChange.evidence.fileName").value("사업자등록증.pdf"))
                .andExpect(jsonPath("$.actions.businessTypeChange").value("CANCEL"));

        adminGet("/v1/admin/creators/" + creator.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementAccount.pendingBusinessTypeChange.changeRequestId").value(requestId))
                .andExpect(jsonPath("$.settlementAccount.pendingBusinessTypeChange.type").value("TO_BUSINESS"));
    }

    @Test
    @DisplayName("[BT-02] 열린 요청 중복 409 · 현재 구분과 같은 방향 409 · 등록번호 형식 400 · 증빙 누락 400")
    void createValidation() throws Exception {
        Map<String, Object> sameDirection = body("INDIVIDUAL");
        sameDirection.put("residentRegistrationNumber", RESIDENT);
        create(sameDirection).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_BUSINESS_TYPE_MISMATCH"));
        toBusiness("1234567890").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("올바른 사업자등록번호 형식으로 입력해 주세요. (예: 000-00-00000)"));
        Map<String, Object> noEvidence = body("BUSINESS");
        noEvidence.put("businessRegistrationNumber", "123-45-67890");
        noEvidence.remove("evidenceFileUrl");
        create(noEvidence).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_EVIDENCE_REQUIRED"));

        toBusiness("123-45-67890").andExpect(status().isCreated());
        toBusiness("123-45-67890").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ALREADY_PENDING"));
    }

    @Test
    @DisplayName("[BT-03 · BT-04] 비사업자 전환 — 항목은 마스킹만 · 원문은 암호문으로만 · 응답에 원문 없음 → 취소하면 암호문도 지운다")
    void toIndividualStoresOnlyCipher() throws Exception {
        makeBusinessCreator();
        String response = toIndividual(RESIDENT).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("2345678");
        long requestId = idOf(response);

        ChangeRequest stored = request(requestId);
        assertThat(stored.getResidentNumberEnc()).isNotNull().doesNotContain("2345678");
        assertThat(personalDataCipher.decrypt(stored.getResidentNumberEnc())).isEqualTo("9508122345678");
        assertThat(inTransaction(() -> request(requestId).getItems().stream()
                .map(i -> i.getRequestedValue()).toList())).contains("INDIVIDUAL", "950812-*******");
        creatorGet(STUDIO + "/settlement")
                .andExpect(jsonPath("$.businessTypeChange.maskedResidentNumber").value("950812-*******"));

        studioPost(STUDIO + "/business-type-change-requests/" + requestId + "/cancel").andExpect(status().isNoContent());
        assertThat(request(requestId).getStatus()).isEqualTo(ChangeRequestStatus.CANCELED);
        assertThat(request(requestId).getResidentNumberEnc()).isNull();
        creatorGet(STUDIO + "/settlement").andExpect(jsonPath("$.businessTypeChange").doesNotExist())
                .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_INDIVIDUAL"));
    }

    @Test
    @DisplayName("[C3a] 어드민 상세(사업자 전환) — 대조표 6행 source · 계좌 마스킹 · 실명은 대기 중에만 · 대조 값 · 「승인하면」(기존 구분 유지 정산)")
    void adminDetailToBusiness() throws Exception {
        registerCreatorAccount();
        Settlement retained = seedSettlement("가을 공구", 1_000_000, SettlementStatus.REVIEWING);
        long requestId = idOf(toBusiness("214-37-55120").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        JsonNode body = json(adminGet(QUEUE + "/" + requestId).andExpect(status().isOk())
                .andExpect(jsonPath("$.requester.type").value("CREATOR"))
                .andExpect(jsonPath("$.requesterName").doesNotExist())
                .andExpect(jsonPath("$.diff.length()").value(6))
                .andExpect(jsonPath("$.diff[0].fieldKey").value("CREATOR_BUSINESS_TYPE"))
                .andExpect(jsonPath("$.diff[0].currentValue").value("개인 (비사업자)"))
                .andExpect(jsonPath("$.diff[0].requestedValue").value("개인사업자"))
                .andExpect(jsonPath("$.diff[0].source").value("REQUEST"))
                .andExpect(jsonPath("$.diff[1].requestedValue").value("214-37-55120"))
                .andExpect(jsonPath("$.diff[2].source").value("DOCUMENT"))
                .andExpect(jsonPath("$.diff[3].fieldKey").value("PAYOUT_METHOD"))
                .andExpect(jsonPath("$.diff[3].source").value("DERIVED"))
                .andExpect(jsonPath("$.diff[4].source").value("CHECK"))
                .andExpect(jsonPath("$.diff[5].source").value("LIVE"))
                .andExpect(jsonPath("$.evidence.documentLabel").value("사업자등록증"))
                .andExpect(jsonPath("$.holderCheck.currentHolder").value("김지민"))
                .andExpect(jsonPath("$.holderCheck.rule").value("REPRESENTATIVE_NAME"))
                .andExpect(jsonPath("$.holderCheck.mismatch").value(false))
                .andExpect(jsonPath("$.compareItems[0].value").value("김지민"))
                .andExpect(jsonPath("$.approvalImpact.appliesFrom").value("NEXT_SETTLEMENT"))
                .andExpect(jsonPath("$.approvalImpact.payoutMethodBefore").value("WITHHOLDING_3_3"))
                .andExpect(jsonPath("$.approvalImpact.payoutMethodAfter").value("TAX_INVOICE"))
                .andExpect(jsonPath("$.approvalImpact.retainedSettlements[0].settlementId").value(retained.getId()))
                .andExpect(jsonPath("$.approvalImpact.accountChangeRequired").value(false))
                .andExpect(jsonPath("$.history[0].actorLabel").value("쇼룸 스튜디오")));
        assertThat(body.toString()).doesNotContain("123456789012");

        adminPost(QUEUE + "/" + requestId + "/reject", Map.of("reasonType", "LICENSE_UNREADABLE")).andExpect(status().isOk());
        adminGet(QUEUE + "/" + requestId + "?status=ALL")
                .andExpect(jsonPath("$.holderCheck.currentHolder").value("김*민"))
                .andExpect(jsonPath("$.approvalImpact").doesNotExist());
    }

    @Test
    @DisplayName("[C5b] 어드민 상세(비사업자 전환) — 폐업사실증명원 · 등록번호 「폐업」(DERIVED) · 생년월일 서버 대조 MATCH / MISMATCH")
    void adminDetailToIndividualBirthdayCheck() throws Exception {
        makeBusinessCreator();
        jdbc.update("UPDATE creator SET birthday = '1995-08-12' WHERE creator_id = ?", creator.getId());
        long matching = idOf(toIndividual(RESIDENT).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        adminGet(QUEUE + "/" + matching).andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence.documentLabel").value("폐업사실증명원"))
                .andExpect(jsonPath("$.diff[1].requestedValue").value("폐업"))
                .andExpect(jsonPath("$.diff[1].source").value("DERIVED"))
                .andExpect(jsonPath("$.diff[2].fieldKey").value("CREATOR_RESIDENT_NUMBER"))
                .andExpect(jsonPath("$.diff[2].requestedValue").value("950812-*******"))
                .andExpect(jsonPath("$.compareItems[1].key").value("BIRTHDAY"))
                .andExpect(jsonPath("$.compareItems[1].serverCheck").value("MATCH"))
                .andExpect(jsonPath("$.referenceItems[0].value").value("950812-*******"));

        studioPost(STUDIO + "/business-type-change-requests/" + matching + "/cancel").andExpect(status().isNoContent());
        long other = idOf(toIndividual("950813-2345678").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        adminGet(QUEUE + "/" + other).andExpect(jsonPath("$.compareItems[1].serverCheck").value("MISMATCH"));
    }

    @Test
    @DisplayName("[BT-05] 사업자 전환 승인 — 구분 · 등록번호 · 등록증(증빙) 반영 · 주민번호 암호문은 남는다 · 이력 · 배너 승인 → 확인하면 사라짐 · 정산 정보 확정일")
    void approveToBusiness() throws Exception {
        registerCreatorResidentNumber();
        long requestId = idOf(toBusiness("214-37-55120").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        adminPost(QUEUE + "/" + requestId + "/approve", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.requester.type").value("CREATOR"));
        adminPost(QUEUE + "/" + requestId + "/approve", Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_NOT_PENDING"));

        Creator converted = reloadCreator();
        assertThat(converted.getBusinessType()).isEqualTo(CreatorBusinessType.BUSINESS);
        assertThat(converted.getBusinessRegistrationNumber()).isEqualTo("214-37-55120");
        assertThat(converted.getBusinessLicenseImageUrl()).isEqualTo("https://cdn.showroomz.test/creator-document/evidence.pdf");
        assertThat(converted.getResidentRegistrationNumberEnc()).isNotNull();
        assertThat(events()).contains(CreatorAdminEventType.BUSINESS_TYPE_CHANGE_APPROVED);

        creatorGet(STUDIO + "/settlement")
                .andExpect(jsonPath("$.businessType.value").value("BUSINESS"))
                .andExpect(jsonPath("$.businessType.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.business.license.fileName").value("사업자등록증.pdf"))
                .andExpect(jsonPath("$.businessTypeChange.status").value("APPROVED"))
                .andExpect(jsonPath("$.actions.businessTypeChange").value("REQUEST_TO_INDIVIDUAL"));
        studioPost(STUDIO + "/business-type-change-requests/" + requestId + "/acknowledge").andExpect(status().isNoContent());
        studioPost(STUDIO + "/business-type-change-requests/" + requestId + "/acknowledge").andExpect(status().isNoContent());
        creatorGet(STUDIO + "/settlement").andExpect(jsonPath("$.businessTypeChange").doesNotExist());
        adminGet("/v1/admin/creators/" + creator.getId())
                .andExpect(jsonPath("$.settlementAccount.pendingBusinessTypeChange").doesNotExist());
    }

    @Test
    @DisplayName("[BT-06] 비사업자 전환 승인 — 주민번호(요청 암호문 그대로) · 등록 시각 반영 · 등록번호는 남는다 · 요청 행 암호문은 지운다")
    void approveToIndividual() throws Exception {
        makeBusinessCreator();
        long requestId = idOf(toIndividual(RESIDENT).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        adminPost(QUEUE + "/" + requestId + "/approve", Map.of()).andExpect(status().isOk());

        Creator converted = reloadCreator();
        assertThat(converted.getBusinessType()).isEqualTo(CreatorBusinessType.INDIVIDUAL);
        assertThat(personalDataCipher.decrypt(converted.getResidentRegistrationNumberEnc())).isEqualTo("9508122345678");
        assertThat(converted.getResidentRegistrationNumberMasked()).isEqualTo("950812-*******");
        assertThat(converted.getResidentNumberRegisteredAt()).isNotNull();
        assertThat(converted.getBusinessRegistrationNumber()).isEqualTo("312-81-40177");
        assertThat(request(requestId).getResidentNumberEnc()).isNull();
    }

    @Test
    @DisplayName("[BT-07] 반려 — 브랜드 사유 400 · 기타 상세 없음 400 · 정형 사유 → 배너 사유 문장 그대로 · 이력 · 즉시 재요청 가능")
    void reject() throws Exception {
        long requestId = idOf(toBusiness("214-37-55120").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String reject = QUEUE + "/" + requestId + "/reject";

        adminPost(reject, Map.of("reasonType", "EVIDENCE_MISSING")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_REJECT_REASON_TYPE_MISMATCH"));
        adminPost(reject, Map.of("reasonType", "OTHER")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_REJECT_DETAIL_REQUIRED"));
        adminGet(QUEUE + "/reject-reasons?type=TO_BUSINESS").andExpect(jsonPath("$.length()").value(6));

        adminPost(reject, Map.of("reasonType", "LICENSE_OWNER_NAME_MISMATCH")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectReason").value("등록증의 대표자명이 본인 실명과 다름"));
        creatorGet(STUDIO + "/settlement")
                .andExpect(jsonPath("$.businessTypeChange.status").value("REJECTED"))
                .andExpect(jsonPath("$.businessTypeChange.rejectReason.label").value("등록증의 대표자명이 본인 실명과 다름"));
        assertThat(events()).contains(CreatorAdminEventType.BUSINESS_TYPE_CHANGE_REJECTED);
        assertThat(reloadCreator().getBusinessType()).isEqualTo(CreatorBusinessType.INDIVIDUAL);

        // 반려 배너를 닫지 않고 다시 요청 — 새 요청이 옛 배너를 대신한다.
        toBusiness("214-37-55120").andExpect(status().isCreated());
        assertThat(request(requestId).getResultAcknowledgedAt()).isNotNull();
    }

    @Test
    @DisplayName("[BT-08] 승인 직전 다른 경로로 구분이 바뀜 409 · PG 셀러가 연결된 인플루언서 409(재등록 대기) — 요청은 대기 그대로")
    void approveGuards() throws Exception {
        long requestId = idOf(toBusiness("214-37-55120").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        jdbc.update("UPDATE creator SET pg_partner_id = 'FAKE-SELLER-1' WHERE creator_id = ?", creator.getId());
        adminPost(QUEUE + "/" + requestId + "/approve", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_PG_RESYNC_PENDING"));

        jdbc.update("UPDATE creator SET pg_partner_id = NULL, business_type = 'BUSINESS' WHERE creator_id = ?", creator.getId());
        adminPost(QUEUE + "/" + requestId + "/approve", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_BUSINESS_TYPE_MISMATCH"));
        assertThat(request(requestId).getStatus()).isEqualTo(ChangeRequestStatus.PENDING);
    }

    @Test
    @DisplayName("[회귀] 셀러 API 는 인플루언서 유형 400 · 셀러가 인플루언서 요청 id 로 취소 · 확인하면 403(500 아님)")
    void brandPathGuards() throws Exception {
        long requestId = idOf(toBusiness("214-37-55120").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/v1/seller/change-requests").header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of(
                                "type", "TO_BUSINESS", "reason", "전환",
                                "items", List.of(Map.of("fieldKey", "CREATOR_BUSINESS_TYPE", "requestedValue", "BUSINESS")),
                                "evidenceFileUrl", "https://x", "evidenceFileName", "a.pdf", "evidenceFileSize", 10))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_TYPE_NOT_ALLOWED"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/v1/seller/change-requests/latest?type=TO_INDIVIDUAL").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/seller/change-requests/" + requestId + "/cancel").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHANGE_REQUEST_ACCESS_DENIED"));
    }

    // ------------------------------------------------------------------ helpers

    private void makeBusinessCreator() {
        makeCreatorBusiness();
        jdbc.update("UPDATE creator SET business_registration_number = ? WHERE creator_id = ?", "312-81-40177", creator.getId());
    }

    private ResultActions toBusiness(String businessRegistrationNumber) throws Exception {
        Map<String, Object> body = body("BUSINESS");
        body.put("businessRegistrationNumber", businessRegistrationNumber);
        return create(body);
    }

    private ResultActions toIndividual(String residentNumber) throws Exception {
        Map<String, Object> body = body("INDIVIDUAL");
        body.put("residentRegistrationNumber", residentNumber);
        body.put("evidenceFileName", "폐업사실증명원.pdf");
        return create(body);
    }

    private Map<String, Object> body(String targetType) {
        Map<String, Object> body = new HashMap<>();
        body.put("targetType", targetType);
        body.put("evidenceFileUrl", "https://cdn.showroomz.test/creator-document/evidence.pdf");
        body.put("evidenceFileName", "사업자등록증.pdf");
        body.put("evidenceFileSize", 1_258_291);
        return body;
    }

    private ResultActions create(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(STUDIO + "/business-type-change-requests").header(HttpHeaders.AUTHORIZATION, creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions studioPost(String url) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    private long idOf(String json) {
        try {
            return objectMapper.readTree(json).path("requestId").asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ChangeRequest request(long requestId) {
        return brandChangeRequests.findById(requestId).orElseThrow();
    }

    private Creator reloadCreator() {
        return creatorRepository.findById(creator.getId()).orElseThrow();
    }

    /**
     * 어드민 처리 이력 API 가 보여 주는 사건 — 사업자 구분 변경 승인 · 반려는 요청 행에서 파생되고 저장되지 않는다(정합 계획서 R1-8). 저장 이력에
     * 변경 요청 행이 새로 생기지 않는 것도 함께 본다(같은 사건이 두 줄이 되지 않게).
     */
    private List<CreatorAdminEventType> events() throws Exception {
        assertThat(adminHistories.findByCreatorIdOrderByCreatedAtDescIdDesc(creator.getId()))
                .noneMatch(h -> "CHANGE_REQUEST".equals(h.getRefType()));
        List<CreatorAdminEventType> types = new java.util.ArrayList<>();
        json(adminGet("/v1/admin/creators/" + creator.getId() + "/histories?page=1&size=100"))
                .path("content").forEach(item -> types.add(CreatorAdminEventType.valueOf(item.path("eventType").asText())));
        return types;
    }
}
