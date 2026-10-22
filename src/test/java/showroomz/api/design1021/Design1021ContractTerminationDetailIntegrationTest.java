package showroomz.api.design1021;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.admin.thread.AdminThreadTestSupport;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.service.GroupBuyLifecycleService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1021 설계서 1-4 해지 — P1 기본 흐름 밖의 경계: 해지할 수 없는 상태 · 사유 메모 · 정지 중 요청 · 공구 오픈 시 요청 실효 · 직권 해지 · 철회 뒤 재요청.
 */
@DisplayName("1021 1-4 해지 상세 — 상태 경계 · 정지 중 · 공구 오픈 실효 · 직권 해지 · 재요청")
class Design1021ContractTerminationDetailIntegrationTest extends AdminThreadTestSupport {

    @Autowired private GroupBuyLifecycleService lifecycle;

    @Test
    @DisplayName("서명 진행중은 해지 대상이 아니다(취소 대상) — 요청 409 · 직권 해지 409 · 걸린 요청이 없으면 철회 409")
    void signingIsNotTerminable() throws Exception {
        Contract signing = seed(ContractStatus.SIGNING, s -> s.createdAt(now.minusDays(2)));

        sellerPost(signing, "/termination-request", Map.of("reasonCode", "QUALITY_ISSUE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_TERMINATION_NOT_ALLOWED"));
        terminate(signing, null, "TERMINATE_CANNOT_PROCEED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_TERMINATION_NOT_ALLOWED"));
        sellerDelete(signing, "/termination-request").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_TERMINATION_REQUEST_NOT_PENDING"));
        assertThat(reload(signing).getStatus()).isEqualTo(ContractStatus.SIGNING);
    }

    @Test
    @DisplayName("사유 「기타」는 메모 필수 — 빈 메모 400 · 메모가 있으면 접수 · 카드 본문 = 메모")
    void etcReasonRequiresMemo() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING, s -> s.createdAt(now.minusDays(2)));

        sellerPost(c, "/termination-request", Map.of("reasonCode", "ETC", "memo", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_REASON_MEMO_REQUIRED"));
        sellerPost(c, "/termination-request", Map.of("reasonCode", "ETC", "memo", "  협찬 일정이 취소됐습니다. "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminationRequest.status").value("PENDING"));
        adminMessages(creatorChannel)
                .andExpect(jsonPath("$.content[0].card.detail.notice.body").value("협찬 일정이 취소됐습니다."));
        verify(notifier, atLeastOnce()).notifyCounterparty(any(), eq("TERMINATION_REQUESTED"));
    }

    @Test
    @DisplayName("Q3 — 계정 이용 정지 중인 브랜드도 해지를 요청할 수 있다(거두는 일) · 어드민 처리도 된다")
    void suspendedBrandMayRequestTermination() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING, s -> s.createdAt(now.minusDays(2)));
        setMarketStatus(brand, "SUSPENDED");

        sellerPost(c, "/termination-request", Map.of("reasonCode", "NEGOTIATION_BROKEN")).andExpect(status().isOk());
        Long requestId = json(detail(c)).at("/terminationRequest/requestId").asLong();
        terminate(c, requestId, "TERMINATE_RENEGOTIATION").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"));
    }

    @Test
    @DisplayName("공구가 열리면 걸린 해지 요청은 실효(LAPSED) — 어드민 큐에서 빠지고 이후 해지는 409(공구 중단으로)")
    void openingGroupBuyLapsesPendingRequest() throws Exception {
        Contract c = pendingWithDocuments();
        adminPost(BASE + "/" + c.getId() + "/group-buy", null).andExpect(status().isOk());
        Long groupBuyId = reload(c).getGroupBuyId();
        sellerPost(c, "/termination-request", Map.of("reasonCode", "QUALITY_ISSUE")).andExpect(status().isOk());
        summary().andExpect(jsonPath("$.queues.TERMINATION").value(1));

        jdbc.update("UPDATE group_buy SET status = 'READY', start_at = ? WHERE group_buy_id = ?",
                LocalDateTime.now().minusMinutes(5), groupBuyId);
        assertThat(lifecycle.open(groupBuyId, LocalDateTime.now())).isTrue();

        assertThat(jdbc.queryForObject("SELECT status FROM contract_termination_request WHERE contract_id = ?",
                String.class, c.getId())).isEqualTo("LAPSED");
        summary().andExpect(jsonPath("$.queues.TERMINATION").value(0));
        detail(c).andExpect(jsonPath("$.terminationRequest").value(nullValue()))
                .andExpect(jsonPath("$.permissions.canTerminate").value(false));
        terminate(c, null, "TERMINATE_CANNOT_PROCEED").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("직권 해지 — 요청 없이 운영자가 처리 · 사유 라벨 · 요청 사유 없음 · 이력 주체 운영자 · 양측 카드 · 양측 알림")
    void adminTerminatesWithoutRequest() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING, s -> s.createdAt(now.minusDays(2)));

        terminate(c, null, "TERMINATE_CONTENT_ERROR").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"));

        detail(c).andExpect(jsonPath("$.termination.reasonLabel").value("계약 내용 오류 — 성립 후 발견"))
                .andExpect(jsonPath("$.termination.requestReasonLabel").value(nullValue()));
        assertThat(historyOf(c)).anyMatch(h -> h.getEventType() == ContractEventType.TERMINATED
                && h.getActorType() == ContractActorType.ADMIN);
        adminMessages(brandChannel).andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_TERMINATED"));
        adminMessages(creatorChannel).andExpect(jsonPath("$.content[0].card.cardType").value("CONTRACT_TERMINATED"));
        verify(notifier).notifyBothParties(any(), eq("TERMINATED"));

        // 종결된 계약은 다시 해지할 수 없다.
        terminate(c, null, "TERMINATE_CONTENT_ERROR").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("철회한 요청은 큐에서 빠지고 다시 요청할 수 있다 · 다른 요청 id 로 처리하면 409")
    void withdrawThenRequestAgain() throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING, s -> s.createdAt(now.minusDays(2)));
        sellerPost(c, "/termination-request", Map.of("reasonCode", "QUALITY_ISSUE")).andExpect(status().isOk());
        Long first = json(detail(c)).at("/terminationRequest/requestId").asLong();
        sellerDelete(c, "/termination-request").andExpect(status().isOk());
        summary().andExpect(jsonPath("$.queues.TERMINATION").value(0));
        assertThat(historyOf(c)).anyMatch(h -> h.getEventType() == ContractEventType.TERMINATION_REQUEST_WITHDRAWN);

        sellerPost(c, "/termination-request", Map.of("reasonCode", "PRICE_TERMS_ERROR")).andExpect(status().isOk());
        Long second = json(detail(c)).at("/terminationRequest/requestId").asLong();
        assertThat(second).isNotEqualTo(first);
        terminate(c, first, "TERMINATE_CANNOT_PROCEED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_TERMINATION_REQUEST_NOT_PENDING"));
        terminate(c, second, "TERMINATE_CANNOT_PROCEED").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------

    private Contract pendingWithDocuments() {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING, s -> s.createdAt(now.minusDays(2)));
        attach(c, ContractDocumentType.SIGNED_PDF);
        attach(c, ContractDocumentType.AUDIT_TRAIL);
        return c;
    }

    private ResultActions terminate(Contract c, Long requestId, String reasonCode) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("reasonCode", reasonCode);
        body.put("explanation", "운영 확인 결과 해지합니다.");
        body.put("counterpartyConsentConfirmed", true);
        body.put("groupBuyNotOpenedConfirmed", true);
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

    private ResultActions sellerDelete(Contract c, String path) throws Exception {
        return mockMvc.perform(delete(SELLER_CONTRACTS + "/" + c.getId() + path)
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    private com.fasterxml.jackson.databind.JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
