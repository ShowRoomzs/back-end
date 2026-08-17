package showroomz.api.admin.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.history.entity.UserStatusHistory;
import showroomz.domain.history.repository.UserStatusHistoryRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §25-3 어드민 소비자 목록 — 통합 테스트.
 *
 * <p>이 화면의 골격은 <b>개인정보 열람 통제</b>라, 마스킹이 서버에서 끝나는지를 응답 본문으로
 * 확인한다. 원본이 페이로드에 실려 있으면 화면이 아무리 가려도 통제가 아니다.
 *
 * <p>나머지 셋은 쿼리가 하는 일이라 단위 테스트로는 검증할 수 없다 — 검색 3축 판별, 탭·요약
 * 건수, 누적 주문 집계(취소 제외)와 그 정렬.
 */
@DisplayName("[통합] §25-3 어드민 소비자 목록")
class AdminConsumerListIntegrationTest extends IntegrationTestSupport {

    private static final String PATH = "/v1/admin/users";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserStatusHistoryRepository userStatusHistoryRepository;
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

    private String adminToken;
    private ProductVariant variant;

    private Users hong;      // 활성 · 카카오 · 주문 2건(+ 전부 취소된 주문 1건)
    private Users yuri;      // 정지 · 애플 · 주문 0건
    private Users dohyun;    // 탈퇴 · 네이버 · 주문 1건

    @BeforeEach
    void setUpConsumers() {
        Seller admin = fixture.createAdmin("admin@showroomz.test", "김운영");
        adminToken = adminToken(admin);

        Market market = fixture.createBrand("brand@showroomz.test", "제니의 뷰티룸").market();
        variant = createVariant(market);

        hong = createConsumer("hong", "홍길동", "홍길동", "010-1111-1234",
                ProviderType.KAKAO, UserStatus.NORMAL);
        yuri = createConsumer("yuri", "유리", "박지은", "01022222031",
                ProviderType.APPLE, UserStatus.SUSPENDED);
        dohyun = createConsumer("dohyun", "도현", "임도현", "010-3333-3306",
                ProviderType.NAVER, UserStatus.WITHDRAWN);

        // 가입만 하고 약관 동의를 끝내지 않은 계정 — 아직 회원이 아니라 목록에 나오면 안 된다
        createGuest("half-signed-up");

        placeOrder(hong, OrderProductStatus.PURCHASE_CONFIRMED);
        placeOrder(hong, OrderProductStatus.PENDING);
        placeOrder(hong, OrderProductStatus.CANCELLED);
        placeOrder(dohyun, OrderProductStatus.PURCHASE_CONFIRMED);
    }

