package showroomz.api.app.order;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.order.service.OrderExpirationService;
import showroomz.api.app.order.service.PaymentConfirmService;
import showroomz.api.app.order.service.PaymentHealthService;
import showroomz.api.app.order.service.PaymentReconciliationService;
import showroomz.api.common.payment.service.PaymentWebhookService;
import showroomz.api.seller.groupbuy.GroupBuyTestSupport;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.address.repository.DeliveryAddressRepository;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.cart.repository.CartRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.repository.PaymentCancelRepository;
import showroomz.domain.payment.repository.PaymentReconciliationIssueRepository;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.domain.payment.repository.PaymentWebhookEventRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.payment.portone.FakePaymentGateway;
import showroomz.global.payment.portone.PortOnePaymentGateway;
import showroomz.global.payment.portone.PortOneWebhookVerifier;
import showroomz.support.ContractOptions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C9 결제 통합 테스트 배선(결제 계획서 8절). 진행 중 공구 1건(크림 27,200 · 세럼 24,000, 정가 34,000 · 30,000) · 배송비 3,000 ·
 * 무료배송 50,000 · 소비자 1명(기본 배송지). 포트원은 {@link FakePaymentGateway}가 대신한다 — 시나리오를 결제마다 지정한다.
 *
 * <p>기본 시나리오(사전 등록된 결제는 조회하면 등록 금액으로 PAID)로 「주문 → complete → PAID」가 그대로 돈다.
 */
public abstract class OrderPaymentTestSupport extends GroupBuyTestSupport {

    protected static final String ORDERS = "/v1/user/orders";
    protected static final String PAYMENTS = "/v1/user/payments";
    protected static final String WEBHOOK = "/v1/webhooks/portone";
    protected static final String WEBHOOK_SECRET = "whsec_aW50ZWdyYXRpb24tdGVzdC13ZWJob29rLXNlY3JldA==";

    protected static final int CREAM_PRICE = 27_200;
    protected static final int CREAM_REGULAR = 34_000;
    protected static final int DELIVERY_FEE = 3_000;

    @Autowired protected CartRepository cartRepository;
    @Autowired protected DeliveryAddressRepository deliveryAddressRepository;
    @Autowired protected OrderRepository orderRepository;
    @Autowired protected PaymentRepository paymentRepository;
    @Autowired protected PaymentCancelRepository paymentCancelRepository;
    @Autowired protected PaymentWebhookEventRepository webhookEventRepository;
    @Autowired protected PaymentReconciliationIssueRepository issueRepository;
    @Autowired protected OrderExpirationService expirationService;
    @Autowired protected PaymentConfirmService confirmService;
    @Autowired protected PaymentWebhookService webhookService;
    @Autowired protected PaymentReconciliationService reconciliationService;
    @Autowired protected PaymentHealthService healthService;
    @Autowired protected PortOnePaymentGateway gateway;

    protected FakePaymentGateway fake;
    protected GroupBuy groupBuy;
    protected ProductVariant creamVariant;
    protected ProductVariant serumVariant;
    protected Users consumer;
    protected String consumerToken;
    protected DeliveryAddress address;

    @BeforeEach
    void setUpOrderFixtures() {
        fake = (FakePaymentGateway) gateway;
        fake.reset();

        groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        // 공구 상태는 SQL 로 옮겼으므로 상품 동기화도 직접 맞춘다(운영은 오픈 스케줄러가 resync 한다).
        for (Product product : java.util.List.of(cream, serum)) {
            jdbc.update("UPDATE product SET group_buy_status = 'IN_PROGRESS' WHERE product_id = ?", product.getProductId());
        }
        jdbc.update("UPDATE market SET default_delivery_fee = ?, free_shipping_threshold = ? WHERE market_id = ?",
                DELIVERY_FEE, 50_000, brand.marketId());
        creamVariant = ContractOptions.variantsOf(productVariantRepository, cream).get(0);
        serumVariant = ContractOptions.variantsOf(productVariantRepository, serum).get(0);
        setStock(creamVariant, 10);
        setStock(serumVariant, 10);

        consumer = createConsumer("sumin", "김수민");
        consumerToken = bearerToken(consumer.getUsername(), RoleType.USER, consumer.getId());
        address = deliveryAddressRepository.save(DeliveryAddress.builder()
                .user(consumer).recipientName("김수민").zipCode("06234").address("서울 강남구 테헤란로 000")
                .detailAddress("쇼룸타워 12층").phoneNumber("010-1234-5678").memo("문 앞에 놓아주세요").isDefault(true).build());
    }

