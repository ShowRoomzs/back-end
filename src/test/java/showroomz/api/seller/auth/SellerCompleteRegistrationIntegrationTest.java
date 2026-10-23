package showroomz.api.seller.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTestSupport;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 온보딩(§6 · 06 설계서 8절) — 승인 후 첫 로그인이 내린 registerToken 으로 배송 · 반품 필수 정보를 등록한다.
 * 로그인부터 태워 「강제 진입 게이트」(registerToken)까지 함께 본다.
 */
@DisplayName("파트너센터 온보딩 — 제주 추가비 · 발송 기한 필수 · 무료배송 미입력 = 없음")
class SellerCompleteRegistrationIntegrationTest extends IntegrationTestSupport {

    private static final String AUTH = "/v1/seller/auth";

    private BrandFixture.Brand brand;

    @BeforeEach
    void setUp() {
        brand = fixture.createBrand("onboarding@brand.com", "온보딩브랜드");
        Seller seller = brand.seller();
        seller.setIsNewMember(true);
        sellerRepository.save(seller);
    }

    @Test
    @DisplayName("[OB-01] 전 필드 입력 — 201 · 마켓에 그대로 저장 · isNewMember=false · 기본정보 배송 정책에 제주 · 도서산간이 따로 보인다")
    void completeWithAllFields() throws Exception {
        complete(registerToken(), fullRequest()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.isNewMember").value(false));

        Market market = reloadMarket();
        assertThat(market.getJejuSurcharge()).isEqualTo(5000);
        assertThat(market.getRemoteAreaSurcharge()).isEqualTo(4000);
        assertThat(market.getShippingLeadDays()).isEqualTo(3);
        assertThat(market.getFreeShippingThreshold()).isEqualTo(50000);
        assertThat(market.getDefaultDeliveryFee()).isEqualTo(3000);
        assertThat(sellerRepository.findByEmail(brand.email()).orElseThrow().getIsNewMember()).isFalse();

        mockMvc.perform(get("/v1/seller/basic-info/delivery-policy")
                        .header(HttpHeaders.AUTHORIZATION, sellerToken(brand.seller())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jejuSurcharge").value(5000))
                .andExpect(jsonPath("$.remoteAreaSurcharge").value(4000));
    }

    @Test
    @DisplayName("[OB-02] 제주 · 도서산간 생략 — 둘 다 0")
    void surchargesDefaultToZero() throws Exception {
        Map<String, Object> request = fullRequest();
        request.remove("jejuSurcharge");
        request.remove("remoteAreaSurcharge");

        complete(registerToken(), request).andExpect(status().isCreated());

        Market market = reloadMarket();
        assertThat(market.getJejuSurcharge()).isZero();
        assertThat(market.getRemoteAreaSurcharge()).isZero();
    }

    @Test
    @DisplayName("[OB-03] 무료배송 기준금액 생략 — 0 이 아니라 null(무료배송 없음)로 저장한다. 0 이면 모든 주문이 무료배송이 된다")
    void freeShippingThresholdOmittedMeansNone() throws Exception {
        Map<String, Object> request = fullRequest();
        request.remove("freeShippingThreshold");

        complete(registerToken(), request).andExpect(status().isCreated());

        assertThat(reloadMarket().getFreeShippingThreshold()).isNull();
    }

    @Test
    @DisplayName("[OB-04] 발송 기한 생략 — 400 「발송 기한을 입력해 주세요.」 · 온보딩이 끝나지 않는다")
    void shippingLeadDaysRequired() throws Exception {
        Map<String, Object> request = fullRequest();
        request.remove("shippingLeadDays");

        complete(registerToken(), request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("발송 기한을 입력해 주세요."));

        assertThat(sellerRepository.findByEmail(brand.email()).orElseThrow().getIsNewMember()).isTrue();
    }

    @Test
    @DisplayName("[OB-08] 반품비 · 교환비 입력 폐기(1021 기-18) — 구 FE 가 보내도 무시하고 201")
    void claimFeesIgnored() throws Exception {
        Map<String, Object> request = fullRequest();
        request.put("returnFee", 3000);
        request.put("exchangeFee", 6000);

        complete(registerToken(), request).andExpect(status().isCreated());
        assertThat(reloadMarket().getDefaultDeliveryFee()).isEqualTo(3000);
    }

    @Test
    @DisplayName("[OB-09] 수취인 연락처 — 011 10자리는 통과 · 010-12 는 400")
    void recipientContact() throws Exception {
        Map<String, Object> invalid = fullRequest();
        invalid.put("contact", "010-12");
        complete(registerToken(), invalid).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("올바른 휴대폰 번호 형식으로 입력해 주세요. (예: 010-1234-5678)"));

        Map<String, Object> oldCarrier = fullRequest();
        oldCarrier.put("contact", "011-123-4567");
        complete(registerToken(), oldCarrier).andExpect(status().isCreated());
        assertThat(reloadMarket().getShippingContact()).isEqualTo("011-123-4567");
    }

    @Test
    @DisplayName("[OB-10] 기본정보 배송 정책 저장 — 제주 7,000 이 저장되고 생략하면 0")
    void basicInfoDeliveryPolicyJeju() throws Exception {
        complete(registerToken(), fullRequest()).andExpect(status().isCreated());
        String token = sellerToken(brand.seller());

        Map<String, Object> policy = new HashMap<>(Map.of(
                "shippingLeadDays", 3, "defaultDeliveryFee", 3000, "jejuSurcharge", 7000, "remoteAreaSurcharge", 5000));
        mockMvc.perform(put("/v1/seller/basic-info/delivery-policy").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(policy)))
                .andExpect(status().is2xxSuccessful());
        assertThat(reloadMarket().getJejuSurcharge()).isEqualTo(7000);

        policy.remove("jejuSurcharge");
        mockMvc.perform(put("/v1/seller/basic-info/delivery-policy").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(policy)))
                .andExpect(status().is2xxSuccessful());
        assertThat(reloadMarket().getJejuSurcharge()).isZero();
    }

