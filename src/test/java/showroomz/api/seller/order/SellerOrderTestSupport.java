package showroomz.api.seller.order;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.order.OrderPaymentTestSupport;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderCancelRequestItem;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderFulfillmentHistory;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderFulfillmentHistoryRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.domain.order.type.CancelRequestReason;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.support.BrandFixture;
import showroomz.support.ContractOptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 주문 관리(34 설계서) 통합 테스트의 공통 배선.
 *
 * <p>하위주문은 <b>실제 결제 경로</b>(FakePaymentGateway → PAID 전이 훅)로만 태어나고, 이후 상태도 브랜드 API·도메인 서비스로만
 * 옮긴다 — 상태 컬럼을 SQL 로 바꾸면 이력·부수 효과가 빠진다(시나리오 문서 5-2). SQL 은 <b>시각 소급</b>(결제일·발송기한·
 * 송장 등록 시각)과, 범위 밖 서피스(소비자 앱 C10 취소 요청)의 테이블 계약 적재에만 쓴다.
 *
 * <p>무료배송 문턱을 100,000 으로 올려 2항목 주문(51,200)에도 배송비 3,000 이 붙게 한다 — 부분·전체 취소의 배송비 규칙(1-10)을
 * 0원이 아닌 배송비로 검증하기 위해서다.
 */
public abstract class SellerOrderTestSupport extends OrderPaymentTestSupport {

    protected static final String SELLER_ORDERS = "/v1/seller/orders";
    protected static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    protected static final int SHIPPING_LEAD_DAYS = 2;
    protected static final int SERUM_PRICE = 24_000;
    protected static final int FREE_SHIPPING_THRESHOLD = 100_000;

    @Autowired protected OrderDeliveryGroupRepository deliveryGroupRepository;
    @Autowired protected OrderProductRepository orderProductRepository;
    @Autowired protected OrderCancelRequestRepository cancelRequestRepository;
    @Autowired protected OrderFulfillmentHistoryRepository historyRepository;
    @Autowired protected OrderFulfillmentService fulfillmentService;

    @BeforeEach
    void setUpSellerOrderFixtures() {
        // 발송기한 스냅샷의 출처(설계서 3-6) — PAID 전이 전에 박아 둔다.
        jdbc.update("UPDATE market SET shipping_lead_days = ?, free_shipping_threshold = ? WHERE market_id = ?",
                SHIPPING_LEAD_DAYS, FREE_SHIPPING_THRESHOLD, brand.marketId());
    }

    // ------------------------------------------------------------------ 하위주문 — 상태별 출발점

    /** 크림 1개 결제 완료 → 하위주문 NEW(27,200 + 배송비 3,000). */
    protected OrderDeliveryGroup paidGroup() throws Exception {
        return paidGroup(creamVariant, 1);
    }

    protected OrderDeliveryGroup paidGroup(ProductVariant variant, int quantity) throws Exception {
        Created created = placeCardOrder(variant, quantity);
        complete(created.paymentId()).andExpect(status().isOk());
        return onlyGroupOf(created.orderId());
    }

