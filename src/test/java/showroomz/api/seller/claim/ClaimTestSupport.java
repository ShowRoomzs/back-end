package showroomz.api.seller.claim;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.claim.service.ClaimPaymentService;
import showroomz.api.seller.order.SellerOrderTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.service.OrderClaimService.Invoice;
import showroomz.domain.order.service.OrderClaimService.Item;
import showroomz.domain.order.service.OrderClaimService.RequestCommand;
import showroomz.domain.order.service.OrderClaimService.RequestResult;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.support.BrandFixture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 반품 · 교환 보강 테스트(dev/앱 반품 교환/반품교환_테스트_보강_시나리오.md)의 공통 배선 — 배송완료 하위주문 · 클레임 신청 ·
 * 판정 · 추적 결과 · 엑셀을 짧게 만든다. 판정 · 재발송은 실제 API 로, 받는 API 가 없는 구간(신청 원장 · 추적 반영 ·
 * 어드민 진입점)은 도메인 진입점으로 간다. 상태 컬럼은 SQL 로 바꾸지 않는다.
 */
public abstract class ClaimTestSupport extends SellerOrderTestSupport {

    protected static final String SELLER_CLAIMS = "/v1/seller/claims";
    protected static final String USER_CLAIMS = "/v1/user/claims";

    @Autowired protected OrderClaimService claimService;
    @Autowired protected ClaimPaymentService claimPaymentService;

    // ------------------------------------------------------------------ 배송완료 하위주문

    /** 한 옵션 · 수량 지정 · 1시간 전 배송완료. 무료배송 기준은 100,000 이다 — 낮추려면 {@link #freeShippingFrom}. */
    protected OrderDeliveryGroup deliveredGroup(ProductVariant variant, int quantity) throws Exception {
        return deliveredGroup(variant, quantity, LocalDateTime.now().minusHours(1));
    }

    protected OrderDeliveryGroup deliveredGroup(ProductVariant variant, int quantity, LocalDateTime deliveredAt)
            throws Exception {
        return delivered(shipped(prepared(paidGroup(variant, quantity)), "CJ", newInvoice()), deliveredAt.withNano(0));
    }

    /** 크림 1 + 세럼 1(51,200). */
    protected OrderDeliveryGroup deliveredTwoItemGroup() throws Exception {
        return delivered(shipped(prepared(paidTwoItemGroup()), "CJ", newInvoice()),
                LocalDateTime.now().minusHours(1).withNano(0));
    }

    /** 무료배송 기준을 바꾼다 — 그 뒤 결제하는 주문부터 적용된다(주문 시점 값으로 고정). */
    protected void freeShippingFrom(int threshold) {
        jdbc.update("UPDATE market SET free_shipping_threshold = ? WHERE market_id = ?", threshold, brand.marketId());
    }

    protected static String newInvoice() {
        return String.valueOf(100_000_000_000L + (long) (Math.random() * 899_999_999_999L));
    }

    // ------------------------------------------------------------------ 신청(도메인)

    protected RequestResult requestClaim(OrderDeliveryGroup group, ClaimType type, ClaimReason reason,
                                         List<Item> items, String invoice) {
        return claimService.request(new RequestCommand(consumer.getId(), group.getId(), type, reason,
                reason.isDetailRequired() ? "상세 내용입니다" : null, List.of(), items,
                invoice == null ? null : new Invoice(DeliveryCarrier.CJ, invoice), null), LocalDateTime.now());
    }

    protected List<Item> allItems(OrderDeliveryGroup group) {
        return items(group).stream().map(p -> new Item(p.getId(), p.getQuantity())).toList();
    }

