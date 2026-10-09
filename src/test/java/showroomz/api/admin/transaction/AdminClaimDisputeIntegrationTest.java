package showroomz.api.admin.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.claim.ClaimTestSupport;
import showroomz.domain.member.user.entity.Users;
import showroomz.support.IntegrationTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 반려 이의 자동 연결(기획 §38-8 B-12 확정 2026-10-09) — 앱 「이의 제기」 문의가 클레임에 붙고, 어드민 반품·교환(06b)이
 * 요약 「반려 이의 N건」 · 목록 보조줄 · 상세 ④ 「소비자 이의」로 보인다. 기각은 문의 답변, 인용은 반려 보류 종결로 빠진다.
 */
@IntegrationTest
class AdminClaimDisputeIntegrationTest extends ClaimTestSupport {

    private static final String INQUIRIES = "/v1/user/inquiries";
    private static final String ADMIN_CLAIMS = "/v1/admin/claims";

    private String admin;

    @BeforeEach
    void setUpAdmin() {
        admin = adminToken(fixture.createAdmin("dispute-ops@showroomz.test", "운영자"));
    }

    @Test
    @DisplayName("[DP-01] 이의 제기 — 문의에 claimId · 클레임 주문이 채워지고 어드민 요약 · 목록 · 상세에 보인다")
    void disputeLinkedAndShownToAdmin() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));

        long inquiryId = json(dispute(claimId, null).andExpect(status().isCreated())).get("inquiryId").asLong();

        Map<String, Object> inquiry = jdbc.queryForMap("SELECT claim_id, order_id FROM one_to_one_inquiry WHERE inquiry_id = ?",
                inquiryId);
        assertThat(((Number) inquiry.get("claim_id")).longValue()).isEqualTo(claimId);
        assertThat(((Number) inquiry.get("order_id")).longValue()).isEqualTo(orderIdOf(claimId));
        assertThat(((Number) claimRow(claimId).get("dispute_inquiry_id")).longValue()).isEqualTo(inquiryId);
        assertThat(claimRow(claimId).get("disputed_at")).isNotNull();

        adminGet(ADMIN_CLAIMS + "/summary").andExpect(status().isOk()).andExpect(jsonPath("$.disputeCount").value(1));
        JsonNode rows = json(adminGet(ADMIN_CLAIMS + "?tab=REJECT_HOLD").andExpect(status().isOk())).get("content");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("disputeOpen").asBoolean()).isTrue();
        assertThat(rows.get(0).get("disputedAt").isNull()).isFalse();
        adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.dispute.inquiryId").value(inquiryId))
                .andExpect(jsonPath("$.dispute.content").value("받았을 때부터 오염이 있었습니다."))
                .andExpect(jsonPath("$.dispute.imageUrls[0]").value("https://img.test/d1.jpg"))
                .andExpect(jsonPath("$.dispute.answered").value(false));
        userGet(INQUIRIES + "/" + inquiryId).andExpect(status().isOk()).andExpect(jsonPath("$.claimId").value(claimId));
    }

    @Test
    @DisplayName("[DP-02] 이의 없는 반려 보류 — 요약 0 · 상세 dispute null")
    void noDispute() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));

        adminGet(ADMIN_CLAIMS + "/summary").andExpect(jsonPath("$.disputeCount").value(0));
        adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(jsonPath("$.dispute").doesNotExist());
    }

    @Test
    @DisplayName("[DP-03] 가드 — 반려 보류 아님 409 · 남의 클레임 404 · 유형 다름 400 · 다른 주문 400")
    void guards() throws Exception {
        Long received = received(returnClaim(deliveredGroup(creamVariant, 1)));
        dispute(received, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_STATE_CHANGED"));

        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        Users stranger = createConsumer("dispute-stranger", "타인");
        String strangerToken = bearerToken(stranger.getEmail(), RoleType.USER, stranger.getId());
        mockMvc.perform(post(INQUIRIES).header(HttpHeaders.AUTHORIZATION, strangerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(disputeBody(claimId, null))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));

        Map<String, Object> wrongType = disputeBody(claimId, null);
        wrongType.put("type", "DELIVERY");
        userPost(INQUIRIES, wrongType).andExpect(status().isBadRequest());

        Long otherOrderId = orderIdOf(received);
        userPost(INQUIRIES, disputeBody(claimId, otherOrderId)).andExpect(status().isBadRequest());

        assertThat(claimRow(claimId).get("dispute_inquiry_id")).isNull();
    }

    @Test
    @DisplayName("[DP-04] 답변 전 이의가 있으면 409 — 답변(기각) 뒤에는 다시 걸 수 있고 요약에서 빠졌다가 다시 들어온다")
    void oneWaitingDisputeAtATime() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        long first = json(dispute(claimId, null).andExpect(status().isCreated())).get("inquiryId").asLong();

        dispute(claimId, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_DISPUTE_ALREADY_EXISTS"));

        adminPost("/v1/admin/inquiries/" + first + "/answer", Map.of("content", "검수 사진상 사용 흔적이 확인되어 반려가 유지됩니다."))
                .andExpect(status().isOk());
        adminGet(ADMIN_CLAIMS + "/summary").andExpect(jsonPath("$.disputeCount").value(0));
        adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(jsonPath("$.dispute.answered").value(true));

        long second = json(dispute(claimId, null).andExpect(status().isCreated())).get("inquiryId").asLong();
        assertThat(((Number) claimRow(claimId).get("dispute_inquiry_id")).longValue()).isEqualTo(second);
        adminGet(ADMIN_CLAIMS + "/summary").andExpect(jsonPath("$.disputeCount").value(1));
    }

    @Test
    @DisplayName("[DP-05] 이의 문의 수정은 내용만 — 주문을 바꾸면 400 · 삭제하면 클레임 이의가 풀린다")
    void editAndDelete() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        long inquiryId = json(dispute(claimId, null).andExpect(status().isCreated())).get("inquiryId").asLong();
        Long otherOrderId = orderIdOf(received(returnClaim(deliveredGroup(creamVariant, 1))));

        Map<String, Object> edit = disputeBody(null, null);
        edit.put("content", "사진을 더 첨부합니다.");
        userPut(INQUIRIES + "/" + inquiryId, edit).andExpect(status().is2xxSuccessful());
        assertThat(jdbc.queryForObject("SELECT content FROM one_to_one_inquiry WHERE inquiry_id = ?", String.class, inquiryId))
                .isEqualTo("사진을 더 첨부합니다.");
        assertThat(((Number) jdbc.queryForObject("SELECT order_id FROM one_to_one_inquiry WHERE inquiry_id = ?",
                Long.class, inquiryId)).longValue()).isEqualTo(orderIdOf(claimId));

        edit.put("orderId", otherOrderId);
        userPut(INQUIRIES + "/" + inquiryId, edit).andExpect(status().isBadRequest());

        mockMvc.perform(delete(INQUIRIES + "/" + inquiryId).header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().is2xxSuccessful());
        assertThat(claimRow(claimId).get("dispute_inquiry_id")).isNull();
        assertThat(claimRow(claimId).get("disputed_at")).isNull();
        adminGet(ADMIN_CLAIMS + "/summary").andExpect(jsonPath("$.disputeCount").value(0));
    }

    @Test
    @DisplayName("[DP-06] 인용하면 반려 보류를 벗어나 요약에서 빠진다 — 상세에는 이의가 기록으로 남는다")
    void acceptedDisputeLeavesCount() throws Exception {
        Long claimId = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        long inquiryId = json(dispute(claimId, null).andExpect(status().isCreated())).get("inquiryId").asLong();

        adminPost(ADMIN_CLAIMS + "/" + claimId + "/dispute-acceptance",
                Map.of("detail", "1:1 문의 사진상 배송 시점 오염")).andExpect(status().isOk());

        adminGet(ADMIN_CLAIMS + "/summary").andExpect(jsonPath("$.disputeCount").value(0));
        adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(jsonPath("$.dispute.inquiryId").value(inquiryId));
        JsonNode rows = json(adminGet(ADMIN_CLAIMS + "?tab=DONE")).get("content");
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.get("claim").get("claimId").asLong()).isEqualTo(claimId);
            assertThat(row.get("disputeOpen").asBoolean()).isFalse();
        });
    }

    @Test
    @DisplayName("[DP-07] 교환 반려도 이의를 받는다 — 요약에 세지만 인용은 409")
    void exchangeDisputeRecordedButNotAcceptable() throws Exception {
        Long claimId = rejectedClaim(exchangeClaim(deliveredGroup(creamVariant, 1),
                addSamePriceVariant(creamVariant, 10)));

        dispute(claimId, null).andExpect(status().isCreated());

        adminGet(ADMIN_CLAIMS + "/summary").andExpect(jsonPath("$.disputeCount").value(1));
        adminGet(ADMIN_CLAIMS + "/" + claimId).andExpect(jsonPath("$.canAcceptDispute").value(false))
                .andExpect(jsonPath("$.dispute.answered").value(false));
    }

    // ------------------------------------------------------------------ 도우미

    private ResultActions dispute(Long claimId, Long orderId) throws Exception {
        return userPost(INQUIRIES, disputeBody(claimId, orderId));
    }

    private static Map<String, Object> disputeBody(Long claimId, Long orderId) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "CANCEL_EXCHANGE_RETURN");
        body.put("content", "받았을 때부터 오염이 있었습니다.");
        body.put("imageUrls", List.of("https://img.test/d1.jpg"));
        body.put("claimId", claimId);
        body.put("orderId", orderId);
        return body;
    }

    private Long orderIdOf(Long claimId) {
        return ((Number) claimRow(claimId).get("order_id")).longValue();
    }

    private ResultActions userPut(String url, Object body) throws Exception {
        return mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions adminGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}