    /** 크림 1 + 세럼 1 — 한 하위주문에 항목 2줄(51,200 + 배송비 3,000). 부분 취소 검증의 출발점. */
    protected OrderDeliveryGroup paidTwoItemGroup() throws Exception {
        Cart cream = cartItem(creamVariant, 1);
        Cart serum = cartItem(serumVariant, 1);
        Created created = created(createOrder(Map.of(
                "idempotencyKey", newKey(),
                "cartItemIds", List.of(cream.getId(), serum.getId()),
                "deliveryMemo", "문 앞에 놓아주세요",
                "payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        complete(created.paymentId()).andExpect(status().isOk());
        return onlyGroupOf(created.orderId());
    }

    protected OrderDeliveryGroup preparingGroup() throws Exception {
        return prepared(paidGroup());
    }

    protected OrderDeliveryGroup preparingTwoItemGroup() throws Exception {
        return prepared(paidTwoItemGroup());
    }

    protected OrderDeliveryGroup prepared(OrderDeliveryGroup group) throws Exception {
        prepareStart(group.getId()).andExpect(status().isOk()).andExpect(jsonPath("$.succeeded").value(1));
        return reload(group);
    }

    protected OrderDeliveryGroup shippingGroup(String trackingNumber) throws Exception {
        return shipped(preparingGroup(), "CJ", trackingNumber);
    }

    protected OrderDeliveryGroup shipped(OrderDeliveryGroup group, String carrier, String trackingNumber) throws Exception {
        registerShipment(group.getId(), carrier, trackingNumber)
                .andExpect(status().isOk()).andExpect(jsonPath("$.succeeded").value(1));
        return reload(group);
    }

    /** 배송완료 — 추적 스텁 기간이라 판정 로직(applyTracking)을 직접 태운다(시나리오 문서 5-2). */
    protected OrderDeliveryGroup delivered(OrderDeliveryGroup shippingGroup, LocalDateTime deliveredAt) {
        track(shippingGroup, new TrackSnapshot(deliveredAt, deliveredAt, false, false), batchNow());
        return reload(shippingGroup);
    }

    protected OrderDeliveryGroup returning(OrderDeliveryGroup shippingGroup) {
        LocalDateTime now = batchNow();
        track(shippingGroup, new TrackSnapshot(now.minusHours(1), null, true, false), now);
        return reload(shippingGroup);
    }

    protected void track(OrderDeliveryGroup group, TrackSnapshot snapshot, LocalDateTime now) {
        fulfillmentService.applyTracking(reload(group), Optional.ofNullable(snapshot), now, 24, 7);
    }

    /** 소비자 앱 C10 은 범위 밖 — 테이블 계약대로 직접 심는다(34 설계서 1-5). 기본은 전 항목 · 단순 변심. */
    protected OrderCancelRequest seedCancelRequest(OrderDeliveryGroup group) {
        return seedCancelRequest(group, items(group), CancelRequestReason.CHANGE_OF_MIND, null,
                LocalDateTime.now().withNano(0));
    }

    protected OrderCancelRequest seedCancelRequest(OrderDeliveryGroup group, List<OrderProduct> targets,
                                                   CancelRequestReason reason, String reasonDetail,
                                                   LocalDateTime requestedAt) {
        return transactionTemplate.execute(tx -> {
            OrderDeliveryGroup attached = deliveryGroupRepository.findById(group.getId()).orElseThrow();
            OrderCancelRequest request = OrderCancelRequest.builder()
                    .deliveryGroup(attached)
                    .order(attached.getOrder())
                    .requestedBy(consumer.getId())
                    .reasonCode(reason)
                    .reasonDetail(reasonDetail)
                    .statusAtRequest(attached.getFulfillmentStatus())
                    .requestedAt(requestedAt)
                    .build();
            for (OrderProduct item : targets) {
                request.addItem(OrderCancelRequestItem.builder()
                        .cancelRequest(request)
                        .orderProduct(orderProductRepository.getReferenceById(item.getId()))
                        .quantity(item.getQuantity())
                        .refundAmount(item.getPrice() * item.getQuantity())
                        .build());
            }
            return cancelRequestRepository.save(request);
        });
    }

    // ------------------------------------------------------------------ 조회 · 검증 보조

    protected OrderDeliveryGroup onlyGroupOf(Long orderId) {
        List<OrderDeliveryGroup> groups = deliveryGroupRepository.findByOrderId(orderId);
        assertThat(groups).hasSize(1);
        return reload(groups.get(0));
    }

    /** 주문을 fetch join 으로 함께 올린다 — 테스트는 트랜잭션 밖이라 지연 로딩이 안 된다. */
    protected OrderDeliveryGroup reload(OrderDeliveryGroup group) {
        return deliveryGroupRepository.findOwned(group.getId(), brand.marketId()).orElseThrow();
    }

    protected List<OrderProduct> items(OrderDeliveryGroup group) {
        return orderProductRepository.findByDeliveryGroupIds(List.of(group.getId()));
    }

    protected OrderProduct itemOf(OrderDeliveryGroup group, ProductVariant variant) {
        return items(group).stream()
                .filter(item -> item.getVariantId().equals(variant.getVariantId()))
                .findFirst().orElseThrow();
    }

    /** 처리 이력 — 최신순(상세 모달과 같은 정렬). */
    protected List<OrderFulfillmentHistory> history(OrderDeliveryGroup group) {
        return historyRepository.findByDeliveryGroupId(group.getId());
    }

    protected long historyCount(OrderDeliveryGroup group, FulfillmentEventType eventType) {
        return history(group).stream().filter(h -> h.getEventType() == eventType).count();
    }

    protected List<Map<String, Object>> refundTasks(OrderDeliveryGroup group) {
        return jdbc.queryForList("SELECT source, source_id, refund_amount, status FROM order_refund_task "
                + "WHERE delivery_group_id = ? ORDER BY refund_task_id", group.getId());
    }

    protected String orderNumberOf(OrderDeliveryGroup group) {
        return group.getOrder().getOrderNumber();
    }

    // ------------------------------------------------------------------ 시각

    /**
     * 배치(추적·구매확정)에 넘길 실행 시각 — 앞선 API 요청보다 늦어야 이력 최신순이 실제 순서와 맞는다.
     * 초 단위로 잘라야 DB 왕복 뒤 equals 비교가 되므로, 자른 만큼 1초를 더해 「직전 요청과 같은 초」를 피한다.
     */
    protected static LocalDateTime batchNow() {
        return LocalDateTime.now().withNano(0).plusSeconds(1);
    }

    /** JacksonConfig 의 LocalDateTime 직렬화 형식({@code yyyy-MM-dd'T'HH:mm:ss'Z'}) — 응답 JSON 과 비교할 때. */
    protected static String jsonTime(LocalDateTime value) {
        return value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }

    protected void backdatePaidAt(OrderDeliveryGroup group, LocalDateTime paidAt) {
        jdbc.update("UPDATE orders SET paid_at = ? WHERE order_id = ?", paidAt, group.getOrder().getId());
    }

    protected void backdateShipDueAt(OrderDeliveryGroup group, LocalDateTime shipDueAt) {
        jdbc.update("UPDATE order_delivery_group SET ship_due_at = ? WHERE delivery_group_id = ?",
                shipDueAt, group.getId());
    }

    protected void backdateShippedAt(OrderDeliveryGroup group, LocalDateTime shippedAt) {
        jdbc.update("UPDATE order_delivery_group SET shipped_at = ? WHERE delivery_group_id = ?",
                shippedAt, group.getId());
    }

    /**
     * 한 주문 · 같은 브랜드의 하위주문 2번째 — 결제 경로로 만들려면 같은 브랜드 공구 2건이 동시에 진행돼야 해서,
     * 그룹 행만 덧붙이고 PAID 훅과 같은 전이(activate)로 NEW 에 올린다.
     */
    protected OrderDeliveryGroup addSecondGroup(OrderDeliveryGroup first) {
        Long id = transactionTemplate.execute(tx -> {
            OrderDeliveryGroup attached = deliveryGroupRepository.findById(first.getId()).orElseThrow();
            OrderDeliveryGroup second = deliveryGroupRepository.save(OrderDeliveryGroup.builder()
                    .order(attached.getOrder())
                    .market(attached.getMarket())
                    .productTotal(0)
                    .deliveryFee(0)
                    .freeShippingApplied(false)
                    .marketName(attached.getMarketName())
                    .build());
            deliveryGroupRepository.activate(second.getId(), orderNumberOf(first) + "-02", first.getShipDueAt());
            return second.getId();
        });
        return deliveryGroupRepository.findOwned(id, brand.marketId()).orElseThrow();
    }

    // ------------------------------------------------------------------ 다른 브랜드

    protected BrandFixture.Brand otherBrand() {
        return fixture.createBrand("other-brand@showroomz.test", "타브랜드");
    }

    /** 다른 브랜드의 진행 중 공구 1건(상품 1종 · 재고 10 · 발송기한 D+3) — 그 공구의 옵션을 돌려준다. */
    protected ProductVariant openOtherBrandGroupBuy(BrandFixture.Brand other) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        GroupBuy otherGroupBuy = seed(other, creator, "타브랜드 공구", now.minusDays(3), now.plusDays(4));
        moveTo(otherGroupBuy.getId(), GroupBuyStatus.IN_PROGRESS);
        Product product = productRepository.findAll().stream()
                .filter(p -> p.getName().equals("상품 타브랜드 공구")).findFirst().orElseThrow();
        jdbc.update("UPDATE product SET group_buy_status = 'IN_PROGRESS' WHERE product_id = ?", product.getProductId());
        jdbc.update("UPDATE market SET default_delivery_fee = ?, free_shipping_threshold = ?, shipping_lead_days = ? "
                + "WHERE market_id = ?", DELIVERY_FEE, FREE_SHIPPING_THRESHOLD, 3, other.marketId());
        ProductVariant variant = ContractOptions.variantsOf(productVariantRepository, product).get(0);
        setStock(variant, 10);
        return variant;
    }

    /** 다른 브랜드 공구 옵션을 같은 소비자가 바로 구매·결제 → 그 브랜드의 하위주문 NEW(그 브랜드 마켓으로 다시 읽는다). */
    protected OrderDeliveryGroup paidOtherBrandGroup(BrandFixture.Brand other, ProductVariant otherVariant)
            throws Exception {
        GroupBuy otherGroupBuy = groupBuyRepository.findAll().stream()
                .filter(gb -> !gb.getId().equals(groupBuy.getId())).findFirst().orElseThrow();
        Created created = created(createOrder(Map.of("idempotencyKey", newKey(),
                "direct", Map.of("variantId", otherVariant.getVariantId(), "quantity", 1,
                        "groupBuyId", otherGroupBuy.getId()),
                "payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        complete(created.paymentId()).andExpect(status().isOk());
        Long id = deliveryGroupRepository.findByOrderId(created.orderId()).get(0).getId();
        return deliveryGroupRepository.findOwned(id, other.marketId()).orElseThrow();
    }

    // ------------------------------------------------------------------ 요청

    protected ResultActions prepareStart(Long... deliveryGroupIds) throws Exception {
        return sellerPost(SELLER_ORDERS + "/prepare-start", Map.of("deliveryGroupIds", List.of(deliveryGroupIds)));
    }

    protected ResultActions registerShipment(Long deliveryGroupId, String carrier, String trackingNumber)
            throws Exception {
        return registerShipments(List.of(shipmentRow(deliveryGroupId, carrier, trackingNumber)));
    }

    protected ResultActions registerShipments(List<Map<String, Object>> rows) throws Exception {
        return sellerPost(SELLER_ORDERS + "/shipments", Map.of("rows", rows));
    }

    protected Map<String, Object> shipmentRow(Long deliveryGroupId, String carrier, String trackingNumber) {
        return Map.of("deliveryGroupId", deliveryGroupId, "carrier", carrier, "trackingNumber", trackingNumber);
    }

    protected ResultActions updateShipment(Long deliveryGroupId, String carrier, String trackingNumber)
            throws Exception {
        return sellerPatch(SELLER_ORDERS + "/" + deliveryGroupId + "/shipment",
                Map.of("carrier", carrier, "trackingNumber", trackingNumber));
    }

    protected ResultActions directCancel(List<Long> deliveryGroupIds, String reasonCode, String consumerMessage)
            throws Exception {
        return sellerPost(SELLER_ORDERS + "/cancel", Map.of(
                "deliveryGroupIds", deliveryGroupIds, "reasonCode", reasonCode, "consumerMessage", consumerMessage));
    }

    protected ResultActions approve(Long cancelRequestId) throws Exception {
        return sellerPost(SELLER_ORDERS + "/cancel-requests/" + cancelRequestId + "/approve", Map.of());
    }

    protected ResultActions reject(Long cancelRequestId, String reason) throws Exception {
        return sellerPost(SELLER_ORDERS + "/cancel-requests/" + cancelRequestId + "/reject", Map.of("reason", reason));
    }

    protected ResultActions orderDetail(Long deliveryGroupId) throws Exception {
        return sellerGet(SELLER_ORDERS + "/" + deliveryGroupId);
    }

    protected ResultActions sellerGet(String url) throws Exception {
        return sellerGet(brandToken, url);
    }

    protected ResultActions sellerGet(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, token));
    }

    protected ResultActions sellerPost(String url, Object body) throws Exception {
        return sellerPost(brandToken, url, body);
    }

    protected ResultActions sellerPost(String token, String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : toJson(body)));
    }

    protected ResultActions sellerPatch(String url, Object body) throws Exception {
        return sellerPatch(brandToken, url, body);
    }

    protected ResultActions sellerPatch(String token, String url, Object body) throws Exception {
        return mockMvc.perform(patch(url).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions uploadShipments(String token, byte[] xlsx) throws Exception {
        return mockMvc.perform(multipart(SELLER_ORDERS + "/shipments/parse")
                .file(new MockMultipartFile("file", "shipments.xlsx", XLSX, xlsx))
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    // ------------------------------------------------------------------ 엑셀

    /** 송장 업로드 파일 — 헤더(주문번호 · 택배사 · 송장번호) + 문자열 셀 행. {@code null} 행은 빈 행으로 둔다. */
    protected static byte[] shipmentXlsx(List<String[]> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("송장 업로드");
            Row header = sheet.createRow(0);
            String[] headers = {"주문번호", "택배사", "송장번호"};
            for (int c = 0; c < headers.length; c++) {
                header.createCell(c).setCellValue(headers[c]);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                String[] values = rows.get(r);
                if (values == null) {
                    continue;
                }
                for (int c = 0; c < values.length; c++) {
                    if (values[c] != null) {
                        row.createCell(c).setCellValue(values[c]);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 응답 xlsx 의 첫 시트를 표시 문자열 표로 읽는다(헤더 포함). */
    protected static List<List<String>> readSheet(byte[] xlsx) {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> table = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    cells.add(formatter.formatCellValue(row.getCell(c)));
                }
                table.add(cells);
            }
            return table;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
