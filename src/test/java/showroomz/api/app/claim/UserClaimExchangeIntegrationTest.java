package showroomz.api.app.claim;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.claim.service.ClaimPaymentService;
import showroomz.api.seller.order.SellerOrderTestSupport;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.payment.portone.PortOneStatus;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 교환(앱 클레임 설계서 7절 #6 ~ #9 · #12 · #18 · #21 · 35 설계서 6절 #7 · #8) — 옵션 검증 · 재고 선점 · 재발송 배송비
 * 선결제 · 결제 대기 초안 · 선결제 환불 · 교환받을 배송지 변경. 포트원은 시나리오 지정 더블이다.
 * 픽스처에 같은 가격의 옵션 「리필」을 크림 · 세럼에 하나씩 더한다(재고 5).
 */
@IntegrationTest
class UserClaimExchangeIntegrationTest extends SellerOrderTestSupport {

    private static final String CLAIMS = "/v1/user/claims";
    private static final String SELLER_CLAIMS = "/v1/seller/claims";

    @Autowired private ClaimPaymentService claimPaymentService;

    private ProductVariant creamRefill;
    private ProductVariant serumRefill;

    @BeforeEach
    void addSamePriceOptions() {
        creamRefill = addVariant(creamVariant);
        serumRefill = addVariant(serumVariant);
    }

    // ------------------------------------------------------------------ 폼 · 옵션 검증

    @Test
    @DisplayName("교환 폼 — 같은 가격의 옵션을 받은 옵션까지 내리고(품절은 표시만), 재발송 배송비와 교환받을 배송지 기본값을 내린다")
    void form() throws Exception {
        OrderDeliveryGroup group = deliveredGroup();
        setStock(creamRefill, 0);

        userGet(CLAIMS + "/form?orderProductId=" + items(group).get(0).getId() + "&type=EXCHANGE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EXCHANGE"))
                .andExpect(jsonPath("$.items[0].exchangeOptions", hasSize(2)))
                .andExpect(jsonPath("$.items[0].exchangeOptions[0].variantId").value(creamVariant.getVariantId()))
                .andExpect(jsonPath("$.items[0].exchangeOptions[0].current").value(true))
                .andExpect(jsonPath("$.items[0].exchangeOptions[1].variantId").value(creamRefill.getVariantId()))
                .andExpect(jsonPath("$.items[0].exchangeOptions[1].optionName").value("리필"))
                .andExpect(jsonPath("$.items[0].exchangeOptions[1].soldOut").value(true))
                .andExpect(jsonPath("$.reasons[0].hint").value("다른 옵션이 더 마음에 들어요"))
                .andExpect(jsonPath("$.fees.consumerFault").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.fees.sellerFault").value(0))
                .andExpect(jsonPath("$.reshipTo.recipientName").value("김수민"))
                .andExpect(jsonPath("$.reshipTo.phone").value("010-1234-5678"));
    }

    @Test
    @DisplayName("고객 귀책인데 받은 옵션과 같은 옵션이면 400, 브랜드 귀책이면 같은 옵션으로 결제 없이 접수된다(#6)")
    void sameOption() throws Exception {
        OrderDeliveryGroup group = deliveredGroup();
        int before = stockOf(creamVariant);

        create(body(group, "CHANGE_OF_MIND", null, creamVariant, DELIVERY_FEE)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLAIM_EXCHANGE_SAME_OPTION"));
        create(body(group, "CHANGE_OF_MIND", null, serumVariant, DELIVERY_FEE)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLAIM_EXCHANGE_OPTION_INVALID"));
        assertThat(count("order_claim")).isZero();

        Long claimId = created(create(body(group, "DAMAGED_OR_DEFECTIVE", "뚜껑이 깨져서 왔어요", creamVariant, 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.payment").value(nullValue())));

        // 재고는 요청하는 순간 잡는다.
        assertThat(stockOf(creamVariant)).isEqualTo(before - 1);
        assertThat(count("order_claim_charge")).isZero();
        userGet(CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.items[0].statusLabel").value("교환 요청"))
                .andExpect(jsonPath("$.exchangePayment.reshipFee").value(0))
                .andExpect(jsonPath("$.exchangePayment.methodLabel").value("결제 없음"))
                .andExpect(jsonPath("$.info.reshipAddressChangeable").value(true))
                .andExpect(jsonPath("$.refund").value(nullValue()));
    }

    @Test
    @DisplayName("교환할 옵션의 재고가 없으면 409 — 초안을 남기지 않는다")
    void outOfStock() throws Exception {
        OrderDeliveryGroup group = deliveredGroup();
        setStock(creamRefill, 0);

        create(body(group, "CHANGE_OF_MIND", null, creamRefill, DELIVERY_FEE)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_EXCHANGE_OUT_OF_STOCK"));
        assertThat(count("order_claim_collection")).isZero();
    }

    // ------------------------------------------------------------------ 선결제

    @Test
    @DisplayName("고객 귀책 교환 — 결제가 끝나야 접수된다. 그 전에는 브랜드 화면에도 주문 내역에도 없다(#7)")
    void paymentThenAccepted() throws Exception {
        OrderDeliveryGroup group = deliveredGroup();

        JsonNode created = json(create(body(group, "CHANGE_OF_MIND", null, creamRefill, DELIVERY_FEE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.payment.paymentId", startsWith("clm-")))
                .andExpect(jsonPath("$.payment.totalAmount").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.payment.orderName").value("교환 재발송 배송비")));
        Long claimId = created.get("claimIds").get(0).asLong();
        String paymentId = created.get("payment").get("paymentId").asText();

        assertThat(stockOf(creamRefill)).isEqualTo(4);
        assertThat(fake.isPreRegistered(paymentId)).isTrue();
        sellerGet(SELLER_CLAIMS + "?tab=ALL").andExpect(jsonPath("$.content", empty()));
        userGet("/v1/user/orders").andExpect(jsonPath("$.content[0].items[0].status").value("DELIVERED"));
        userGet(CLAIMS + "/" + claimId).andExpect(status().isNotFound());
        assertThat(events(claimId)).isEmpty();

        fake.willReturnPaid(paymentId, DELIVERY_FEE);
        completeClaimPayment(paymentId).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.claimStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.claimIds[0]").value(claimId));
        // 멱등 — 다시 불러도 같다.
        completeClaimPayment(paymentId).andExpect(jsonPath("$.paymentStatus").value("PAID"));

        assertThat(jdbc.queryForMap("SELECT * FROM order_claim_charge")).containsEntry("type", "EXCHANGE_RESHIP")
                .containsEntry("status", "PAID").containsEntry("paid_payment_id", paymentId);
        assertThat(events(claimId)).containsExactly("REQUESTED");
        sellerGet(SELLER_CLAIMS + "?tab=ALL").andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].exchangeOptionName").value("리필"));
        sellerGet(SELLER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.exchangeFeeCharged").value(true));
        userGet("/v1/user/orders").andExpect(jsonPath("$.content[0].items[0].status").value("EXCHANGE_IN_PROGRESS"));
        userGet(CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.items[0].exchangeOptionName").value("리필"))
                .andExpect(jsonPath("$.exchangePayment.reshipFee").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.exchangePayment.paidAmount").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.exchangePayment.methodLabel").value("신한카드"));
    }

    @Test
    @DisplayName("앱이 콜백을 못 보내도 웹훅이 같은 확정을 한다 — paymentId 접두(clm-)로 클레임 결제 쪽으로 넘어온다")
    void webhookConfirms() throws Exception {
        OrderDeliveryGroup group = deliveredGroup();
        JsonNode created = json(create(body(group, "CHANGE_OF_MIND", null, creamRefill, DELIVERY_FEE))
                .andExpect(status().isCreated()));
        String paymentId = created.get("payment").get("paymentId").asText();
        fake.willReturnPaid(paymentId, DELIVERY_FEE);

        webhook("wh-claim-1", "Transaction.Paid", paymentId).andExpect(status().isOk());

        assertThat(paymentStatus(paymentId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT status FROM order_claim WHERE claim_id = ?", String.class,
                created.get("claimIds").get(0).asLong())).isEqualTo("REQUESTED");
        // 모르는 클레임 결제는 조용히 무시한다 — 주문 결제 쪽으로 넘기지 않는다.
        webhook("wh-claim-2", "Transaction.Paid", "clm-999999-1").andExpect(status().isOk());
    }

    @Test
    @DisplayName("결제 실패 뒤 — 같은 키로 다시 보내면 결제 시도만 새로, 새 키로 다시 요청하면 이전 초안을 지우고 재고를 다시 잡는다(#8)")
    void retryAfterFailedPayment() throws Exception {
        OrderDeliveryGroup group = deliveredGroup();
        Map<String, Object> body = body(group, "CHANGE_OF_MIND", null, creamRefill, DELIVERY_FEE);
        JsonNode first = json(create(body).andExpect(status().isCreated()));
        String firstPayment = first.get("payment").get("paymentId").asText();
        fake.willReturnStatus(firstPayment, PortOneStatus.FAILED, DELIVERY_FEE);

        completeClaimPayment(firstPayment).andExpect(jsonPath("$.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.claimStatus").value("PAYMENT_PENDING"));

        // 같은 키 — 요청은 그대로, 결제 시도만 2번째.
        JsonNode sameKey = json(create(body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING")));
        assertThat(sameKey.get("requestId").asLong()).isEqualTo(first.get("requestId").asLong());
        assertThat(sameKey.get("payment").get("paymentId").asText()).endsWith("-2");
        assertThat(stockOf(creamRefill)).isEqualTo(4);

        // 새 키 — 묶여 있던 수량과 재고가 풀린 뒤 다시 잡힌다(신청 수량 초과가 아니다).
        JsonNode newKey = json(create(body(group, "ORDER_MISTAKE", null, creamRefill, DELIVERY_FEE))
                .andExpect(status().isCreated()));
        assertThat(newKey.get("requestId").asLong()).isNotEqualTo(first.get("requestId").asLong());
        assertThat(count("order_claim_collection")).isEqualTo(1);
        assertThat(count("order_claim")).isEqualTo(1);
        assertThat(stockOf(creamRefill)).isEqualTo(4);
        // 폼도 다시 열린다 — 결제 대기 초안은 신청 가능 수량을 묶지 않는다.
        userGet(CLAIMS + "/form?orderProductId=" + items(group).get(0).getId() + "&type=EXCHANGE")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("받을 수 없는 결제는 자동 취소한다 — 금액 불일치 · 30분 지나 초안이 지워진 뒤 도착한 결제(#9)")
    void autoCancel() throws Exception {
        OrderDeliveryGroup mismatch = deliveredGroup();
        String wrongAmount = paymentIdOf(create(body(mismatch, "CHANGE_OF_MIND", null, creamRefill, DELIVERY_FEE)));
        fake.willReturnPaid(wrongAmount, 100);

        completeClaimPayment(wrongAmount).andExpect(jsonPath("$.paymentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.claimStatus").value("PAYMENT_PENDING"));
        assertThat(fake.cancelCalls()).contains(wrongAmount);

        OrderDeliveryGroup late = deliveredGroup();
        String latePayment = paymentIdOf(create(body(late, "CHANGE_OF_MIND", null, creamRefill, DELIVERY_FEE)));
        fake.willReturnNotFound(wrongAmount);
        fake.willReturnNotFound(latePayment);
        int reserved = stockOf(creamRefill);

        // 30분 뒤 정리 — 결제되지 않은 결제는 실패로 닫고, 결제 없는 초안은 지우며 재고를 푼다.
        claimPaymentService.reconcile(LocalDateTime.now().plusMinutes(31));

        assertThat(paymentStatus(latePayment)).isEqualTo("FAILED");
        assertThat(count("order_claim")).isZero();
        assertThat(count("order_claim_collection")).isZero();
        assertThat(count("order_claim_charge")).isZero();
        assertThat(stockOf(creamRefill)).isEqualTo(reserved + 2);

        // 뒤늦게 결제가 도착하면(웹훅) 받을 요청이 없다 — 취소한다.
        fake.willReturnPaid(latePayment, DELIVERY_FEE);
        claimPaymentService.confirm(latePayment, true);

        assertThat(paymentStatus(latePayment)).isEqualTo("CANCELLED");
        assertThat(fake.cancelCalls()).contains(latePayment);
    }

    // ------------------------------------------------------------------ 철회 · 검수

    @Test
    @DisplayName("선결제한 교환의 철회 — 두 항목 중 하나면 결제 유지, 마지막 하나까지 철회하면 결제 취소 · 재고 원복(#12)")
    void withdrawRefundsPrepayment() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        Map<String, Object> body = body(group, "CHANGE_OF_MIND", null, creamRefill, DELIVERY_FEE);
        body.put("items", List.of(
                Map.of("orderProductId", items(group).get(0).getId(), "exchangeVariantId", creamRefill.getVariantId()),
                Map.of("orderProductId", items(group).get(1).getId(), "exchangeVariantId", serumRefill.getVariantId())));
        JsonNode created = json(create(body).andExpect(status().isCreated()));
        String paymentId = created.get("payment").get("paymentId").asText();
        fake.willReturnPaid(paymentId, DELIVERY_FEE);
        completeClaimPayment(paymentId).andExpect(jsonPath("$.claimStatus").value("REQUESTED"));
        Long first = created.get("claimIds").get(0).asLong();
        Long second = created.get("claimIds").get(1).asLong();
        assertThat(stockOf(creamRefill)).isEqualTo(4);
        assertThat(stockOf(serumRefill)).isEqualTo(4);

        userPost(CLAIMS + "/" + first + "/withdraw").andExpect(status().isOk());
        assertThat(stockOf(creamRefill)).isEqualTo(5);
        assertThat(paymentStatus(paymentId)).isEqualTo("PAID");
        assertThat(fake.cancelCalls()).doesNotContain(paymentId);

        userPost(CLAIMS + "/" + second + "/withdraw").andExpect(status().isOk());
        assertThat(stockOf(serumRefill)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT status FROM order_claim_charge", String.class)).isEqualTo("REFUNDED");
        assertThat(paymentStatus(paymentId)).isEqualTo("CANCELLED");
        assertThat(fake.cancelCalls()).contains(paymentId);
    }

    @Test
    @DisplayName("교환 검수 — 통과는 재발송 대기(판매 집계 불변), 거절은 선점 재고를 되돌리고 선결제분으로 재발송비를 충당한다(#18 · 35 #7 · #8)")
    void inspection() throws Exception {
        OrderDeliveryGroup passed = deliveredGroup();
        Long passClaim = paidExchange(passed, creamRefill);
        OrderDeliveryGroup rejected = deliveredGroup();
        Long rejectClaim = paidExchange(rejected, creamRefill);
        assertThat(stockOf(creamRefill)).isEqualTo(3);
        long salesBefore = jdbc.queryForObject("SELECT SUM(price * (quantity - returned_quantity)) FROM order_product",
                Long.class);

        receive(passClaim, rejectClaim);
        sellerPost(SELLER_CLAIMS + "/" + passClaim + "/inspection/pass", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("RESHIP_READY"))
                .andExpect(jsonPath("$.summary.reshipReason").value("EXCHANGE"))
                // 보낼 물건은 새 옵션이다.
                .andExpect(jsonPath("$.summary.shipLabel").value(items(passed).get(0).getProductName() + " 리필"));
        sellerPost(SELLER_CLAIMS + "/" + rejectClaim + "/inspection/reject", rejectBody()).andExpect(status().isOk())
                // 선결제분으로 충당 — 거절 보류를 거치지 않는다. 보낼 물건은 원래 옵션이다.
                .andExpect(jsonPath("$.summary.status").value("RESHIP_READY"))
                .andExpect(jsonPath("$.summary.reshipReason").value("REJECT_RETURN"));

        assertThat(stockOf(creamRefill)).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT status FROM order_claim_charge WHERE type = 'REJECT_RESHIP'", String.class))
                .containsExactly("COVERED");
        assertThat(count("order_refund_task")).isZero();
        assertThat(jdbc.queryForObject("SELECT SUM(price * (quantity - returned_quantity)) FROM order_product",
                Long.class)).isEqualTo(salesBefore);
        userGet(CLAIMS + "/" + rejectClaim)
                .andExpect(jsonPath("$.items[0].statusLabel").value("교환 반려"))
                .andExpect(jsonPath("$.items[0].statusSub").value("받은 상품을 다시 보내드려요"))
                .andExpect(jsonPath("$.reshipFee.state").value("COVERED"))
                .andExpect(jsonPath("$.info.reshipAddressChangeable").value(false));
        userGet(CLAIMS + "/" + passClaim)
                .andExpect(jsonPath("$.items[0].statusLabel").value("교환 승인"))
                .andExpect(jsonPath("$.items[0].statusSub").value("새 상품 발송 준비 중"));
    }

    @Test
    @DisplayName("0원으로 요청한 교환(브랜드 귀책)이 거절되면 재발송 배송비 결제가 필요하다(#18)")
    void rejectedZeroFeeExchangeNeedsPayment() throws Exception {
        OrderDeliveryGroup group = deliveredGroup();
        Map<String, Object> body = body(group, "DAMAGED_OR_DEFECTIVE", "불량입니다", creamVariant, 0);
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", "684922013378"));
        Long claimId = created(create(body).andExpect(status().isCreated()));
        int reserved = stockOf(creamVariant);

        receive(claimId);
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", rejectBody()).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("REJECT_HOLD"));

        assertThat(stockOf(creamVariant)).isEqualTo(reserved + 1);
        assertThat(jdbc.queryForMap("SELECT * FROM order_claim_charge")).containsEntry("type", "REJECT_RESHIP")
                .containsEntry("status", "PENDING");
        userGet(CLAIMS + "/" + claimId).andExpect(jsonPath("$.reshipFee.state").value("PAYABLE"))
                .andExpect(jsonPath("$.items[0].phase").value("REJECTED_PAY"));
    }

    // ------------------------------------------------------------------ 교환받을 배송지

    @Test
    @DisplayName("교환받을 배송지 — 요청 때 고르거나 검수 전까지 바꿀 수 있고, 검수를 통과하면 409. 반품은 바꿀 수 없다(#21)")
    void reshipAddress() throws Exception {
        DeliveryAddress other = deliveryAddressRepository.save(DeliveryAddress.builder()
                .user(consumer).recipientName("이하늘").zipCode("48058").address("부산 해운대구 센텀로 11")
                .detailAddress("101동 202호").phoneNumber("010-9876-4321").memo("경비실").isDefault(false).build());
        OrderDeliveryGroup group = deliveredGroup();
        Map<String, Object> body = body(group, "DAMAGED_OR_DEFECTIVE", "불량입니다", creamVariant, 0);
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", "684922013378"));
        body.put("reshipAddressId", other.getId());
        Long claimId = created(create(body).andExpect(status().isCreated()));

        userGet(CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.info.reshipTo.recipientName").value("이하*"))
                .andExpect(jsonPath("$.info.reshipTo.address").value("부산 해운대구 센텀로 11"))
                .andExpect(jsonPath("$.info.pickupFrom").value(nullValue()));

        changeAddress(claimId, address.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.reshipTo.recipientName").value("김수*"));
        assertThat(events(claimId)).contains("RESHIP_ADDRESS_CHANGED");

        receive(claimId);
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/pass", Map.of()).andExpect(status().isOk());
        changeAddress(claimId, other.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_ADDRESS_NOT_CHANGEABLE"));
        assertThat(jdbc.queryForObject("SELECT reship_recipient FROM order_claim_collection", String.class))
                .isEqualTo("김수민");

        Map<String, Object> returnBody = body(deliveredGroup(), "CHANGE_OF_MIND", null, null, null);
        returnBody.put("type", "RETURN");
        Long returnClaim = created(create(returnBody).andExpect(status().isCreated()));
        changeAddress(returnClaim, other.getId()).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ 픽스처

    /** 같은 상품에 같은 가격의 옵션 「리필」을 더하고 그 공구 계약에 싣는다 — 재고 5. */
    private ProductVariant addVariant(ProductVariant base) {
        Product product = productVariantRepository.findByProductIdsOrderByVariantId(
                List.of(productIdOf(base))).get(0).getProduct();
        ProductVariant added = productVariantRepository.save(new ProductVariant(product, "리필",
                base.getRegularPrice(), base.getSalePrice(), 5, false));
        jdbc.update("INSERT INTO contract_item_option (contract_item_id, variant_id, variant_name, regular_price, "
                + "min_quantity, sort_order) SELECT contract_item_id, ?, '리필', regular_price, 0, 1 "
                + "FROM contract_item_option WHERE variant_id = ?", added.getVariantId(), base.getVariantId());
        return added;
    }

    private Long productIdOf(ProductVariant variant) {
        return jdbc.queryForObject("SELECT product_id FROM product_variant WHERE variant_id = ?", Long.class,
                variant.getVariantId());
    }

    private OrderDeliveryGroup deliveredGroup() throws Exception {
        return delivered(shipped(prepared(paidGroup()), "CJ", newInvoice()),
                LocalDateTime.now().minusHours(1).withNano(0));
    }

    private OrderDeliveryGroup deliveredTwoItemGroup() throws Exception {
        return delivered(shipped(prepared(paidTwoItemGroup()), "CJ", newInvoice()),
                LocalDateTime.now().minusHours(1).withNano(0));
    }

    private static String newInvoice() {
        return String.valueOf(100_000_000_000L + (long) (Math.random() * 899_999_999_999L));
    }

    /** 첫 항목을 교환 요청하는 본문 — 카드 결제. */
    private Map<String, Object> body(OrderDeliveryGroup group, String reason, String detail, ProductVariant exchangeTo,
                                     Integer expectedFee) {
        OrderProduct product = items(group).get(0);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderProductId", product.getId());
        if (exchangeTo != null) {
            item.put("exchangeVariantId", exchangeTo.getVariantId());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("type", "EXCHANGE");
        body.put("deliveryGroupId", group.getId());
        body.put("items", new ArrayList<>(List.of(item)));
        body.put("reasonCode", reason);
        body.put("reasonDetail", detail);
        body.put("expectedFee", expectedFee);
        body.put("payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN"));
        return body;
    }

    /** 고객 귀책 교환을 결제까지 끝내 회수 중으로 만든다. */
    private Long paidExchange(OrderDeliveryGroup group, ProductVariant exchangeTo) throws Exception {
        Map<String, Object> body = body(group, "CHANGE_OF_MIND", null, exchangeTo, DELIVERY_FEE);
        body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", newInvoice()));
        JsonNode created = json(create(body).andExpect(status().isCreated()));
        String paymentId = created.get("payment").get("paymentId").asText();
        fake.willReturnPaid(paymentId, DELIVERY_FEE);
        completeClaimPayment(paymentId).andExpect(jsonPath("$.claimStatus").value("COLLECTING"));
        return created.get("claimIds").get(0).asLong();
    }

    private void receive(Long... claimIds) throws Exception {
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimIds)))
                .andExpect(jsonPath("$.succeeded").value(claimIds.length));
    }

    private static Map<String, Object> rejectBody() {
        return Map.of("reasonCode", "USED", "detail", "사용 흔적이 있습니다.",
                "evidenceImageUrls", List.of("https://img.test/e1.jpg"));
    }

    private ResultActions create(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(CLAIMS).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions completeClaimPayment(String paymentId) throws Exception {
        return userPost(CLAIMS + "/payments/" + paymentId + "/complete");
    }

    private ResultActions changeAddress(Long claimId, Long addressId) throws Exception {
        return mockMvc.perform(patch(CLAIMS + "/" + claimId + "/reship-address")
                .header(HttpHeaders.AUTHORIZATION, consumerToken).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("addressId", addressId))));
    }

    private ResultActions userGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private ResultActions userPost(String url) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private Long created(ResultActions actions) throws Exception {
        return json(actions).get("claimIds").get(0).asLong();
    }

    private String paymentIdOf(ResultActions actions) throws Exception {
        return json(actions.andExpect(status().isCreated())).get("payment").get("paymentId").asText();
    }

    private String paymentStatus(String paymentId) {
        return jdbc.queryForObject("SELECT status FROM order_claim_payment WHERE payment_id = ?", String.class,
                paymentId);
    }

    private List<String> events(Long claimId) {
        return jdbc.queryForList("SELECT event_type FROM order_claim_history WHERE claim_id = ? "
                + "ORDER BY claim_history_id", String.class, claimId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
