package showroomz.api.admin.transaction;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.scenario.OrderFlowTestSupport;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.support.IntegrationTest;

import java.util.List;
import java.util.Map;

/**
 * 어드민 거래 관리(06a ~ 06d) 권한 — 엔드포인트 23개 전부를 브랜드 셀러 · 소비자 토큰으로 403, 토큰 없이 401 인지 본다. 화면별 테스트는
 * 대표 한두 개만 치므로, 새 엔드포인트가 권한 설정 밖으로 새는 것을 여기서 잡는다. id 는 없는 값이어도 된다 — 권한이 먼저 막는다.
 */
@IntegrationTest
class AdminTransactionAuthIntegrationTest extends OrderFlowTestSupport {

    private record Endpoint(HttpMethod method, String path) {

        MockHttpServletRequestBuilder request() {
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(method, path);
            return method == HttpMethod.GET ? builder : builder.contentType(MediaType.APPLICATION_JSON).content("{}");
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
            // 06a 주문 조회
            new Endpoint(HttpMethod.GET, "/v1/admin/orders"),
            new Endpoint(HttpMethod.GET, "/v1/admin/orders/summary"),
            new Endpoint(HttpMethod.GET, "/v1/admin/orders/1"),
            new Endpoint(HttpMethod.PATCH, "/v1/admin/orders/groups/1/delivered-at"),
            new Endpoint(HttpMethod.POST, "/v1/admin/orders/groups/1/shipment"),
            new Endpoint(HttpMethod.POST, "/v1/admin/orders/groups/1/cancel"),
            new Endpoint(HttpMethod.POST, "/v1/admin/orders/groups/1/refund-tasks"),
            new Endpoint(HttpMethod.POST, "/v1/admin/orders/groups/1/defect-claims"),
            new Endpoint(HttpMethod.POST, "/v1/admin/orders/groups/1/lost"),
            new Endpoint(HttpMethod.POST, "/v1/admin/orders/groups/1/delivered"),
            // 06b 반품·교환
            new Endpoint(HttpMethod.GET, "/v1/admin/claims"),
            new Endpoint(HttpMethod.GET, "/v1/admin/claims/summary"),
            new Endpoint(HttpMethod.GET, "/v1/admin/claims/1"),
            new Endpoint(HttpMethod.POST, "/v1/admin/claims/1/dispute-acceptance"),
            new Endpoint(HttpMethod.POST, "/v1/admin/claims/1/refund-tasks"),
            // 06c 환불 관리
            new Endpoint(HttpMethod.GET, "/v1/admin/refunds"),
            new Endpoint(HttpMethod.GET, "/v1/admin/refunds/summary"),
            new Endpoint(HttpMethod.GET, "/v1/admin/refunds/1"),
            new Endpoint(HttpMethod.POST, "/v1/admin/refunds/1/execute"),
            new Endpoint(HttpMethod.POST, "/v1/admin/refunds/1/void"),
            new Endpoint(HttpMethod.POST, "/v1/admin/refunds/1/manual-complete"),
            // 06d 예외 관리
            new Endpoint(HttpMethod.GET, "/v1/admin/order-exceptions"),
            new Endpoint(HttpMethod.GET, "/v1/admin/order-exceptions/summary"));

    @Test
    @DisplayName("[AU-01] 엔드포인트 23개 — 브랜드 셀러 · 소비자 토큰 403 · 토큰 없음 401")
    void everyEndpointRequiresAdmin() throws Exception {
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(ENDPOINTS).hasSize(23);
        for (Endpoint endpoint : ENDPOINTS) {
            int seller = statusOf(endpoint, brandToken);
            int consumer = statusOf(endpoint, consumerToken);
            int anonymous = statusOf(endpoint, null);
            softly.assertThat(seller).as(endpoint + " · 셀러").isEqualTo(403);
            softly.assertThat(consumer).as(endpoint + " · 소비자").isEqualTo(403);
            softly.assertThat(anonymous).as(endpoint + " · 토큰 없음").isEqualTo(401);
        }
        softly.assertAll();
    }

    @Test
    @DisplayName("[S-03] 인플루언서(CREATOR) 토큰 — 엔드포인트 23개 전부 403")
    void creatorTokenForbidden() throws Exception {
        String creatorToken = bearerToken(creator.getUser().getUsername(), RoleType.CREATOR, creator.getUser().getId());
        SoftAssertions softly = new SoftAssertions();
        for (Endpoint endpoint : ENDPOINTS) {
            softly.assertThat(statusOf(endpoint, creatorToken)).as(endpoint + " · 인플루언서").isEqualTo(403);
        }
        softly.assertAll();
    }

    @Test
    @DisplayName("[S-01] 어드민 권한이지만 userId 가 빠진 토큰 — 쓰기 API 는 401 UNAUTHORIZED(운영자 id 없이 기록하지 않는다) · 조회는 통과")
    void adminTokenWithoutUserId() throws Exception {
        Seller admin = fixture.createAdmin("no-pk-admin@showroomz.test", "운영자");
        String token = bearerToken(admin.getEmail(), RoleType.ADMIN, null);
        SoftAssertions softly = new SoftAssertions();
        for (Endpoint endpoint : ENDPOINTS) {
            if (endpoint.method() == HttpMethod.GET) {
                continue;
            }
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(endpoint.method(), endpoint.path())
                    .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODIES.getOrDefault(endpoint.path().replaceAll(".*/", ""), "{}"));
            MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
            softly.assertThat(response.getStatus()).as(endpoint + " · userId 없음").isEqualTo(401);
            softly.assertThat(response.getContentAsString()).as(endpoint + " · 코드").contains("\"UNAUTHORIZED\"");
        }
        softly.assertThat(statusOf(new Endpoint(HttpMethod.GET, "/v1/admin/refunds"), token)).isEqualTo(200);
        // 06a 상세는 조회지만 열람 로그가 운영자 id 를 요구한다 — 같은 401.
        softly.assertThat(statusOf(new Endpoint(HttpMethod.GET, "/v1/admin/orders/1"), token)).isEqualTo(401);
        softly.assertAll();
    }

    /** 본문 검증(400)이 운영자 확인보다 먼저 막지 않게 — 엔드포인트마다 형식이 맞는 본문. id 는 없는 값이다. */
    private static final Map<String, String> VALID_BODIES = Map.of(
            "delivered-at", "{\"deliveredAt\":\"2026-10-01T10:00:00\",\"reason\":\"정정\"}",
            "shipment", "{\"carrier\":\"CJ\",\"trackingNumber\":\"400000000001\",\"note\":\"대행\"}",
            "cancel", "{\"reasonCode\":\"DEFECT\",\"consumerMessage\":\"회수\"}",
            "refund-tasks", "{\"reason\":\"RECALL\",\"amount\":1000,\"detail\":\"근거\"}",
            "defect-claims", "{\"items\":[{\"orderProductId\":1,\"quantity\":1}],\"reasonCode\":\"DAMAGED_OR_DEFECTIVE\","
                    + "\"detail\":\"내용\",\"evidenceImageUrls\":[\"https://img.test/d.jpg\"]}",
            "lost", "{\"reason\":\"분실\"}",
            "delivered", "{\"deliveredAt\":\"2026-10-01T10:00:00\",\"reason\":\"수령\"}",
            "dispute-acceptance", "{\"detail\":\"근거\"}",
            "void", "{\"reason\":\"오편입\"}",
            "manual-complete", "{\"note\":\"콘솔 처리\"}");

    private int statusOf(Endpoint endpoint, String token) throws Exception {
        MockHttpServletRequestBuilder builder = endpoint.request();
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, token);
        }
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }
}
