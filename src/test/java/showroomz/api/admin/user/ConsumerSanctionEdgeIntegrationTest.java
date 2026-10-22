package showroomz.api.admin.user;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserSanctionRepository;
import showroomz.domain.member.user.service.UserSanctionService;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.notification.entity.DeviceToken;
import showroomz.domain.notification.repository.DeviceTokenRepository;
import showroomz.domain.notification.service.PushSender;
import showroomz.domain.notification.type.DevicePlatform;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.domain.wishlist.entitiy.Wishlist;
import showroomz.domain.wishlist.repository.WishlistRepository;
import showroomz.support.IntegrationTestSupport;
import showroomz.support.OrderFixture;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 51-6 소비자 계정 이용 정지 — 실제 데이터(상품 · 주문 · 문의 · 좋아요)가 있는 상태의 경계(설계서 12절 US-06 · 07 · 10 · 11 · 12 · 16).
 *
 * <p>{@code ConsumerSanctionIntegrationTest}가 「막히는가」를 빈 대상으로 본다면, 여기서는 정지 <b>전에</b> 만들어 둔 것 — 결제 대기 주문 · 남긴
 * 상품 문의 · 누른 좋아요 · 쓴 소명 — 이 정지 뒤에 어떻게 되는지를 본다. 새 결제 · 수정은 막히고, 되돌리기 · 취소는 열려 있어야 한다.
 */
@DisplayName("[통합] 51-6 소비자 계정 이용 정지 — 기존 데이터 · 탈퇴 회원 · 알림 실패")
class ConsumerSanctionEdgeIntegrationTest extends IntegrationTestSupport {

    private static final String ADMIN = "/v1/admin/users";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSanctionRepository sanctionRepository;
    @Autowired
    private UserSanctionService sanctionService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductVariantRepository productVariantRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderProductRepository orderProductRepository;
    @Autowired
    private ProductInquiryRepository productInquiryRepository;
    @Autowired
    private WishlistRepository wishlistRepository;
    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @MockitoBean
    private PushSender pushSender;

    private String adminToken;
    private Users mia;
    private String miaToken;
    private ProductVariant variant;

    @BeforeEach
    void setUpData() {
        adminToken = adminToken(fixture.createAdmin("admin@showroomz.test", "김운영"));
        mia = createConsumer("mia", "미아");
        miaToken = bearerToken(mia.getUsername(), RoleType.USER, mia.getId());
        variant = createVariant(fixture.createBrand("brand@showroomz.test", "라보에이치").market());
    }

    // ------------------------------------------------------------------ 정지 전에 만들어 둔 것

    @Test
    @DisplayName("정지 전에 만든 결제 대기 주문 — 새 결제창(재시도)은 막히고, 결제 전 취소는 된다")
    void pendingOrderBeforeSuspension() throws Exception {
        Order order = new OrderFixture(orderRepository, orderProductRepository, transactionTemplate)
                .pendingOrder(mia, variant, 1, 24900);
        suspend(mia).andExpect(status().isOk());

        mockMvc.perform(post("/v1/user/orders/" + order.getId() + "/payments").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, miaToken)
                        .content("{\"payment\":{\"method\":\"CARD\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_SUSPENDED"))
                .andExpect(jsonPath("$.message").value("이용 정지 중에는 구매할 수 없어요."));