    /** 그 하위주문의 전 항목 · 고객 귀책 반품 · 회수 중으로 시작. */
    protected Long returnClaim(OrderDeliveryGroup group) {
        return requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group), newInvoice())
                .claimIds().get(0);
    }

    /** 첫 항목 · 브랜드 귀책 교환(결제 없음) · 지정 옵션 · 회수 중으로 시작. */
    protected Long exchangeClaim(OrderDeliveryGroup group, ProductVariant to) {
        OrderProduct product = items(group).get(0);
        return requestClaim(group, ClaimType.EXCHANGE, ClaimReason.DAMAGED_OR_DEFECTIVE,
                List.of(new Item(product.getId(), product.getQuantity(), to.getVariantId())), newInvoice())
                .claimIds().get(0);
    }

    // ------------------------------------------------------------------ 판정(파트너센터 API)

    protected Long received(Long claimId) throws Exception {
        sellerPost(SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.succeeded").value(1));
        return claimId;
    }

    /** 입고 확인 → 검수 통과. */
    protected Long passed(Long claimId) throws Exception {
        sellerPost(SELLER_CLAIMS + "/" + received(claimId) + "/inspection/pass", Map.of())
                .andExpect(status().isOk());
        return claimId;
    }

    /** 입고 확인 → 검수 거절. */
    protected Long rejectedClaim(Long claimId) throws Exception {
        sellerPost(SELLER_CLAIMS + "/" + received(claimId) + "/inspection/reject", rejectBody())
                .andExpect(status().isOk());
        return claimId;
    }

    protected static Map<String, Object> rejectBody() {
        return Map.of("reasonCode", "USED", "detail", "용기 입구에 사용 흔적이 있습니다.",
                "evidenceImageUrls", List.of("https://img.test/e1.jpg"));
    }

    protected ResultActions registerReship(Long claimId, String carrier, String trackingNumber) throws Exception {
        return sellerPost(SELLER_CLAIMS + "/reshipments", Map.of("items", List.of(reshipRow(claimId, carrier,
                trackingNumber))));
    }

    protected static Map<String, Object> reshipRow(Long claimId, String carrier, String trackingNumber) {
        Map<String, Object> row = new HashMap<>();
        row.put("claimId", claimId);
        row.put("carrier", carrier);
        row.put("trackingNumber", trackingNumber);
        return row;
    }

    /** 다른 브랜드의 재발송 대기 클레임 — 그 브랜드 토큰으로 준비 · 송장 · 입고 확인 · 통과까지 간다. */
    protected Long otherBrandReshipReadyClaim() throws Exception {
        BrandFixture.Brand other = otherBrand();
        String token = sellerToken(other.seller());
        ProductVariant variant = openOtherBrandGroupBuy(other);
        OrderDeliveryGroup group = paidOtherBrandGroup(other, variant);
        sellerPost(token, SELLER_ORDERS + "/prepare-start", Map.of("deliveryGroupIds", List.of(group.getId())))
                .andExpect(jsonPath("$.succeeded").value(1));
        sellerPost(token, SELLER_ORDERS + "/shipments",
                Map.of("rows", List.of(shipmentRow(group.getId(), "CJ", newInvoice()))))
                .andExpect(jsonPath("$.succeeded").value(1));
        LocalDateTime deliveredAt = LocalDateTime.now().minusHours(1).withNano(0);
        fulfillmentService.applyTracking(deliveryGroupRepository.findOwned(group.getId(), other.marketId())
                        .orElseThrow(), Optional.of(new TrackSnapshot(deliveredAt, deliveredAt, false, false)),
                LocalDateTime.now(), 24, 7);
        OrderProduct product = items(group).get(0);
        Long claimId = requestClaim(group, ClaimType.EXCHANGE, ClaimReason.DAMAGED_OR_DEFECTIVE,
                List.of(new Item(product.getId(), 1, variant.getVariantId())), newInvoice()).claimIds().get(0);
        sellerPost(token, SELLER_CLAIMS + "/receive", Map.of("claimIds", List.of(claimId)))
                .andExpect(jsonPath("$.succeeded").value(1));
        sellerPost(token, SELLER_CLAIMS + "/" + claimId + "/inspection/pass", Map.of()).andExpect(status().isOk());
        return claimId;
    }

    // ------------------------------------------------------------------ 앱 요청

    /** 앱 요청 본문 — 항목 하나. 결제 수단은 신한카드. */
    protected Map<String, Object> claimBody(OrderDeliveryGroup group, String type, String reason, Long orderProductId,
                                            Integer quantity, Long exchangeVariantId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderProductId", orderProductId);
        item.put("quantity", quantity);
        item.put("exchangeVariantId", exchangeVariantId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("type", type);
        body.put("deliveryGroupId", group.getId());
        body.put("items", new java.util.ArrayList<>(List.of(item)));
        body.put("reasonCode", reason);
        body.put("reasonDetail", "DAMAGED_OR_DEFECTIVE".equals(reason) ? "뚜껑이 깨져서 왔어요" : null);
        body.put("payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN"));
        return body;
    }

    protected ResultActions userGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    protected ResultActions userPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions userPatch(String url, Object body) throws Exception {
        return mockMvc.perform(patch(url).header(HttpHeaders.AUTHORIZATION, consumerToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions completeClaimPayment(String paymentId) throws Exception {
        return mockMvc.perform(post(USER_CLAIMS + "/payments/" + paymentId + "/complete")
                .header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    protected JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ 추적 결과

    protected static TrackEvent scan(LocalDateTime at, String location, String description, int level) {
        return new TrackEvent(at, location, description, level);
    }

    /** 포트가 돌려주는 결과 — 이력은 누적(앞 회차 + 새 스캔)으로 넣는다. */
    protected static TrackSnapshot snapshotOf(LocalDateTime lastEventAt, LocalDateTime deliveredAt,
                                              TrackEvent... events) {
        return new TrackSnapshot(lastEventAt, deliveredAt, false, false, List.of(events),
                events.length == 0 ? null : events[events.length - 1].level());
    }

    // ------------------------------------------------------------------ 옵션 · 엑셀

    /** 같은 상품 · 같은 가격의 옵션 「리필」 — 교환할 수 있는 옵션은 판매가가 같아야 한다. */
    protected ProductVariant addSamePriceVariant(ProductVariant base, int stock) {
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

    /** 문자열 셀만 든 xlsx — 첫 행이 머리글이다. */
    protected static byte[] xlsx(List<String[]> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows.get(r).length; c++) {
                    row.createCell(c).setCellValue(rows.get(r)[c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected ResultActions uploadReshipments(byte[] file) throws Exception {
        return mockMvc.perform(multipart(SELLER_CLAIMS + "/reshipments/parse")
                .file(new MockMultipartFile("file", "reship.xlsx", XLSX, file))
                .header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    // ------------------------------------------------------------------ DB 원값

    protected Map<String, Object> claimRow(Long claimId) {
        return jdbc.queryForMap("SELECT * FROM order_claim WHERE claim_id = ?", claimId);
    }

    protected Map<String, Object> collectionRow(Long collectionId) {
        return jdbc.queryForMap("SELECT * FROM order_claim_collection WHERE collection_id = ?", collectionId);
    }

    protected Long collectionIdOf(Long claimId) {
        return jdbc.queryForObject("SELECT collection_id FROM order_claim WHERE claim_id = ?", Long.class, claimId);
    }

    protected String claimStatus(Long claimId) {
        return jdbc.queryForObject("SELECT status FROM order_claim WHERE claim_id = ?", String.class, claimId);
    }

    protected Long chargeIdOf(Long claimId) {
        return jdbc.queryForObject("SELECT charge_id FROM order_claim_charge WHERE collection_id = ?", Long.class,
                collectionIdOf(claimId));
    }

    protected String chargeStatusOf(Long claimId) {
        return jdbc.queryForObject("SELECT status FROM order_claim_charge WHERE collection_id = ?", String.class,
                collectionIdOf(claimId));
    }

    protected String claimPaymentStatus(String paymentId) {
        return jdbc.queryForObject("SELECT status FROM order_claim_payment WHERE payment_id = ?", String.class,
                paymentId);
    }

    protected List<String> events(Long claimId) {
        return jdbc.queryForList("SELECT event_type FROM order_claim_history WHERE claim_id = ? "
                + "ORDER BY claim_history_id", String.class, claimId);
    }

    protected int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    protected List<Long> refundTaskIds(OrderDeliveryGroup group) {
        return jdbc.queryForList("SELECT refund_task_id FROM order_refund_task WHERE delivery_group_id = ? "
                + "ORDER BY refund_task_id", Long.class, group.getId());
    }
}