    @AfterEach
    void resetFake() {
        fake.reset();
    }

    // ------------------------------------------------------------------ 픽스처

    protected Users createConsumer(String username, String nickname) {
        LocalDateTime now = LocalDateTime.now();
        Users user = new Users(username, nickname, username + "@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.USER, now, now);
        user.setName(nickname);
        user.setPhoneNumber("010-1234-5678");
        return userRepository.save(user);
    }

    protected void setStock(ProductVariant variant, int stock) {
        jdbc.update("UPDATE product_variant SET stock = ? WHERE variant_id = ?", stock, variant.getVariantId());
    }

    protected int stockOf(ProductVariant variant) {
        return jdbc.queryForObject("SELECT stock FROM product_variant WHERE variant_id = ?", Integer.class, variant.getVariantId());
    }

    protected Cart cartItem(ProductVariant variant, int quantity) {
        return cartRepository.save(new Cart(consumer, variant, groupBuy, quantity));
    }

    // ------------------------------------------------------------------ 요청

    protected ResultActions checkout(Object body) throws Exception {
        return mockMvc.perform(post(ORDERS + "/checkout").header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions createOrder(Object body) throws Exception {
        return createOrder(consumerToken, body);
    }

    protected ResultActions createOrder(String token, Object body) throws Exception {
        return mockMvc.perform(post(ORDERS).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions complete(String paymentId) throws Exception {
        return mockMvc.perform(post(PAYMENTS + "/" + paymentId + "/complete").header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    protected ResultActions detail(Long orderId) throws Exception {
        return mockMvc.perform(get(ORDERS + "/" + orderId).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    protected ResultActions cancel(Long orderId) throws Exception {
        return mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"단순 변심\"}"));
    }

    protected ResultActions retry(Long orderId, Map<String, Object> payment) throws Exception {
        return mockMvc.perform(post(ORDERS + "/" + orderId + "/payments").header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("payment", payment))));
    }

    protected ResultActions webhook(String webhookId, String type, String paymentId) throws Exception {
        byte[] body = ("{\"type\":\"" + type + "\",\"timestamp\":\"2026-09-27T05:00:00Z\",\"data\":{\"paymentId\":\"" + paymentId
                + "\",\"storeId\":\"" + FakePaymentGateway.STORE_ID + "\"}}").getBytes(StandardCharsets.UTF_8);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String signature = "v1," + new PortOneWebhookVerifier(WEBHOOK_SECRET).sign(body, webhookId, ts);
        return mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("webhook-id", webhookId).header("webhook-timestamp", ts).header("webhook-signature", signature));
    }

    // ------------------------------------------------------------------ 본문

    protected Map<String, Object> cardOrder(String key, Long cartId) {
        return Map.of("idempotencyKey", key, "cartItemIds", java.util.List.of(cartId),
                "deliveryMemo", "문 앞에 놓아주세요",
                "payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN"));
    }

    protected Map<String, Object> directOrder(String key, ProductVariant variant, int quantity) {
        return Map.of("idempotencyKey", key,
                "direct", Map.of("variantId", variant.getVariantId(), "quantity", quantity, "groupBuyId", groupBuy.getId()),
                "payment", Map.of("method", "EASY_PAY", "easyPayProvider", "KAKAOPAY"));
    }

    protected String newKey() {
        return UUID.randomUUID().toString();
    }

    /** 바로 구매 · 카드 주문 생성 성공 → (orderId, paymentId). 장바구니 행을 만들지 않아 같은 옵션을 여러 번 주문할 수 있다. */
    protected Created placeCardOrder(ProductVariant variant, int quantity) throws Exception {
        Map<String, Object> body = Map.of("idempotencyKey", newKey(),
                "direct", Map.of("variantId", variant.getVariantId(), "quantity", quantity, "groupBuyId", groupBuy.getId()),
                "payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN"));
        return created(createOrder(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    protected Created created(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return new Created(node.get("orderId").asLong(), node.get("payment").get("paymentId").asText(),
                node.get("payment").get("totalAmount").asLong());
    }

    protected Order order(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    protected Payment payment(String paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow();
    }

    public record Created(Long orderId, String paymentId, long amount) {
    }
}
