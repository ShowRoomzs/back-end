package showroomz.api.seller.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import showroomz.api.app.order.service.CheckoutService;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderFulfillmentHistory;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.CancelRequestReason;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.global.config.properties.OrderProperties;
import showroomz.support.IntegrationTest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 준비 시작 · 발주서 · 발주서 컬럼 템플릿(34 설계서 3-1 · §34-4). 준비 시작의 효과는 단 하나 — 소비자 단순 취소권 종료다.
 * 다건은 행 단위 부분 성공이고(2절), 발주서는 개인정보가 나가는 반출이라 다운로드 트랜잭션 안에서 이력을 남긴다(1-8).
 */
@IntegrationTest
class SellerOrderPreparationIntegrationTest extends SellerOrderTestSupport {

    private static final List<String> ALL_COLUMNS = List.of(
            "QUANTITY", "PRODUCT_NAME", "ORDER_NUMBER", "SUB_ORDER_NUMBER", "RECIPIENT", "PHONE", "ZIP_CODE",
            "ADDRESS", "OPTION", "DELIVERY_MEMO", "GROUP_BUY_NAME", "ORDERED_AT", "PAID_AMOUNT");
    private static final List<String> ALL_HEADERS = List.of(
            "수량", "상품명", "주문번호", "하위주문번호", "수취인", "연락처", "우편번호",
            "주소", "옵션", "배송 요청사항", "공구명", "주문일시", "결제금액");
    private static final List<String> BASIC_EIGHT = List.of(
            "ORDER_NUMBER", "RECIPIENT", "PHONE", "ZIP_CODE", "ADDRESS", "PRODUCT_NAME", "OPTION", "QUANTITY");

    @Autowired private CheckoutService checkoutService;
    @Autowired private OrderProperties orderProperties;

    @Nested
    @DisplayName("준비 시작")
    class PrepareStart {

