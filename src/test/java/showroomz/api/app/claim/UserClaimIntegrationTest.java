package showroomz.api.app.claim;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.order.SellerOrderTestSupport;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.service.DeliveryTrackingEventRecorder;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;
import showroomz.support.IntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소비자 앱 반품 API(앱 클레임 설계서 7절 #1 ~ #5 · #10 · #11 · #13 · #14 · #20 · #25) — 폼 · 요청 · 상세 · 회수 송장 · 철회 ·
 * 자동 취소. 하위주문은 실제 결제 경로로 만들고 배송완료는 추적 판정으로 태운다.
 */
@IntegrationTest
class UserClaimIntegrationTest extends SellerOrderTestSupport {

    private static final String CLAIMS = "/v1/user/claims";

    @Autowired private OrderClaimService claimService;
    @Autowired private DeliveryTrackingEventRecorder trackingEventRecorder;

    // ------------------------------------------------------------------ 폼

    @Test
    @DisplayName("폼 — 그 하위주문의 신청 가능 항목 전부 · 고를 수 있는 사유 4종 · 택배사 · 반품 수취 주소 · 차감액을 서버가 내린다")
    void form() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        Long entry = items(group).get(1).getId();

        getForm(entry, "RETURN").andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("RETURN"))
                .andExpect(jsonPath("$.deliveryGroupId").value(group.getId()))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].preselected").value(false))
                .andExpect(jsonPath("$.items[1].orderProductId").value(entry))
                .andExpect(jsonPath("$.items[1].preselected").value(true))
                .andExpect(jsonPath("$.items[1].claimableQuantity").value(1))
                .andExpect(jsonPath("$.reasons[*].code", contains("CHANGE_OF_MIND", "ORDER_MISTAKE",
                        "DAMAGED_OR_DEFECTIVE", "WRONG_OR_LATE_DELIVERY")))
                .andExpect(jsonPath("$.reasons[0].hint").value("상품이 필요 없어짐"))
                .andExpect(jsonPath("$.reasons[0].feeBearer").value("CONSUMER"))
                .andExpect(jsonPath("$.reasons[0].photoAllowed").value(false))
                .andExpect(jsonPath("$.reasons[2].detailRequired").value(true))
                .andExpect(jsonPath("$.reasons[2].photoAllowed").value(true))
                .andExpect(jsonPath("$.carriers[*].code", contains("CJ", "LOTTE", "HANJIN", "EPOST", "LOGEN", "CU")))
                // 배송비를 내고 받은 주문 — 차감이 없다.
                .andExpect(jsonPath("$.fees.consumerFault").value(0))
                .andExpect(jsonPath("$.fees.sellerFault").value(0))
                .andExpect(jsonPath("$.courierPayment.consumerFault").value("PREPAID"))
                .andExpect(jsonPath("$.courierPayment.sellerFault").value("COLLECT"))
                .andExpect(jsonPath("$.invoiceDueDays").value(7))
                .andExpect(jsonPath("$.detailMaxLength").value(250))
                .andExpect(jsonPath("$.photoMax").value(10));
    }

    @Test
    @DisplayName("폼 — 무료배송으로 받은 주문이면 고객 귀책 차감액이 주문 시점 배송비다. 배송완료가 아니거나 남의 주문이면 열리지 않는다")
    void formGuards() throws Exception {
        OrderDeliveryGroup freeShipping = deliveredGroup(4);
        OrderDeliveryGroup shipping = shippingGroup("111122223333");
        Users stranger = createConsumer("stranger", "박타인");
        String strangerToken = bearerToken(stranger.getUsername(), RoleType.USER, stranger.getId());

        getForm(items(freeShipping).get(0).getId(), "RETURN")
                .andExpect(jsonPath("$.fees.consumerFault").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.items[0].claimableQuantity").value(4));
        getForm(items(shipping).get(0).getId(), "RETURN").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_ELIGIBLE"));
        // 교환 폼의 고객 귀책 금액은 차감이 아니라 요청할 때 결제하는 재발송 배송비다.
        getForm(items(freeShipping).get(0).getId(), "EXCHANGE").andExpect(status().isOk())
                .andExpect(jsonPath("$.fees.consumerFault").value(DELIVERY_FEE));
        mockMvc.perform(get(CLAIMS + "/form?orderProductId=" + items(freeShipping).get(0).getId() + "&type=RETURN")
                .header(HttpHeaders.AUTHORIZATION, strangerToken)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 요청 · 상세

    @Test
    @DisplayName("반품 요청(송장 나중에) → 접수 화면 — 회수 대기 · 로즈 「회수 송장 입력 필요」 · 철회·송장 등록 버튼 · 등록 기한(#1)")
    void requestWithoutInvoice() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        Long claimId = created(create(body(group, "CHANGE_OF_MIND", null, null, 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED")));

        claimDetail(claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("RETURN"))
                .andExpect(jsonPath("$.orderId").value(group.getOrder().getId()))
                .andExpect(jsonPath("$.orderNumber").value(orderNumberOf(group)))
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.guide.visible").value(true))
                .andExpect(jsonPath("$.guide.courierPayment").value("PREPAID"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].claimId").value(claimId))
                .andExpect(jsonPath("$.items[0].focused").value(true))
                .andExpect(jsonPath("$.items[0].phase").value("REQUESTED"))
                .andExpect(jsonPath("$.items[0].statusLabel").value("반품 요청"))
                .andExpect(jsonPath("$.items[0].statusSub").value("회수 송장 입력 필요"))
                .andExpect(jsonPath("$.items[0].statusSubTone").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].actions[*].type", contains("WITHDRAW", "REGISTER_COLLECTION_INVOICE")))
                .andExpect(jsonPath("$.items[0].rejection").value(nullValue()))
                .andExpect(jsonPath("$.info.reasonLabel").value("단순 변심"))
                .andExpect(jsonPath("$.info.methodLabel").value("고객 직접 발송"))
                .andExpect(jsonPath("$.info.collectionInvoice").value(nullValue()))
                .andExpect(jsonPath("$.info.invoiceDueDate").value(LocalDate.now().plusDays(7).toString()))
                // 기록 화면이라 내 주소는 가린다.
                .andExpect(jsonPath("$.info.pickupFrom.recipientName").value("김수*"))
                .andExpect(jsonPath("$.info.pickupFrom.phoneNumber").value("010-****-5678"))
                .andExpect(jsonPath("$.refund.approvedAmount").value(CREAM_PRICE))
                .andExpect(jsonPath("$.refund.returnDeduction").value(0))
                .andExpect(jsonPath("$.refund.amount").value(CREAM_PRICE))
                .andExpect(jsonPath("$.refund.confirmed").value(false))
                .andExpect(jsonPath("$.refund.rejectedAmount").value(nullValue()))
                .andExpect(jsonPath("$.reshipFee").value(nullValue()));
    }

    @Test
    @DisplayName("송장을 같이 내면 회수중으로 시작한다 — 할 일이 없고 철회 버튼이 사라지고 회수 조회가 열린다(#1)")
    void requestWithInvoice() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        Long claimId = created(create(body(group, "CHANGE_OF_MIND", null, invoice("CJ", "6849-2201-3378"), 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COLLECTING")));

        claimDetail(claimId)
                .andExpect(jsonPath("$.items[0].phase").value("COLLECTING"))
                .andExpect(jsonPath("$.items[0].statusLabel").value("회수중"))
                .andExpect(jsonPath("$.items[0].statusSub").value("CJ대한통운 684922013378"))
                .andExpect(jsonPath("$.items[0].statusSubTone").value("MUTED"))
                .andExpect(jsonPath("$.items[0].actions[*].type", contains("TRACK_COLLECTION")))
                .andExpect(jsonPath("$.info.collectionInvoice.trackingNumber").value("684922013378"))
                .andExpect(jsonPath("$.info.invoiceDueDate").value(nullValue()));
    }

    @Test
    @DisplayName("같은 멱등키의 재요청은 요청을 둘 만들지 않는다(#2)")
    void idempotent() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        Map<String, Object> body = body(group, "CHANGE_OF_MIND", null, null, 0);

        JsonNode first = json(create(body).andExpect(status().isCreated()));
        JsonNode second = json(create(body).andExpect(status().isCreated()));

        assertThat(second.get("requestId").asLong()).isEqualTo(first.get("requestId").asLong());
        assertThat(second.get("claimIds")).isEqualTo(first.get("claimIds"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_claim_collection", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("두 항목을 한 번에 반품(고객 귀책 · 무료배송 주문) — 차감은 요청당 한 번이다(#3)")
    void twoItemsOneDeduction() throws Exception {
        setFreeShippingThreshold(50_000);
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        Map<String, Object> body = body(group, "CHANGE_OF_MIND", null, null, DELIVERY_FEE);
        body.put("items", items(group).stream().map(p -> Map.of("orderProductId", p.getId())).toList());

        JsonNode created = json(create(body).andExpect(status().isCreated()));

        assertThat(created.get("claimIds")).hasSize(2);
        claimDetail(created.get("claimIds").get(0).asLong())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].focused").value(true))
                .andExpect(jsonPath("$.items[1].focused").value(false))
                .andExpect(jsonPath("$.refund.approvedAmount").value(CREAM_PRICE + SERUM_PRICE))
                .andExpect(jsonPath("$.refund.returnDeduction").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.refund.amount").value(CREAM_PRICE + SERUM_PRICE - DELIVERY_FEE));
    }

    @Test
    @DisplayName("브랜드 귀책이면 무료배송 주문이어도 차감이 없고 착불이다 — 사진을 받는다(#4)")
    void sellerFault() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(4);
        Map<String, Object> body = body(group, "DAMAGED_OR_DEFECTIVE", "뚜껑이 깨져서 왔어요", null, 0);
        body.put("imageUrls", List.of("https://img.test/a.jpg"));

        Long claimId = created(create(body).andExpect(status().isCreated()));

        claimDetail(claimId).andExpect(jsonPath("$.guide.courierPayment").value("COLLECT"))
                .andExpect(jsonPath("$.refund.returnDeduction").value(0))
                .andExpect(jsonPath("$.refund.amount").value(4 * CREAM_PRICE));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_claim_attachment WHERE claim_id = ?", Integer.class,
                claimId)).isEqualTo(1);
    }

    @Test
    @DisplayName("입력 규칙 — 브랜드 귀책의 상세 내용 누락 · 글자 수 초과 · 고를 수 없는 사유·택배사는 400, 고객 귀책의 사진은 무시(#5)")
    void inputRules() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);

        create(body(group, "DAMAGED_OR_DEFECTIVE", null, null, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLAIM_REASON_DETAIL_REQUIRED"));
        create(body(group, "CHANGE_OF_MIND", "가".repeat(251), null, null)).andExpect(status().isBadRequest());
        create(body(group, "SIZE_MISMATCH", null, null, null)).andExpect(status().isBadRequest());
        create(body(group, "CHANGE_OF_MIND", null, invoice("COUPANG", "123456789012"), null))
                .andExpect(status().isBadRequest());
        create(body(group, "CHANGE_OF_MIND", null, invoice("CJ", "---"), null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVOICE_FORMAT_INVALID"));
        // 결제가 필요한 교환인데 결제 수단이 없다.
        Map<String, Object> exchange = body(group, "CHANGE_OF_MIND", null, null, null);
        exchange.put("type", "EXCHANGE");
        create(exchange).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_claim", Integer.class)).isZero();

        Map<String, Object> withPhoto = body(group, "CHANGE_OF_MIND", null, null, null);
        withPhoto.put("imageUrls", List.of("https://img.test/a.jpg"));
        create(withPhoto).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_claim_attachment", Integer.class)).isZero();
    }

    @Test
    @DisplayName("폼에서 본 금액과 서버 계산이 다르면 409 CLAIM_AMOUNT_CHANGED(#10)")
    void amountChanged() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(4);

        create(body(group, "CHANGE_OF_MIND", null, null, 0)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_AMOUNT_CHANGED"));
        // 브랜드가 그사이 배송비를 바꿔도 주문 시점 값이라 달라지지 않는다.
        jdbc.update("UPDATE market SET default_delivery_fee = 4000 WHERE market_id = ?", brand.marketId());
        create(body(group, "CHANGE_OF_MIND", null, null, DELIVERY_FEE)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("이미 요청한 항목 · 배송완료가 아닌 주문 · 남의 주문은 받지 않는다(#20)")
    void requestGuards() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        OrderDeliveryGroup shipping = shippingGroup("111122223333");
        Users stranger = createConsumer("stranger", "박타인");
        String strangerToken = bearerToken(stranger.getUsername(), RoleType.USER, stranger.getId());

        create(body(group, "CHANGE_OF_MIND", null, null, null)).andExpect(status().isCreated());
        create(body(group, "CHANGE_OF_MIND", null, null, null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_QUANTITY_EXCEEDED"));
        create(body(shipping, "CHANGE_OF_MIND", null, null, null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_ELIGIBLE"));
        mockMvc.perform(post(CLAIMS).header(HttpHeaders.AUTHORIZATION, strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(deliveredGroup(1), "CHANGE_OF_MIND", null, null, null))))
                .andExpect(status().isNotFound());
        // 요청한 항목은 폼에서도 빠진다 — 그 항목으로는 폼이 열리지 않는다.
        getForm(items(group).get(0).getId(), "RETURN").andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ 회수 송장 · 철회 · 자동 취소

    @Test
    @DisplayName("회수 송장 — 등록하면 박스 전체가 회수중, 조회되기 전에는 수정할 수 있고 스캔된 뒤에는 409(#14)")
    void collectionInvoice() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        Map<String, Object> body = body(group, "CHANGE_OF_MIND", null, null, null);
        body.put("items", items(group).stream().map(p -> Map.of("orderProductId", p.getId())).toList());
        JsonNode created = json(create(body).andExpect(status().isCreated()));
        Long claimId = created.get("claimIds").get(0).asLong();

        putInvoice(claimId, invoice("EPOST", "6012345678901")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].phase").value("COLLECTING"))
                .andExpect(jsonPath("$.items[1].phase").value("COLLECTING"))
                .andExpect(jsonPath("$.info.collectionInvoice.carrierLabel").value("우체국택배"));
        // 회수중에서는 같은 API 가 수정이다.
        putInvoice(claimId, invoice("HANJIN", "512345678901")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.collectionInvoice.trackingNumber").value("512345678901"));
        putInvoice(claimId, invoice("COUPANG", "512345678901")).andExpect(status().isBadRequest());

        trackingEventRecorder.record(DeliveryCarrier.HANJIN, "512345678901",
                List.of(new TrackEvent(LocalDateTime.now(), "서울강남", "집화처리", 2)));
        putInvoice(claimId, invoice("CJ", "111111111111")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_INVOICE_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("철회 — 회수 대기에서만. 항목은 배송완료로 돌아가고 다시 요청할 수 있다. 회수중이면 409(#11)")
    void withdraw() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        Long claimId = created(create(body(group, "CHANGE_OF_MIND", null, null, null)));

        withdraw(claimId).andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(claimId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result").value("CANCELLED"));
        withdraw(claimId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_WITHDRAW_NOT_ALLOWED"));
        // 취소된 요청도 상세는 열린다(알림 딥링크) — 버튼은 없다.
        claimDetail(claimId).andExpect(jsonPath("$.items[0].phase").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].statusLabel").value("요청 취소"))
                .andExpect(jsonPath("$.items[0].actions", empty()))
                .andExpect(jsonPath("$.guide.visible").value(false));

        Long again = created(create(body(group, "CHANGE_OF_MIND", null, invoice("CJ", "684922013378"), null))
                .andExpect(status().isCreated()));
        withdraw(again).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_WITHDRAW_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("7일 안에 송장을 넣지 않으면 요청이 자동 취소되고 구매확정 보류가 풀린다 — 그 뒤 송장 등록은 409(#13)")
    void invoiceExpiry() throws Exception {
        OrderDeliveryGroup group = delivered(shipped(prepared(paidGroup()), "CJ", "684922013378"),
                LocalDateTime.now().minusDays(8).withNano(0));
        JsonNode created = json(create(body(group, "CHANGE_OF_MIND", null, null, null)));
        Long claimId = created.get("claimIds").get(0).asLong();
        Long requestId = created.get("requestId").asLong();
        jdbc.update("UPDATE order_claim_collection SET invoice_due_at = ? WHERE collection_id = ?",
                LocalDateTime.now().minusMinutes(1), requestId);

        // 배치가 닫기 전이라도 기한이 지난 등록은 받지 않는다.
        putInvoice(claimId, invoice("CJ", "111111111111")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_STATE_CHANGED"));
        assertThat(claimService.expireInvoice(requestId, LocalDateTime.now())).isEqualTo(1);

        claimDetail(claimId).andExpect(jsonPath("$.items[0].phase").value("CANCELLED"));
        assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
        putInvoice(claimId, invoice("CJ", "111111111111")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("남의 요청은 상세 · 송장 등록 · 철회 모두 404(#25)")
    void othersClaim() throws Exception {
        Long claimId = created(create(body(deliveredGroup(1), "CHANGE_OF_MIND", null, null, null)));
        Users stranger = createConsumer("stranger", "박타인");
        String strangerToken = bearerToken(stranger.getUsername(), RoleType.USER, stranger.getId());

        mockMvc.perform(get(CLAIMS + "/" + claimId).header(HttpHeaders.AUTHORIZATION, strangerToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
        mockMvc.perform(put(CLAIMS + "/" + claimId + "/collection-invoice")
                        .header(HttpHeaders.AUTHORIZATION, strangerToken).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoice("CJ", "684922013378"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(CLAIMS + "/" + claimId + "/withdraw").header(HttpHeaders.AUTHORIZATION, strangerToken))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM order_claim WHERE claim_id = ?", String.class, claimId))
                .isEqualTo("REQUESTED");
    }

    // ------------------------------------------------------------------ 픽스처

    @Test
    @DisplayName("수량 일부만 신청 — 3개 중 1개를 요청하면 남은 2개가 신청 가능 수량으로 남고, 초과·0은 받지 않는다. 수량을 생략하면 남은 전량이다(D3)")
    void partialQuantity() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(3);
        Long orderProductId = items(group).get(0).getId();
        String formUrl = CLAIMS + "/form?orderProductId=" + orderProductId + "&type=RETURN";
        mockMvc.perform(get(formUrl).header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(jsonPath("$.items[0].claimableQuantity").value(3));

        create(quantityBody(group, orderProductId, 4))
                .andExpect(jsonPath("$.code").value("CLAIM_QUANTITY_EXCEEDED"));
        create(quantityBody(group, orderProductId, 0)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_claim", Integer.class)).isZero();

        Long first = created(create(quantityBody(group, orderProductId, 1)).andExpect(status().isCreated()));
        assertThat(jdbc.queryForObject("SELECT quantity FROM order_claim WHERE claim_id = ?", Integer.class, first))
                .isEqualTo(1);
        claimDetail(first).andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(CREAM_PRICE));
        mockMvc.perform(get(formUrl).header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(jsonPath("$.items[0].claimableQuantity").value(2));
        create(quantityBody(group, orderProductId, 3))
                .andExpect(jsonPath("$.code").value("CLAIM_QUANTITY_EXCEEDED"));

        // 수량을 생략하면 남은 전량.
        Long second = created(create(quantityBody(group, orderProductId, null)).andExpect(status().isCreated()));
        assertThat(jdbc.queryForObject("SELECT quantity FROM order_claim WHERE claim_id = ?", Integer.class, second))
                .isEqualTo(2);
        mockMvc.perform(get(formUrl).header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_ELIGIBLE"));
    }

    private Map<String, Object> quantityBody(OrderDeliveryGroup group, Long orderProductId, Integer quantity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderProductId", orderProductId);
        item.put("quantity", quantity);
        Map<String, Object> body = body(group, "CHANGE_OF_MIND", null, null, null);
        body.put("items", List.of(item));
        return body;
    }

    private OrderDeliveryGroup deliveredGroup(int quantity) throws Exception {
        return delivered(shipped(prepared(paidGroup(creamVariant, quantity)), "CJ", newInvoice()),
                LocalDateTime.now().minusHours(1).withNano(0));
    }

    private OrderDeliveryGroup deliveredTwoItemGroup() throws Exception {
        return delivered(shipped(prepared(paidTwoItemGroup()), "CJ", newInvoice()),
                LocalDateTime.now().minusHours(1).withNano(0));
    }

    private void setFreeShippingThreshold(int threshold) {
        jdbc.update("UPDATE market SET free_shipping_threshold = ? WHERE market_id = ?", threshold, brand.marketId());
    }

    private static String newInvoice() {
        return String.valueOf(100_000_000_000L + (long) (Math.random() * 899_999_999_999L));
    }

    /** 첫 항목 하나를 반품 요청하는 본문 — 테스트가 필요한 값만 바꿔 쓴다. */
    private Map<String, Object> body(OrderDeliveryGroup group, String reason, String detail, Map<String, Object> invoice,
                                     Integer expectedFee) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("type", "RETURN");
        body.put("deliveryGroupId", group.getId());
        body.put("items", List.of(Map.of("orderProductId", items(group).get(0).getId())));
        body.put("reasonCode", reason);
        body.put("reasonDetail", detail);
        body.put("invoice", invoice);
        body.put("expectedFee", expectedFee);
        return body;
    }

    private static Map<String, Object> invoice(String carrier, String trackingNumber) {
        return Map.of("carrier", carrier, "trackingNumber", trackingNumber);
    }

    private ResultActions getForm(Long orderProductId, String type) throws Exception {
        return mockMvc.perform(get(CLAIMS + "/form?orderProductId=" + orderProductId + "&type=" + type)
                .header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private ResultActions create(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(CLAIMS).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions claimDetail(Long claimId) throws Exception {
        return mockMvc.perform(get(CLAIMS + "/" + claimId).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private ResultActions putInvoice(Long claimId, Map<String, Object> invoice) throws Exception {
        return mockMvc.perform(put(CLAIMS + "/" + claimId + "/collection-invoice")
                .header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(invoice)));
    }

    private ResultActions withdraw(Long claimId) throws Exception {
        return mockMvc.perform(post(CLAIMS + "/" + claimId + "/withdraw")
                .header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    /** 접수 응답의 첫 클레임 id. */
    private Long created(ResultActions actions) throws Exception {
        return json(actions).get("claimIds").get(0).asLong();
    }
}