    @Test
    @DisplayName("이름·휴대폰은 서버가 가려서 내려보낸다 — 목록에는 해제 경로가 없어 원본이 실릴 이유가 없다")
    void namesAndPhonesAreMaskedByTheServer() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("keyword", "홍길동")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].memberNo").value("CST-" + hong.getId()))
                .andExpect(jsonPath("$.content[0].nickname").value("홍길동"))
                .andExpect(jsonPath("$.content[0].maskedName").value("홍*동"))
                .andExpect(jsonPath("$.content[0].maskedPhone").value("010-****-1234"))
                // 원본 필드는 응답 스키마에 아예 없다
                .andExpect(jsonPath("$.content[0].name").doesNotExist())
                .andExpect(jsonPath("$.content[0].phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.content[0].email").doesNotExist());
    }

    @Test
    @DisplayName("전체 탭은 가입 미완료 계정을 빼고 요약 건수를 상태별로 나눠 준다")
    void allTabSummarySplitsCountsByStatus() throws Exception {
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.summary.total").value(3))
                .andExpect(jsonPath("$.summary.active").value(1))
                .andExpect(jsonPath("$.summary.suspended").value(1))
                .andExpect(jsonPath("$.summary.withdrawn").value(1))
                // 최근 30일 신규 정지는 정지 탭의 요약이다 — 다른 탭에서는 행 자체를 그리지 않는다
                .andExpect(jsonPath("$.summary.newSuspendedIn30Days").doesNotExist());
    }

    @Test
    @DisplayName("정지 탭은 요약에 최근 30일 신규 정지를 덧붙인다")
    void suspendedTabAddsNewSuspensionCount() throws Exception {
        userStatusHistoryRepository.save(UserStatusHistory.builder()
                .user(yuri)
                .previousStatus(UserStatus.NORMAL)
                .newStatus(UserStatus.SUSPENDED)
                .reason("반품 남용")
                .build());

        mockMvc.perform(get(PATH)
                        .param("tab", "SUSPENDED")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(yuri.getId()))
                .andExpect(jsonPath("$.content[0].status").value("SUSPENDED"))
                .andExpect(jsonPath("$.summary.suspended").value(1))
                .andExpect(jsonPath("$.summary.newSuspendedIn30Days").value(1));
    }

    @Test
    @DisplayName("숫자 4자리는 휴대폰 뒤 4자리로 찾는다 — 전체 번호를 넣으면 0건이고, 빈 상태가 규칙을 알려 준다")
    void fourDigitsSearchThePhoneSuffix() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("keyword", "2031")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(yuri.getId()));

        mockMvc.perform(get(PATH)
                        .param("keyword", "010-2222-2031")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("CST- 접두사는 회원번호 축이고, 뒤가 숫자가 아니면 전체 목록이 아니라 0건이다")
    void memberNumberSearchIsExactAndFailsClosed() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("keyword", "CST-" + dohyun.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(dohyun.getId()));

        mockMvc.perform(get(PATH)
                        .param("keyword", "CST-없음")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("누적 주문은 취소만 남은 주문을 세지 않고, 그 값으로 정렬된다")
    void orderCountExcludesFullyCancelledOrdersAndDrivesSorting() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("sort", "ORDER_COUNT_DESC")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                // 홍길동은 주문 3건 중 1건이 전부 취소라 2건으로 센다
                .andExpect(jsonPath("$.content[0].userId").value(hong.getId()))
                .andExpect(jsonPath("$.content[0].orderCount").value(2))
                .andExpect(jsonPath("$.content[1].userId").value(dohyun.getId()))
                .andExpect(jsonPath("$.content[1].orderCount").value(1))
                .andExpect(jsonPath("$.content[2].userId").value(yuri.getId()))
                .andExpect(jsonPath("$.content[2].orderCount").value(0));
    }

    @Test
    @DisplayName("가입 수단 필터는 탭 건수에도 함께 반영된다 — 탭 숫자가 지금 보는 범위와 어긋나면 안 된다")
    void providerFilterAlsoNarrowsTheSummary() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("providerType", "KAKAO")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.summary.total").value(1))
                .andExpect(jsonPath("$.summary.active").value(1))
                .andExpect(jsonPath("$.summary.suspended").value(0))
                .andExpect(jsonPath("$.summary.withdrawn").value(0));
    }

    // ------------------------------------------------------------------ 픽스처

    private Users createConsumer(String username, String nickname, String name, String phoneNumber,
                                 ProviderType providerType, UserStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users(username, nickname, username + "@showroomz.test", "Y", null,
                providerType, RoleType.USER, now, now);
        user.setName(name);
        user.setPhoneNumber(phoneNumber);
        user.setStatus(status);
        return userRepository.save(user);
    }

    private void createGuest(String username) {
        LocalDateTime now = LocalDateTime.now();
        Users guest = new Users(username, username, username + "@showroomz.test", "N", null,
                ProviderType.KAKAO, RoleType.GUEST, now, now);
        userRepository.save(guest);
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

        return productVariantRepository.save(
                new ProductVariant(product, "단품", 38000, 24900, 10, true));
    }

    private void placeOrder(Users user, OrderProductStatus status) {
        Order order = orderRepository.save(Order.builder().user(user).build());
        orderProductRepository.save(OrderProduct.builder()
                .order(order)
                .variant(variant)
                .productName("시카 리페어 앰플 30ml")
                .optionName("단품")
                .quantity(1)
                .price(24900)
                .orderDate(LocalDateTime.now())
                .status(status)
                .build());
    }
}
