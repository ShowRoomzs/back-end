package showroomz.api.admin.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.address.repository.DeliveryAddressRepository;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.history.entity.LoginHistory;
import showroomz.domain.history.entity.UserConsentHistory;
import showroomz.domain.history.entity.UserStatusHistory;
import showroomz.domain.history.repository.LoginHistoryRepository;
import showroomz.domain.history.repository.UserConsentHistoryRepository;
import showroomz.domain.history.repository.UserStatusHistoryRepository;
import showroomz.domain.history.type.ConsentType;
import showroomz.domain.history.type.LoginStatus;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.UserSanction;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserPrivacyAccessLogRepository;
import showroomz.domain.member.user.repository.UserSanctionRepository;
import showroomz.domain.member.user.type.UserReleaseReason;
import showroomz.domain.member.user.type.UserSuspensionReason;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.notification.entity.DeviceToken;
import showroomz.domain.notification.repository.DeviceTokenRepository;
import showroomz.domain.notification.type.DevicePlatform;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderCancelRequestItem;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.type.CancelRequestReason;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.support.IntegrationTestSupport;
import showroomz.support.OrderFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 소비자 상세의 거래 지표 · 기본 배송지(55 계획서 M3-1) — 하드코딩 더미를 걷어 낸 뒤 실제 주문 · 결제에서 나오는지 본다.
 *
 * <p>구매액은 순결제액이다 — 결제된 주문 총액에서 <b>그 주문 결제의</b> PG 취소 성공액만 뺀다. 실패한 취소 · 다른 결제(중복 결제 자동 취소)의
 * 취소 · 결제 대기 주문은 섞이지 않아야 한다.
 *
 * <p>시안 필드 보강(M3-2) — 회원번호 · 이전/다음 · 최근 로그인 · 알림 수신 · 본인확인 마스킹 · 최근 주문 · 최근 문의 · 배송지 · 진행 중 칩 ·
 * 판단 메타. 마스킹은 서버가 끝내므로 원본이 응답에 없는지까지 본다. 열람 해제(M3-3)는 이 운영자 · 이 소비자 · 10분 세션이다.
 */
