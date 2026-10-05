package showroomz.api.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.claim.service.ClaimPaymentService;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 반품 · 교환 전체 사이클(dev/앱 반품 교환/반품교환_전체사이클_통합테스트_시나리오.md) — CY-0 · R1 ~ R3 · X1(배송지 → 재발송
 * 엑셀) · X2 · J1 ~ J4 · C · Q. 교환 선결제 한 바퀴의 기본형과 이벤트 발행은 {@link OrderClaimScenarioIntegrationTest}가 본다.
 * Q-6(같은 주문을 나눠 반품할 때의 차감)은 판정 대기라 두지 않는다.
 *
 * <p>결제는 실제 결제 경로(FakePaymentGateway), 판정 · 재발송은 파트너센터 API, 사람이 없는 구간(추적 · 구매확정 · 자동 취소 ·
 * 어드민 진입점)은 시나리오 7-1 대로 도메인 진입점을 부른다. 상태 컬럼은 SQL 로 바꾸지 않는다 — 시각 · 설정 컬럼만 당긴다.
 * 픽스처의 무료배송 기준은 50,000 이다(크림 1 = 유료배송 · 크림 + 세럼 = 무료배송 · 크림 3 = 무료배송).
 */
@IntegrationTest
@DisplayName("[시나리오 CY] 반품 · 교환 전체 사이클")
class OrderClaimCycleScenarioIntegrationTest extends OrderFlowTestSupport {

    private static final String USER_CLAIMS = "/v1/user/claims";
    private static final String SELLER_CLAIMS = "/v1/seller/claims";
    private static final Map<String, Object> CARD = Map.of("method", "CARD", "cardIssuer", "SHINHAN");

    @Autowired private OrderClaimService claimService;
    @Autowired private ClaimPaymentService claimPaymentService;
    @Autowired private GroupBuySalesReader salesReader;

    // ================================================================== CY-0