    @Test
    @DisplayName("[55 P3] 기본정보 배송 정책에 무료배송 기준금액 0 을 저장하면 null — 조회도 null(무료배송 없음) · 50,000 은 그대로")
    void basicInfoZeroThresholdStoredAsNone() throws Exception {
        complete(registerToken(), fullRequest()).andExpect(status().isCreated());
        String token = sellerToken(brand.seller());

        Map<String, Object> policy = new HashMap<>(Map.of(
                "shippingLeadDays", 3, "defaultDeliveryFee", 3000, "freeShippingThreshold", 0));
        mockMvc.perform(put("/v1/seller/basic-info/delivery-policy").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(policy)))
                .andExpect(status().is2xxSuccessful());
        assertThat(reloadMarket().getFreeShippingThreshold()).isNull();
        mockMvc.perform(get("/v1/seller/basic-info/delivery-policy").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeShippingThreshold").doesNotExist());

        policy.put("freeShippingThreshold", 50_000);
        mockMvc.perform(put("/v1/seller/basic-info/delivery-policy").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(policy)))
                .andExpect(status().is2xxSuccessful());
        assertThat(reloadMarket().getFreeShippingThreshold()).isEqualTo(50_000);
    }

    @Test
    @DisplayName("[OV-06 · 55 P3] 무료배송 기준금액 0 을 명시하면 null(무료배송 없음)로 저장한다 — 시안 06 「0원이면 무료배송을 적용하지 않습니다」")
    void explicitZeroThresholdStoredAsNone() throws Exception {
        Map<String, Object> request = fullRequest();
        request.put("freeShippingThreshold", 0);

        complete(registerToken(), request).andExpect(status().isCreated());

        assertThat(reloadMarket().getFreeShippingThreshold()).isNull();
    }

    @Test
    @DisplayName("[OS-01] 가입 토큰이 없거나 위조면 401 — 온보딩이 끝나지 않고 배송 정책도 저장되지 않는다")
    void registerTokenRequired() throws Exception {
        Market before = reloadMarket();

        mockMvc.perform(post(AUTH + "/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(fullRequest())))
                .andExpect(status().isUnauthorized());
        complete("not-a-jwt", fullRequest()).andExpect(status().isUnauthorized());

        assertThat(sellerRepository.findByEmail(brand.email()).orElseThrow().getIsNewMember()).isTrue();
        // 픽스처가 넣어 둔 값(「김담당」 · 「1층 물류센터」) 그대로 — 요청 값(「4층 401호」 · 제주 5,000)이 들어가지 않았다.
        Market market = reloadMarket();
        assertThat(market.getShippingDetailAddress()).isEqualTo(before.getShippingDetailAddress()).isNotEqualTo("4층 401호");
        assertThat(market.getJejuSurcharge()).isEqualTo(before.getJejuSurcharge()).isNotEqualTo(5000);
        assertThat(market.getShippingLeadDays()).isEqualTo(before.getShippingLeadDays());
    }

    @Test
    @DisplayName("[OS-02] 같은 가입 토큰으로 다시 완료하면 400 ALREADY_REGISTERED — 처음 값이 덮이지 않는다")
    void completeTwiceRejected() throws Exception {
        String token = registerToken();
        complete(token, fullRequest()).andExpect(status().isCreated());

        Map<String, Object> second = fullRequest();
        second.put("jejuSurcharge", 9000);
        second.put("shippingLeadDays", 7);
        complete(token, second).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));

        Market market = reloadMarket();
        assertThat(market.getJejuSurcharge()).isEqualTo(5000);
        assertThat(market.getShippingLeadDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("[OB-10 ③] 기본정보 배송 정책 — 제주 추가비 -1 은 400 · 저장값 불변")
    void basicInfoDeliveryPolicyRejectsNegativeJeju() throws Exception {
        complete(registerToken(), fullRequest()).andExpect(status().isCreated());

        Map<String, Object> policy = new HashMap<>(Map.of(
                "shippingLeadDays", 3, "defaultDeliveryFee", 3000, "jejuSurcharge", -1, "remoteAreaSurcharge", 5000));
        mockMvc.perform(put("/v1/seller/basic-info/delivery-policy").header(HttpHeaders.AUTHORIZATION, sellerToken(brand.seller()))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(policy)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("제주 추가비는 0 이상이어야 합니다."));

        Market market = reloadMarket();
        assertThat(market.getJejuSurcharge()).isEqualTo(5000);
        assertThat(market.getRemoteAreaSurcharge()).isEqualTo(4000);
    }

    // ------------------------------------------------------------------ helpers

    private String registerToken() throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(post(AUTH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", brand.email(), "password", RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String token = body.path("registerToken").asText(null);
        if (token == null || token.isBlank()) {
            token = body.path("accessToken").asText();
        }
        assertThat(token).isNotBlank();
        return token;
    }

    private ResultActions complete(String registerToken, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post(AUTH + "/complete-registration")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + registerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)));
    }

    private Map<String, Object> fullRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("recipientName", "김담당");
        request.put("contact", "010-1234-5678");
        request.put("address", "서울특별시 강남구 테헤란로 123");
        request.put("detailAddress", "4층 401호");
        request.put("defaultDeliveryFee", 3000);
        request.put("freeShippingThreshold", 50000);
        request.put("jejuSurcharge", 5000);
        request.put("remoteAreaSurcharge", 4000);
        request.put("shippingLeadDays", 3);
        return request;
    }

    private Market reloadMarket() {
        return marketRepository.findById(brand.marketId()).orElseThrow();
    }
}
