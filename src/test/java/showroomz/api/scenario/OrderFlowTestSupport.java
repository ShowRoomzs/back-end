package showroomz.api.scenario;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
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
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderFulfillmentHistoryRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.domain.order.type.CancelRequestReason;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.support.BrandFixture;
import showroomz.support.ContractOptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
 * 시나리오 E2E-A~E · X(dev/전체플로우_통합테스트_시나리오.md 3~6절)의 공통 배선 — 「결제 완료까지 실행한 뒤 분기한다」.
 *
 * <p>진행 중 공구 1건(크림 27,200 · 세럼 24,000 · 배송비 3,000 · 무료배송 50,000)과 소비자 1명은
 * {@link OrderPaymentTestSupport}가 깐다. 하위주문은 <b>실제 결제 경로로만</b> 만든다 — 하위주문번호·발송기한 스냅샷·
 * 이력이 운영과 같은 코드에서 생긴다.
 *
 * <p>사람이 없는 구간(배송 추적)은 시나리오 5-2대로 추적 반영 서비스를 직접 부르고, 시각 컬럼만 SQL로 당긴다.
 * 상태 컬럼은 SQL로 바꾸지 않는다.
 */
public abstract class OrderFlowTestSupport extends OrderPaymentTestSupport {

    protected static final String SELLER_ORDERS = "/v1/seller/orders";
    protected static final int SERUM_PRICE = 24_000;
    /** 발송기한 = 결제 시각 + 이 값(34 설계서 3-6). PAID 전이 전에 마켓에 박아 둔다. */
    protected static final int SHIPPING_LEAD_DAYS = 2;
    protected static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired protected OrderDeliveryGroupRepository deliveryGroupRepository;
    @Autowired protected OrderProductRepository orderProductRepository;
    @Autowired protected OrderCancelRequestRepository cancelRequestRepository;
    @Autowired protected OrderFulfillmentHistoryRepository fulfillmentHistoryRepository;
    @Autowired protected OrderFulfillmentService fulfillmentService;

    @BeforeEach
    void setShippingLeadDays() {
        jdbc.update("UPDATE market SET shipping_lead_days = ? WHERE market_id = ?", SHIPPING_LEAD_DAYS, brand.marketId());
    }

    // ------------------------------------------------------------------ 결제까지

    /** 결제 완료 한 건 — 주문 id · 결제 id · 그 주문의 (단일) 하위주문. */
    protected record Purchase(Long orderId, String paymentId, OrderDeliveryGroup group) {
    }