        mockMvc.perform(post("/v1/user/orders/" + order.getId() + "/cancel").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("정지 전에 남긴 상품 문의 — 수정은 막히고(쓰기는 쓰기다), 삭제는 된다")
    void productInquiryBeforeSuspension() throws Exception {
        ProductInquiry inquiry = productInquiryRepository.save(ProductInquiry.builder()
                .user(mia).product(variant.getProduct()).type(ProductInquiryType.OPTION)
                .content("민감성인데 아침에도 써도 될까요?").build());
        suspend(mia).andExpect(status().isOk());

        mockMvc.perform(put("/v1/user/product-inquiries/" + inquiry.getId()).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, miaToken)
                        .content("{\"type\":\"OPTION\",\"content\":\"내용을 바꿉니다\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_SUSPENDED"))
                .andExpect(jsonPath("$.message").value("이용 정지 중에는 문의할 수 없어요."));
        assertThat(productInquiryRepository.findById(inquiry.getId()).orElseThrow().getContent())
                .isEqualTo("민감성인데 아침에도 써도 될까요?");

        mockMvc.perform(delete("/v1/user/product-inquiries/" + inquiry.getId()).header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(status().is2xxSuccessful());
        assertThat(productInquiryRepository.findById(inquiry.getId())).isEmpty();
    }

    @Test
    @DisplayName("정지 전에 누른 좋아요 — 취소는 되고, 다시 누르는 것은 막힌다")
    void wishlistBeforeSuspension() throws Exception {
        Product product = variant.getProduct();
        wishlistRepository.save(new Wishlist(mia, product));
        suspend(mia).andExpect(status().isOk());

        mockMvc.perform(delete("/v1/user/wishlist/" + product.getProductId()).header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(status().is2xxSuccessful());
        assertThat(wishlistRepository.countByUser_Id(mia.getId())).isZero();

        mockMvc.perform(post("/v1/user/wishlist/" + product.getProductId()).header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_SUSPENDED"));
        assertThat(wishlistRepository.countByUser_Id(mia.getId())).isZero();
    }

    @Test
    @DisplayName("정지 소명 문의는 내용만 고칠 수 있고, 정지가 풀린 뒤에도 답변 전이면 고칠 수 있다")
    void appealCanBeEditedEvenAfterRelease() throws Exception {
        suspend(mia).andExpect(status().isOk());
        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/v1/user/inquiries")
                        .contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, miaToken)
                        .content("{\"type\":\"ACCOUNT\",\"content\":\"정지 사유를 확인하고 싶어요\",\"sanctionAppeal\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long inquiryId = created.get("inquiryId").asLong();

        updateInquiry(inquiryId, "{\"type\":\"SERVICE\",\"content\":\"유형을 바꿔 봅니다\"}")
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(ADMIN + "/" + mia.getId() + "/release").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, adminToken).content("{\"reason\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        updateInquiry(inquiryId, "{\"type\":\"ACCOUNT\",\"content\":\"주문 내역을 다시 확인해 주세요\"}")
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/v1/user/inquiries/" + inquiryId).header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(jsonPath("$.content").value("주문 내역을 다시 확인해 주세요"))
                .andExpect(jsonPath("$.typeName").value("계정 이용 정지"));
    }

    @Test
    @DisplayName("어드민 1:1 문의 목록 — 소명 문의는 표식과 「계정 이용 정지」 유형명으로 보이고, 유형 필터는 계정에 넣는다")
    void adminInquiryListShowsAppeal() throws Exception {
        suspend(mia).andExpect(status().isOk());
        mockMvc.perform(post("/v1/user/inquiries").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, miaToken)
                        .content("{\"type\":\"ACCOUNT\",\"content\":\"정지 사유를 확인하고 싶어요\",\"sanctionAppeal\":true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/admin/inquiries").param("type", "ACCOUNT").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("ACCOUNT"))
                .andExpect(jsonPath("$.content[0].typeName").value("계정 이용 정지"))
                .andExpect(jsonPath("$.content[0].sanctionAppeal").value(true));
    }

    // ------------------------------------------------------------------ 탈퇴 회원

    @Test
    @DisplayName("정지 중 탈퇴한 회원 — 어드민은 열린 정지를 보되 조치 · 메모 기입은 못 하고, 앱 상태는 403 이다")
    void withdrawnWhileSuspended() throws Exception {
        suspend(mia).andExpect(status().isOk());
        mockMvc.perform(delete("/v1/user/auth/withdraw").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, miaToken).content("{\"agreeConsent\":true}"))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(ADMIN + "/" + mia.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCard.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.statusCard.sanction.reason").value("ABUSIVE_LANGUAGE"))
                .andExpect(jsonPath("$.statusCard.actions.canSuspend").value(false))
                .andExpect(jsonPath("$.statusCard.actions.canRelease").value(false))
                .andExpect(jsonPath("$.histories.content[0].eventType").value("WITHDRAWN"));

        mockMvc.perform(post(ADMIN + "/" + mia.getId() + "/memos").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, adminToken).content("{\"content\":\"탈퇴 후 메모\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_SANCTION_WITHDRAWN"));

        mockMvc.perform(get("/v1/user/account-status").header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_WITHDRAWN"));

        // 같은 소셜 계정으로 돌아오면(SocialLoginService 복구 분기) 정지로 복귀하고, 이력에 재가입이 남는다
        transactionTemplate.executeWithoutResult(status ->
                sanctionService.restoreOnRejoin(userRepository.findById(mia.getId()).orElseThrow()));
        mockMvc.perform(get(ADMIN + "/" + mia.getId() + "/histories").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.content[0].eventType").value("REJOINED"))
                .andExpect(jsonPath("$.content[0].detail").value("정지 상태로 복귀"))
                .andExpect(jsonPath("$.content[1].eventType").value("WITHDRAWN"));
        mockMvc.perform(get("/v1/user/account-status").header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspension.noticePending").value(true));
    }

    // ------------------------------------------------------------------ 알림 실패

    @Test
    @DisplayName("푸시 발송이 실패해도 정지는 커밋된 채 200 이다 — 앱은 다음 진입에 상태를 읽는다")
    void pushFailureDoesNotUndoSuspension() throws Exception {
        deviceTokenRepository.save(new DeviceToken(mia, "mia-device-token", DevicePlatform.IOS, LocalDateTime.now()));
        given(pushSender.send(anyList(), any())).willThrow(new IllegalStateException("FCM 응답 없음"));

        suspend(mia).andExpect(status().isOk());

        assertThat(userRepository.findById(mia.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(sanctionRepository.findByUserIdAndReleasedAtIsNull(mia.getId())).isPresent();
        mockMvc.perform(get("/v1/user/account-status").header(HttpHeaders.AUTHORIZATION, miaToken))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    // ------------------------------------------------------------------ 헬퍼

    private ResultActions suspend(Users target) throws Exception {
        return mockMvc.perform(post(ADMIN + "/" + target.getId() + "/suspend").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, adminToken).content("{\"reason\":\"ABUSIVE_LANGUAGE\"}"));
    }

    private ResultActions updateInquiry(long inquiryId, String body) throws Exception {
        return mockMvc.perform(put("/v1/user/inquiries/" + inquiryId).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, miaToken).content(body));
    }

    private Users createConsumer(String username, String nickname) {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users(username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.KAKAO, RoleType.USER, now, now);
        return userRepository.save(user);
    }

    private ProductVariant createVariant(Market market) {
        Category category = new Category();
        category.setName("뷰티");
        categoryRepository.save(category);

        Product product = new Product();
        product.setMarket(market);
        product.setCategory(category);
        product.setName("시카 리페어 앰플 30ml");
        product.setRegularPrice(38000);
        product.setSalePrice(24900);
        productRepository.save(product);

        return productVariantRepository.save(new ProductVariant(product, "단품", 38000, 24900, 10, true));
    }
}