        @Test
        @DisplayName("다건은 행 단위 부분 성공 — 취소 요청 걸림 · 상태 아님 · 없는 id 는 사유와 함께 제외된다")
        void batchPartialSuccess() throws Exception {
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup requested = paidGroup();
            seedCancelRequest(requested);
            OrderDeliveryGroup shipping = shippingGroup("200030004000");

            prepareStart(fresh.getId(), requested.getId(), shipping.getId(), 999_999L)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.succeeded").value(1))
                    .andExpect(jsonPath("$.skipped.length()").value(3))
                    .andExpect(skippedCode(requested.getId(), "CANCEL_REQUEST_PENDING_EXISTS"))
                    .andExpect(skippedCode(shipping.getId(), "ORDER_STATE_CHANGED"))
                    .andExpect(skippedCode(999_999L, "ORDER_STATE_CHANGED"));

            OrderDeliveryGroup prepared = reload(fresh);
            assertThat(prepared.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(prepared.getPrepareStartedAt()).isNotNull();
            assertThat(prepared.getPrepareStartedBy()).isEqualTo(brand.seller().getId());
            assertThat(reload(requested).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(reload(shipping).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(historyCount(requested, FulfillmentEventType.PREPARE_STARTED)).isZero();
        }

        @Test
        @DisplayName("이력 PREPARE_STARTED — 주체 브랜드 · 처리자 id · 「소비자 취소권 종료」 병기")
        void historyRecordsSeller() throws Exception {
            OrderDeliveryGroup group = preparingGroup();

            OrderFulfillmentHistory latest = history(group).get(0);
            assertThat(latest.getEventType()).isEqualTo(FulfillmentEventType.PREPARE_STARTED);
            assertThat(latest.getEventType().getLabel()).contains("소비자 취소권 종료");
            assertThat(latest.getActorType()).isEqualTo(FulfillmentActorType.SELLER);
            assertThat(latest.getActorId()).isEqualTo(brand.seller().getId());
            assertThat(latest.getOccurredAt()).isEqualTo(reload(group).getPrepareStartedAt());
        }

        @Test
        @DisplayName("같은 id 를 두 번 보내도 한 번만 처리된다 — 이력 중복 없음")
        void duplicateIdsCountOnce() throws Exception {
            OrderDeliveryGroup group = paidGroup();

            prepareStart(group.getId(), group.getId())
                    .andExpect(jsonPath("$.succeeded").value(1))
                    .andExpect(jsonPath("$.skipped", empty()));
            assertThat(historyCount(group, FulfillmentEventType.PREPARE_STARTED)).isEqualTo(1);
        }

        @Test
        @DisplayName("빈 목록은 400")
        void emptyIdsIsBadRequest() throws Exception {
            sellerPost(SELLER_ORDERS + "/prepare-start", Map.of("deliveryGroupIds", List.of()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("소비자 취소 선점(결제 CANCEL_REQUESTED)이 먼저 커밋되면 준비 시작은 0행 — 「취소됐는데 발송」이 없다(X-03)")
        void consumerCancelClaimWinsOverPreparation() throws Exception {
            OrderDeliveryGroup group = paidGroup();
            checkoutService.claimUserCancel(consumer.getId(), group.getOrder().getId(), "단순 변심",
                    LocalDateTime.now());

            prepareStart(group.getId())
                    .andExpect(jsonPath("$.succeeded").value(0))
                    .andExpect(skippedCode(group.getId(), "ORDER_STATE_CHANGED"));
            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
        }

        @Test
        @DisplayName("소비자 취소로 끝난 하위주문은 준비 시작할 수 없다")
        void cancelledGroupCannotPrepare() throws Exception {
            OrderDeliveryGroup group = paidGroup();
            cancel(group.getOrder().getId()).andExpect(status().isOk());

            prepareStart(group.getId())
                    .andExpect(jsonPath("$.succeeded").value(0))
                    .andExpect(skippedCode(group.getId(), "ORDER_STATE_CHANGED"));
        }
    }

    @Nested
    @DisplayName("발주서")
    class PurchaseOrder {

        @Test
        @DisplayName("고른 컬럼 순서 = 열 순서 · 행 단위는 주문 항목 · 수취인·주소는 하위주문 값 반복")
        void columnsAndItemRows() throws Exception {
            OrderDeliveryGroup twoItems = paidTwoItemGroup();
            OrderDeliveryGroup single = paidGroup();
            OrderProduct cream = itemOf(twoItems, creamVariant);

            byte[] file = downloadPurchaseOrder(Map.of(
                    "deliveryGroupIds", List.of(twoItems.getId(), single.getId()),
                    "columns", ALL_COLUMNS,
                    "startPreparation", false))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(XLSX))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith(
                            "attachment; filename*=UTF-8''" + URLEncoder.encode("발주서_", StandardCharsets.UTF_8))))
                    .andReturn().getResponse().getContentAsByteArray();

            List<List<String>> sheet = readSheet(file);
            assertThat(sheet.get(0)).containsExactlyElementsOf(ALL_HEADERS);
            assertThat(sheet).hasSize(1 + 3); // 헤더 + 2항목 + 1항목
            List<String> creamRow = rowOf(sheet, orderNumberOf(twoItems), "글로우 크림 50ml");
            assertThat(creamRow).containsExactly(
                    "1", "글로우 크림 50ml", orderNumberOf(twoItems), orderNumberOf(twoItems) + "-01",
                    "김수민", "010-1234-5678", "06234", "서울 강남구 테헤란로 000 쇼룸타워 12층",
                    cream.getOptionName() == null ? "" : cream.getOptionName(), "문 앞에 놓아주세요",
                    "글로우 크림 앵콜 공구",
                    twoItems.getOrder().getPaidAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    String.valueOf(CREAM_PRICE));
            // 같은 하위주문의 세럼 행도 수취인·주소를 반복한다.
            assertThat(rowOf(sheet, orderNumberOf(twoItems), "글로우 세럼 30ml"))
                    .contains("김수민", "서울 강남구 테헤란로 000 쇼룸타워 12층", String.valueOf(SERUM_PRICE));
            assertThat(reload(twoItems).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(reload(single).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
        }

        @Test
        @DisplayName("다운로드마다 반출 이력 1행 — 누가 · 몇 건 · 어떤 컬럼 · 동시 처리 여부(§34-13 #14)")
        void downloadLogRecorded() throws Exception {
            OrderDeliveryGroup first = paidGroup();
            OrderDeliveryGroup second = paidGroup();

            downloadPurchaseOrder(Map.of("deliveryGroupIds", List.of(first.getId(), second.getId()),
                    "columns", List.of("RECIPIENT", "PHONE", "ADDRESS"), "startPreparation", false))
                    .andExpect(status().isOk());

            Map<String, Object> log = jdbc.queryForMap("SELECT * FROM purchase_order_download_log");
            assertThat(((Number) log.get("market_id")).longValue()).isEqualTo(brand.marketId());
            assertThat(((Number) log.get("seller_id")).longValue()).isEqualTo(brand.seller().getId());
            assertThat(((Number) log.get("delivery_group_count")).intValue()).isEqualTo(2);
            assertThat(log.get("columns")).isEqualTo("RECIPIENT,PHONE,ADDRESS");
            assertThat(log.get("prepare_started")).isEqualTo(false);
            assertThat(log.get("downloaded_at")).isNotNull();
        }

        @Test
        @DisplayName("「다운로드와 함께 준비 시작」은 기본 ON — NEW 만 전이 · 이미 준비중인 건은 파일에만 실리고 이력이 늘지 않는다")
        void startsPreparationByDefault() throws Exception {
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup alreadyPreparing = preparingGroup();

            byte[] file = downloadPurchaseOrder(Map.of(
                    "deliveryGroupIds", List.of(fresh.getId(), alreadyPreparing.getId()),
                    "columns", List.of("ORDER_NUMBER")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();

            assertThat(readSheet(file)).hasSize(1 + 2);
            assertThat(reload(fresh).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(history(fresh).get(0).getDetail()).isEqualTo("발주서 다운로드 동시 처리");
            assertThat(historyCount(alreadyPreparing, FulfillmentEventType.PREPARE_STARTED)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT prepare_started FROM purchase_order_download_log", Boolean.class))
                    .isTrue();
        }

        @Test
        @DisplayName("선택 없이 열면 현재 탭·검색 조건 전체가 대상 — 탭 생략은 신규 탭")
        void noSelectionUsesCurrentTab() throws Exception {
            OrderDeliveryGroup first = paidGroup();
            paidGroup();
            OrderDeliveryGroup preparing = preparingGroup();

            assertThat(readSheet(downloadBytes(Map.of("columns", List.of("ORDER_NUMBER"),
                    "startPreparation", false)))).hasSize(1 + 2);
            assertThat(readSheet(downloadBytes(Map.of("columns", List.of("ORDER_NUMBER"),
                    "startPreparation", false, "tab", "PREPARING"))))
                    .containsExactly(List.of("주문번호"), List.of(orderNumberOf(preparing)));
            assertThat(readSheet(downloadBytes(Map.of("columns", List.of("ORDER_NUMBER"),
                    "startPreparation", false, "searchType", "ORDER_NUMBER", "keyword", orderNumberOf(first)))))
                    .containsExactly(List.of("주문번호"), List.of(orderNumberOf(first)));
        }

        @Test
        @DisplayName("검토 중 취소 요청이 걸린 하위주문은 발주서에도 싣지 않고 준비 시작도 하지 않는다")
        void pendingCancelExcluded() throws Exception {
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup requested = paidGroup();
            seedCancelRequest(requested);

            byte[] file = downloadBytes(Map.of("deliveryGroupIds", List.of(fresh.getId(), requested.getId()),
                    "columns", List.of("ORDER_NUMBER")));

            assertThat(readSheet(file)).containsExactly(List.of("주문번호"), List.of(orderNumberOf(fresh)));
            assertThat(reload(fresh).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(reload(requested).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(jdbc.queryForObject("SELECT delivery_group_count FROM purchase_order_download_log",
                    Integer.class)).isEqualTo(1);
        }

        @Test
        @DisplayName("내려받을 주문이 없으면 400 PURCHASE_ORDER_EMPTY — 반출 이력도 남지 않는다")
        void emptyTargetIsRejected() throws Exception {
            OrderDeliveryGroup requested = paidGroup();
            seedCancelRequest(requested);

            downloadPurchaseOrder(Map.of("deliveryGroupIds", List.of(requested.getId()), "columns", List.of("ORDER_NUMBER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_EMPTY"));
            downloadPurchaseOrder(Map.of("columns", List.of("ORDER_NUMBER"), "tab", "SHIPPING"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_EMPTY"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_download_log", Integer.class)).isZero();
        }

        @Test
        @DisplayName("결제 전(PENDING) 하위주문은 id 를 골라 보내도 발주서에 실리지 않는다 — 개인정보 반출 대상이 아니다")
        void unpaidGroupIsNeverExported() throws Exception {
            OrderDeliveryGroup unpaid = onlyGroupOf(placeCardOrder(creamVariant, 1).orderId());

            downloadPurchaseOrder(Map.of("deliveryGroupIds", List.of(unpaid.getId()),
                    "columns", List.of("RECIPIENT", "PHONE", "ADDRESS"), "startPreparation", false))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_EMPTY"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_download_log", Integer.class)).isZero();
        }

        @Test
        @DisplayName("취소 확정된 항목은 발송하지 않는다 — 부분 승인 뒤 발주서에는 남은 항목만")
        void cancelledItemsSkipped() throws Exception {
            OrderDeliveryGroup group = preparingTwoItemGroup();
            OrderCancelRequest request = seedCancelRequest(group, List.of(itemOf(group, creamVariant)),
                    CancelRequestReason.CHANGE_OF_MIND, null, LocalDateTime.now().withNano(0));
            approve(request.getId()).andExpect(status().isOk());

            assertThat(readSheet(downloadBytes(Map.of("deliveryGroupIds", List.of(group.getId()),
                    "columns", List.of("PRODUCT_NAME", "QUANTITY")))))
                    .containsExactly(List.of("상품명", "수량"), List.of("글로우 세럼 30ml", "1"));
        }

        @Test
        @DisplayName("컬럼 — 중복은 한 번만 · 빈 목록과 없는 컬럼 코드는 400")
        void columnValidation() throws Exception {
            OrderDeliveryGroup group = paidGroup();

            assertThat(readSheet(downloadBytes(Map.of("deliveryGroupIds", List.of(group.getId()),
                    "columns", List.of("ORDER_NUMBER", "QUANTITY", "ORDER_NUMBER"), "startPreparation", false)))
                    .get(0)).containsExactly("주문번호", "수량");
            downloadPurchaseOrder(Map.of("deliveryGroupIds", List.of(group.getId()), "columns", List.of()))
                    .andExpect(status().isBadRequest());
            downloadPurchaseOrder(Map.of("deliveryGroupIds", List.of(group.getId()), "columns", List.of("BIRTHDAY")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("「이 구성을 기본값으로 저장」 — 다운로드와 함께 템플릿이 저장된다")
        void saveAsDefault() throws Exception {
            OrderDeliveryGroup group = paidGroup();

            downloadPurchaseOrder(Map.of("deliveryGroupIds", List.of(group.getId()),
                    "columns", List.of("RECIPIENT", "ADDRESS", "QUANTITY"), "saveAsDefault", true))
                    .andExpect(status().isOk());

            sellerGet(SELLER_ORDERS + "/purchase-order/template")
                    .andExpect(jsonPath("$.columns", contains("RECIPIENT", "ADDRESS", "QUANTITY")));
        }
    }

    @Nested
    @DisplayName("발주서 컬럼 템플릿")
    class Template {

        @Test
        @DisplayName("저장된 구성이 없으면 기본 8종 · 선택 가능 컬럼 13종(기본/추가 구분)")
        void defaultsToBasicEight() throws Exception {
            sellerGet(SELLER_ORDERS + "/purchase-order/template")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.columns", contains(BASIC_EIGHT.toArray())))
                    .andExpect(jsonPath("$.available.length()").value(13))
                    .andExpect(jsonPath("$.available[?(@.code == 'ORDER_NUMBER')].basic", contains(true)))
                    .andExpect(jsonPath("$.available[?(@.code == 'SUB_ORDER_NUMBER')].basic", contains(false)))
                    .andExpect(jsonPath("$.available[?(@.code == 'DELIVERY_MEMO')].header", contains("배송 요청사항")));
        }

        @Test
        @DisplayName("저장은 마켓당 1행 upsert — 순서를 보존하고 다시 저장하면 덮어쓴다")
        void upsertKeepsOrder() throws Exception {
            putTemplate(List.of("SUB_ORDER_NUMBER", "RECIPIENT", "ADDRESS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.columns", contains("SUB_ORDER_NUMBER", "RECIPIENT", "ADDRESS")));
            putTemplate(List.of("QUANTITY", "RECIPIENT", "RECIPIENT"))
                    .andExpect(jsonPath("$.columns", contains("QUANTITY", "RECIPIENT")));

            sellerGet(SELLER_ORDERS + "/purchase-order/template")
                    .andExpect(jsonPath("$.columns", contains("QUANTITY", "RECIPIENT")));
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT COUNT(*) AS cnt, MAX(updated_by) AS updated_by FROM market_purchase_order_template "
                            + "WHERE market_id = ?", brand.marketId());
            assertThat(((Number) row.get("cnt")).intValue()).isEqualTo(1);
            assertThat(((Number) row.get("updated_by")).longValue()).isEqualTo(brand.seller().getId());
        }

        @Test
        @DisplayName("빈 구성은 저장할 수 없다 — 400")
        void emptyTemplateRejected() throws Exception {
            putTemplate(List.of()).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("템플릿은 마켓마다 따로다 — 다른 브랜드의 저장이 내 기본값을 바꾸지 않는다")
        void templatePerMarket() throws Exception {
            String otherToken = sellerToken(otherBrand().seller());
            putTemplate(List.of("PHONE")).andExpect(status().isOk());

            mockMvc.perform(put(SELLER_ORDERS + "/purchase-order/template")
                            .header(HttpHeaders.AUTHORIZATION, otherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("columns", List.of("QUANTITY")))))
                    .andExpect(status().isOk());

            sellerGet(SELLER_ORDERS + "/purchase-order/template").andExpect(jsonPath("$.columns", contains("PHONE")));
            sellerGet(otherToken, SELLER_ORDERS + "/purchase-order/template")
                    .andExpect(jsonPath("$.columns", contains("QUANTITY")));
        }
    }

    @Nested
    @DisplayName("보강 — 요청 상한 · 발주서 대상 범위 · 반출 기록(PO)")
    class Boundaries {

        @Test
        @DisplayName("[PO-01] 다건 요청 상한 — 준비 시작 500 · 직권 취소 200 · 송장 500행까지는 받고, 넘으면 400 · 한 건도 처리하지 않는다")
        void batchSizeLimits() throws Exception {
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup preparing = preparingGroup();

            prepareStart(idsWith(fresh.getId(), 500).toArray(Long[]::new))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.succeeded").value(1))
                    .andExpect(jsonPath("$.skipped.length()").value(499));
            OrderDeliveryGroup another = paidGroup();
            prepareStart(idsWith(another.getId(), 501).toArray(Long[]::new))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            assertThat(reload(another).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);

            directCancel(idsWith(another.getId(), 201), "SOLD_OUT", "품절")
                    .andExpect(status().isBadRequest());
            assertThat(reload(another).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
            directCancel(idsWith(another.getId(), 200), "SOLD_OUT", "품절")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.succeeded").value(1))
                    .andExpect(jsonPath("$.skipped.length()").value(199));

            registerShipments(shipmentRowsWith(preparing.getId(), 501))
                    .andExpect(status().isBadRequest());
            assertThat(reload(preparing).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            registerShipments(shipmentRowsWith(preparing.getId(), 500))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.succeeded").value(1))
                    .andExpect(jsonPath("$.skipped.length()").value(499));
        }

        @Test
        @DisplayName("[PO-02] 골라 보낸 id 에 배송중·배송완료·취소가 섞여도 발주서에는 작업 큐(신규·준비중)만 · 반출 건수도 그만큼(N7)")
        void selectedTargetsLimitedToWorkQueue() throws Exception {
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup preparing = preparingGroup();
            OrderDeliveryGroup shipping = shippingGroup("620030004000");
            OrderDeliveryGroup delivered = delivered(shippingGroup("620030004001"), LocalDateTime.now().withNano(0));
            OrderDeliveryGroup cancelled = paidGroup();
            directCancel(List.of(cancelled.getId()), "SOLD_OUT", "품절").andExpect(status().isOk());

            byte[] file = downloadBytes(Map.of("deliveryGroupIds", List.of(fresh.getId(), preparing.getId(),
                            shipping.getId(), delivered.getId(), cancelled.getId()),
                    "columns", List.of("ORDER_NUMBER", "PHONE")));

            assertThat(readSheet(file)).extracting(row -> row.get(0))
                    .containsExactlyInAnyOrder("주문번호", orderNumberOf(fresh), orderNumberOf(preparing));
            assertThat(jdbc.queryForObject("SELECT delivery_group_count FROM purchase_order_download_log",
                    Integer.class)).isEqualTo(2);

            downloadPurchaseOrder(Map.of("deliveryGroupIds", List.of(shipping.getId(), delivered.getId(),
                    cancelled.getId()), "columns", List.of("PHONE", "ADDRESS")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_EMPTY"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_download_log", Integer.class))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("[PO-02] 발주서가 없는 탭(전체 · 배송중 등)으로 열면 대상이 없다 — 400 PURCHASE_ORDER_EMPTY")
        void tabsWithoutPurchaseOrder() throws Exception {
            paidGroup();
            shippingGroup("620030004002");

            for (String tab : List.of("ALL", "SHIPPING", "CANCELLED")) {
                downloadPurchaseOrder(Map.of("columns", List.of("ORDER_NUMBER"), "tab", tab))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_EMPTY"));
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_download_log", Integer.class)).isZero();
        }

        @Test
        @DisplayName("[PO-03] 탭 전체 발주서에 소비자 취소 PG 대기 건이 있으면 — 파일에는 싣고 준비 시작만 생략(이력 없음)")
        void consumerCancelInFlightIsExportedButNotPrepared() throws Exception {
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup claimed = paidGroup();
            checkoutService.claimUserCancel(consumer.getId(), claimed.getOrder().getId(), "단순 변심",
                    LocalDateTime.now());

            assertThat(readSheet(downloadBytes(Map.of("columns", List.of("ORDER_NUMBER"))))).hasSize(1 + 2);

            assertThat(reload(fresh).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(reload(claimed).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(historyCount(claimed, FulfillmentEventType.PREPARE_STARTED)).isZero();
            Map<String, Object> log = jdbc.queryForMap("SELECT * FROM purchase_order_download_log");
            assertThat(((Number) log.get("delivery_group_count")).intValue()).isEqualTo(2);
            assertThat(log.get("prepare_started")).isEqualTo(true);
        }

        @Test
        @DisplayName("[PO-04] 「준비 시작 없이 다운로드」를 3번 — 상태 NEW 그대로 · 반출 기록은 매번(3행)")
        void downloadWithoutPreparationIsLoggedEveryTime() throws Exception {
            OrderDeliveryGroup group = paidGroup();

            for (int i = 0; i < 3; i++) {
                downloadBytes(Map.of("deliveryGroupIds", List.of(group.getId()), "columns", List.of("RECIPIENT"),
                        "startPreparation", false));
            }

            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(historyCount(group, FulfillmentEventType.PREPARE_STARTED)).isZero();
            assertThat(jdbc.queryForList("SELECT prepare_started FROM purchase_order_download_log", Boolean.class))
                    .containsExactly(false, false, false);
        }

        @Test
        @DisplayName("[PO-05] 발주서 대상 상한 — 넘으면 조용히 자르지 않고 400 PURCHASE_ORDER_TOO_MANY · 반출·전이 없음(N7)")
        void purchaseOrderCap() throws Exception {
            OrderDeliveryGroup first = paidGroup();
            OrderDeliveryGroup second = paidGroup();
            OrderDeliveryGroup third = paidGroup();
            int original = orderProperties.getPurchaseOrderMaxGroups();
            orderProperties.setPurchaseOrderMaxGroups(2);
            try {
                downloadPurchaseOrder(Map.of("columns", List.of("ORDER_NUMBER")))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_TOO_MANY"));
                downloadPurchaseOrder(Map.of("deliveryGroupIds", List.of(first.getId(), second.getId(), third.getId()),
                        "columns", List.of("ORDER_NUMBER")))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_TOO_MANY"));
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_download_log", Integer.class))
                        .isZero();
                assertThat(List.of(first, second, third))
                        .allSatisfy(g -> assertThat(reload(g).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW));

                assertThat(readSheet(downloadBytes(Map.of("deliveryGroupIds", List.of(first.getId(), second.getId()),
                        "columns", List.of("ORDER_NUMBER"))))).hasSize(1 + 2);
            } finally {
                orderProperties.setPurchaseOrderMaxGroups(original);
            }
        }

        @Test
        @DisplayName("[PO-06] 기본값 저장 + 중복 컬럼 — 템플릿도 중복을 지운 순서로 저장된다")
        void saveAsDefaultDeduplicates() throws Exception {
            OrderDeliveryGroup group = paidGroup();

            downloadBytes(Map.of("deliveryGroupIds", List.of(group.getId()),
                    "columns", List.of("ORDER_NUMBER", "QUANTITY", "ORDER_NUMBER"), "saveAsDefault", true,
                    "startPreparation", false));

            sellerGet(SELLER_ORDERS + "/purchase-order/template")
                    .andExpect(jsonPath("$.columns", contains("ORDER_NUMBER", "QUANTITY")));
        }

        @Test
        @DisplayName("[PO-07] 탭 전체 발주서도 목록과 같은 기간 검증 — 366일 400 ORDER_SEARCH_RANGE_EXCEEDED · 역전 400 · 반출 없음")
        void tabTargetUsesSameRangeRule() throws Exception {
            paidGroup();
            LocalDate today = LocalDate.now();

            downloadPurchaseOrder(Map.of("columns", List.of("ORDER_NUMBER"),
                    "from", today.minusDays(366).toString(), "to", today.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ORDER_SEARCH_RANGE_EXCEEDED"));
            downloadPurchaseOrder(Map.of("columns", List.of("ORDER_NUMBER"),
                    "from", today.toString(), "to", today.minusDays(1).toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_download_log", Integer.class)).isZero();
        }

        @Test
        @DisplayName("[PO-08] 선택 id 와 탭 필터를 함께 보내면 선택이 이긴다 — 필터는 보지 않는다")
        void selectionWinsOverFilter() throws Exception {
            OrderDeliveryGroup group = paidGroup();

            assertThat(readSheet(downloadBytes(Map.of("deliveryGroupIds", List.of(group.getId()),
                    "columns", List.of("ORDER_NUMBER"), "startPreparation", false,
                    "tab", "PREPARING", "searchType", "ORDER_NUMBER", "keyword", "NO-SUCH-ORDER"))))
                    .containsExactly(List.of("주문번호"), List.of(orderNumberOf(group)));
        }
    }

    // ------------------------------------------------------------------ 보조

    /** 실제 id 1개 + 없는 id 로 채운 {@code size}건. */
    private static List<Long> idsWith(Long realId, int size) {
        List<Long> ids = new ArrayList<>();
        ids.add(realId);
        LongStream.range(0, size - 1).forEach(i -> ids.add(9_000_000L + i));
        return ids;
    }

    private List<Map<String, Object>> shipmentRowsWith(Long realId, int size) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(shipmentRow(realId, "CJ", "630000000000"));
        for (int i = 1; i < size; i++) {
            rows.add(shipmentRow(9_000_000L + i, "CJ", "63%010d".formatted(i)));
        }
        return rows;
    }

    private ResultActions downloadPurchaseOrder(Map<String, Object> body) throws Exception {
        return sellerPost(SELLER_ORDERS + "/purchase-order", new HashMap<>(body));
    }

    private byte[] downloadBytes(Map<String, Object> body) throws Exception {
        return downloadPurchaseOrder(body).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private ResultActions putTemplate(List<String> columns) throws Exception {
        return mockMvc.perform(put(SELLER_ORDERS + "/purchase-order/template")
                .header(HttpHeaders.AUTHORIZATION, brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("columns", columns))));
    }

    private static List<String> rowOf(List<List<String>> sheet, String orderNumber, String productName) {
        return sheet.stream()
                .filter(row -> row.contains(orderNumber) && row.contains(productName))
                .findFirst().orElseThrow(() -> new AssertionError(orderNumber + " / " + productName + " 행이 없다"));
    }

    private static ResultMatcher skippedCode(Long deliveryGroupId, String code) {
        return jsonPath("$.skipped[?(@.deliveryGroupId == " + deliveryGroupId + ")].code", contains(code));
    }
}
