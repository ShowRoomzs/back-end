package showroomz.api.design1021;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.admin.thread.AdminThreadTestSupport;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractCancelRequestChannel;
import showroomz.domain.contract.type.ContractCloseReasonCode;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 P1 — 계약 성립 시점 · 공구 생성(1-3) · 해지(1-4) · 취소 동의 확인(1-5) · 카드(1-6) · 초안 승인 기록(1-8).
 */
@DisplayName("1021 P1 — 계약 성립 · 공구 생성 · 해지 · 취소 동의 · 초안 승인 기록")
class Design1021P1ContractIntegrationTest extends AdminThreadTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    private showroomz.domain.contract.service.ContractDraftReviewService draftReviewService;
    @org.springframework.beans.factory.annotation.Autowired
    private showroomz.domain.contract.repository.ContractDraftSubmissionRepository draftSubmissionRepository;

    // ------------------------------------------------------------------ 1-3 성립 시점

    @Test
    @DisplayName("기-05 — 양측 서명이 차면 계약 성립(나중 서명 시각) · 이력 ESTABLISHED · 서명을 되돌리면 성립도 거둔다")
    void establishedOnBothSignatures() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);
        var brandAt = now.minusHours(3);
        var creatorAt = now.minusHours(2);
        signatures(c, brandAt, creatorAt, reload(c).getVersion()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUSION_PENDING"));

        Contract established = reload(c);
        assertThat(established.getEstablishedAt()).isEqualTo(creatorAt);
        assertThat(historyOf(c)).anyMatch(h -> h.getEventType() == ContractEventType.ESTABLISHED
                && h.getActorType() == ContractActorType.SYSTEM && h.getOccurredAt().equals(creatorAt));
        detail(c).andExpect(status().isOk())
                .andExpect(jsonPath("$.contract.statusLabel").value("공구 생성 대기"))
                .andExpect(jsonPath("$.stepper.establishedAt").value(startsWith(creatorAt.toLocalDate().toString())));

        signatures(c, brandAt, null, established.getVersion()).andExpect(status().isOk());
        assertThat(reload(c).getEstablishedAt()).isNull();
    }

    @Test
    @DisplayName("기-05 — [공구 생성] 새 경로 · 구 /conclude 별칭 모두 공구를 만든다 · concludedAt 은 처리 시각")
    void createGroupBuyAndAlias() throws Exception {
        Contract first = pendingWithDocuments();
        adminPost(BASE + "/" + first.getId() + "/group-buy", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUDED"))
                .andExpect(jsonPath("$.groupBuyNumber").isNotEmpty());
        assertThat(reload(first).getConcludedAt()).isAfter(reload(first).getEstablishedAt());

        Contract second = pendingWithDocuments();
        conclude(second).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONCLUDED"));
    }

    // ------------------------------------------------------------------ 1-5 취소 동의

    @Test
    @DisplayName("기-06 — 성립한 계약(공구 생성 대기)은 취소가 아니라 해지 · 당사자 요청 취소는 상대 동의 체크 필수 · 결과 카드는 양측 채널")
    void cancelRequiresConsentAndPostsBothSides() throws Exception {
        Contract pending = seed(ContractStatus.CONCLUSION_PENDING, s -> s.createdAt(now.minusDays(2)));
        cancel(pending, true, ContractCloseReasonCode.PARTY_RENEGOTIATION, null, ContractActorType.ADMIN, null, null)
                .andExpect(status().isConflict());

        Contract signing = seed(ContractStatus.SIGNING, s -> s.createdAt(now.minusDays(2)));
        cancel(signing, true, ContractCloseReasonCode.PARTY_RENEGOTIATION, null, ContractActorType.SELLER,
                ContractCancelRequestChannel.THREAD, now.minusHours(1), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_CHECKLIST_REQUIRED"));

        adminPost(BASE + "/" + signing.getId() + "/close-consent-card",
                Map.of("requesterType", "SELLER", "reasonSummary", "공구 일정이 맞지 않아 취소를 요청했습니다."))
                .andExpect(status().isOk());
        adminMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_CLOSE_CONSENT_REQUEST"))
                .andExpect(jsonPath("$.content[0].card.detail.notice.kind").value("CANCEL"))
                // 취소 · 해지가 한 카드 종류라 제목을 안내 종류로 나눈다(58 계획 5-22)
                .andExpect(jsonPath("$.content[0].card.title").value("계약 취소 동의 확인"));

        // 직권(ADMIN)도 동의 확인이 필수다(58 계획 5-20)
        cancel(signing, true, ContractCloseReasonCode.PARTY_RENEGOTIATION, null, ContractActorType.ADMIN, null, null,
                null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_CHECKLIST_REQUIRED"));

        cancel(signing, true, ContractCloseReasonCode.PARTY_RENEGOTIATION, null, ContractActorType.SELLER,
                ContractCancelRequestChannel.THREAD, now.minusHours(1), true)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED"));
        adminMessages(brandChannel).andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_ADMIN_CANCELED"));
        adminMessages(creatorChannel).andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_ADMIN_CANCELED"));
        assertThat(reload(signing).getCancelRequestThreadId()).isEqualTo(brandChannel.getId());
    }

    // ------------------------------------------------------------------ 1-4 해지

    @Test
    @DisplayName("기-05 — 브랜드 해지 요청 → 상대 채널 동의 카드 · 중복 409 · 어드민 큐 · 체크 누락 400 · 해지 처리 → 양측 결과 카드")
    void terminationRequestAndHandle() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING, s -> s.createdAt(now.minusDays(2)));

        sellerPost(c, "/termination-request", Map.of("reasonCode", "QUALITY_ISSUE", "memo", "원료 수급이 늦어집니다."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminationRequest.status").value("PENDING"))
                .andExpect(jsonPath("$.permissions.canRequestTermination").value(false))
                .andExpect(jsonPath("$.permissions.canWithdrawTermination").value(true));
        sellerPost(c, "/termination-request", Map.of("reasonCode", "QUALITY_ISSUE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_TERMINATION_ALREADY_PENDING"));

        adminMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_CLOSE_CONSENT_REQUEST"))
                .andExpect(jsonPath("$.content[0].card.detail.notice.kind").value("TERMINATE"))
                .andExpect(jsonPath("$.content[0].card.detail.notice.body").value("원료 수급이 늦어집니다."));
        creatorGet(CREATOR_CONTRACTS + "/" + c.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.terminationRequested.reasonLabel").value("상품 품질 이슈"));

        summary().andExpect(jsonPath("$.queues.TERMINATION").value(1));
        Long requestId = json(detail(c).andExpect(status().isOk())
                .andExpect(jsonPath("$.terminationRequest.requesterType").value("SELLER"))
                .andExpect(jsonPath("$.permissions.canTerminate").value(true)))
                .at("/terminationRequest/requestId").asLong();

        terminate(c, requestId, false).andExpect(status().isBadRequest());
        terminate(c, requestId, true).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TERMINATED"));

        detail(c).andExpect(jsonPath("$.termination.reasonLabel").value("당사자 요청 — 공구 진행 불가"))
                .andExpect(jsonPath("$.termination.requestReasonLabel").value("상품 품질 이슈"))
                .andExpect(jsonPath("$.terminationRequest").value(nullValue()));
        sellerGet(c).andExpect(jsonPath("$.terminationRequest.status").value("HANDLED"))
                .andExpect(jsonPath("$.closure.reasonLabel").value("당사자 요청 — 공구 진행 불가"))
                .andExpect(jsonPath("$.permissions.canDuplicate").value(true))
                .andExpect(jsonPath("$.fixedFee.obligationAlive").value(false));
        adminMessages(brandChannel).andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_TERMINATED"));
        adminMessages(creatorChannel).andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_TERMINATED"));
        assertThat(historyOf(c)).anyMatch(h -> h.getEventType() == ContractEventType.TERMINATION_REQUESTED)
                .anyMatch(h -> h.getEventType() == ContractEventType.TERMINATED);
        summary().andExpect(jsonPath("$.queues.TERMINATION").value(0));
    }

    @Test
    @DisplayName("기-05 — 생성된 공구도 함께 종결(TERMINATED) · 오픈된 공구는 해지 불가(409) · 요청 철회")
    void terminationClosesGroupBuyBeforeOpenOnly() throws Exception {
        Contract c = pendingWithDocuments();
        adminPost(BASE + "/" + c.getId() + "/group-buy", null).andExpect(status().isOk());
        Long groupBuyId = reload(c).getGroupBuyId();

        sellerPost(c, "/termination-request", Map.of("reasonCode", "NEGOTIATION_BROKEN")).andExpect(status().isOk());
        mockMvc.perform(delete(SELLER_CONTRACTS + "/" + c.getId() + "/termination-request")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.terminationRequest.status").value("WITHDRAWN"));

        terminate(c, null, true).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM group_buy WHERE group_buy_id = ?", String.class, groupBuyId))
                .isEqualTo("TERMINATED");

        Contract opened = pendingWithDocuments();
        adminPost(BASE + "/" + opened.getId() + "/group-buy", null).andExpect(status().isOk());
        jdbc.update("UPDATE group_buy SET status = 'IN_PROGRESS' WHERE group_buy_id = ?", reload(opened).getGroupBuyId());
        terminate(opened, null, true).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_TERMINATION_NOT_ALLOWED"));
        sellerPost(opened, "/termination-request", Map.of("reasonCode", "QUALITY_ISSUE"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ 1-8 초안 승인 기록

    @Test
    @DisplayName("기-30 — 초안 승인 기록은 성립 뒤에만 · 세 서피스가 같은 기록을 읽는다 · 삭제하면 빠진다")
    void draftApprovals() throws Exception {
        Contract signing = seed(ContractStatus.SIGNING);
        sellerPost(signing, "/draft-approvals", Map.of("title", "릴스 1차 초안")).andExpect(status().isConflict());

        Contract c = seed(ContractStatus.CONCLUSION_PENDING);
        Long approvalId = json(sellerPost(c, "/draft-approvals", Map.of("title", "릴스 1차 초안"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftApprovals", hasSize(1)))
                .andExpect(jsonPath("$.draftApprovals[0].approvedByName").value(brand.market().getMarketName())))
                .at("/draftApprovals/0/approvalId").asLong();
        creatorGet(CREATOR_CONTRACTS + "/" + c.getId())
                .andExpect(jsonPath("$.draftApprovals[0].title").value("릴스 1차 초안"));

        mockMvc.perform(delete(SELLER_CONTRACTS + "/" + c.getId() + "/draft-approvals/" + approvalId)
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draftApprovals", hasSize(0)));
    }

    @Test
    @DisplayName("B-13 사전 검수 — 인플루언서 초안 제출(= 수령) · 회신 기한 수령 + 3영업일 · 수정 요청 회신 · 승인 회신 · 무회신이면 승인 간주 기록")
    void draftDeemedApproval() throws Exception {
        Contract noReview = seed(ContractStatus.CONCLUSION_PENDING);
        creatorPost(noReview, "/draft-submissions", Map.of("title", "릴스 1차 초안")).andExpect(status().isConflict());

        Contract c = seed(ContractStatus.CONCLUSION_PENDING);
        jdbc.update("UPDATE contract SET brand_pre_review = TRUE WHERE contract_id = ?", c.getId());
        com.fasterxml.jackson.databind.JsonNode submitted = json(creatorPost(c, "/draft-submissions",
                Map.of("title", "릴스 1차 초안")).andExpect(status().isOk())
                .andExpect(jsonPath("$.draftSubmissions", hasSize(1)))
                .andExpect(jsonPath("$.draftSubmissions[0].status").value("PENDING")));
        long first = submitted.at("/draftSubmissions/0/submissionId").asLong();
        assertThat(submitted.at("/draftSubmissions/0/reviewDueAt").asText()).isNotBlank();

        // 수정 요청 회신 — 기한 안 회신이라 승인 간주되지 않는다.
        sellerPost(c, "/draft-submissions/" + first + "/revision-request", Map.of("note", "제품 사용 장면을 넣어 주세요"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftSubmissions[0].status").value("REVISION_REQUESTED"))
                .andExpect(jsonPath("$.draftSubmissions[0].decisionNote").value("제품 사용 장면을 넣어 주세요"));
        sellerPost(c, "/draft-submissions/" + first + "/revision-request", Map.of("note", "다시"))
                .andExpect(status().isConflict());
        assertThat(draftReviewService.deemApproveIfDue(first, now.plusDays(30))).isFalse();

        // 두 번째 제출 — 승인 회신(초안 승인 기록과 이어진다).
        long second = json(creatorPost(c, "/draft-submissions", Map.of("title", "릴스 2차 초안")).andExpect(status().isOk()))
                .at("/draftSubmissions/1/submissionId").asLong();
        sellerPost(c, "/draft-approvals", Map.of("title", "릴스 2차 초안", "submissionId", second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftSubmissions[1].status").value("APPROVED"))
                .andExpect(jsonPath("$.draftSubmissions[1].approvalId").isNumber());

        // 세 번째 제출 — 무회신 → 기한 전에는 그대로 · 지나면 승인 간주(초안 승인 기록 · 출처 「승인 간주」).
        long third = json(creatorPost(c, "/draft-submissions", Map.of("title", "릴스 3차 초안")).andExpect(status().isOk()))
                .at("/draftSubmissions/2/submissionId").asLong();
        LocalDateTime due = draftSubmissionRepository.findById(third).orElseThrow().getReviewDueAt();
        assertThat(draftReviewService.findIdsDueForDeemedApproval(due.minusMinutes(1), 100)).doesNotContain(third);
        assertThat(draftReviewService.deemApproveIfDue(third, due.minusMinutes(1))).isFalse();
        assertThat(draftReviewService.findIdsDueForDeemedApproval(due.plusMinutes(1), 100)).contains(third);
        assertThat(draftReviewService.deemApproveIfDue(third, due.plusMinutes(1))).isTrue();
        assertThat(draftReviewService.deemApproveIfDue(third, due.plusMinutes(2))).isFalse();
        sellerGet(c).andExpect(jsonPath("$.draftSubmissions[2].status").value("DEEMED_APPROVED"))
                .andExpect(jsonPath("$.draftApprovals", hasSize(2)))
                .andExpect(jsonPath("$.draftApprovals[1].title").value("릴스 3차 초안"))
                .andExpect(jsonPath("$.draftApprovals[1].sourceLabel").value("초안 수령 후 3영업일 무회신 · 승인 간주"));
        creatorGet(CREATOR_CONTRACTS + "/" + c.getId())
                .andExpect(jsonPath("$.draftSubmissions[2].status").value("DEEMED_APPROVED"));
    }

    // ------------------------------------------------------------------

    private Contract pendingWithDocuments() {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING, s -> s.createdAt(now.minusDays(2)));
        attach(c, ContractDocumentType.SIGNED_PDF);
        attach(c, ContractDocumentType.AUDIT_TRAIL);
        return c;
    }

    private ResultActions terminate(Contract c, Long requestId, boolean checked) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("reasonCode", "TERMINATE_CANNOT_PROCEED");
        body.put("explanation", "공급 일정 차질로 양측 합의 하에 해지합니다.");
        body.put("counterpartyConsentConfirmed", checked);
        body.put("groupBuyNotOpenedConfirmed", checked);
        body.put("requestId", requestId);
        return adminPost(BASE + "/" + c.getId() + "/terminate", body);
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        var request = post(url).header(HttpHeaders.AUTHORIZATION, adminToken);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        }
        return mockMvc.perform(request);
    }

    private ResultActions sellerPost(Contract c, String path, Object body) throws Exception {
        return mockMvc.perform(post(SELLER_CONTRACTS + "/" + c.getId() + path)
                .header(HttpHeaders.AUTHORIZATION, brandToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    private ResultActions creatorPost(Contract c, String path, Object body) throws Exception {
        return mockMvc.perform(post(CREATOR_CONTRACTS + "/" + c.getId() + path)
                .header(HttpHeaders.AUTHORIZATION, creatorToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    private ResultActions sellerGet(Contract c) throws Exception {
        return mockMvc.perform(get(SELLER_CONTRACTS + "/" + c.getId()).header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    private ResultActions creatorGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    private com.fasterxml.jackson.databind.JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