@DisplayName("[통합] 55 M3 어드민 소비자 상세 — 거래 지표 · 시안 필드 보강")
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
    @Autowired private OrderDeliveryGroupRepository deliveryGroupRepository;
    @Autowired private LoginHistoryRepository loginHistoryRepository;
    @Autowired private UserConsentHistoryRepository consentHistoryRepository;
    @Autowired private DeviceTokenRepository deviceTokenRepository;
    @Autowired private OneToOneInquiryRepository oneToOneInquiryRepository;
    @Autowired private ProductInquiryRepository productInquiryRepository;
    @Autowired private UserSanctionRepository sanctionRepository;
    @Autowired private UserPrivacyAccessLogRepository privacyLogRepository;
    @Autowired private OrderCancelRequestRepository cancelRequestRepository;
    @Autowired private OrderRefundTaskRepository refundTaskRepository;
    @Autowired private UserStatusHistoryRepository statusHistoryRepository;

    private String adminToken;
    private Seller admin;
    private Market market;
    private ProductVariant variant;
    private OrderFixture orders;

    @BeforeEach
    void setUp() {
        admin = fixture.createAdmin("admin@showroomz.test", "김운영");
        adminToken = adminToken(admin);
        market = fixture.createBrand("brand@showroomz.test", "제니의 뷰티룸").market();
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

    /** 취소 시각이 아니라 결제월을 따른다 — 이번 달 화면 숫자가 지난달 주문의 취소로 깎이면 「이번 달 구매액」이 음수까지 갈 수 있다. */
    @Test
    @DisplayName("[55 M3-05] 결제월 경계 — 지난달 결제를 이번 달에 취소하면 누적에서만 빠지고 이번 달 구매액 · 주문 수는 그대로")
    void cancelOfLastMonthPaymentStaysInLastMonth() throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);
        LocalDateTime lastMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay().minusDays(3);
        LocalDateTime thisMonth = LocalDateTime.now().withNano(0);

        Order partial = orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.PURCHASE_CONFIRMED);
        paidAt(partial, lastMonth);
        cancel(attachPayment(partial, 1, true), 10_000, "SUCCEEDED");     // 취소 요청 · 성공은 이번 달(now)

        Order full = orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.CANCELLED);
        paidAt(full, lastMonth.plusHours(1));
        cancel(attachPayment(full, 1, true), PRICE, "SUCCEEDED");         // 지난달 결제 · 이번 달 전액 취소

        Order current = orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.PAID);
        paidAt(current, thisMonth);
        attachPayment(current, 1, true);

        mockMvc.perform(get(PATH + hong.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPurchaseAmount").value(2L * PRICE - 10_000))
                .andExpect(jsonPath("$.thisMonthPurchaseAmount").value(PRICE))
                .andExpect(jsonPath("$.totalOrderCount").value(2))
                .andExpect(jsonPath("$.thisMonthOrderCount").value(1));
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

    // ------------------------------------------------------------------ M3-2 시안 필드 보강

    @Test
    @DisplayName("계정 정보 — 회원번호 · 이전/다음(GUEST 건너뜀 · 양 끝 null) · 최근 로그인 · 기기 · 알림 수신(토큰 ∧ 토글)")
    void accountInfo() throws Exception {
        Users first = createConsumer("first", UserStatus.NORMAL);
        createUser("guest", RoleType.GUEST, UserStatus.NORMAL);
        Users middle = createConsumer("middle", UserStatus.NORMAL);
        Users last = createConsumer("last", UserStatus.NORMAL);
        LocalDateTime loginAt = LocalDateTime.now().minusHours(3).withNano(0);
        jdbc.update("UPDATE users SET last_login_at = ? WHERE user_id = ?", loginAt, middle.getId());
        loginHistoryRepository.save(LoginHistory.builder().user(middle).clientIp("127.0.0.1")
                .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148")
                .status(LoginStatus.SUCCESS).build());

        detail(middle)
                .andExpect(jsonPath("$.memberNo").value("CST-" + middle.getId()))
                .andExpect(jsonPath("$.navigation.previousId").value(first.getId()))
                .andExpect(jsonPath("$.navigation.nextId").value(last.getId()))
                .andExpect(jsonPath("$.lastLoginAt", org.hamcrest.Matchers.startsWith(iso(loginAt))))
                .andExpect(jsonPath("$.lastLoginDevice").value("IPHONE"))
                .andExpect(jsonPath("$.notification.destroyed").value(false))
                .andExpect(jsonPath("$.notification.deviceRegistered").value(false))
                .andExpect(jsonPath("$.notification.appPushOn").value(false))
                .andExpect(jsonPath("$.notification.toggles.length()").value(4))
                .andExpect(jsonPath("$.notification.toggles[1].toggle").value("FOLLOW_POST"))
                .andExpect(jsonPath("$.notification.toggles[1].on").value(true))
                .andExpect(jsonPath("$.notification.toggles[3].toggle").value("BENEFIT"))
                .andExpect(jsonPath("$.notification.marketingAgree").value(false));
        detail(first).andExpect(jsonPath("$.navigation.previousId").doesNotExist())
                .andExpect(jsonPath("$.lastLoginDevice").doesNotExist());
        detail(last).andExpect(jsonPath("$.navigation.nextId").doesNotExist());

        deviceTokenRepository.save(new DeviceToken(middle, "middle-device", DevicePlatform.IOS, LocalDateTime.now()));
        detail(middle).andExpect(jsonPath("$.notification.deviceRegistered").value(true))
                .andExpect(jsonPath("$.notification.appPushOn").value(true));
        // 토큰이 있어도 토글이 모두 꺼져 있으면 「끔」
        jdbc.update("UPDATE users SET follow_post_push_agree = false WHERE user_id = ?", middle.getId());
        detail(middle).andExpect(jsonPath("$.notification.appPushOn").value(false));
    }

    @Test
    @DisplayName("본인확인 — 서버 마스킹(원본 미포함) · 성별은 그대로 · 최상위 birthday 도 마스킹 · 재인증하면 최초 인증 대신 최근 갱신")
    void identityIsMaskedByServer() throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);
        LocalDateTime verifiedAt = LocalDateTime.now().minusDays(30).withNano(0);
        jdbc.update("UPDATE users SET birthday = ?, gender = ?, identity_verified_at = ? WHERE user_id = ?",
                "1990-03-17", "FEMALE", verifiedAt, hong.getId());

        String body = detail(hong)
                .andExpect(jsonPath("$.identity.destroyed").value(false))
                .andExpect(jsonPath("$.identity.unmasked").value(false))
                .andExpect(jsonPath("$.identity.name").value("홍*동"))
                .andExpect(jsonPath("$.identity.birthday").value("1990.**.**"))
                .andExpect(jsonPath("$.identity.age").doesNotExist())
                .andExpect(jsonPath("$.identity.phone").value("010-****-1234"))
                .andExpect(jsonPath("$.identity.gender").value("FEMALE"))
                .andExpect(jsonPath("$.identity.genderLabel").value("여성"))
                .andExpect(jsonPath("$.identity.nationality").doesNotExist())
                .andExpect(jsonPath("$.identity.carrier").doesNotExist())
                .andExpect(jsonPath("$.identity.firstVerifiedAt", org.hamcrest.Matchers.startsWith(iso(verifiedAt))))
                .andExpect(jsonPath("$.identity.renewedAt").doesNotExist())
                .andExpect(jsonPath("$.birthday").value("1990.**.**"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("홍길동").doesNotContain("1990-03-17").doesNotContain("1111");

        consentHistoryRepository.save(UserConsentHistory.builder().user(hong)
                .consentType(ConsentType.IDENTITY_VERIFICATION).agreed(true).build());
        detail(hong).andExpect(jsonPath("$.identity.firstVerifiedAt").doesNotExist())
                .andExpect(jsonPath("$.identity.renewedAt", org.hamcrest.Matchers.startsWith(iso(verifiedAt))));
    }

    @Test
    @DisplayName("탈퇴 회원 — 본인확인 · 배송지 · 알림 수신 파기 · 최근 로그인 null · 판단 메타 거래 값 null(가입 후 경과 · 정지 이력은 남음)")
    void withdrawnShowsDestroyed() throws Exception {
        Users gone = createConsumer("gone", UserStatus.WITHDRAWN);
        jdbc.update("UPDATE users SET last_login_at = ? WHERE user_id = ?", LocalDateTime.now(), gone.getId());
        address(gone, "서울특별시 마포구 월드컵로 12", true);
        orders.paidOrder(gone, variant, 1, PRICE, OrderProductStatus.PURCHASE_CONFIRMED);

        detail(gone)
                .andExpect(jsonPath("$.identity.destroyed").value(true))
                .andExpect(jsonPath("$.identity.name").doesNotExist())
                .andExpect(jsonPath("$.addresses.destroyed").value(true))
                .andExpect(jsonPath("$.addresses.content.length()").value(0))
                .andExpect(jsonPath("$.notification.destroyed").value(true))
                .andExpect(jsonPath("$.notification.appPushOn").doesNotExist())
                .andExpect(jsonPath("$.lastLoginAt").doesNotExist())
                .andExpect(jsonPath("$.judgement.orderCount").doesNotExist())
                .andExpect(jsonPath("$.judgement.purchaseAmount").doesNotExist())
                .andExpect(jsonPath("$.judgement.claimRate").doesNotExist())
                .andExpect(jsonPath("$.judgement.sinceJoined.totalDays").value(0))
                .andExpect(jsonPath("$.judgement.sanctions.count").value(0));
    }

    /** 시안 탈퇴 계정 상태 카드 「탈퇴 일시」(56 C3) — 처리 이력 「회원 탈퇴」 파생 행과 같은 원천이라 두 시각이 같아야 한다. */
    @Test
    @DisplayName("[56 C3] 탈퇴 계정 statusCard.withdrawal — 탈퇴 일시 = 가장 최근 탈퇴 전이(재가입 뒤 다시 탈퇴하면 나중 것) = 처리 이력 「회원 탈퇴」 시각 · 활성 · 정지는 null · 전이 기록 없는 옛 탈퇴는 withdrawnAt null")
    void withdrawnStatusCardCarriesWithdrawnAt() throws Exception {
        Users gone = createConsumer("gone", UserStatus.WITHDRAWN);
        LocalDateTime first = LocalDateTime.now().minusDays(90).withNano(0);
        LocalDateTime rejoined = LocalDateTime.now().minusDays(60).withNano(0);
        LocalDateTime last = LocalDateTime.now().minusDays(5).withNano(0);
        statusTransition(gone, UserStatus.NORMAL, UserStatus.WITHDRAWN, first);
        statusTransition(gone, UserStatus.WITHDRAWN, UserStatus.NORMAL, rejoined);
        statusTransition(gone, UserStatus.NORMAL, UserStatus.WITHDRAWN, last);

        JsonNode body = json(detail(gone));
        assertThat(body.at("/statusCard/status").asText()).isEqualTo("WITHDRAWN");
        assertThat(body.at("/statusCard/withdrawal/withdrawnAt").asText()).startsWith(iso(last));
        JsonNode withdrawnRow = null;
        for (JsonNode row : body.at("/histories/content")) {
            if ("WITHDRAWN".equals(row.path("eventType").asText())) {
                withdrawnRow = row;
                break;
            }
        }
        assertThat(withdrawnRow).isNotNull();
        assertThat(withdrawnRow.path("occurredAt").asText()).isEqualTo(body.at("/statusCard/withdrawal/withdrawnAt").asText());
        // 상태 변경 응답(정지 · 해제)과 같은 카드 — 탈퇴 회원이 아니면 비어 있다
        detail(createConsumer("hong", UserStatus.NORMAL)).andExpect(jsonPath("$.statusCard.withdrawal").doesNotExist());
        detail(createConsumer("paused", UserStatus.SUSPENDED)).andExpect(jsonPath("$.statusCard.withdrawal").doesNotExist());
        detail(createConsumer("legacy", UserStatus.WITHDRAWN))
                .andExpect(jsonPath("$.statusCard.withdrawal").exists())
                .andExpect(jsonPath("$.statusCard.withdrawal.withdrawnAt").doesNotExist());
    }

    @Test
    @DisplayName("최근 주문 5건 — 어드민 주문 목록(userId)과 같은 행 · 결제일 최신순 · total 은 그 목록 건수 · 다른 소비자 주문 제외")
    void recentOrdersReuseAdminOrderList() throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);
        Users other = createConsumer("other", UserStatus.NORMAL);
        List<Long> ids = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusDays(10).withNano(0);
        for (int i = 0; i < 6; i++) {
            Order order = orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.PAID);
            paidAt(order, base.plusDays(i));
            group(order, "PREPARING");
            ids.add(order.getId());
        }
        group(orders.paidOrder(other, variant, 1, PRICE, OrderProductStatus.PAID), "PREPARING");

        detail(hong)
                .andExpect(jsonPath("$.recentOrders.total").value(6))
                .andExpect(jsonPath("$.recentOrders.content.length()").value(5))
                .andExpect(jsonPath("$.recentOrders.content[0].orderId").value(ids.get(5)))
                .andExpect(jsonPath("$.recentOrders.content[4].orderId").value(ids.get(1)))
                .andExpect(jsonPath("$.recentOrders.content[0].totalAmount").value(PRICE))
                .andExpect(jsonPath("$.recentOrders.content[0].groups[0].brandName").value("제니의 뷰티룸"))
                .andExpect(jsonPath("$.recentOrders.content[0].groups[0].status").value("PREPARING"))
                .andExpect(jsonPath("$.recentOrders.content[0].groups[0].itemSummary").value("시카 리페어 앰플 30ml 단품"));
        // 「전체 주문 내역 보기」 — 같은 목록 API 가 userId 로 거른다
        mockMvc.perform(get("/v1/admin/orders").param("userId", String.valueOf(hong.getId()))
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(6))
                .andExpect(jsonPath("$.content[0].orderId").value(ids.get(5)));
        mockMvc.perform(get("/v1/admin/orders/summary").param("userId", String.valueOf(other.getId()))
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabCounts.ALL").value(1));
    }

    /**
     * 시안 「최근 주문」 상태 칸의 「반품완료」 · 「교환완료」(56 C2) — 행의 status(배송 이행)는 그대로이고 groups[].claim 이 따로 싣는다. 한
     * 하위주문에 여러 클레임이 있으면 가장 최근에 신청한 것, 결제 대기 · 요청 취소는 세지 않는다. 주문 목록에도 같은 값이 나간다.
     */
    @Test
    @DisplayName("[56 C2] 최근 주문 groups[].claim — 반품완료 · 교환완료 · 여러 건이면 가장 최근 신청(건수 함께) · 요청 취소 · 결제 대기만 있으면 null · status 는 이행 상태 그대로")
    void recentOrdersCarryClaimSummary() throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);
        LocalDateTime base = LocalDateTime.now().minusDays(10).withNano(0);
        Order plain = paidDelivered(hong, base);
        Order returned = paidDelivered(hong, base.plusDays(1));
        claim(returned, onlyGroup(returned), hong, "RETURN", "COMPLETED", "REFUNDED");
        Order exchanged = paidDelivered(hong, base.plusDays(2));
        claim(exchanged, onlyGroup(exchanged), hong, "EXCHANGE", "COMPLETED", "EXCHANGED");
        Order twice = paidDelivered(hong, base.plusDays(3));
        Long rejectedBox = claim(twice, onlyGroup(twice), hong, "RETURN", "COMPLETED", "REJECTED");
        jdbc.update("UPDATE order_claim SET requested_at = ? WHERE collection_id = ?", base, rejectedBox);
        claim(twice, onlyGroup(twice), hong, "RETURN", "REQUESTED", null);
        Order withdrawn = paidDelivered(hong, base.plusDays(4));
        claim(withdrawn, onlyGroup(withdrawn), hong, "RETURN", "COMPLETED", "CANCELLED");
        claim(withdrawn, onlyGroup(withdrawn), hong, "EXCHANGE", "PAYMENT_PENDING", null);

        JsonNode rows = json(detail(hong)).path("recentOrders").path("content");
        assertThat(rows).hasSize(5);
        java.util.Map<Long, JsonNode> byOrder = new java.util.HashMap<>();
        rows.forEach(row -> byOrder.put(row.path("orderId").asLong(), row.path("groups").get(0)));
        for (JsonNode group : byOrder.values()) {
            assertThat(group.path("status").asText()).isEqualTo("DELIVERED");
        }
        assertThat(byOrder.get(plain.getId()).path("claim").isNull()).isTrue();
        assertThat(byOrder.get(withdrawn.getId()).path("claim").isNull()).isTrue();
        JsonNode refunded = byOrder.get(returned.getId()).path("claim");
        assertThat(refunded.path("label").asText()).isEqualTo("반품완료");
        assertThat(refunded.path("type").asText()).isEqualTo("RETURN");
        assertThat(refunded.path("result").asText()).isEqualTo("REFUNDED");
        assertThat(refunded.path("count").asInt()).isEqualTo(1);
        assertThat(byOrder.get(exchanged.getId()).path("claim").path("label").asText()).isEqualTo("교환완료");
        JsonNode latest = byOrder.get(twice.getId()).path("claim");
        assertThat(latest.path("status").asText()).isEqualTo("REQUESTED");
        assertThat(latest.path("label").asText()).isEqualTo("반품 진행중");
        assertThat(latest.path("count").asInt()).isEqualTo(2);
        assertThat(latest.path("claimNumber").asText()).isEqualTo("CLM-" + latest.path("claimId").asLong());

        // 주문 목록도 같은 행이다
        JsonNode listed = json(mockMvc.perform(get("/v1/admin/orders").param("userId", String.valueOf(hong.getId()))
                .header(HttpHeaders.AUTHORIZATION, adminToken)).andExpect(status().isOk())).path("content");
        assertThat(listed.findValuesAsText("label")).containsExactlyInAnyOrder("반품완료", "교환완료", "반품 진행중");
    }

    @Test
    @DisplayName("최근 문의 3건 — 1:1 · 상품 문의 최신순 합산 · 삭제 집행 제외 · 번호(INQ- · QNA-) · 제목 = 본문 앞 40자 · 상태 라벨")
    void recentInquiriesMergeBothKinds() throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);
        Product product = variant.getProduct();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        OneToOneInquiry oldOne = oneToOne(hong, CsCategory.DELIVERY, "세럼 배송이 언제 오나요?", now.minusDays(4));
        OneToOneInquiry newOne = oneToOne(hong, CsCategory.CANCEL_EXCHANGE_RETURN, "크림 반품 회수가 안 와요", now.minusDays(1));
        ProductInquiry oldProduct = productInquiry(hong, product, "토너를 화장솜 없이 써도 되나요?", now.minusDays(3));
        String longContent = "앰플을 아침 저녁으로 바르는데 피부가 따끔거려요.\n민감성 피부에도 괜찮은 성분인지 알고 싶어요. 사용 순서도 알려 주세요.";
        ProductInquiry newProduct = productInquiry(hong, product, longContent, now.minusHours(2));
        ProductInquiry deleted = productInquiry(hong, product, "삭제된 문의", now.minusHours(1));
        jdbc.update("UPDATE product_inquiry SET exposure_status = 'DELETED' WHERE product_inquiry_id = ?", deleted.getId());
        jdbc.update("UPDATE one_to_one_inquiry SET status = 'ANSWERED' WHERE inquiry_id = ?", newOne.getId());

        JsonNode inquiries = json(detail(hong)).path("recentInquiries");
        assertThat(inquiries.path("total").asLong()).isEqualTo(4);
        JsonNode content = inquiries.path("content");
        assertThat(content).hasSize(3);
        assertThat(content.get(0).path("kind").asText()).isEqualTo("PRODUCT");
        assertThat(content.get(0).path("inquiryId").asLong()).isEqualTo(newProduct.getId());
        assertThat(content.get(0).path("kindLabel").asText()).isEqualTo("상품");
        assertThat(content.get(0).path("inquiryNumber").asText()).startsWith("QNA-");
        assertThat(content.get(0).path("typeLabel").asText()).isEqualTo(ProductInquiryType.INGREDIENT_USAGE.getDescription());
        assertThat(content.get(0).path("title").asText()).hasSize(41).endsWith("…").doesNotContain("\n");
        assertThat(content.get(0).path("statusLabel").asText()).isEqualTo("답변 대기");
        assertThat(content.get(1).path("kind").asText()).isEqualTo("ONE_TO_ONE");
        assertThat(content.get(1).path("inquiryId").asLong()).isEqualTo(newOne.getId());
        assertThat(content.get(1).path("inquiryNumber").asText()).startsWith("INQ-");
        assertThat(content.get(1).path("typeLabel").asText()).isEqualTo(CsCategory.CANCEL_EXCHANGE_RETURN.getDescription());
        assertThat(content.get(1).path("statusLabel").asText()).isEqualTo("답변 완료");
        assertThat(content.get(2).path("inquiryId").asLong()).isEqualTo(oldProduct.getId());
        // 두 문의는 id 공간이 따로라 (구분, id) 쌍으로 본다 — 4번째 1:1 · 삭제 집행된 상품 문의는 빠진다
        List<String> keys = new ArrayList<>();
        content.forEach(row -> keys.add(row.path("kind").asText() + ":" + row.path("inquiryId").asLong()));
        assertThat(keys).doesNotContain("ONE_TO_ONE:" + oldOne.getId(), "PRODUCT:" + deleted.getId());
    }

    @Test
    @DisplayName("배송지 — 기본 먼저 · 시 · 군 · 구까지만(구가 있는 시는 구까지) · 상세 · 우편번호 null · 수령인 · 연락처 마스킹 · 최근 사용일 null")
    void addressesAreMaskedToDistrict() throws Exception {
        Users mia = createConsumer("mia", UserStatus.NORMAL);
        address(mia, "서울특별시 강남구 테헤란로 123", false);
        address(mia, "경기도 고양시 일산동구 중앙로 1", false);
        address(mia, "서울특별시 마포구 월드컵로 12", true);

        String body = detail(mia)
                .andExpect(jsonPath("$.addresses.destroyed").value(false))
                .andExpect(jsonPath("$.addresses.total").value(3))
                .andExpect(jsonPath("$.addresses.content[0].defaultAddress").value(true))
                .andExpect(jsonPath("$.addresses.content[0].address").value("서울특별시 마포구"))
                .andExpect(jsonPath("$.addresses.content[0].detailAddress").doesNotExist())
                .andExpect(jsonPath("$.addresses.content[0].zipCode").doesNotExist())
                .andExpect(jsonPath("$.addresses.content[0].recipientName").value("김*민"))
                .andExpect(jsonPath("$.addresses.content[0].phoneNumber").value("010-****-5678"))
                .andExpect(jsonPath("$.addresses.content[0].lastUsedOrderAt").doesNotExist())
                .andExpect(jsonPath("$.addresses.content[*].address", org.hamcrest.Matchers.containsInAnyOrder(
                        "서울특별시 마포구", "서울특별시 강남구", "경기도 고양시 일산동구")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("월드컵로").doesNotContain("테헤란로").doesNotContain("06234").doesNotContain("3층");
    }

    @Test
    @DisplayName("진행 중 칩 — 1건 이상만 · 열린 문의(WARNING) · 배송중(주문 수) · 반품·교환(클레임 수 · 56 C1) · 없으면 빈 배열")
    void pendingChips() throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);
        detail(hong).andExpect(jsonPath("$.pendingChips.length()").value(0));

        oneToOne(hong, CsCategory.DELIVERY, "배송 문의", LocalDateTime.now().minusHours(1));
        Order shipping = orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.PAID);
        group(shipping, "SHIPPING");
        Order returned = orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.PAID);
        OrderDeliveryGroup returnedGroup = group(returned, "DELIVERED");
        claim(returned, returnedGroup, hong, "RETURN", "REQUESTED", null);

        detail(hong)
                .andExpect(jsonPath("$.pendingChips.length()").value(3))
                .andExpect(jsonPath("$.pendingChips[0].key").value("OPEN_INQUIRY"))
                .andExpect(jsonPath("$.pendingChips[0].tone").value("WARNING"))
                .andExpect(jsonPath("$.pendingChips[0].count").value(1))
                .andExpect(jsonPath("$.pendingChips[0].target").value("INQUIRIES"))
                .andExpect(jsonPath("$.pendingChips[1].key").value("SHIPPING"))
                .andExpect(jsonPath("$.pendingChips[1].tone").value("NEUTRAL"))
                .andExpect(jsonPath("$.pendingChips[1].count").value(1))
                .andExpect(jsonPath("$.pendingChips[2].key").value("RETURN_EXCHANGE"))
                .andExpect(jsonPath("$.pendingChips[2].target").value("CLAIMS"));
    }

    /**
     * 칩 건수 = 칩이 가리키는 목록을 그 탭 · 상태 + userId 로 부른 건수(56 C1). links 의 query 를 그대로 붙여 실제 목록 API 를 불러 맞춘다 —
     * 목록마다 경계가 다르다: 상품 문의 「답변대기」는 삭제 요청 건을 뺀다 · 주문 「취소」 탭은 취소된 하위주문까지 담는다 · 클레임 목록은 행 단위이고
     * 기본 1개월이 있다 · 환불 「실패」 탭은 완료를 뺀다. 다른 소비자의 같은 데이터는 어디에도 섞이지 않아야 한다.
     */
    @Test
    @DisplayName("[56 C1] 진행 중 칩 건수 = links 목록을 그 query(탭 · 상태 · userId)로 부른 건수 — 삭제 요청 상품 문의 · 취소된 하위주문 · 종결 · 결제 대기 클레임 · 완료 환불 · 다른 소비자 제외 · 한 박스 2건은 2 · 40일 전 진행 클레임 포함")
    void pendingChipsMatchTargetLists() throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);
        Users other = createConsumer("other", UserStatus.NORMAL);
        for (Users user : List.of(hong, other)) {
            seedPendingWork(user);
        }

        JsonNode chips = json(detail(hong)).path("pendingChips");
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (JsonNode chip : chips) {
            long sum = 0;
            for (JsonNode link : chip.path("links")) {
                long listed = listTotal(link.path("target").asText(), link.path("query"));
                assertThat(listed).as("%s → %s %s", chip.path("key").asText(), link.path("target").asText(),
                        link.path("query")).isEqualTo(link.path("count").asLong());
                assertThat(link.path("query").path("userId").asText()).isEqualTo(String.valueOf(hong.getId()));
                sum += listed;
            }
            assertThat(chip.path("count").asLong()).as(chip.path("key").asText()).isEqualTo(sum);
            counts.put(chip.path("key").asText(), chip.path("count").asLong());
        }
        assertThat(counts).containsExactly(
                java.util.Map.entry("OPEN_INQUIRY", 2L), java.util.Map.entry("SHIPPING", 1L),
                java.util.Map.entry("CANCEL_REQUESTED", 1L), java.util.Map.entry("RETURN_EXCHANGE", 3L),
                java.util.Map.entry("REFUND_FAILED", 1L));

        JsonNode openInquiry = chips.get(0).path("links");
        assertThat(openInquiry).hasSize(2);
        assertThat(openInquiry.get(0).path("target").asText()).isEqualTo("INQUIRIES");
        assertThat(openInquiry.get(0).path("query").path("status").asText()).isEqualTo("WAITING");
        assertThat(openInquiry.get(1).path("target").asText()).isEqualTo("PRODUCT_INQUIRIES");
        JsonNode cancel = chips.get(2).path("links").get(0).path("query");
        assertThat(cancel.path("tab").asText()).isEqualTo("CANCEL");
        assertThat(cancel.path("cancelRequested").asText()).isEqualTo("true");
        JsonNode claims = chips.get(3).path("links").get(0).path("query");
        assertThat(claims.path("tab").asText()).isEqualTo("ALL");
        assertThat(claims.path("openOnly").asText()).isEqualTo("true");
        assertThat(chips.get(4).path("links").get(0).path("query").path("tab").asText()).isEqualTo("FAILED");

        // 취소 탭만으로는 취소된 하위주문까지 나온다 — 칩이 cancelRequested 를 싣는 이유
        assertThat(listTotal("ORDERS", objectMapper.valueToTree(java.util.Map.of("tab", "CANCEL",
                "userId", String.valueOf(hong.getId()))))).isEqualTo(2);
        // 0건인 목록은 links 에서 빠진다 — 상품 문의만 남으면 링크 1개
        jdbc.update("UPDATE one_to_one_inquiry SET status = 'ANSWERED', answered_at = ? WHERE user_id = ?",
                LocalDateTime.now(), hong.getId());
        JsonNode onlyProduct = json(detail(hong)).path("pendingChips").get(0);
        assertThat(onlyProduct.path("key").asText()).isEqualTo("OPEN_INQUIRY");
        assertThat(onlyProduct.path("count").asLong()).isEqualTo(1);
        assertThat(onlyProduct.path("links")).hasSize(1);
        assertThat(onlyProduct.path("links").get(0).path("target").asText()).isEqualTo("PRODUCT_INQUIRIES");
    }

    @Test
    @DisplayName("[56 C1] 1:1 문의 목록 userId — 그 소비자 문의만 · 탭 건수도 그 소비자 기준 · 상세 이전 · 다음도 그 소비자 안에서")
    void oneToOneInquiryListByConsumer() throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);
        Users other = createConsumer("other", UserStatus.NORMAL);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        OneToOneInquiry older = oneToOne(hong, CsCategory.DELIVERY, "세럼 배송이 언제 오나요?", now.minusDays(3));
        OneToOneInquiry between = oneToOne(other, CsCategory.DELIVERY, "다른 소비자 문의", now.minusDays(2));
        OneToOneInquiry newer = oneToOne(hong, CsCategory.CANCEL_EXCHANGE_RETURN, "크림 반품 회수가 안 와요", now.minusDays(1));
        // 답변 완료는 답변 시각이 함께 있다(목록 「응답 소요」가 그 시각으로 계산된다)
        jdbc.update("UPDATE one_to_one_inquiry SET status = 'ANSWERED', answered_at = ? WHERE inquiry_id = ?",
                now.minusHours(20), newer.getId());

        mockMvc.perform(get("/v1/admin/inquiries").param("userId", String.valueOf(hong.getId()))
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                .andExpect(jsonPath("$.content[0].inquiryId").value(newer.getId()))
                .andExpect(jsonPath("$.content[1].inquiryId").value(older.getId()))
                .andExpect(jsonPath("$.statusCounts.all").value(2))
                .andExpect(jsonPath("$.statusCounts.waiting").value(1))
                .andExpect(jsonPath("$.statusCounts.answered").value(1));
        mockMvc.perform(get("/v1/admin/inquiries").param("status", "WAITING").param("userId", String.valueOf(hong.getId()))
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(1))
                .andExpect(jsonPath("$.content[0].inquiryId").value(older.getId()));
        mockMvc.perform(get("/v1/admin/inquiries").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(3));

        mockMvc.perform(get("/v1/admin/inquiries/" + newer.getId()).param("userId", String.valueOf(hong.getId()))
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prevInquiryId").doesNotExist())
                .andExpect(jsonPath("$.nextInquiryId").value(older.getId()));
        mockMvc.perform(get("/v1/admin/inquiries/" + newer.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.nextInquiryId").value(between.getId()));
    }

    @Test
    @DisplayName("판단 메타 — 반품 · 교환률 = 접수된 주문 ÷ 누적 주문(요청 취소 제외 · 반려 포함) · 정지 이력 횟수 · 직전 정지 · 가입 후 경과")
    void judgementMeta() throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);
        jdbc.update("UPDATE users SET created_at = ? WHERE user_id = ?",
                LocalDate.now().minusMonths(6).minusDays(13).atTime(10, 20), hong.getId());
        Order a = orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.RETURNED);
        claim(a, group(a, "DELIVERED"), hong, "RETURN", "COMPLETED", "REFUNDED");
        Order b = orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.PURCHASE_CONFIRMED);
        claim(b, group(b, "CONFIRMED"), hong, "EXCHANGE", "COMPLETED", "CANCELLED");   // 소비자가 거둬들인 요청 — 세지 않는다
        Order c = orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.PURCHASE_CONFIRMED);
        claim(c, group(c, "CONFIRMED"), hong, "RETURN", "COMPLETED", "REJECTED");      // 반려 종결 — 센다
        orders.paidOrder(hong, variant, 1, PRICE, OrderProductStatus.PURCHASE_CONFIRMED);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        UserSanction first = UserSanction.suspend(hong.getId(), UserSuspensionReason.RETURN_ABUSE, null, admin.getId(),
                "김운영", now.minusMonths(3));
        first.release(UserReleaseReason.RESOLVED, admin.getId(), "김운영", now.minusMonths(2));
        sanctionRepository.save(first);
        sanctionRepository.save(UserSanction.suspend(hong.getId(), UserSuspensionReason.ABUSIVE_LANGUAGE, null, admin.getId(),
                "김운영", now.minusDays(1)));

        detail(hong)
                .andExpect(jsonPath("$.judgement.orderCount").value(4))
                .andExpect(jsonPath("$.judgement.claimOrderCount").value(2))
                .andExpect(jsonPath("$.judgement.claimRate").value(50.0))
                .andExpect(jsonPath("$.judgement.purchaseAmount").value(4L * PRICE))
                .andExpect(jsonPath("$.judgement.sanctions.count").value(2))
                .andExpect(jsonPath("$.judgement.sanctions.last.sanctionId").value(first.getId()))
                .andExpect(jsonPath("$.judgement.sanctions.last.reason").value("RETURN_ABUSE"))
                .andExpect(jsonPath("$.judgement.sanctions.last.reasonLabel").value("반품 남용"))
                .andExpect(jsonPath("$.judgement.sinceJoined.months").value(6))
                .andExpect(jsonPath("$.judgement.sinceJoined.days").value(13));

        Users fresh = createConsumer("fresh", UserStatus.NORMAL);
        detail(fresh).andExpect(jsonPath("$.judgement.orderCount").value(0))
                .andExpect(jsonPath("$.judgement.claimRate").doesNotExist())
                .andExpect(jsonPath("$.judgement.sanctions.count").value(0))
                .andExpect(jsonPath("$.judgement.sanctions.last").doesNotExist());
    }

    // ------------------------------------------------------------------ M3-3 개인정보 열람 해제

    @Test
    @DisplayName("해제 — 실값(이름 · 생년월일 · 나이 · 휴대폰 · 배송지 상세) · 로그 1건(재해제 멱등) · 처리 이력 · 다른 운영자 세션 미적용 · 잠금 멱등")
    void unmaskAndLock() throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);
        jdbc.update("UPDATE users SET birthday = ?, phone_number = ? WHERE user_id = ?", "1990-03-17", "01028411234", hong.getId());
        address(hong, "서울특별시 마포구 월드컵로 12", true);
        String otherToken = adminToken(fixture.createAdmin("admin2@showroomz.test", "박운영"));

        detail(hong).andExpect(jsonPath("$.unmask.allowed").value(true))
                .andExpect(jsonPath("$.unmask.unmasked").value(false))
                .andExpect(jsonPath("$.unmask.lastReason").doesNotExist());

        unmask(hong, adminToken, "CS_RESPONSE").andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.unmasked").value(true))
                .andExpect(jsonPath("$.identity.name").value("홍길동"))
                .andExpect(jsonPath("$.identity.birthday").value("1990.03.17"))
                .andExpect(jsonPath("$.identity.age").isNumber())
                .andExpect(jsonPath("$.identity.phone").value("010-2841-1234"))
                .andExpect(jsonPath("$.birthday").value("1990.03.17"))
                .andExpect(jsonPath("$.addresses.content[0].address").value("서울특별시 마포구 월드컵로 12"))
                .andExpect(jsonPath("$.addresses.content[0].detailAddress").value("3층"))
                .andExpect(jsonPath("$.addresses.content[0].zipCode").value("06234"))
                .andExpect(jsonPath("$.addresses.content[0].recipientName").value("김수민"))
                .andExpect(jsonPath("$.unmask.unmasked").value(true))
                .andExpect(jsonPath("$.unmask.operatorName").value("김운영"))
                .andExpect(jsonPath("$.unmask.unmaskedAt").isNotEmpty())
                .andExpect(jsonPath("$.unmask.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.unmask.remainingSeconds", org.hamcrest.Matchers.lessThanOrEqualTo(600)))
                .andExpect(jsonPath("$.unmask.lastReason").value("CS_RESPONSE"))
                .andExpect(jsonPath("$.histories.content[0].eventType").value("PRIVACY_UNMASKED"))
                .andExpect(jsonPath("$.histories.content[0].tone").value("WARNING"))
                .andExpect(jsonPath("$.histories.content[0].detail").value("CS 응대"))
                .andExpect(jsonPath("$.histories.content[0].refType").value("PRIVACY_ACCESS"))
                .andExpect(jsonPath("$.histories.content[0].actorName").value("김운영"));
        // 유효 세션이 있으면 새 행을 만들지 않는다 — 사유도 바뀌지 않는다
        unmask(hong, adminToken, "DELIVERY_ACCIDENT").andExpect(status().isOk());
        assertThat(privacyLogRepository.findByUserId(hong.getId())).hasSize(1);
        assertThat(privacyLogRepository.findByUserId(hong.getId()).get(0).getReason().name()).isEqualTo("CS_RESPONSE");
        assertThat(privacyLogRepository.findByUserId(hong.getId()).get(0).getFields()).isEqualTo("NAME,BIRTHDAY,PHONE,ADDRESS");

        // 다른 운영자 · 다른 소비자에는 실값이 새지 않는다
        mockMvc.perform(get(PATH + hong.getId()).header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(jsonPath("$.identity.unmasked").value(false))
                .andExpect(jsonPath("$.identity.name").value("홍*동"))
                .andExpect(jsonPath("$.addresses.content[0].detailAddress").doesNotExist());
        Users other = createConsumer("other", UserStatus.NORMAL);
        detail(other).andExpect(jsonPath("$.identity.unmasked").value(false));

        // 다른 운영자가 따로 열면 로그가 따로 쌓이고, 잠금은 자기 세션만 닫는다
        unmask(hong, otherToken, "PAYMENT_RETURN_DISPUTE").andExpect(status().isOk());
        assertThat(privacyLogRepository.findByUserId(hong.getId())).hasSize(2);
        lock(hong, adminToken).andExpect(status().isNoContent());
        detail(hong).andExpect(jsonPath("$.identity.unmasked").value(false))
                .andExpect(jsonPath("$.identity.name").value("홍*동"))
                .andExpect(jsonPath("$.unmask.lastReason").value("PAYMENT_RETURN_DISPUTE"));
        mockMvc.perform(get(PATH + hong.getId()).header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(jsonPath("$.identity.unmasked").value(true))
                .andExpect(jsonPath("$.unmask.operatorName").value("박운영"));
        lock(hong, adminToken).andExpect(status().isNoContent());   // 세션이 없어도 성공(멱등)
    }

    @Test
    @DisplayName("10분 고정 만료 — 지나면 다시 마스킹 · 직전 사유 프리필 · 재해제하면 새 행")
    void sessionExpires() throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);
        unmask(hong, adminToken, "INVESTIGATION_COOPERATION").andExpect(status().isOk());
        jdbc.update("UPDATE user_privacy_access_log SET expires_at = ? WHERE user_id = ?",
                LocalDateTime.now().minusSeconds(1), hong.getId());

        detail(hong).andExpect(jsonPath("$.identity.unmasked").value(false))
                .andExpect(jsonPath("$.identity.name").value("홍*동"))
                .andExpect(jsonPath("$.unmask.unmasked").value(false))
                .andExpect(jsonPath("$.unmask.remainingSeconds").doesNotExist())
                .andExpect(jsonPath("$.unmask.lastReason").value("INVESTIGATION_COOPERATION"));
        unmask(hong, adminToken, "INVESTIGATION_COOPERATION").andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.unmasked").value(true));
        assertThat(privacyLogRepository.findByUserId(hong.getId())).hasSize(2);
    }

    @Test
    @DisplayName("탈퇴(파기) 회원 409 · 버튼 비활성 · 기록 없음 · 사유 누락 400 · 없는 회원 404")
    void unmaskRejected() throws Exception {
        Users gone = createConsumer("gone", UserStatus.WITHDRAWN);
        detail(gone).andExpect(jsonPath("$.unmask.allowed").value(false));
        unmask(gone, adminToken, "CS_RESPONSE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_PRIVACY_UNMASK_NOT_AVAILABLE"));
        assertThat(privacyLogRepository.findByUserId(gone.getId())).isEmpty();

        Users hong = createConsumer("hong", UserStatus.NORMAL);
        mockMvc.perform(post(PATH + hong.getId() + "/privacy-unmask").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, adminToken).content("{}"))
                .andExpect(status().isBadRequest());
        unmask(hong, adminToken, "OTHER").andExpect(status().isBadRequest());
        assertThat(privacyLogRepository.findByUserId(hong.getId())).isEmpty();
        mockMvc.perform(post(PATH + "999999/privacy-unmask").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, adminToken).content(toJson(java.util.Map.of("reason", "CS_RESPONSE"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "999999/privacy-lock").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[55 M3-24] 마스킹 해제 권한이 없는 운영자 — 403 USER_PRIVACY_UNMASK_FORBIDDEN · 버튼 비활성 · 로그 · 처리 이력 없음 · 실값 미노출")
    void unmaskForbiddenWithoutPrivilege(
            @Autowired showroomz.api.admin.user.service.AdminUserPrivacyService privacyService,
            @Autowired showroomz.api.admin.common.AdminPrivacySessions privacySessions) throws Exception {
        Users hong = createConsumer("hong", UserStatus.NORMAL);

        // 등급 모델이 아직 없어 AdminPrivilege 는 운영자면 통과시킨다(최고관리자 한정 예정 · §55 C-8). 판정을 쓰는 두 곳에 「권한 없음」을
        // 잠시 꽂는다 — 스파이 빈은 컨텍스트를 갈라 놓으므로 쓰지 않는다. finally 에서 원래 판정으로 되돌린다.
        showroomz.api.admin.common.AdminPrivilege denying = new showroomz.api.admin.common.AdminPrivilege() {
            @Override
            public boolean canUnmaskPrivacy(Long operatorId) {
                return false;
            }
        };
        Object service = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(privacyService);
        Object sessions = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(privacySessions);
        Object original = org.springframework.test.util.ReflectionTestUtils.getField(service, "privilege");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "privilege", denying);
        org.springframework.test.util.ReflectionTestUtils.setField(sessions, "privilege", denying);
        try {
            detail(hong).andExpect(jsonPath("$.unmask.allowed").value(false));
            unmask(hong, adminToken, "CS_RESPONSE").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_PRIVACY_UNMASK_FORBIDDEN"));
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(service, "privilege", original);
            org.springframework.test.util.ReflectionTestUtils.setField(sessions, "privilege", original);
        }

        assertThat(privacyLogRepository.findByUserId(hong.getId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_admin_history WHERE user_id = ? AND event_type = 'PRIVACY_UNMASKED'",
                Long.class, hong.getId())).isZero();
        detail(hong).andExpect(jsonPath("$.unmask.allowed").value(true))
                .andExpect(jsonPath("$.identity.unmasked").value(false))
                .andExpect(jsonPath("$.identity.name").value("홍*동"));
    }

    // ------------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.ResultActions detail(Users user) throws Exception {
        return mockMvc.perform(get(PATH + user.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions unmask(Users user, String token, String reason)
            throws Exception {
        return mockMvc.perform(post(PATH + user.getId() + "/privacy-unmask").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, token).content(toJson(java.util.Map.of("reason", reason))));
    }

    private org.springframework.test.web.servlet.ResultActions lock(Users user, String token) throws Exception {
        return mockMvc.perform(post(PATH + user.getId() + "/privacy-lock").header(HttpHeaders.AUTHORIZATION, token));
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String iso(LocalDateTime at) {
        return at.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /** 하위주문 1건 — 상태는 결제 훅 대신 바로 쓴다. 주문 상품을 그 하위주문에 붙여 목록 「항목 요약」이 나오게 한다. */
    private OrderDeliveryGroup group(Order order, String fulfillmentStatus) {
        OrderDeliveryGroup group = deliveryGroupRepository.save(OrderDeliveryGroup.builder()
                .order(order).market(market).productTotal(PRICE).deliveryFee(0).freeShippingApplied(false)
                .marketName("제니의 뷰티룸").build());
        jdbc.update("UPDATE order_delivery_group SET fulfillment_status = ?, sub_order_number = ? WHERE delivery_group_id = ?",
                fulfillmentStatus, "SUB-" + group.getId(), group.getId());
        jdbc.update("UPDATE order_product SET delivery_group_id = ? WHERE order_id = ?", group.getId(), order.getId());
        return deliveryGroupRepository.findById(group.getId()).orElseThrow();
    }

    /** 반품 · 교환 1건(박스 1 + 클레임 1) — H2 는 컬럼 DEFAULT 를 쓰지 않아 NOT NULL 컬럼을 모두 채운다. 박스(요청) id 를 돌려준다. */
    private Long claim(Order order, OrderDeliveryGroup group, Users user, String type, String claimStatus, String result) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO order_claim_collection (order_id, delivery_group_id, market_id, user_id, type, reason_code, "
                        + "fee_bearer, invoice_due_at, return_deduction, auto_received, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'CHANGE_OF_MIND', 'CONSUMER', ?, 0, false, ?)",
                order.getId(), group.getId(), market.getId(), user.getId(), type, now.plusDays(3), now);
        Long collectionId = jdbc.queryForObject("SELECT MAX(collection_id) FROM order_claim_collection", Long.class);
        claimInBox(collectionId, order, group, user, type, claimStatus, result);
        return collectionId;
    }

    /** 상태 전이 로그 1행 — 탈퇴 · 재가입 경로 대신 테이블 계약대로 심고 시각만 소급한다. */
    private void statusTransition(Users user, UserStatus previous, UserStatus next, LocalDateTime at) {
        UserStatusHistory saved = statusHistoryRepository.save(UserStatusHistory.builder()
                .user(user).previousStatus(previous).newStatus(next).reason(null).build());
        jdbc.update("UPDATE user_status_history SET created_at = ? WHERE user_status_history_id = ?", at, saved.getId());
    }

    /** 결제 · 배송완료 주문 1건(하위주문 1). */
    private Order paidDelivered(Users user, LocalDateTime paidAt) {
        Order order = orders.paidOrder(user, variant, 1, PRICE, OrderProductStatus.PAID);
        paidAt(order, paidAt);
        group(order, "DELIVERED");
        return order;
    }

    private OrderDeliveryGroup onlyGroup(Order order) {
        return deliveryGroupRepository.findByOrderId(order.getId()).get(0);
    }

    /** 같은 박스에 클레임 1건 더 — 목록은 클레임(행) 단위다. */
    private void claimInBox(Long collectionId, Order order, OrderDeliveryGroup group, Users user, String type,
                            String claimStatus, String result) {
        LocalDateTime now = LocalDateTime.now();
        Long orderProductId = jdbc.queryForObject("SELECT MIN(order_product_id) FROM order_product WHERE order_id = ?",
                Long.class, order.getId());
        jdbc.update("INSERT INTO order_claim (collection_id, order_id, delivery_group_id, order_product_id, market_id, user_id, "
                        + "type, quantity, reason_code, fee_bearer, status, stage_entered_at, requested_at, collect_due_at, "
                        + "notice_count, inspect_notice_count, fault_changed_to_seller, opened_by, result) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 1, 'CHANGE_OF_MIND', 'CONSUMER', ?, ?, ?, ?, 0, 0, false, 'CONSUMER', ?)",
                collectionId, order.getId(), group.getId(), orderProductId, market.getId(), user.getId(), type, claimStatus,
                now, now, now.plusDays(3), result);
    }

    /**
     * 진행 중 칩 다섯 축의 데이터 — 칩에 들어가는 것 하나씩과, 같은 목록 근처에 있지만 칩에서 빠져야 하는 것을 함께 심는다(56 C1).
     * 하위주문 상태는 결제 훅 대신 바로 쓴다.
     */
    private void seedPendingWork(Users user) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Product product = variant.getProduct();
        // 열린 문의 — 1:1 대기 1(답변 완료 1 은 빠짐) · 상품 대기 1(대기지만 삭제 요청 검토 중 1 · 삭제 집행 1 은 빠짐)
        oneToOne(user, CsCategory.DELIVERY, "배송 문의", now.minusHours(3));
        OneToOneInquiry answered = oneToOne(user, CsCategory.DELIVERY, "답변된 문의", now.minusHours(2));
        jdbc.update("UPDATE one_to_one_inquiry SET status = 'ANSWERED', answered_at = ? WHERE inquiry_id = ?",
                now.minusHours(1), answered.getId());
        productInquiry(user, product, "대기 문의", now.minusHours(3));
        exposure(productInquiry(user, product, "삭제 요청된 문의", now.minusHours(2)), "DELETE_REQUESTED");
        exposure(productInquiry(user, product, "삭제된 문의", now.minusHours(1)), "DELETED");
        // 배송중 1
        group(orders.paidOrder(user, variant, 1, PRICE, OrderProductStatus.PAID), "SHIPPING");
        // 취소 요청 검토 중 1 — 취소된 하위주문은 취소 탭에 있지만 칩이 아니다
        cancelRequest(group(orders.paidOrder(user, variant, 1, PRICE, OrderProductStatus.PAID), "PREPARING"), user);
        group(orders.paidOrder(user, variant, 1, PRICE, OrderProductStatus.CANCELLED), "CANCELLED");
        // 반품 · 교환 진행 3 — 한 박스 2건 + 40일 전 신청 1(기본 1개월 밖). 종결 · 결제 대기(접수 전)는 빠진다
        Order boxed = orders.paidOrder(user, variant, 2, PRICE, OrderProductStatus.PAID);
        OrderDeliveryGroup boxedGroup = group(boxed, "DELIVERED");
        Long box = claim(boxed, boxedGroup, user, "RETURN", "REQUESTED", null);
        claimInBox(box, boxed, boxedGroup, user, "RETURN", "REQUESTED", null);
        Order old = orders.paidOrder(user, variant, 1, PRICE, OrderProductStatus.PAID);
        Long oldBox = claim(old, group(old, "DELIVERED"), user, "EXCHANGE", "REQUESTED", null);
        jdbc.update("UPDATE order_claim SET requested_at = ?, stage_entered_at = ? WHERE collection_id = ?",
                now.minusDays(40), now.minusDays(40), oldBox);
        Order done = orders.paidOrder(user, variant, 1, PRICE, OrderProductStatus.RETURNED);
        claim(done, group(done, "DELIVERED"), user, "RETURN", "COMPLETED", "REFUNDED");
        Order unpaid = orders.paidOrder(user, variant, 1, PRICE, OrderProductStatus.PAID);
        claim(unpaid, group(unpaid, "DELIVERED"), user, "EXCHANGE", "PAYMENT_PENDING", null);
        // 환불 실패 1 — 완료는 빠진다
        Order failed = orders.paidOrder(user, variant, 1, PRICE, OrderProductStatus.PAID);
        refundTask(failed, group(failed, "DELIVERED"), "FAILED");
        Order refunded = orders.paidOrder(user, variant, 1, PRICE, OrderProductStatus.PAID);
        refundTask(refunded, group(refunded, "DELIVERED"), "DONE");
    }

    /** 칩 링크의 목록 API 를 query 그대로 불러 전체 건수를 읽는다. */
    private long listTotal(String target, JsonNode query) throws Exception {
        String path = switch (target) {
            case "INQUIRIES" -> "/v1/admin/inquiries";
            case "PRODUCT_INQUIRIES" -> "/v1/admin/product-inquiries";
            case "ORDERS" -> "/v1/admin/orders";
            case "CLAIMS" -> "/v1/admin/claims";
            case "REFUNDS" -> "/v1/admin/refunds";
            default -> throw new IllegalArgumentException(target);
        };
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                get(path).header(HttpHeaders.AUTHORIZATION, adminToken);
        query.fields().forEachRemaining(field -> request.param(field.getKey(), field.getValue().asText()));
        return json(mockMvc.perform(request).andExpect(status().isOk())).path("pageInfo").path("totalResults").asLong();
    }

    private void exposure(ProductInquiry inquiry, String exposureStatus) {
        jdbc.update("UPDATE product_inquiry SET exposure_status = ? WHERE product_inquiry_id = ?", exposureStatus,
                inquiry.getId());
    }

    /** 검토 중 취소 요청 — 앱 C10 경로 대신 테이블 계약대로 심는다(전 항목 · 단순 변심). */
    private void cancelRequest(OrderDeliveryGroup group, Users user) {
        transactionTemplate.executeWithoutResult(tx -> {
            OrderDeliveryGroup attached = deliveryGroupRepository.findById(group.getId()).orElseThrow();
            OrderCancelRequest request = OrderCancelRequest.builder()
                    .deliveryGroup(attached).order(attached.getOrder()).requestedBy(user.getId())
                    .reasonCode(CancelRequestReason.CHANGE_OF_MIND).statusAtRequest(attached.getFulfillmentStatus())
                    .requestedAt(LocalDateTime.now().withNano(0)).build();
            for (OrderProduct item : orderProductRepository.findByDeliveryGroupIds(List.of(attached.getId()))) {
                request.addItem(OrderCancelRequestItem.builder().cancelRequest(request).orderProduct(item)
                        .quantity(item.getQuantity()).refundAmount(item.getPrice() * item.getQuantity()).build());
            }
            cancelRequestRepository.save(request);
        });
    }

    /** 환불 작업 — PG 자동 · 직권 취소 출처. 상태는 집행 대신 바로 쓴다. */
    private void refundTask(Order order, OrderDeliveryGroup group, String taskStatus) {
        Long taskId = transactionTemplate.execute(tx -> refundTaskRepository.save(OrderRefundTask.builder()
                .deliveryGroup(deliveryGroupRepository.findById(group.getId()).orElseThrow())
                .order(orderRepository.findById(order.getId()).orElseThrow())
                .source(RefundTaskSource.SELLER_DIRECT_CANCEL).sourceId(group.getId()).refundAmount(PRICE)
                .origin(RefundTaskOrigin.PG_AUTO).partialCancel(false).build()).getId());
        jdbc.update("UPDATE order_refund_task SET status = ? WHERE refund_task_id = ?", taskStatus, taskId);
    }

    private OneToOneInquiry oneToOne(Users user, CsCategory type, String content, LocalDateTime createdAt) {
        OneToOneInquiry inquiry = oneToOneInquiryRepository.save(OneToOneInquiry.builder()
                .user(user).type(type).content(content).build());
        jdbc.update("UPDATE one_to_one_inquiry SET created_at = ? WHERE inquiry_id = ?", createdAt, inquiry.getId());
        return inquiry;
    }

    private ProductInquiry productInquiry(Users user, Product product, String content, LocalDateTime createdAt) {
        ProductInquiry inquiry = productInquiryRepository.save(ProductInquiry.builder()
                .user(user).product(product).type(ProductInquiryType.INGREDIENT_USAGE).content(content).secret(false).build());
        jdbc.update("UPDATE product_inquiry SET created_at = ? WHERE product_inquiry_id = ?", createdAt, inquiry.getId());
        return inquiry;
    }

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
        return createUser(username, RoleType.USER, status);
    }

    private Users createUser(String username, RoleType roleType, UserStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users(username, username, username + "@showroomz.test", "Y", null,
                ProviderType.KAKAO, roleType, now, now);
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