    protected Purchase purchase(ProductVariant variant, int quantity) {
        try {
            Created created = placeCardOrder(variant, quantity);
            complete(created.paymentId()).andExpect(status().isOk());
            List<OrderDeliveryGroup> groups = deliveryGroupRepository.findByOrderId(created.orderId());
            assertThat(groups).hasSize(1);
            return new Purchase(created.orderId(), created.paymentId(), reloadGroup(groups.get(0)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 신규(NEW) 하위주문 — 크림 1개 · 배송비 3,000. */
    protected OrderDeliveryGroup paidGroup() {
        return purchase(creamVariant, 1).group();
    }

    /** 크림 + 세럼 2항목 하위주문 — 장바구니 경로(바로 구매는 한 옵션뿐이다). */
    protected OrderDeliveryGroup paidGroupWithTwoItems() {
        try {
            Cart first = cartItem(creamVariant, 1);
            Cart second = cartItem(serumVariant, 1);
            Created created = created(createOrder(Map.of(
                    "idempotencyKey", newKey(),
                    "cartItemIds", List.of(first.getId(), second.getId()),
                    "deliveryMemo", "부재 시 경비실에 맡겨주세요",
                    "payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN")))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            complete(created.paymentId()).andExpect(status().isOk());
            List<OrderDeliveryGroup> groups = deliveryGroupRepository.findByOrderId(created.orderId());
            assertThat(groups).hasSize(1);
            return reloadGroup(groups.get(0));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected OrderDeliveryGroup preparingGroup() {
        return preparing(paidGroup());
    }

    protected OrderDeliveryGroup preparing(OrderDeliveryGroup group) {
        try {
            prepareStart(group.getId()).andExpect(jsonPath("$.succeeded").value(1));
            return reloadGroup(group);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected OrderDeliveryGroup shippingGroup(String trackingNumber) {
        try {
            OrderDeliveryGroup group = preparingGroup();
            registerShipment(group, "CJ", trackingNumber).andExpect(jsonPath("$.succeeded").value(1));
            return reloadGroup(group);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ 배송 추적(시나리오 5-2)

    /** 추적 배치 1회분 — 포트가 돌려준 결과({@code null}이면 이벤트 없음)를 반영 서비스에 그대로 태운다. */
    protected OrderDeliveryGroup track(OrderDeliveryGroup group, TrackSnapshot snapshot, LocalDateTime now) {
        fulfillmentService.applyTracking(reloadGroup(group), Optional.ofNullable(snapshot), now, 24, 7);
        return reloadGroup(group);
    }

    protected OrderDeliveryGroup deliveredGroup(String trackingNumber, LocalDateTime deliveredAt) {
        OrderDeliveryGroup group = shippingGroup(trackingNumber);
        return track(group, new TrackSnapshot(deliveredAt, deliveredAt, false, false), LocalDateTime.now());
    }

    protected OrderDeliveryGroup returningGroup(String trackingNumber) {
        OrderDeliveryGroup group = shippingGroup(trackingNumber);
        LocalDateTime now = LocalDateTime.now();
        return track(group, new TrackSnapshot(now, null, true, false), now);
    }

    // ------------------------------------------------------------------ 브랜드가 섞인 주문(한 결제 · 하위주문 둘)

    /** 한 결제에 이 브랜드 하위주문(크림 1)과 다른 브랜드 하위주문(상품 1)이 함께 — 다른 브랜드 셀러 토큰 · 마켓을 같이 든다. */
    protected record MixedOrder(Long orderId, String paymentId, OrderDeliveryGroup mine, OrderDeliveryGroup other,
                                Long otherMarketId, String otherToken) {
    }

    /** 다른 브랜드의 진행 중 공구(상품 1 · 재고 10 · 배송비 3,000)를 열고 크림과 함께 장바구니로 한 번에 결제한다. */
    protected MixedOrder placeMixedBrandOrder() {
        try {
            BrandFixture.Brand other = fixture.createBrand("mixed-brand@showroomz.test", "브랜드비");
            LocalDateTime now = LocalDateTime.now().withNano(0);
            GroupBuy otherGroupBuy = seed(other, creator, "브랜드비 토너 공구", now.minusDays(3), now.plusDays(4));
            moveTo(otherGroupBuy.getId(), GroupBuyStatus.IN_PROGRESS);
            Long productId = jdbc.queryForObject("SELECT product_id FROM product WHERE market_id = ?", Long.class,
                    other.marketId());
            jdbc.update("UPDATE product SET group_buy_status = 'IN_PROGRESS' WHERE product_id = ?", productId);
            jdbc.update("UPDATE market SET default_delivery_fee = ?, free_shipping_threshold = ?, shipping_lead_days = ? "
                    + "WHERE market_id = ?", DELIVERY_FEE, 50_000, SHIPPING_LEAD_DAYS, other.marketId());
            ProductVariant otherVariant = ContractOptions.variantsOf(productVariantRepository,
                    productRepository.findById(productId).orElseThrow()).get(0);
            setStock(otherVariant, 10);
            Cart mineCart = cartItem(creamVariant, 1);
            Cart otherCart = cartRepository.save(new Cart(consumer, otherVariant, otherGroupBuy, 1));
            Created created = created(createOrder(Map.of(
                    "idempotencyKey", newKey(),
                    "cartItemIds", List.of(mineCart.getId(), otherCart.getId()),
                    "payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN")))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            complete(created.paymentId()).andExpect(status().isOk());
            List<OrderDeliveryGroup> groups = deliveryGroupRepository.findByOrderId(created.orderId());
            assertThat(groups).hasSize(2);
            OrderDeliveryGroup mine = null;
            OrderDeliveryGroup otherGroup = null;
            for (OrderDeliveryGroup group : groups) {
                Optional<OrderDeliveryGroup> owned = deliveryGroupRepository.findOwned(group.getId(), brand.marketId());
                if (owned.isPresent()) {
                    mine = owned.get();
                } else {
                    otherGroup = reloadGroup(group.getId(), other.marketId());
                }
            }
            return new MixedOrder(created.orderId(), created.paymentId(), mine, otherGroup, other.marketId(),
                    sellerToken(other.seller()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 섞인 주문의 두 하위주문을 각 브랜드 토큰으로 준비 → 발송 → 배송완료(추적 반영)까지. */
    protected MixedOrder deliverBoth(MixedOrder order, LocalDateTime deliveredAt) {
        try {
            OrderDeliveryGroup mine = deliveredGroupOf(order.mine(), brand.marketId(), brandToken, deliveredAt);
            OrderDeliveryGroup other = deliveredGroupOf(order.other(), order.otherMarketId(), order.otherToken(), deliveredAt);
            return new MixedOrder(order.orderId(), order.paymentId(), mine, other, order.otherMarketId(), order.otherToken());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private OrderDeliveryGroup deliveredGroupOf(OrderDeliveryGroup group, Long marketId, String token,
                                                LocalDateTime deliveredAt) throws Exception {
        sellerPost(SELLER_ORDERS + "/prepare-start", Map.of("deliveryGroupIds", List.of(group.getId())), token)
                .andExpect(jsonPath("$.succeeded").value(1));
        sellerPost(SELLER_ORDERS + "/shipments", Map.of("rows", List.of(Map.of("deliveryGroupId", group.getId(),
                "carrier", "CJ", "trackingNumber", String.valueOf(400_000_000_000L + group.getId())))), token)
                .andExpect(jsonPath("$.succeeded").value(1));
        LocalDateTime at = deliveredAt.withNano(0);
        fulfillmentService.applyTracking(reloadGroup(group.getId(), marketId),
                Optional.of(new TrackSnapshot(at, at, false, false)), LocalDateTime.now(), 24, 7);
        return reloadGroup(group.getId(), marketId);
    }

    // ------------------------------------------------------------------ 소비자 앱 C10(범위 밖) — 테이블 계약대로 적재

    protected OrderCancelRequest seedCancelRequest(OrderDeliveryGroup group, List<OrderProduct> items) {
        return seedCancelRequest(group, items, CancelRequestReason.CHANGE_OF_MIND, null);
    }

    protected OrderCancelRequest seedCancelRequest(OrderDeliveryGroup group, List<OrderProduct> items,
                                                   CancelRequestReason reason, String reasonDetail) {
        return transactionTemplate.execute(tx -> {
            OrderDeliveryGroup attached = deliveryGroupRepository.findById(group.getId()).orElseThrow();
            OrderCancelRequest request = OrderCancelRequest.builder()
                    .deliveryGroup(attached)
                    .order(attached.getOrder())
                    .requestedBy(consumer.getId())
                    .reasonCode(reason)
                    .reasonDetail(reasonDetail)
                    .statusAtRequest(attached.getFulfillmentStatus())
                    .requestedAt(LocalDateTime.now())
                    .build();
            for (OrderProduct item : items) {
                request.addItem(OrderCancelRequestItem.builder()
                        .cancelRequest(request)
                        .orderProduct(item)
                        .quantity(item.getQuantity())
                        .refundAmount(item.getPrice() * item.getQuantity())
                        .build());
            }
            return cancelRequestRepository.save(request);
        });
    }

    // ------------------------------------------------------------------ 읽기

    protected OrderDeliveryGroup reloadGroup(OrderDeliveryGroup group) {
        return reloadGroup(group.getId(), brand.marketId());
    }

    /** 주문을 fetch join 으로 함께 올린다 — 테스트는 트랜잭션 밖이라 지연 로딩이 안 된다. */
    protected OrderDeliveryGroup reloadGroup(Long deliveryGroupId, Long marketId) {
        return deliveryGroupRepository.findOwned(deliveryGroupId, marketId).orElseThrow();
    }

    protected List<OrderProduct> itemsOf(OrderDeliveryGroup group) {
        return orderProductRepository.findByDeliveryGroupIds(List.of(group.getId()));
    }

    /** 상품명 스냅샷으로 찾는다 — 트랜잭션 밖이라 옵션(지연 로딩)을 건드리지 않는다. */
    protected OrderProduct itemOf(OrderDeliveryGroup group, Product product) {
        return itemsOf(group).stream()
                .filter(item -> item.getProductName().equals(product.getName()))
                .findFirst().orElseThrow();
    }

    /** 이력 이벤트 — 최신순(상세 모달과 같은 순서). */
    protected List<String> fulfillmentEvents(OrderDeliveryGroup group) {
        return fulfillmentHistoryRepository.findByDeliveryGroupId(group.getId()).stream()
                .map(h -> h.getEventType().name())
                .toList();
    }

    protected record RefundTask(String source, int amount, String status) {
    }

    /** 환불 큐의 작업 행 — 기록 전용 행(39 설계서 0-4)은 뺀다. {@code SellerOrderTestSupport#refundTasks}와 같은 규칙. */
    protected List<RefundTask> refundTasks(OrderDeliveryGroup group) {
        return jdbc.query("SELECT source, refund_amount, status FROM order_refund_task WHERE delivery_group_id = ? "
                        + "AND source NOT IN ('USER_CANCEL_BEFORE_PREPARE', 'CLAIM_PAYMENT_CANCELLED') "
                        + "ORDER BY refund_task_id",
                (rs, i) -> new RefundTask(rs.getString(1), rs.getInt(2), rs.getString(3)), group.getId());
    }

    // ------------------------------------------------------------------ 파트너센터 요청

    protected ResultActions sellerGet(String url) throws Exception {
        return sellerGet(url, brandToken);
    }

    protected ResultActions sellerGet(String url, String token) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, token));
    }

    protected ResultActions sellerPost(String url, Object body) throws Exception {
        return sellerPost(url, body, brandToken);
    }

    protected ResultActions sellerPost(String url, Object body, String token) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions sellerPatch(String url, Object body) throws Exception {
        return sellerPatch(url, body, brandToken);
    }

    protected ResultActions sellerPatch(String url, Object body, String token) throws Exception {
        return mockMvc.perform(patch(url).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions sellerOrders(String query) throws Exception {
        return sellerGet(SELLER_ORDERS + (query == null ? "" : "?" + query));
    }

    protected ResultActions sellerOrder(OrderDeliveryGroup group) throws Exception {
        return sellerGet(SELLER_ORDERS + "/" + group.getId());
    }

    protected ResultActions sellerSummary() throws Exception {
        return sellerGet(SELLER_ORDERS + "/summary");
    }

    protected ResultActions prepareStart(Long... deliveryGroupIds) throws Exception {
        return sellerPost(SELLER_ORDERS + "/prepare-start", Map.of("deliveryGroupIds", Arrays.asList(deliveryGroupIds)));
    }

    protected ResultActions registerShipment(OrderDeliveryGroup group, String carrier, String trackingNumber)
            throws Exception {
        return sellerPost(SELLER_ORDERS + "/shipments", Map.of("rows", List.of(
                Map.of("deliveryGroupId", group.getId(), "carrier", carrier, "trackingNumber", trackingNumber))));
    }

    protected ResultActions updateShipment(OrderDeliveryGroup group, String carrier, String trackingNumber)
            throws Exception {
        return sellerPatch(SELLER_ORDERS + "/" + group.getId() + "/shipment",
                Map.of("carrier", carrier, "trackingNumber", trackingNumber));
    }

    protected ResultActions directCancel(List<Long> ids, String reasonCode, String message) throws Exception {
        return sellerPost(SELLER_ORDERS + "/cancel",
                Map.of("deliveryGroupIds", ids, "reasonCode", reasonCode, "consumerMessage", message));
    }

    protected ResultActions approveRequest(OrderCancelRequest request) throws Exception {
        return sellerPost(SELLER_ORDERS + "/cancel-requests/" + request.getId() + "/approve", Map.of());
    }

    protected ResultActions rejectRequest(OrderCancelRequest request, String reason) throws Exception {
        return sellerPost(SELLER_ORDERS + "/cancel-requests/" + request.getId() + "/reject", Map.of("reason", reason));
    }

    protected ResultActions parseShipments(byte[] xlsx) throws Exception {
        return parseShipments(xlsx, brandToken);
    }

    protected ResultActions parseShipments(byte[] xlsx, String token) throws Exception {
        return mockMvc.perform(multipart(SELLER_ORDERS + "/shipments/parse")
                .file(new MockMultipartFile("file", "송장.xlsx", XLSX, xlsx))
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    // ------------------------------------------------------------------ 소비자 앱 요청

    protected ResultActions appOrder(Long orderId) throws Exception {
        return mockMvc.perform(get(ORDERS + "/" + orderId).header(HttpHeaders.AUTHORIZATION, consumerToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 엑셀

    /** 송장 업로드 파일 — 헤더(주문번호 · 택배사 · 송장번호) + 행. */
    protected byte[] shipmentFile(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("송장 업로드");
            Row header = sheet.createRow(0);
            List<String> headers = List.of("주문번호", "택배사", "송장번호");
            for (int c = 0; c < headers.size(); c++) {
                header.createCell(c).setCellValue(headers.get(c));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows.get(r).size(); c++) {
                    row.createCell(c).setCellValue(rows.get(r).get(c));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    protected List<List<String>> readSheet(byte[] xlsx) throws Exception {
        DataFormatter formatter = new DataFormatter();
        List<List<String>> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Row row : workbook.getSheetAt(0)) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    cells.add(formatter.formatCellValue(row.getCell(c)));
                }
                rows.add(cells);
            }
        }
        return rows;
    }
}
