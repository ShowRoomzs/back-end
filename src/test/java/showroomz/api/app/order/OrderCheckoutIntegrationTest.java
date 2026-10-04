package showroomz.api.app.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.type.PaymentStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 결제 PR 3·4 — 주문서 · 주문 생성 · 재고 예약 · 멱등키 · 결제 전 취소(결제 계획서 5-2 · 5-3 · 4-3 · 8절). */
@DisplayName("[통합] C9 주문서 · 주문 생성")
class OrderCheckoutIntegrationTest extends OrderPaymentTestSupport {

    @Test
    @DisplayName("주문서 — 장바구니 선택으로 그룹·배송비·금액 4줄·결제수단·CTA 를 내린다(장바구니 합계와 같은 식)")
    void checkoutFromCart() throws Exception {
        Cart cart = cartItem(creamVariant, 1);

        checkout(Map.of("cartItemIds", List.of(cart.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryAddress.id").value(address.getId()))
                .andExpect(jsonPath("$.deliveryAddress.isDefault").value(true))
                .andExpect(jsonPath("$.memoPresets.length()").value(4))
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].groupBuyId").value(groupBuy.getId()))
                .andExpect(jsonPath("$.groups[0].marketName").value("글로우랩"))
                .andExpect(jsonPath("$.groups[0].items[0].cartId").value(cart.getId()))
                .andExpect(jsonPath("$.groups[0].items[0].price.regularPrice").value(CREAM_REGULAR))
                .andExpect(jsonPath("$.groups[0].items[0].price.salePrice").value(CREAM_PRICE))
                .andExpect(jsonPath("$.groups[0].items[0].price.discountRate").value(20))
                .andExpect(jsonPath("$.groups[0].shipping.deliveryFee").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.groups[0].shipping.isFreeShipping").value(false))
                .andExpect(jsonPath("$.summary.productTotal").value(CREAM_REGULAR))
                .andExpect(jsonPath("$.summary.discountTotal").value(CREAM_REGULAR - CREAM_PRICE))
                .andExpect(jsonPath("$.summary.deliveryFeeTotal").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.summary.totalAmount").value(CREAM_PRICE + DELIVERY_FEE))
                .andExpect(jsonPath("$.paymentMethods.cardIssuers.length()").value(10))
                .andExpect(jsonPath("$.paymentMethods.easyPayProviders").value(org.hamcrest.Matchers.contains("KAKAOPAY", "NAVERPAY", "TOSSPAY")))
                .andExpect(jsonPath("$.ctaLabel").value("30,200원 결제하기"));
    }

    @Test
    @DisplayName("주문서 — 바로 구매 2개면 무료배송 기준(50,000)을 넘겨 배송비 0 · 공구 없는 바로 구매는 400")
    void checkoutDirect() throws Exception {
        checkout(Map.of("direct", Map.of("variantId", creamVariant.getVariantId(), "quantity", 2, "groupBuyId", groupBuy.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].items[0].cartId").doesNotExist())
                .andExpect(jsonPath("$.groups[0].shipping.isFreeShipping").value(true))
                .andExpect(jsonPath("$.summary.deliveryFeeTotal").value(0))
                .andExpect(jsonPath("$.summary.totalAmount").value(CREAM_PRICE * 2));

        checkout(Map.of("direct", Map.of("variantId", creamVariant.getVariantId(), "quantity", 1)))
                .andExpect(status().isBadRequest());
        checkout(Map.of()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("주문서 — 살 수 없는 항목(마감·품절·계약에 없는 옵션)이 섞이면 조용히 빼지 않고 막는다")
    void checkoutRejectsUnpurchasable() throws Exception {
        Cart soldOut = cartItem(serumVariant, 1);
        setStock(serumVariant, 0);
        checkout(Map.of("cartItemIds", List.of(soldOut.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_PURCHASABLE"))
                .andExpect(jsonPath("$.message").value("품절되어 주문할 수 없어요"));

        // 다른 브랜드의 공구를 실어 와도 그 계약에 이 옵션이 없어 걸린다(4-6 ②).
        var otherBrand = fixture.createBrand("other@showroomz.test", "다른브랜드");
        var otherGroupBuy = seed(otherBrand, creator, "다른 공구", java.time.LocalDateTime.now().minusDays(1),
                java.time.LocalDateTime.now().plusDays(3));
        moveTo(otherGroupBuy.getId(), GroupBuyStatus.IN_PROGRESS);
        checkout(Map.of("direct", Map.of("variantId", creamVariant.getVariantId(), "quantity", 1, "groupBuyId", otherGroupBuy.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이 공구에서 판매하지 않는 옵션이에요"));

        // 배송지가 없으면 에러가 아니라 null — CTA 만 잠근다.
        deliveryAddressRepository.deleteAll();
        Cart cart = cartItem(creamVariant, 1);
        checkout(Map.of("cartItemIds", List.of(cart.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryAddress").doesNotExist());
    }

    @Test
    @DisplayName("주문 생성 — 재고를 예약하고 결제창 재료를 내린다. 금액은 서버가 계산한 값이다")
    void createOrderReservesStockAndReturnsPaymentWindow() throws Exception {
        Cart cart = cartItem(creamVariant, 2);

        String body = createOrder(cardOrder(newKey(), cart.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.matchesPattern("\\d{8}-\\d{6}")))
                .andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.payment.paymentId").value(org.hamcrest.Matchers.endsWith("-1")))
                .andExpect(jsonPath("$.payment.storeId").value("store-fake"))
                .andExpect(jsonPath("$.payment.channelKey").value("channel-key-fake"))
                .andExpect(jsonPath("$.payment.totalAmount").value(CREAM_PRICE * 2))
                .andExpect(jsonPath("$.payment.currency").value("KRW"))
                .andExpect(jsonPath("$.payment.payMethod").value("CARD"))
                .andExpect(jsonPath("$.payment.cardCompany").value("SHINHAN_CARD"))
                .andExpect(jsonPath("$.payment.orderName").value("글로우 크림 50ml"))
                .andExpect(jsonPath("$.payment.customer.fullName").value("김수민"))
                .andReturn().getResponse().getContentAsString();
        Created created = created(body);

        assertThat(stockOf(creamVariant)).isEqualTo(8);
        assertThat(order(created.orderId()).getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order(created.orderId()).getTotalAmount()).isEqualTo(CREAM_PRICE * 2);
        assertThat(order(created.orderId()).getDeliveryFeeTotal()).isZero();
        assertThat(payment(created.paymentId()).getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment(created.paymentId()).getPreRegisteredAt()).isNotNull();
        assertThat(fake.isPreRegistered(created.paymentId())).isTrue();
        // 장바구니 행은 결제 완료 때 지운다 — 아직 남아 있다.
        assertThat(cartRepository.findById(cart.getId())).isPresent();
    }

    @Test
    @DisplayName("주문 생성 — 주문서를 열어 둔 사이 금액이 바뀌면 409 · 배송지 없으면 400 · 재고 부족이면 400 이고 아무것도 남지 않는다")
    void createOrderGuards() throws Exception {
        Cart cart = cartItem(creamVariant, 1);
        Map<String, Object> changed = new java.util.HashMap<>(cardOrder(newKey(), cart.getId()));
        changed.put("expectedTotalAmount", 1);
        createOrder(changed).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_AMOUNT_CHANGED"));

        Cart tooMany = cartItem(serumVariant, 5);
        setStock(serumVariant, 4);
        createOrder(cardOrder(newKey(), tooMany.getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(stockOf(serumVariant)).isEqualTo(4);
        assertThat(orderRepository.count()).isZero();

        deliveryAddressRepository.deleteAll();
        createOrder(cardOrder(newKey(), cart.getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ORDER_ADDRESS_REQUIRED"));
        assertThat(stockOf(creamVariant)).isEqualTo(10);
    }

    @Test
    @DisplayName("멱등키 — 같은 키의 재요청은 새 주문을 만들지 않고 같은 응답을 돌려준다 · 재고는 1회만 차감")
    void idempotentCreate() throws Exception {
        Cart cart = cartItem(creamVariant, 1);
        String key = newKey();
        Created first = created(createOrder(cardOrder(key, cart.getId())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        Created second = created(createOrder(cardOrder(key, cart.getId())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(stockOf(creamVariant)).isEqualTo(9);
        assertThat(orderRepository.count()).isEqualTo(1);
        // 다른 사용자의 같은 키와는 충돌하지 않는다 — UK 는 (user_id, idempotency_key).
        var other = createConsumer("other", "다른사람");
        deliveryAddressRepository.save(showroomz.domain.address.entity.DeliveryAddress.builder()
                .user(other).recipientName("다른사람").zipCode("1").address("a").detailAddress("b").phoneNumber("010-0000-0000")
                .isDefault(true).build());
        createOrder(bearerToken(other.getUsername(), showroomz.api.app.auth.entity.RoleType.USER, other.getId()),
                directOrder(key, creamVariant, 1)).andExpect(status().isCreated());
        assertThat(orderRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("사전 등록 실패 — 주문은 남고 502 · 같은 키로 재요청하면 사전 등록을 다시 시도해 같은 주문을 돌려준다")
    void preRegisterFailureThenRetry() throws Exception {
        Cart cart = cartItem(creamVariant, 1);
        // 첫 시도의 paymentId 는 주문번호로 정해진다 — 사전 등록 호출 순서로 실패를 심는다.
        String key = newKey();
        fake.willFailPreRegisterNext();
        createOrder(cardOrder(key, cart.getId()))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(creamVariant)).isEqualTo(9);
        var order = orderRepository.findAll().get(0);
        assertThat(paymentRepository.findByOrder_IdOrderByAttemptDesc(order.getId()).get(0).getPreRegisteredAt()).isNull();

        Created retried = created(createOrder(cardOrder(key, cart.getId())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(retried.orderId()).isEqualTo(order.getId());
        assertThat(payment(retried.paymentId()).getPreRegisteredAt()).isNotNull();
        assertThat(fake.preRegisterCalls()).hasSize(2);
    }

    @Test
    @DisplayName("결제 전 취소 — 즉시 취소하고 재고를 돌려놓는다 · 살아 있는 결제는 CANCELLED · 다시 취소하면 409")
    void cancelBeforePayment() throws Exception {
        Created created = placeCardOrder(creamVariant, 3);
        assertThat(stockOf(creamVariant)).isEqualTo(7);

        cancel(created.orderId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"));
        assertThat(stockOf(creamVariant)).isEqualTo(10);
        assertThat(order(created.orderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order(created.orderId()).getStockReleasedAt()).isNotNull();
        assertThat(payment(created.paymentId()).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(fake.cancelCalls()).isEmpty();

        cancel(created.orderId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        // 남의 주문은 403.
        var other = createConsumer("other", "다른사람");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(ORDERS + "/" + created.orderId())
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION,
                                bearerToken(other.getUsername(), showroomz.api.app.auth.entity.RoleType.USER, other.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("동시성 — 재고 1개에 주문 2건이 동시에 오면 하나만 성공한다 · 같은 멱등키 동시 2건은 주문 1개")
    void concurrentOrders() throws Exception {
        setStock(creamVariant, 1);
        Cart a = cartItem(creamVariant, 1);
        var other = createConsumer("other", "다른사람");
        deliveryAddressRepository.save(showroomz.domain.address.entity.DeliveryAddress.builder()
                .user(other).recipientName("다른사람").zipCode("1").address("a").detailAddress("b").phoneNumber("010-0000-0000")
                .isDefault(true).build());
        String otherToken = bearerToken(other.getUsername(), showroomz.api.app.auth.entity.RoleType.USER, other.getId());

        java.util.List<Integer> statuses = runConcurrently(
                () -> createOrder(cardOrder(newKey(), a.getId())).andReturn().getResponse().getStatus(),
                () -> createOrder(otherToken, directOrder(newKey(), creamVariant, 1)).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(201, 400);
        assertThat(stockOf(creamVariant)).isZero();
        assertThat(orderRepository.count()).isEqualTo(1);

        setStock(serumVariant, 10);
        String key = newKey();
        java.util.List<Integer> sameKey = runConcurrently(
                () -> createOrder(directOrder(key, serumVariant, 1)).andReturn().getResponse().getStatus(),
                () -> createOrder(directOrder(key, serumVariant, 1)).andReturn().getResponse().getStatus());
        assertThat(sameKey).containsExactly(201, 201);
        assertThat(stockOf(serumVariant)).isEqualTo(9);
        assertThat(orderRepository.count()).isEqualTo(2);
    }

    @SafeVarargs
    private java.util.List<Integer> runConcurrently(java.util.concurrent.Callable<Integer>... tasks) throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(tasks.length);
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Integer>> futures = new java.util.ArrayList<>();
        for (java.util.concurrent.Callable<Integer> task : tasks) {
            futures.add(pool.submit(() -> {
                gate.await();
                return task.call();
            }));
        }
        gate.countDown();
        java.util.List<Integer> results = new java.util.ArrayList<>();
        for (var future : futures) {
            results.add(future.get(30, java.util.concurrent.TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return results;
    }
}
