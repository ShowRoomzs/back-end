package showroomz.api.admin.transaction;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import showroomz.api.scenario.OrderFlowTestSupport;
import showroomz.support.IntegrationTest;

import java.util.List;

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

    private int statusOf(Endpoint endpoint, String token) throws Exception {
        MockHttpServletRequestBuilder builder = endpoint.request();
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, token);
        }
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }
}
