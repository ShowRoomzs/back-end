package showroomz.api.creator.basicinfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스튜디오 기본정보 관리 — 권한 · 개인정보(50 테스트 시나리오 10절 AU · PV). `/v1/creator/**` 는 인플루언서만 들어오고, 화면 응답 어디에도 본인 인증
 * 원값 · 계좌번호 전체 · 주민등록번호 뒤 7자리 · 암호문이 실리지 않는다.
 */
@DisplayName("스튜디오 기본정보 — 권한 · 원값 미노출")
class CreatorBasicInfoAccessIntegrationTest extends SettlementTestSupport {

    private static final String BASE = "/v1/creator/basic-info";

    /** 11개 엔드포인트 — 경로 변수는 아무 id 나 둔다(권한이 먼저 막는다). */
    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint(HttpMethod.GET, BASE + "/account"),
            new Endpoint(HttpMethod.PATCH, BASE + "/account/business-email"),
            new Endpoint(HttpMethod.POST, BASE + "/withdrawal-requests"),
            new Endpoint(HttpMethod.POST, BASE + "/withdrawal-requests/1/cancel"),
            new Endpoint(HttpMethod.POST, BASE + "/withdrawal-requests/1/acknowledge"),
            new Endpoint(HttpMethod.GET, BASE + "/settlement"),
            new Endpoint(HttpMethod.GET, BASE + "/settlement-account/change-impact"),
            new Endpoint(HttpMethod.PUT, BASE + "/settlement-account"),
            new Endpoint(HttpMethod.POST, BASE + "/business-type-change-requests"),
            new Endpoint(HttpMethod.POST, BASE + "/business-type-change-requests/1/cancel"),
            new Endpoint(HttpMethod.POST, BASE + "/business-type-change-requests/1/acknowledge"));

    private record Endpoint(HttpMethod method, String url) {
    }

    @Test
    @DisplayName("[AU-01] 11개 엔드포인트 — 토큰 없음 401 · 소비자 · 셀러 · 어드민 403")
    void onlyCreatorsAllowed() throws Exception {
        for (Endpoint endpoint : ENDPOINTS) {
            mockMvc.perform(call(endpoint)).andExpect(status().isUnauthorized());
            for (String token : List.of(consumerToken, brandToken, adminToken)) {
                mockMvc.perform(call(endpoint).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
            }
        }
    }

    @Test
    @DisplayName("[AU-02] 인플루언서 역할인데 회원 행이 없으면 404 CREATOR_NOT_FOUND")
    void creatorRowMissing() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Users orphan = userRepository.save(new Users("creator-orphan", "고아", "orphan@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.CREATOR, now, now));
        String token = bearerToken(orphan.getUsername(), RoleType.CREATOR, orphan.getId());

        for (String url : List.of(BASE + "/account", BASE + "/settlement", BASE + "/settlement-account/change-impact")) {
            mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("[PV-01] 원값 미노출 — 계정 탭 · 정산 정보 · 계좌 변경 · 비사업자 전환 요청 응답에 실명 · 휴대폰 · 계좌번호 · 주민번호 · 암호문이 없다")
    void rawValuesNeverInResponses() throws Exception {
        registerCreatorAccount();
        registerCreatorResidentNumber();
        jdbc.update("UPDATE creator SET phone_number = ? WHERE creator_id = ?", "010-1234-6416", creator.getId());
        if (!bankRepository.existsById("088")) {
            fixture.createBank("088", "신한은행");
        }
        String residentEnc = creatorRepository.findById(creator.getId()).orElseThrow().getResidentRegistrationNumberEnc();

        StringBuilder bodies = new StringBuilder();
        bodies.append(creatorGet(BASE + "/account").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        bodies.append(creatorGet(BASE + "/settlement").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        bodies.append(mockMvc.perform(request(HttpMethod.PUT, BASE + "/settlement-account")
                        .header(HttpHeaders.AUTHORIZATION, creatorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("bankCode", "088", "accountNumber", "1101234567",
                                "bankbookImageUrl", "https://cdn.showroomz.test/creator-document/bankbook.jpg"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        jdbc.update("UPDATE creator SET business_type = 'BUSINESS' WHERE creator_id = ?", creator.getId());
        Map<String, Object> toIndividual = new HashMap<>();
        toIndividual.put("targetType", "INDIVIDUAL");
        toIndividual.put("residentRegistrationNumber", "950812-2345678");
        toIndividual.put("evidenceFileUrl", "https://cdn.showroomz.test/creator-document/closure.pdf");
        toIndividual.put("evidenceFileName", "폐업사실증명원.pdf");
        toIndividual.put("evidenceFileSize", 430_080);
        bodies.append(mockMvc.perform(request(HttpMethod.POST, BASE + "/business-type-change-requests")
                        .header(HttpHeaders.AUTHORIZATION, creatorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(toIndividual)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        bodies.append(creatorGet(BASE + "/settlement").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(bodies.toString())
                .doesNotContain("김지민")
                .doesNotContain("010-1234-6416").doesNotContain("01012346416")
                .doesNotContain("123456789012").doesNotContain("1101234567")
                .doesNotContain(RESIDENT_NUMBER).doesNotContain(RESIDENT_NUMBER.replace("-", ""))
                .doesNotContain("2345678")
                .doesNotContain(residentEnc)
                .contains("김*민").contains("010-****-6416").contains("950812-*******");
    }

    private MockHttpServletRequestBuilder call(Endpoint endpoint) {
        MockHttpServletRequestBuilder builder = request(endpoint.method(), endpoint.url());
        if (endpoint.method() != HttpMethod.GET) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return builder;
    }
}