    @Test
    @DisplayName("[CY-0] 배송 구간 — 주문 시점 배송비 저장 · 준비 전 배송지 변경이 발주서에 실린다 · 준비 뒤 변경 409 · 배송 조회 3단계 · 배송완료에서 반품·교환이 열린다")
    void deliveryLeg() throws Exception {
        Purchase purchase = purchase(creamVariant, 1);
        OrderDeliveryGroup group = purchase.group();
        Long orderId = purchase.orderId();
        Long orderProductId = itemsOf(group).get(0).getId();
        String trackingUrl = ORDERS + "/" + orderId + "/items/" + orderProductId + "/tracking";

        // 0-1 주문 시점 배송비
        assertThat(jdbc.queryForObject("SELECT base_delivery_fee FROM order_delivery_group WHERE delivery_group_id = ?",
                Integer.class, group.getId())).isEqualTo(DELIVERY_FEE);

        // 0-2 준비 전 배송지 변경
        Long moved = newAddress("이사간", "서울 중구 세종대로 110");
        userPatch(ORDERS + "/" + orderId + "/delivery-address", Map.of("addressId", moved)).andExpect(status().isOk());
        appOrder(orderId).andExpect(jsonPath("$.addressChangeable").value(true));

        // 0-3 발송 전 배송 조회
        userGet(trackingUrl).andExpect(jsonPath("$.state").value("NOT_SHIPPED"))
                .andExpect(jsonPath("$.stageIndex").value(-1));

        // 0-4 발주서 = 준비 시작 — 바꾼 주소가 실린다
        byte[] purchaseOrder = sellerPost(SELLER_ORDERS + "/purchase-order", Map.of(
                "columns", List.of("ORDER_NUMBER", "RECIPIENT", "ADDRESS"), "startPreparation", true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        List<List<String>> sheet = readSheet(purchaseOrder);
        assertThat(sheet.get(1).get(1)).isEqualTo("이사간");
        assertThat(sheet.get(1).get(2)).startsWith("서울 중구 세종대로 110");
        assertThat(reloadGroup(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);

        // 0-5 준비 뒤 배송지 변경
        userPatch(ORDERS + "/" + orderId + "/delivery-address", Map.of("addressId", address.getId()))
                .andExpect(status().isConflict());
        appOrder(orderId).andExpect(jsonPath("$.addressChangeable").value(false));

        // 0-6 · 0-7 송장 → 이동 중
        String invoice = "300040005001";
        registerShipment(group, "CJ", invoice).andExpect(jsonPath("$.succeeded").value(1));
        appOrder(orderId).andExpect(jsonPath("$.items[0].actions[*].type", contains("TRACK_DELIVERY")));
        LocalDateTime now = LocalDateTime.now().withNano(0);
        TrackEvent picked = new TrackEvent(now.minusHours(5), "강남집배점", "집화처리", 2);
        TrackEvent moving = new TrackEvent(now.minusHours(2), "곤지암Hub", "간선하차", 3);
        track(group, new TrackSnapshot(now.minusHours(2), null, false, false, List.of(picked, moving), 3), now);
        userGet(trackingUrl).andExpect(jsonPath("$.state").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.stageIndex").value(1))
                .andExpect(jsonPath("$.scans[0].location").value("곤지암Hub"))
                .andExpect(jsonPath("$.carrier.tel").value(notNullValue()));

        // 0-8 배송완료 — 반품 · 교환이 열린다
        TrackEvent done = new TrackEvent(now.minusMinutes(30), "중구", "배송완료", 6);
        track(group, new TrackSnapshot(now.minusMinutes(30), now.minusMinutes(30), false, false,
                List.of(picked, moving, done), 6), now);
        userGet(trackingUrl).andExpect(jsonPath("$.state").value("DELIVERED"))
                .andExpect(jsonPath("$.stageIndex").value(2))
                .andExpect(jsonPath("$.scans.length()").value(3));
        userGet(ORDERS).andExpect(jsonPath("$.content[0].items[0].actions[*].type",
                contains("TRACK_DELIVERY", "RETURN_EXCHANGE")));
        appOrder(orderId).andExpect(jsonPath("$.items[0].actions[*].type",
                contains("TRACK_DELIVERY", "RETURN_REQUEST", "EXCHANGE_REQUEST")));

        // 0-9 상품 상세의 반품·교환 배송비 = 실제 계산에 쓰이는 기본 배송비
        mockMvc.perform(get("/v1/common/products/" + cream.getProductId()))
                .andExpect(jsonPath("$.delivery.returnFee").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.delivery.exchangeFee").value(DELIVERY_FEE));
        userGet(USER_CLAIMS + "/form?orderProductId=" + orderProductId + "&type=EXCHANGE")
                .andExpect(jsonPath("$.fees.consumerFault").value(DELIVERY_FEE));
    }

    // ================================================================== CY-R

    @Test
    @DisplayName("[CY-R1] 반품 한 바퀴(송장 나중에) — 요청 → 송장 등록 → 회수 이동 · 도착 → 입고 확인 → 통과(27,200) → 환불 대기도 보류 → 집행 → 반품 항목 없이 구매확정")
    void returnFullCycle() throws Exception {
        OrderDeliveryGroup group = shipAndDeliver(paidGroup(), LocalDateTime.now().minusHours(1));
        Long orderId = group.getOrder().getId();
        Long orderProductId = itemsOf(group).get(0).getId();

        // R1-1 폼 — 배송비를 내고 받은 주문이라 더 빼는 것이 없다
        userGet(USER_CLAIMS + "/form?orderProductId=" + orderProductId + "&type=RETURN")
                .andExpect(jsonPath("$.reasons.length()").value(4))
                .andExpect(jsonPath("$.carriers.length()").value(6))
                .andExpect(jsonPath("$.returnTo.address").value(notNullValue()))
                .andExpect(jsonPath("$.fees.consumerFault").value(0));

        // R1-2 요청(송장 나중에) — 회수 대기 · 할 일 · 보류 시작
        Long claimId = appReturnWithoutInvoice(group);
        assertThat(claimStatus(claimId)).isEqualTo("REQUESTED");
        userGet(USER_CLAIMS + "/" + claimId)
                .andExpect(jsonPath("$.items[0].statusLabel").value("반품 요청"))
                .andExpect(jsonPath("$.items[0].statusSub").value("회수 송장 입력 필요"))
                .andExpect(jsonPath("$.info.invoiceDueDate").value(LocalDateTime.now().plusDays(7).toLocalDate()
                        .toString()));
        appOrder(orderId).andExpect(jsonPath("$.items[0].todo.claimId").value(claimId))
                .andExpect(jsonPath("$.items[0].todo.dueDate").value(notNullValue()));
        sellerGet(SELLER_CLAIMS + "/summary").andExpect(jsonPath("$.tabCounts.COLLECT_WAIT").value(1));
        assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now().plusDays(8))).isFalse();

        // R1-3 회수 송장 등록
        String invoice = "300040005401";
        userPut(USER_CLAIMS + "/" + claimId + "/collection-invoice", Map.of("carrier", "CJ", "trackingNumber", invoice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actions[*].type", contains("TRACK_COLLECTION")));
        sellerGet(SELLER_CLAIMS + "/summary").andExpect(jsonPath("$.tabCounts.COLLECTING").value(1));

        // R1-4 아직 택배사에 잡히지 않았다
        String trackingUrl = USER_CLAIMS + "/" + claimId + "/collection-tracking";
        userGet(trackingUrl).andExpect(jsonPath("$.trackable").value(false))
                .andExpect(jsonPath("$.stageIndex").value(-1))
                .andExpect(jsonPath("$.invoiceEditable").value(true));

        // R1-5 집화 → 이동 — 스캔된 송장은 고칠 수 없다
        Long collectionId = collectionId(claimId);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        TrackEvent picked = new TrackEvent(now.minusHours(6), "강남집배점", "집화처리", 2);
        TrackEvent moving = new TrackEvent(now.minusHours(3), "곤지암Hub", "간선상차", 3);
        claimService.applyCollectionTracking(collectionId, DeliveryCarrier.CJ, invoice,
                new TrackSnapshot(picked.occurredAt(), null, false, false, List.of(picked), 2), now);
        userGet(trackingUrl).andExpect(jsonPath("$.stageIndex").value(0));
        claimService.applyCollectionTracking(collectionId, DeliveryCarrier.CJ, invoice,
                new TrackSnapshot(moving.occurredAt(), null, false, false, List.of(picked, moving), 3), now);
        userGet(trackingUrl).andExpect(jsonPath("$.stageIndex").value(1))
                .andExpect(jsonPath("$.invoiceEditable").value(false));
        userPut(USER_CLAIMS + "/" + claimId + "/collection-invoice",
                Map.of("carrier", "CJ", "trackingNumber", "300040005402"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_INVOICE_NOT_EDITABLE"));

        // R1-6 브랜드 도착
        TrackEvent arrived = new TrackEvent(now.minusHours(1), "브랜드", "배송완료", 6);
        claimService.applyCollectionTracking(collectionId, DeliveryCarrier.CJ, invoice,
                new TrackSnapshot(arrived.occurredAt(), arrived.occurredAt(), false, false,
                        List.of(picked, moving, arrived), 6), now);
        assertThat(claimStatus(claimId)).isEqualTo("ARRIVED");
        sellerGet(SELLER_CLAIMS + "/summary").andExpect(jsonPath("$.tabCounts.INSPECTION").value(1));
        sellerSummary().andExpect(jsonPath("$.actionBar.incomingCheck").value(1));
        userGet(trackingUrl).andExpect(jsonPath("$.stageIndex").value(2));

        // R1-7 입고 확인
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                .andExpect(jsonPath("$.succeeded").value(1));
        assertThat(claimRow(claimId).get("inspect_due_at")).isNotNull();
        userGet(trackingUrl).andExpect(jsonPath("$.stageIndex").value(3))
                .andExpect(jsonPath("$.events[0].source").value("BRAND"))
                .andExpect(jsonPath("$.events[0].description").value("입고 · 검수 시작"));

        // R1-8 통과 — 낸 배송비는 돌려주지 않는다
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/pass", Map.of()).andExpect(status().isOk());
        assertThat(claimStatus(claimId)).isEqualTo("REFUND_PENDING");
        assertThat(itemsOf(group).get(0).getReturnedQuantity()).isEqualTo(1);
        assertThat(refundTasks(group)).containsExactly(new RefundTask("CLAIM_RETURN_PASSED", CREAM_PRICE, "PENDING"));
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.refund.amount").value(CREAM_PRICE))
                .andExpect(jsonPath("$.refund.confirmed").value(false));
        sellerSummary().andExpect(jsonPath("$.actionBar.incomingCheck").value(0));

        // R1-9 환불 대기도 보류다
        assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now().plusDays(8))).isFalse();

        // R1-10 집행
        claimService.completeRefund(refundTaskId(group), CREAM_PRICE, 1L, LocalDateTime.now());
        assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "REFUNDED");
        assertThat(refundTasks(group)).containsExactly(new RefundTask("CLAIM_RETURN_PASSED", CREAM_PRICE, "DONE"));
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.refund.confirmed").value(true));
        appOrder(orderId).andExpect(jsonPath("$.items[0].status").value("RETURNED"))
                .andExpect(jsonPath("$.items[0].actions[*].type", contains("CLAIM_DETAIL")))
                .andExpect(jsonPath("$.items[0].actions[0].label").value("반품 상세"));

        // R1-11 닫힘 — 하위주문은 확정되지만 반품 항목은 구매확정으로 오르지 않고 판매에서 빠진다
        assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now().plusDays(8))).isTrue();
        assertThat(itemsOf(group).get(0).getStatus()).isEqualTo(OrderProductStatus.RETURNED);
        assertThat(salesReader.readSales(groupBuy.getId()).map(GroupBuySalesReader.GroupBuySales::amount).orElse(0L))
                .isZero();
    }

    @Test
    @DisplayName("[CY-R2] 무료배송 두 항목 한 박스 — 차감은 한 번, 주문 뒤 브랜드가 배송비를 올려도 주문 시점 값으로 계산 · 집행액은 두 항목에 나뉜다")
    void returnTwoItemsAtOrderTimeFee() throws Exception {
        OrderDeliveryGroup group = shipAndDeliver(paidGroupWithTwoItems(), LocalDateTime.now().minusHours(1));
        userGet(USER_CLAIMS + "/form?orderProductId=" + itemsOf(group).get(0).getId() + "&type=RETURN")
                .andExpect(jsonPath("$.fees.consumerFault").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.items.length()").value(2));

        Map<String, Object> body = claimBody(group, "RETURN", "CHANGE_OF_MIND", itemsOf(group), null);
        body.put("invoice", invoice());
        body.put("expectedFee", DELIVERY_FEE);
        List<Long> claimIds = claimIds(userPost(USER_CLAIMS, body).andExpect(status().isCreated()));
        assertThat(claimIds).hasSize(2);
        assertThat(collection(claimIds.get(0))).containsEntry("return_deduction", DELIVERY_FEE);

        // 주문 뒤 브랜드가 기본 배송비를 5,000 으로 올린다
        jdbc.update("UPDATE market SET default_delivery_fee = 5000 WHERE market_id = ?", brand.marketId());
        for (Long claimId : claimIds) {
            receiveAndPass(claimId);
        }

        int expected = CREAM_PRICE + SERUM_PRICE - DELIVERY_FEE;
        assertThat(refundTasks(group)).containsExactly(new RefundTask("CLAIM_RETURN_PASSED", expected, "PENDING"));
        claimService.completeRefund(refundTaskId(group), expected, 1L, LocalDateTime.now());

        assertThat(claimIds.stream().mapToInt(id -> jdbc.queryForObject(
                "SELECT refunded_amount FROM order_claim WHERE claim_id = ?", Integer.class, id)).sum())
                .isEqualTo(expected);
        assertThat(itemsOf(group)).extracting(OrderProduct::getStatus).containsOnly(OrderProductStatus.RETURNED);
    }

    @Test
    @DisplayName("[CY-R3] 브랜드 귀책 반품 — 무료배송 주문이어도 차감 없이 착불 · 사진이 브랜드 상세에 실리고 상품 금액 전액이 환불된다")
    void sellerFaultReturn() throws Exception {
        OrderDeliveryGroup group = shipAndDeliver(paidGroupWithTwoItems(), LocalDateTime.now().minusHours(1));
        Map<String, Object> body = claimBody(group, "RETURN", "DAMAGED_OR_DEFECTIVE", itemsOf(group), null);
        body.put("reasonDetail", "용기가 깨져서 왔어요");
        body.put("imageUrls", List.of("https://img.test/c1.jpg", "https://img.test/c2.jpg"));
        body.put("invoice", invoice());
        List<Long> claimIds = claimIds(userPost(USER_CLAIMS, body).andExpect(status().isCreated()));

        assertThat(collection(claimIds.get(0))).containsEntry("return_deduction", 0);
        userGet(USER_CLAIMS + "/" + claimIds.get(0)).andExpect(jsonPath("$.guide.courierPayment").value("COLLECT"));
        sellerGet(SELLER_CLAIMS + "/" + claimIds.get(0))
                .andExpect(jsonPath("$.consumerAttachments.length()").value(2));
        for (Long claimId : claimIds) {
            receiveAndPass(claimId);
        }
        assertThat(refundTasks(group)).containsExactly(
                new RefundTask("CLAIM_RETURN_PASSED", CREAM_PRICE + SERUM_PRICE, "PENDING"));

        // R3-2 집행 — 상품 금액 전액
        claimService.completeRefund(refundTaskId(group), CREAM_PRICE + SERUM_PRICE, 1L, LocalDateTime.now());
        assertThat(itemsOf(group)).extracting(OrderProduct::getStatus).containsOnly(OrderProductStatus.RETURNED);
        assertThat(collection(claimIds.get(0))).containsEntry("refund_amount", CREAM_PRICE + SERUM_PRICE);
    }

    // ================================================================== CY-X2

    @Test
    @DisplayName("[CY-X2] 브랜드 귀책 교환(앱) — 받은 옵션 그대로 결제 없이 접수 · 재고 −1 → 통과 → 재발송 → 도착 + 7일")
    void sellerFaultExchange() throws Exception {
        OrderDeliveryGroup group = shipAndDeliver(paidGroup(), LocalDateTime.now().minusHours(1));
        int stock = stockOf(creamVariant);
        Map<String, Object> body = claimBody(group, "EXCHANGE", "DAMAGED_OR_DEFECTIVE", itemsOf(group),
                creamVariant.getVariantId());
        body.put("reasonDetail", "펌프가 눌리지 않아요");
        body.put("expectedFee", 0);
        Long claimId = claimIds(userPost(USER_CLAIMS, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.payment").value(nullValue()))).get(0);
        assertThat(stockOf(creamVariant)).isEqualTo(stock - 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_claim_charge WHERE collection_id = ?",
                Integer.class, collectionId(claimId))).isZero();
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.exchangePayment.methodLabel").value("결제 없음"));

        userPut(USER_CLAIMS + "/" + claimId + "/collection-invoice", invoice()).andExpect(status().isOk());
        receiveAndPass(claimId);
        String reshipInvoice = "300040005501";
        registerReship(claimId, reshipInvoice);
        LocalDateTime arrivedAt = deliverReship(claimId, reshipInvoice);

        assertThat(claimRow(claimId)).containsEntry("result", "EXCHANGED");
        assertThat(stockOf(creamVariant)).isEqualTo(stock - 1);
        assertThat(fulfillmentService.confirmIfDue(group.getId(), arrivedAt.plusDays(6))).isFalse();
        assertThat(fulfillmentService.confirmIfDue(group.getId(), arrivedAt.plusDays(8))).isTrue();
    }

    // ================================================================== CY-X1

    @Test
    @DisplayName("[CY-X1] 교환받을 배송지 → 재발송 엑셀 왕복 — 요청 때 고른 곳 → 검수 전 변경 → 통과 뒤 잠금 · 엑셀 주소 = 마지막 주소 · 업로드 → 확정 → 도착")
    void exchangeAddressToReshipExcel() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup group = shipAndDeliver(paidGroup(), LocalDateTime.now().minusHours(1));
        Long second = newAddress("회사", "서울 영등포구 여의대로 24");
        Long third = newAddress("본가", "부산 해운대구 해운대로 1");

        Map<String, Object> body = claimBody(group, "EXCHANGE", "CHANGE_OF_MIND", itemsOf(group).subList(0, 1),
                refill.getVariantId());
        body.put("expectedFee", DELIVERY_FEE);
        body.put("reshipAddressId", second);
        JsonNode created = json(userPost(USER_CLAIMS, body).andExpect(status().isCreated()));
        Long claimId = created.get("claimIds").get(0).asLong();
        String paymentId = created.get("payment").get("paymentId").asText();
        fake.willReturnPaid(paymentId, DELIVERY_FEE);
        completeClaimPayment(paymentId).andExpect(jsonPath("$.claimStatus").value("REQUESTED"));
        assertThat(collection(claimId)).containsEntry("reship_recipient", "회사");

        // X1-4 검수 전 변경
        userPatch(USER_CLAIMS + "/" + claimId + "/reship-address", Map.of("addressId", third))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.reshipTo.address").value("부산 해운대구 해운대로 1"));
        userPut(USER_CLAIMS + "/" + claimId + "/collection-invoice", invoice()).andExpect(status().isOk());

        // X1-5 · X1-6 통과 → 잠금
        receiveAndPass(claimId);
        sellerSummary().andExpect(jsonPath("$.actionBar.reshipExchange").value(1));
        userPatch(USER_CLAIMS + "/" + claimId + "/reship-address", Map.of("addressId", second))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_ADDRESS_NOT_CHANGEABLE"));

        // X1-7 재발송 목록 — 마지막 주소 · 보낼 물건은 리필
        byte[] exported = sellerPost(SELLER_CLAIMS + "/reshipments/export", Map.of("columns",
                List.of("CLAIM_NUMBER", "RECIPIENT", "ADDRESS", "OPTION", "RESHIP_REASON")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        List<List<String>> sheet = readSheet(exported);
        assertThat(sheet.get(1).subList(0, 5)).containsExactly("CLM-" + claimId, "본가",
                "부산 해운대구 해운대로 1 상세", "리필", "교환 재발송");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_download_log WHERE kind = 'CLAIM_RESHIP'",
                Integer.class)).isEqualTo(1);

        // X1-8 내려받은 파일에 송장을 채워 올린다 → 확정
        String reshipInvoice = "300040005108";
        List<String[]> filled = new ArrayList<>();
        filled.add(sheet.get(0).toArray(String[]::new));
        List<String> row = new ArrayList<>(sheet.get(1));
        while (row.size() < sheet.get(0).size()) {
            row.add(""); // 빈 「택배사」 「송장번호」 칸은 셀이 없다
        }
        row.set(5, "CJ대한통운");
        row.set(6, reshipInvoice);
        filled.add(row.toArray(String[]::new));
        JsonNode parsed = json(mockMvc.perform(multipart(SELLER_CLAIMS + "/reshipments/parse")
                        .file(new MockMultipartFile("file", "reship.xlsx", XLSX, xlsxOf(filled)))
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validRows").value(1)));
        assertThat(claimStatus(claimId)).isEqualTo("RESHIP_READY");
        JsonNode parsedRow = parsed.get("rows").get(0);
        sellerPost(SELLER_CLAIMS + "/reshipments", Map.of("items", List.of(Map.of(
                "claimId", parsedRow.get("claimId").asLong(), "carrier", parsedRow.get("carrier").asText(),
                "trackingNumber", parsedRow.get("trackingNumber").asText()))))
                .andExpect(jsonPath("$.succeeded").value(1));
        userGet(USER_CLAIMS + "/" + claimId + "/reship-tracking")
                .andExpect(jsonPath("$.context").value("EXCHANGE_RESHIP"))
                .andExpect(jsonPath("$.item.optionName").value("리필"));

        // X1-10 도착
        LocalDateTime at = LocalDateTime.now().minusMinutes(5).withNano(0);
        claimService.applyReshipTracking(claimId, DeliveryCarrier.CJ, reshipInvoice,
                new TrackSnapshot(at, at, false, false, List.of(new TrackEvent(at, "해운대", "배송완료", 6)), 6),
                LocalDateTime.now());
        assertThat(claimStatus(claimId)).isEqualTo("COMPLETED");
        assertThat(reloadGroup(group).getConfirmRestartAt()).isEqualTo(at);
    }

    // ================================================================== CY-J

    @Test
    @DisplayName("[CY-J1] 전체 반려 → 재발송비 결제 → 반송 도착 — 구매확정 보류가 풀리고 타이머는 다시 서지 않으며, 반려된 수량은 다시 신청할 수 없다")
    void rejectPayReturnBack() throws Exception {
        OrderDeliveryGroup group = shipAndDeliver(paidGroup(), LocalDateTime.now().minusDays(1));
        Long orderProductId = itemsOf(group).get(0).getId();
        Long claimId = appReturn(group, itemsOf(group));
        receiveAndReject(claimId);

        assertThat(chargeStatus(claimId)).isEqualTo("PENDING");
        assertThat(refundTasks(group)).isEmpty();
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.reshipFee.state").value("PAYABLE"))
                .andExpect(jsonPath("$.items[0].rejection.evidenceImageUrls.length()").value(1));
        appOrder(group.getOrder().getId()).andExpect(jsonPath("$.items[0].todo.claimId").value(claimId));
        // 반려된 수량은 돌아오지 않는다 — 하위주문은 아직 배송완료다.
        userGet(USER_CLAIMS + "/form?orderProductId=" + orderProductId + "&type=RETURN")
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_ELIGIBLE"));

        String paymentId = json(userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments",
                Map.of("method", "CARD", "cardIssuer", "SHINHAN", "expectedAmount", DELIVERY_FEE))
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(jsonPath("$.claimStatus").value("RESHIP_READY"));
        String reshipInvoice = "300040005201";
        registerReship(claimId, reshipInvoice);
        deliverReship(claimId, reshipInvoice);

        assertThat(claimRow(claimId)).containsEntry("result", "REJECTED");
        assertThat(reloadGroup(group).getConfirmRestartAt()).isNull();
        // 거절은 보류를 풀었고 반송 도착은 타이머를 다시 세우지 않는다 — 원래 배송완료 + 7일이다.
        assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now().plusDays(6).plusHours(1)))
                .isTrue();
    }

    @Test
    @DisplayName("[CY-J2] 일부 반려 — 판정이 다 끝나야 환불액(통과 − 차감 − 재발송비)이 서고, 반려 건은 결제 없이 반송되어 도착으로 닫힌다")
    void partialRejectionToTheEnd() throws Exception {
        OrderDeliveryGroup group = shipAndDeliver(paidGroupWithTwoItems(), LocalDateTime.now().minusHours(1));
        OrderProduct creamItem = itemOf(group, cream);
        OrderProduct serumItem = itemOf(group, serum);
        List<Long> claimIds = claimIds(userPost(USER_CLAIMS, withInvoice(claimBody(group, "RETURN", "CHANGE_OF_MIND",
                List.of(creamItem, serumItem), null))).andExpect(status().isCreated()));
        Long approved = claimIds.get(0);
        Long rejected = claimIds.get(1);

        receiveAndReject(rejected);
        userGet(USER_CLAIMS + "/" + rejected).andExpect(jsonPath("$.reshipFee.state").value("WAITING"));
        userPost(USER_CLAIMS + "/" + rejected + "/reship-fee/payments", CARD).andExpect(status().isConflict());
        assertThat(refundTasks(group)).isEmpty();

        receiveAndPass(approved);
        int expected = CREAM_PRICE - DELIVERY_FEE - DELIVERY_FEE;
        assertThat(refundTasks(group)).containsExactly(new RefundTask("CLAIM_RETURN_PASSED", expected, "PENDING"));
        assertThat(chargeStatus(rejected)).isEqualTo("DEDUCTED");
        assertThat(claimStatus(rejected)).isEqualTo("RESHIP_READY");

        String reshipInvoice = "300040005202";
        registerReship(rejected, reshipInvoice);
        deliverReship(rejected, reshipInvoice);
        claimService.completeRefund(refundTaskId(group), expected, 1L, LocalDateTime.now());

        assertThat(claimRow(approved)).containsEntry("result", "REFUNDED");
        assertThat(claimRow(rejected)).containsEntry("result", "REJECTED");
        assertThat(itemOf(group, cream).getStatus()).isEqualTo(OrderProductStatus.RETURNED);
        assertThat(itemOf(group, serum).getStatus()).isEqualTo(OrderProductStatus.PAID);
    }

    @Test
    @DisplayName("[CY-J2-3] 판정 순서를 반대로(크림 통과 → 세럼 반려) 해도 환불액 · 차감 · 반송이 같다")
    void partialRejectionReversedOrder() throws Exception {
        OrderDeliveryGroup group = shipAndDeliver(paidGroupWithTwoItems(), LocalDateTime.now().minusHours(1));
        List<Long> claimIds = claimIds(userPost(USER_CLAIMS, withInvoice(claimBody(group, "RETURN", "CHANGE_OF_MIND",
                List.of(itemOf(group, cream), itemOf(group, serum)), null))).andExpect(status().isCreated()));
        Long approved = claimIds.get(0);
        Long rejected = claimIds.get(1);

        receiveAndPass(approved);
        assertThat(refundTasks(group)).isEmpty();
        receiveAndReject(rejected);

        assertThat(refundTasks(group)).containsExactly(
                new RefundTask("CLAIM_RETURN_PASSED", CREAM_PRICE - DELIVERY_FEE - DELIVERY_FEE, "PENDING"));
        assertThat(chargeStatus(rejected)).isEqualTo("DEDUCTED");
        assertThat(claimStatus(rejected)).isEqualTo("RESHIP_READY");
        String reshipInvoice = "300040005601";
        registerReship(rejected, reshipInvoice);
        deliverReship(rejected, reshipInvoice);
        assertThat(claimRow(rejected)).containsEntry("result", "REJECTED");
    }

    @Test
    @DisplayName("[CY-J3-2] 0원 교환(브랜드 귀책)이 반려되면 충당할 선결제가 없다 — 재발송비를 결제해야 반송된다")
    void zeroFeeExchangeRejectedNeedsPayment() throws Exception {
        OrderDeliveryGroup group = shipAndDeliver(paidGroup(), LocalDateTime.now().minusHours(1));
        int stock = stockOf(creamVariant);
        Map<String, Object> body = withInvoice(claimBody(group, "EXCHANGE", "DAMAGED_OR_DEFECTIVE", itemsOf(group),
                creamVariant.getVariantId()));
        body.put("reasonDetail", "펌프가 눌리지 않아요");
        Long claimId = claimIds(userPost(USER_CLAIMS, body).andExpect(status().isCreated())).get(0);

        receiveAndReject(claimId);

        assertThat(stockOf(creamVariant)).isEqualTo(stock);
        assertThat(chargeStatus(claimId)).isEqualTo("PENDING");
        assertThat(claimStatus(claimId)).isEqualTo("REJECT_HOLD");
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.reshipFee.state").value("PAYABLE"))
                .andExpect(jsonPath("$.reshipFee.amount").value(DELIVERY_FEE));
        String paymentId = json(userPost(USER_CLAIMS + "/" + claimId + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(jsonPath("$.claimStatus").value("RESHIP_READY"));
        String reshipInvoice = "300040005602";
        registerReship(claimId, reshipInvoice);
        deliverReship(claimId, reshipInvoice);
        assertThat(claimRow(claimId)).containsEntry("result", "REJECTED");
    }

    @Test
    @DisplayName("[CY-J3] 선결제한 교환이 반려되면 — 선점 재고가 돌아오고 선결제분으로 충당, 받은 옵션이 결제 없이 반송되어 도착으로 닫힌다")
    void rejectedExchangeCovered() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup group = shipAndDeliver(paidGroup(), LocalDateTime.now().minusHours(1));
        Map<String, Object> body = withInvoice(claimBody(group, "EXCHANGE", "CHANGE_OF_MIND",
                itemsOf(group).subList(0, 1), refill.getVariantId()));
        JsonNode created = json(userPost(USER_CLAIMS, body).andExpect(status().isCreated()));
        Long claimId = created.get("claimIds").get(0).asLong();
        completeClaimPayment(created.get("payment").get("paymentId").asText());
        assertThat(stockOf(refill)).isEqualTo(4);

        receiveAndReject(claimId);

        assertThat(stockOf(refill)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT status FROM order_claim_charge WHERE collection_id = ? "
                + "AND type = 'REJECT_RESHIP'", String.class, collectionId(claimId))).isEqualTo("COVERED");
        assertThat(claimStatus(claimId)).isEqualTo("RESHIP_READY");
        userGet(USER_CLAIMS + "/" + claimId).andExpect(jsonPath("$.reshipFee.state").value("COVERED"));

        String reshipInvoice = "300040005203";
        registerReship(claimId, reshipInvoice);
        userGet(USER_CLAIMS + "/" + claimId + "/reship-tracking")
                .andExpect(jsonPath("$.context").value("REJECT_RESHIP"))
                // 받은 옵션(픽스처의 기본 옵션은 옵션명이 없다) — 교환하려던 「리필」이 아니다.
                .andExpect(jsonPath("$.item.optionName").value(nullValue()));
        deliverReship(claimId, reshipInvoice);
        assertThat(claimRow(claimId)).containsEntry("result", "REJECTED");
        assertThat(stockOf(refill)).isEqualTo(5);
    }

    @Test
    @DisplayName("[CY-J4] 미결제 — 기한이 지나면 보관 안내가 붙고, 고지 2회 뒤 보관 중 결제는 반송으로 · 결제하지 않은 건은 3개월 뒤 폐기로 닫힌다")
    void unpaidStorage() throws Exception {
        Long paidLater = appReturn(shipAndDeliver(paidGroup(), LocalDateTime.now().minusHours(1)), null);
        Long disposed = appReturn(shipAndDeliver(paidGroup(), LocalDateTime.now().minusHours(1)), null);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        for (Long claimId : List.of(paidLater, disposed)) {
            receiveAndReject(claimId);
            jdbc.update("UPDATE order_claim_charge SET due_at = ? WHERE collection_id = ?", now.minusMonths(5),
                    collectionId(claimId));
        }
        userGet(USER_CLAIMS + "/" + paidLater).andExpect(jsonPath("$.reshipFee.storage.phase").value("NOTICE_PENDING"));

        for (Long claimId : List.of(paidLater, disposed)) {
            claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L, now.minusMonths(5));
            claimService.recordStorageNotice(claimId, "PUSH", FulfillmentActorType.ADMIN, 1L, now.minusMonths(4));
        }
        userGet(USER_CLAIMS + "/" + paidLater).andExpect(jsonPath("$.reshipFee.storage.phase").value("EXPIRED"))
                .andExpect(jsonPath("$.reshipFee.storage.storageDueAt").value(notNullValue()));

        // 기한이 지났어도 폐기 기록 전이면 결제가 곧 반환 요청이다.
        String paymentId = json(userPost(USER_CLAIMS + "/" + paidLater + "/reship-fee/payments", CARD)
                .andExpect(status().isOk())).get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(jsonPath("$.claimStatus").value("RESHIP_READY"));
        String reshipInvoice = "300040005204";
        registerReship(paidLater, reshipInvoice);
        deliverReship(paidLater, reshipInvoice);
        assertThat(claimRow(paidLater)).containsEntry("result", "REJECTED").containsEntry("disposed_at", null);

        claimService.disposeAfterStorage(disposed, 1L, now);
        assertThat(claimRow(disposed).get("disposed_at")).isNotNull();
        assertThat(chargeStatus(disposed)).isEqualTo("VOID");
        userGet(USER_CLAIMS + "/" + disposed).andExpect(jsonPath("$.items[0].phase").value("REJECTED_DISPOSED"));
    }

    // ================================================================== CY-C

    @Test
    @DisplayName("[CY-C] 사라지는 요청 — 철회는 회수 대기에서만 · 7일이 지났으면 철회 순간 구매확정 · 송장 미등록 7일이면 자동 취소 · 선결제 교환 철회는 결제 취소 + 재고 원복")
    void vanishingRequests() throws Exception {
        // C-1 철회 → 다시 요청할 수 있다
        OrderDeliveryGroup recent = shipAndDeliver(paidGroup(), LocalDateTime.now().minusHours(1));
        Long first = appReturnWithoutInvoice(recent);
        userPost(USER_CLAIMS + "/" + first + "/withdraw", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("CANCELLED"));
        assertThat(claimRow(first)).containsEntry("cancel_reason", "WITHDRAWN");
        userGet(USER_CLAIMS + "/form?orderProductId=" + itemsOf(recent).get(0).getId() + "&type=RETURN")
                .andExpect(status().isOk());

        // C-1 7일이 지난 주문의 철회 — 보류가 풀리는 순간 확정된다
        OrderDeliveryGroup old = shipAndDeliver(paidGroup(), LocalDateTime.now().minusDays(8));
        Long held = appReturnWithoutInvoice(old);
        assertThat(fulfillmentService.confirmIfDue(old.getId(), LocalDateTime.now())).isFalse();
        userPost(USER_CLAIMS + "/" + held + "/withdraw", Map.of()).andExpect(status().isOk());
        assertThat(reloadGroup(old).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);

        // C-2 회수 중이면 철회할 수 없다
        Long collecting = appReturn(shipAndDeliver(paidGroup(), LocalDateTime.now().minusHours(1)), null);
        userPost(USER_CLAIMS + "/" + collecting + "/withdraw", Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_WITHDRAW_NOT_ALLOWED"));

        // C-3 송장 미등록 7일 → 자동 취소 · 그 뒤 송장 등록 409
        OrderDeliveryGroup expiring = shipAndDeliver(paidGroup(), LocalDateTime.now().minusHours(1));
        Long expired = appReturnWithoutInvoice(expiring);
        claimService.expireInvoice(collectionId(expired), LocalDateTime.now().plusDays(8));
        assertThat(claimRow(expired)).containsEntry("result", "CANCELLED").containsEntry("cancel_reason",
                "INVOICE_EXPIRED");
        appOrder(expiring.getOrder().getId()).andExpect(jsonPath("$.items[0].claim").value(nullValue()));
        userPut(USER_CLAIMS + "/" + expired + "/collection-invoice", invoice()).andExpect(status().isConflict());

        // C-4 선결제 교환 철회
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup exchangeGroup = shipAndDeliver(paidGroup(), LocalDateTime.now().minusHours(1));
        JsonNode created = json(userPost(USER_CLAIMS, claimBody(exchangeGroup, "EXCHANGE", "CHANGE_OF_MIND",
                itemsOf(exchangeGroup).subList(0, 1), refill.getVariantId())).andExpect(status().isCreated()));
        Long exchangeId = created.get("claimIds").get(0).asLong();
        String paymentId = created.get("payment").get("paymentId").asText();
        completeClaimPayment(paymentId).andExpect(jsonPath("$.claimStatus").value("REQUESTED"));
        userPost(USER_CLAIMS + "/" + exchangeId + "/withdraw", Map.of()).andExpect(status().isOk());
        assertThat(stockOf(refill)).isEqualTo(5);
        assertThat(claimPaymentStatus(paymentId)).isEqualTo("CANCELLED");

        // C-5 결제하지 않은 초안 — 30분 뒤 정리 배치가 지우고 재고를 푼다. 그 뒤 도착한 결제는 자동 취소된다
        OrderDeliveryGroup draftGroup = shipAndDeliver(paidGroup(), LocalDateTime.now().minusHours(1));
        String draftPayment = json(userPost(USER_CLAIMS, claimBody(draftGroup, "EXCHANGE", "CHANGE_OF_MIND",
                itemsOf(draftGroup), refill.getVariantId())).andExpect(status().isCreated()))
                .get("payment").get("paymentId").asText();
        assertThat(stockOf(refill)).isEqualTo(4);
        fake.willReturnNotFound(draftPayment);
        claimPaymentService.reconcile(LocalDateTime.now().plusMinutes(31));
        assertThat(claimPaymentStatus(draftPayment)).isEqualTo("FAILED");
        assertThat(stockOf(refill)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_claim WHERE delivery_group_id = ?", Integer.class,
                draftGroup.getId())).isZero();
        fake.willReturnPaid(draftPayment, DELIVERY_FEE);
        webhook("wh-cycle-c5", "Transaction.Paid", draftPayment).andExpect(status().isOk());
        assertThat(claimPaymentStatus(draftPayment)).isEqualTo("CANCELLED");

        // C-6 회수 중에서 멈춘 요청 — 운영자 직권 종결이 요청 취소로 닫고 보류를 푼다
        OrderDeliveryGroup stuck = shipAndDeliver(paidGroup(), LocalDateTime.now().minusDays(8));
        Long stuckClaim = appReturn(stuck, null);
        assertThat(fulfillmentService.confirmIfDue(stuck.getId(), LocalDateTime.now())).isFalse();
        claimService.closeByAdmin(stuckClaim, 1L, "물건이 도착하지 않음", LocalDateTime.now());
        assertThat(claimRow(stuckClaim)).containsEntry("result", "CANCELLED").containsEntry("cancel_reason", "ADMIN");
        assertThat(reloadGroup(stuck).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
    }

    // ================================================================== CY-Q

    @Test
    @DisplayName("[CY-Q] 수량 일부 — 3개 중 1개 반품(환불 24,200) → 남은 2개 교환 → 새 상품 도착 + 7일 구매확정 · 판매 집계 2개")
    void partialQuantityCycle() throws Exception {
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup group = shipAndDeliver(purchase(creamVariant, 3).group(), LocalDateTime.now().minusHours(1));
        OrderProduct item = itemsOf(group).get(0);
        String formUrl = USER_CLAIMS + "/form?orderProductId=" + item.getId() + "&type=RETURN";
        userGet(formUrl).andExpect(jsonPath("$.items[0].claimableQuantity").value(3));

        // Q-2 1개 반품 → 환불
        Map<String, Object> returnBody = withInvoice(claimBody(group, "RETURN", "CHANGE_OF_MIND", List.of(item), null));
        quantity(returnBody, 1);
        Long returnId = claimIds(userPost(USER_CLAIMS, returnBody).andExpect(status().isCreated())).get(0);
        receiveAndPass(returnId);
        assertThat(refundTasks(group)).containsExactly(
                new RefundTask("CLAIM_RETURN_PASSED", CREAM_PRICE - DELIVERY_FEE, "PENDING"));
        claimService.completeRefund(refundTaskId(group), CREAM_PRICE - DELIVERY_FEE, 1L, LocalDateTime.now());
        appOrder(group.getOrder().getId()).andExpect(jsonPath("$.items[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].returnedQuantity").value(1))
                .andExpect(jsonPath("$.items[0].actions[*].type", hasItem("RETURN_REQUEST")));

        // Q-3 · Q-4 남은 2개 교환 — 재고 2개 · 재발송비 3,000
        userGet(formUrl).andExpect(jsonPath("$.items[0].claimableQuantity").value(2));
        Map<String, Object> exchangeBody = withInvoice(claimBody(group, "EXCHANGE", "CHANGE_OF_MIND", List.of(item),
                refill.getVariantId()));
        quantity(exchangeBody, 2);
        exchangeBody.put("expectedFee", DELIVERY_FEE);
        JsonNode created = json(userPost(USER_CLAIMS, exchangeBody).andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment.totalAmount").value(DELIVERY_FEE)));
        Long exchangeId = created.get("claimIds").get(0).asLong();
        completeClaimPayment(created.get("payment").get("paymentId").asText());
        assertThat(stockOf(refill)).isEqualTo(3);

        // Q-5 통과 → 재발송 → 도착 → 7일
        receiveAndPass(exchangeId);
        String reshipInvoice = "300040005301";
        registerReship(exchangeId, reshipInvoice);
        LocalDateTime arrivedAt = deliverReship(exchangeId, reshipInvoice);
        assertThat(fulfillmentService.confirmIfDue(group.getId(), arrivedAt.plusDays(6))).isFalse();
        assertThat(fulfillmentService.confirmIfDue(group.getId(), arrivedAt.plusDays(8))).isTrue();
        assertThat(itemsOf(group).get(0).getStatus()).isEqualTo(OrderProductStatus.PURCHASE_CONFIRMED);
        assertThat(salesReader.readSales(groupBuy.getId()).orElseThrow().itemQuantities()).singleElement()
                .satisfies(quantity -> assertThat(quantity.quantity()).isEqualTo(2));
    }

    // ================================================================== 보조

    private OrderDeliveryGroup shipAndDeliver(OrderDeliveryGroup paid, LocalDateTime deliveredAt) throws Exception {
        OrderDeliveryGroup group = preparing(paid);
        registerShipment(group, "CJ", String.valueOf(100_000_000_000L + (long) (Math.random() * 899_999_999_999L)))
                .andExpect(jsonPath("$.succeeded").value(1));
        LocalDateTime at = deliveredAt.withNano(0);
        return track(group, new TrackSnapshot(at, at, false, false), LocalDateTime.now());
    }

    private Map<String, Object> claimBody(OrderDeliveryGroup group, String type, String reason,
                                          List<OrderProduct> products, Long exchangeVariantId) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (OrderProduct product : products) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderProductId", product.getId());
            item.put("exchangeVariantId", exchangeVariantId);
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("type", type);
        body.put("deliveryGroupId", group.getId());
        body.put("items", items);
        body.put("reasonCode", reason);
        body.put("payment", CARD);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static void quantity(Map<String, Object> body, int quantity) {
        ((List<Map<String, Object>>) body.get("items")).get(0).put("quantity", quantity);
    }

    private Map<String, Object> withInvoice(Map<String, Object> body) {
        body.put("invoice", invoice());
        return body;
    }

    private static Map<String, Object> invoice() {
        return Map.of("carrier", "CJ",
                "trackingNumber", String.valueOf(200_000_000_000L + (long) (Math.random() * 799_999_999_999L)));
    }

    /** 고객 귀책 반품 · 송장 동시 — 항목을 주지 않으면 하위주문 전 항목. */
    private Long appReturn(OrderDeliveryGroup group, List<OrderProduct> products) throws Exception {
        return claimIds(userPost(USER_CLAIMS, withInvoice(claimBody(group, "RETURN", "CHANGE_OF_MIND",
                products == null ? itemsOf(group) : products, null))).andExpect(status().isCreated())).get(0);
    }

    private Long appReturnWithoutInvoice(OrderDeliveryGroup group) throws Exception {
        return claimIds(userPost(USER_CLAIMS, claimBody(group, "RETURN", "CHANGE_OF_MIND", itemsOf(group), null))
                .andExpect(status().isCreated())).get(0);
    }

    private void receiveAndPass(Long claimId) throws Exception {
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                .andExpect(jsonPath("$.succeeded").value(1));
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/pass", Map.of()).andExpect(status().isOk());
    }

    private void receiveAndReject(Long claimId) throws Exception {
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                .andExpect(jsonPath("$.succeeded").value(1));
        sellerPost(SELLER_CLAIMS + "/" + claimId + "/inspection/reject", Map.of("reasonCode", "USED",
                "detail", "사용 흔적이 있습니다.", "evidenceImageUrls", List.of("https://img.test/e1.jpg")))
                .andExpect(status().isOk());
    }

    private void registerReship(Long claimId, String trackingNumber) throws Exception {
        sellerPost(SELLER_CLAIMS + "/reshipments", Map.of("items", List.of(Map.of(
                "claimId", claimId, "carrier", "CJ", "trackingNumber", trackingNumber))))
                .andExpect(jsonPath("$.succeeded").value(1));
        assertThat(claimStatus(claimId)).isEqualTo("RESHIPPING");
    }

    /** @return 도착 시각 */
    private LocalDateTime deliverReship(Long claimId, String trackingNumber) {
        LocalDateTime at = LocalDateTime.now().minusMinutes(10).withNano(0);
        claimService.applyReshipTracking(claimId, DeliveryCarrier.CJ, trackingNumber,
                new TrackSnapshot(at, at, false, false, List.of(new TrackEvent(at, "강남", "배송완료", 6)), 6),
                LocalDateTime.now());
        assertThat(claimStatus(claimId)).isEqualTo("COMPLETED");
        return at;
    }

    private Long newAddress(String recipient, String addressLine) throws Exception {
        return json(userPost("/v1/user/delivery-addresses", Map.of("recipientName", recipient, "zipCode", "04524",
                "address", addressLine, "detailAddress", "상세", "phoneNumber", "010-2222-3333"))
                .andExpect(status().is2xxSuccessful())).get("id").asLong();
    }

    private ProductVariant addSamePriceVariant(ProductVariant base, int stock) {
        Long productId = jdbc.queryForObject("SELECT product_id FROM product_variant WHERE variant_id = ?",
                Long.class, base.getVariantId());
        Product product = productVariantRepository.findByProductIdsOrderByVariantId(List.of(productId)).get(0)
                .getProduct();
        ProductVariant added = productVariantRepository.save(new ProductVariant(product, "리필",
                base.getRegularPrice(), base.getSalePrice(), stock, false));
        jdbc.update("INSERT INTO contract_item_option (contract_item_id, variant_id, variant_name, regular_price, "
                + "min_quantity, sort_order) SELECT contract_item_id, ?, '리필', regular_price, 0, 1 "
                + "FROM contract_item_option WHERE variant_id = ?", added.getVariantId(), base.getVariantId());
        return added;
    }

    private static byte[] xlsxOf(List<String[]> rows) throws Exception {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet();
            for (int r = 0; r < rows.size(); r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r);
                for (int c = 0; c < rows.get(r).length; c++) {
                    row.createCell(c).setCellValue(Objects.toString(rows.get(r)[c], ""));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private List<Long> claimIds(ResultActions actions) throws Exception {
        List<Long> ids = new ArrayList<>();
        json(actions).get("claimIds").forEach(node -> ids.add(node.asLong()));
        return ids;
    }

    private Long refundTaskId(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT refund_task_id FROM order_refund_task WHERE delivery_group_id = ? "
                + "AND status = 'PENDING'", Long.class, group.getId());
    }

    private Long collectionId(Long claimId) {
        return jdbc.queryForObject("SELECT collection_id FROM order_claim WHERE claim_id = ?", Long.class, claimId);
    }

    private Map<String, Object> collection(Long claimId) {
        return jdbc.queryForMap("SELECT * FROM order_claim_collection WHERE collection_id = ?", collectionId(claimId));
    }

    private Map<String, Object> claimRow(Long claimId) {
        return new HashMap<>(jdbc.queryForMap("SELECT * FROM order_claim WHERE claim_id = ?", claimId));
    }

    private String claimStatus(Long claimId) {
        return jdbc.queryForObject("SELECT status FROM order_claim WHERE claim_id = ?", String.class, claimId);
    }

    private String chargeStatus(Long claimId) {
        return jdbc.queryForObject("SELECT status FROM order_claim_charge WHERE collection_id = ? "
                + "AND type = 'REJECT_RESHIP'", String.class, collectionId(claimId));
    }

    private String claimPaymentStatus(String paymentId) {
        return jdbc.queryForObject("SELECT status FROM order_claim_payment WHERE payment_id = ?", String.class,
                paymentId);
    }

    private ResultActions completeClaimPayment(String paymentId) throws Exception {
        return mockMvc.perform(post(USER_CLAIMS + "/payments/" + paymentId + "/complete")
                .header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private ResultActions userGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private ResultActions userPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions userPatch(String url, Object body) throws Exception {
        return mockMvc.perform(patch(url).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private ResultActions userPut(String url, Object body) throws Exception {
        return mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
