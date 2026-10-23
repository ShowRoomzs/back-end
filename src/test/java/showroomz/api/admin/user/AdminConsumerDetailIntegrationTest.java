package showroomz.api.admin.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.address.repository.DeliveryAddressRepository;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.support.IntegrationTestSupport;
import showroomz.support.OrderFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 소비자 상세의 거래 지표 · 기본 배송지(55 계획서 M3-1) — 하드코딩 더미를 걷어 낸 뒤 실제 주문 · 결제에서 나오는지 본다.
 *
 * <p>구매액은 순결제액이다 — 결제된 주문 총액에서 <b>그 주문 결제의</b> PG 취소 성공액만 뺀다. 실패한 취소 · 다른 결제(중복 결제 자동 취소)의
 * 취소 · 결제 대기 주문은 섞이지 않아야 한다.
 */
@DisplayName("[통합] 55 M3-1 어드민 소비자 상세 — 거래 지표 · 기본 배송지")
class AdminConsumerDetailIntegrationTest extends IntegrationTestSupport {

    private static final String PATH = "/v1/admin/users/";
    private static final int PRICE = 24900;

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderProductRepository orderProductRepository;
    @Autowired private DeliveryAddressRepository deliveryAddressRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private String adminToken;
    private ProductVariant variant;
    private OrderFixture orders;

    @BeforeEach
    void setUp() {
        Seller admin = fixture.createAdmin("admin@showroomz.test", "김운영");
        adminToken = adminToken(admin);
        Market market = fixture.createBrand("brand@showroomz.test", "제니의 뷰티룸").market();
        variant = createVariant(market);
        orders = new OrderFixture(orderRepository, orderProductRepository, transactionTemplate);
    }

    @Test
    @DisplayName("구매액 = 결제 총액 − 그 결제의 PG 취소 성공액 · 이번 달은 결제월 기준 · 누적 주문은 취소만 남은 주문 제외 · 평균 · 최근 주문일")
    void tradeFiguresComeFromOrdersAndCancels() throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime lastMonth = monthStart.minusDays(3);
        LocalDateTime thisMonth = LocalDateTime.now().withNano(0);

        Order a = orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.PURCHASE_CONFIRMED);
        paidAt(a, lastMonth);
        attachPayment(a, 1, true);

        Order b = orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.PAID);
        paidAt(b, thisMonth.minusMinutes(10));
        String bPayment = attachPayment(b, 1, true);
        cancel(bPayment, 10_000, "SUCCEEDED");          // 부분 환불 — 빠진다
        cancel(bPayment, 5_000, "FAILED");              // 실패한 취소 — 빠지지 않는다
        String bDuplicate = attachPayment(b, 2, false);
        cancel(bDuplicate, PRICE, "SUCCEEDED");         // 중복 결제 자동 취소 — 주문 결제가 아니라 빠지지 않는다

        Order c = orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.CANCELLED);
        paidAt(c, thisMonth);
        cancel(attachPayment(c, 1, true), PRICE, "SUCCEEDED");   // 전액 취소

        orders.pendingOrder(hong, variant, 1, PRICE);   // 결제 대기 — 금액 · 주문 수 모두 아님

        long net = 3L * PRICE - 10_000 - PRICE;         // 39,800
        mockMvc.perform(get(PATH + hong.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPurchaseAmount").value(net))
                .andExpect(jsonPath("$.thisMonthPurchaseAmount").value(2L * PRICE - 10_000 - PRICE))
                .andExpect(jsonPath("$.totalOrderCount").value(2))
                .andExpect(jsonPath("$.thisMonthOrderCount").value(1))
                .andExpect(jsonPath("$.averageOrderAmount").value(net / 2))
                .andExpect(jsonPath("$.lastOrderDate",
                        org.hamcrest.Matchers.startsWith(thisMonth.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME))));
    }

    @Test
    @DisplayName("주문이 없으면 0 · 최근 주문일 null · 기본 배송지는 시 · 군 · 구까지만(구가 있는 시는 구까지)")
    void emptyTradeAndMaskedDefaultAddress() throws Exception {
        Users mia = createConsumer("mia", UserStatus.NORMAL);
        address(mia, "서울특별시 마포구 월드컵로 12", false);
        address(mia, "경기도 성남시 분당구 판교역로 166", true);

        mockMvc.perform(get(PATH + mia.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPurchaseAmount").value(0))
                .andExpect(jsonPath("$.thisMonthPurchaseAmount").value(0))
                .andExpect(jsonPath("$.totalOrderCount").value(0))
                .andExpect(jsonPath("$.averageOrderAmount").value(0))
                .andExpect(jsonPath("$.lastOrderDate").doesNotExist())
                .andExpect(jsonPath("$.defaultAddress").value("경기도 성남시 분당구"));
    }

    @Test
    @DisplayName("기본 배송지가 없으면 null · 탈퇴 회원은 파기라 null")
    void defaultAddressAbsentOrDestroyed() throws Exception {
        Users noDefault = createConsumer("nodefault", UserStatus.NORMAL);
        address(noDefault, "서울특별시 마포구 월드컵로 12", false);
        Users withdrawn = createConsumer("gone", UserStatus.WITHDRAWN);
        address(withdrawn, "서울특별시 마포구 월드컵로 12", true);

        mockMvc.perform(get(PATH + noDefault.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.defaultAddress").doesNotExist());
        mockMvc.perform(get(PATH + withdrawn.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.defaultAddress").doesNotExist());
    }

    // ------------------------------------------------------------------ helpers

    private void paidAt(Order order, LocalDateTime paidAt) {
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_id = ?", paidAt, order.getId());
    }

    /** 결제 행 — {@code paid} 면 주문의 결제(paid_payment_id)로 붙인다. */
    private String attachPayment(Order order, int attempt, boolean paid) {
        Order loaded = orderRepository.findById(order.getId()).orElseThrow();
        String paymentId = loaded.getOrderNumber() + "-" + attempt;
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO payment (payment_id, order_id, attempt, status, method, amount, cancelled_amount, "
                        + "cancel_attempts, currency, paid_at, created_at, modified_at) "
                        + "VALUES (?, ?, ?, ?, 'CARD', ?, 0, 0, 'KRW', ?, ?, ?)",
                paymentId, order.getId(), attempt, paid ? "PAID" : "CANCELLED_MISMATCH", loaded.getTotalAmount(), now, now,
                now);
        if (paid) {
            jdbc.update("UPDATE orders SET paid_payment_id = ? WHERE order_id = ?", paymentId, order.getId());
        }
        return paymentId;
    }

    private void cancel(String paymentId, int amount, String status) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO payment_cancel (payment_id, amount, status, requested_by, requested_at, created_at, "
                + "modified_at) VALUES (?, ?, ?, 'ADMIN', ?, ?, ?)", paymentId, amount, status, now, now, now);
    }

    private void address(Users user, String address, boolean isDefault) {
        deliveryAddressRepository.save(DeliveryAddress.builder()
                .user(user).recipientName("김수민").zipCode("06234").address(address).detailAddress("3층")
                .phoneNumber("010-1234-5678").isDefault(isDefault).build());
    }

    private Users createConsumer(String username, UserStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users(username, username, username + "@showroomz.test", "Y", null,
                ProviderType.KAKAO, RoleType.USER, now, now);
        user.setName("홍길동");
        user.setPhoneNumber("010-1111-1234");
        user.setStatus(status);
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
        product.setSalePrice(PRICE);
        productRepository.save(product);

        return productVariantRepository.save(new ProductVariant(product, "단품", 38000, PRICE, 10, true));
    }
}
