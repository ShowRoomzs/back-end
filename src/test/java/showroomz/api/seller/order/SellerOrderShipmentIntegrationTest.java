package showroomz.api.seller.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultMatcher;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderFulfillmentHistory;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.support.IntegrationTest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 송장(34 설계서 3-2 · §34-5 · §34-6) — 확정 지점은 {@code POST /shipments} 하나다. 엑셀 업로드는 파싱·분류만 하고 상태를 바꾸지 않는다.
 * 서버 검사는 ③ 확정 시점(형식 · 전역 중복 · 상태 재확인)과 ④ 송장 수정이다. 택배 연동 전이라 형식 검증은 「생략」으로 기록된다(0-4).
 */
@IntegrationTest
class SellerOrderShipmentIntegrationTest extends SellerOrderTestSupport {

    @Nested
    @DisplayName("송장 등록 확정")
    class Register {

        @Test
        @DisplayName("상품준비중 → 배송중 · 숫자만 저장 · shipped_at 확정 · 이력에 형식 검증 생략 기록")
        void registerMovesToShipping() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            LocalDateTime shipDueAt = group.getShipDueAt();

            registerShipment(group.getId(), "CJ", " 1234-5678 9012 ")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.succeeded").value(1))
                    .andExpect(jsonPath("$.skipped", empty()));

            OrderDeliveryGroup shipped = reload(group);
            assertThat(shipped.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(shipped.getCarrier()).isEqualTo(DeliveryCarrier.CJ);
            assertThat(shipped.getTrackingNumber()).isEqualTo("123456789012");
            assertThat(shipped.getShippedAt()).isNotNull();
            assertThat(shipped.getShipDueAt()).isEqualTo(shipDueAt); // 발송기한은 스냅샷 — 송장 등록이 바꾸지 않는다
            OrderFulfillmentHistory latest = history(group).get(0);
            assertThat(latest.getEventType()).isEqualTo(FulfillmentEventType.INVOICE_REGISTERED);
            assertThat(latest.getActorType()).isEqualTo(FulfillmentActorType.SELLER);
            assertThat(latest.getDetail()).isEqualTo("CJ대한통운 123456789012 · 형식 검증 생략(연동 전)");
            sellerGet(SELLER_ORDERS + "/summary").andExpect(jsonPath("$.actionBar.invoiceRegister").value(0));
        }

        @Test
        @DisplayName("다건은 부분 성공 — 신규 · 취소 요청 · 요청 내 중복은 그 행만 제외 · 빈 송장은 조용히 빠진다")
        void batchPartialSuccess() throws Exception {
            OrderDeliveryGroup ok = preparingGroup();
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup requested = preparingGroup();
            seedCancelRequest(requested);
            OrderDeliveryGroup sameInRequest = prepared(paidGroup(serumVariant, 1));
            OrderDeliveryGroup blank = prepared(paidGroup(serumVariant, 1));

            registerShipments(List.of(
                    shipmentRow(ok.getId(), "CJ", "300040005000"),
                    shipmentRow(fresh.getId(), "CJ", "300040005001"),
                    shipmentRow(requested.getId(), "CJ", "300040005002"),
                    shipmentRow(sameInRequest.getId(), "CJ", "3000-4000-5000"),
                    shipmentRow(blank.getId(), "CJ", "--")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.succeeded").value(1))
                    .andExpect(jsonPath("$.skipped.length()").value(3))
                    // 신규는 준비 시작 전이라 등록 불가(rev.7) — 배송중 → 준비중 복귀 경로가 없다.
                    .andExpect(skippedCode(fresh.getId(), "ORDER_STATE_CHANGED"))
                    .andExpect(skippedCode(requested.getId(), "CANCEL_REQUEST_PENDING_EXISTS"))
                    .andExpect(skippedCode(sameInRequest.getId(), "INVOICE_DUPLICATE"))
                    .andExpect(jsonPath("$.skipped[?(@.deliveryGroupId == " + sameInRequest.getId() + ")].message",
                            contains("같은 요청 안에서 중복된 송장번호입니다.")));

            assertThat(reload(ok).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(reload(fresh).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(reload(requested).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(reload(sameInRequest).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(reload(blank).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(reload(blank).getTrackingNumber()).isNull();
        }

        @Test
        @DisplayName("전역 중복은 겹치는 주문번호를 지목한다 — 택배사가 다르면 같은 번호도 등록된다")
        void globalDuplicateNamesOwner() throws Exception {
            OrderDeliveryGroup owner = shippingGroup("111122223333");
            OrderDeliveryGroup second = preparingGroup();

            registerShipment(second.getId(), "CJ", "1111 2222 3333")
                    .andExpect(jsonPath("$.succeeded").value(0))
                    .andExpect(skippedCode(second.getId(), "INVOICE_DUPLICATE"))
                    .andExpect(jsonPath("$.skipped[0].message").value(orderNumberOf(owner) + "에 이미 등록된 번호입니다."));
            registerShipment(second.getId(), "HANJIN", "111122223333")
                    .andExpect(jsonPath("$.succeeded").value(1));
        }

        @Test
        @DisplayName("중복 검사는 종결 전 상태만 — 배송완료는 막고, 구매확정된 송장번호는 재사용할 수 있다")
        void terminalInvoiceCanBeReused() throws Exception {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            OrderDeliveryGroup previous = delivered(shippingGroup("777700001111"), now.minusDays(8));
            OrderDeliveryGroup next = preparingGroup();

            registerShipment(next.getId(), "CJ", "777700001111")
                    .andExpect(skippedCode(next.getId(), "INVOICE_DUPLICATE"));

            assertThat(fulfillmentService.confirmPurchase(previous.getId(), now, now.minusDays(7))).isTrue();
            registerShipment(next.getId(), "CJ", "777700001111")
                    .andExpect(jsonPath("$.succeeded").value(1));
        }

        @Test
        @DisplayName("요청 형식 — 11종 밖 택배사 · 송장번호 누락 · 빈 행 목록은 400")
        void requestValidation() throws Exception {
            OrderDeliveryGroup group = preparingGroup();

            registerShipment(group.getId(), "FEDEX", "123412341234")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            sellerPost(SELLER_ORDERS + "/shipments",
                    Map.of("rows", List.of(Map.of("deliveryGroupId", group.getId(), "carrier", "CJ"))))
                    .andExpect(status().isBadRequest());
            registerShipments(List.of()).andExpect(status().isBadRequest());
            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
        }
    }

    @Nested
    @DisplayName("엑셀 업로드 — 파싱·분류만(E3)")
    class Upload {

        @Test
        @DisplayName("양식 xlsx — 주문번호 · 택배사 · 송장번호")
        void template() throws Exception {
            byte[] file = sellerGet(SELLER_ORDERS + "/shipments/template")
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(XLSX))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                            + URLEncoder.encode("송장_업로드_양식.xlsx", StandardCharsets.UTF_8).replace("+", "%20")))
                    .andReturn().getResponse().getContentAsByteArray();

            assertThat(readSheet(file)).containsExactly(List.of("주문번호", "택배사", "송장번호"));
        }

        @Test
        @DisplayName("행마다 분류한다 — 정상 · 신규 · 이미 배송중 · 주문 없음 · 택배사 · 송장 누락 · 전역/파일 내 중복 · 취소 요청 · 취소됨")
        void classifiesRowsWithoutChangingState() throws Exception {
            OrderDeliveryGroup ok = preparingGroup();
            OrderDeliveryGroup bySubOrder = preparingGroup();
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup shipping = shippingGroup("400050006000");
            OrderDeliveryGroup badCarrier = prepared(paidGroup(serumVariant, 1));
            OrderDeliveryGroup noTracking = prepared(paidGroup(serumVariant, 1));
            OrderDeliveryGroup dupActive = prepared(paidGroup(serumVariant, 1));
            OrderDeliveryGroup dupInFile = prepared(paidGroup(serumVariant, 1));
            OrderDeliveryGroup requested = preparingGroup();
            seedCancelRequest(requested);
            OrderDeliveryGroup cancelled = paidGroup();
            directCancel(List.of(cancelled.getId()), "SOLD_OUT", "품절").andExpect(status().isOk());

            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{orderNumberOf(ok), "CJ대한통운", "5000-6000-7001"});            // 2
            rows.add(new String[]{bySubOrder.getSubOrderNumber(), "CJ", "500060007002"});          // 3 — 코드명도 받는다
            rows.add(new String[]{orderNumberOf(fresh), "CJ대한통운", "500060007003"});            // 4
            rows.add(new String[]{orderNumberOf(shipping), "CJ대한통운", "500060007004"});         // 5
            rows.add(new String[]{"20990101-999999", "CJ대한통운", "500060007005"});               // 6
            rows.add(new String[]{orderNumberOf(badCarrier), "페덱스", "500060007006"});           // 7
            rows.add(new String[]{orderNumberOf(noTracking), "CJ대한통운", ""});                   // 8
            rows.add(null);                                                                         // 9 — 빈 행
            rows.add(new String[]{orderNumberOf(dupActive), "CJ대한통운", "400050006000"});        // 10
            rows.add(new String[]{orderNumberOf(dupInFile), "CJ대한통운", "500060007001"});        // 11
            rows.add(new String[]{orderNumberOf(requested), "CJ대한통운", "500060007007"});        // 12
            rows.add(new String[]{orderNumberOf(cancelled), "CJ대한통운", "500060007008"});        // 13

            uploadShipments(brandToken, shipmentXlsx(rows))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRows").value(11))
                    .andExpect(jsonPath("$.validRows").value(2))
                    .andExpect(rowField(2, "valid", true))
                    .andExpect(rowField(2, "deliveryGroupId", ok.getId().intValue()))
                    .andExpect(rowField(2, "subOrderNumber", ok.getSubOrderNumber()))
                    .andExpect(rowField(2, "carrier", "CJ"))
                    .andExpect(rowField(2, "trackingNumber", "500060007001"))
                    .andExpect(rowField(3, "valid", true))
                    .andExpect(rowField(3, "deliveryGroupId", bySubOrder.getId().intValue()))
                    .andExpect(rowError(4, "NEW_NOT_ALLOWED"))
                    .andExpect(rowError(5, "ALREADY_SHIPPED"))
                    .andExpect(rowError(6, "ORDER_NOT_FOUND"))
                    .andExpect(rowError(7, "CARRIER_INVALID"))
                    .andExpect(rowError(8, "TRACKING_REQUIRED"))
                    .andExpect(jsonPath("$.rows[?(@.rowNumber == 9)]", empty()))
                    .andExpect(rowError(10, "INVOICE_DUPLICATE"))
                    .andExpect(rowField(10, "message", orderNumberOf(shipping) + "에 이미 등록된 번호입니다."))
                    .andExpect(rowError(11, "INVOICE_DUPLICATE"))
                    .andExpect(rowField(11, "message", "파일 안에서 중복된 송장번호입니다."))
                    .andExpect(rowError(12, "CANCEL_REQUEST_PENDING"))
                    .andExpect(rowError(13, "STATE_INVALID"))
                    // 오류 행은 매칭 하위주문을 내리지 않는다.
                    .andExpect(rowField(4, "deliveryGroupId", null));

            // 상태를 바꾸지 않는다 — 확정은 같은 POST /shipments 다.
            assertThat(reload(ok).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(reload(ok).getTrackingNumber()).isNull();
            assertThat(reload(bySubOrder).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(historyCount(ok, FulfillmentEventType.INVOICE_REGISTERED)).isZero();
        }

        @Test
        @DisplayName("한 주문에 내 하위주문이 여럿이면 주문번호로는 모호하다 — 하위주문번호로 지정해야 한다")
        void ambiguousOrderNeedsSubOrderNumber() throws Exception {
            OrderDeliveryGroup first = preparingGroup();
            OrderDeliveryGroup second = prepared(addSecondGroup(first));

            uploadShipments(brandToken, shipmentXlsx(List.of(
                    new String[]{orderNumberOf(first), "CJ대한통운", "600070008001"},
                    new String[]{orderNumberOf(first) + "-02", "CJ대한통운", "600070008002"},
                    new String[]{orderNumberOf(first) + "-01", "CJ대한통운", "600070008003"})))
                    .andExpect(status().isOk())
                    .andExpect(rowError(2, "AMBIGUOUS_ORDER"))
                    .andExpect(rowField(3, "deliveryGroupId", second.getId().intValue()))
                    .andExpect(rowField(4, "deliveryGroupId", first.getId().intValue()));
        }

        @Test
        @DisplayName("xlsx 가 아니면 400 SHIPMENT_FILE_INVALID")
        void invalidFile() throws Exception {
            uploadShipments(brandToken, "주문번호,택배사,송장번호".getBytes(StandardCharsets.UTF_8))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SHIPMENT_FILE_INVALID"));
        }

        @Test
        @DisplayName("행 상한 1,000 — 1,000행은 받고 1,001행은 400 SHIPMENT_FILE_TOO_MANY_ROWS")
        void rowLimit() throws Exception {
            uploadShipments(brandToken, shipmentXlsx(dummyRows(1_000)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRows").value(1_000))
                    .andExpect(jsonPath("$.validRows").value(0));
            uploadShipments(brandToken, shipmentXlsx(dummyRows(1_001)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SHIPMENT_FILE_TOO_MANY_ROWS"));
        }

        @Test
        @DisplayName("업로드 결과를 그대로 확정하면 정상 행만 배송중이 된다 — 확정 지점은 하나")
        void parsedRowsConfirmThroughSameEndpoint() throws Exception {
            OrderDeliveryGroup ok = preparingGroup();
            OrderDeliveryGroup fresh = paidGroup();

            uploadShipments(brandToken, shipmentXlsx(List.of(
                    new String[]{orderNumberOf(ok), "CJ대한통운", "700080009001"},
                    new String[]{orderNumberOf(fresh), "CJ대한통운", "700080009002"})))
                    .andExpect(jsonPath("$.validRows").value(1));

            registerShipment(ok.getId(), "CJ", "700080009001").andExpect(jsonPath("$.succeeded").value(1));
            assertThat(reload(ok).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(reload(fresh).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
        }
    }

    @Nested
    @DisplayName("송장 수정")
    class Update {

        @Test
        @DisplayName("배송중 송장 수정 — 배지·최종 갱신 리셋 · shipped_at 유지 · 이력에 구→신")
        void updateResetsAlertAndKeepsShippedAt() throws Exception {
            OrderDeliveryGroup group = shippingGroup("444455556666");
            LocalDateTime now = LocalDateTime.now().withNano(0);
            track(group, new TrackSnapshot(now.minusDays(8), null, false, false), now); // 추적 정지
            OrderDeliveryGroup stalled = reload(group);
            assertThat(stalled.getTrackingAlert()).isEqualTo(TrackingAlert.STALLED);
            assertThat(stalled.getLastTrackingAt()).isNotNull();

            updateShipment(group.getId(), "HANJIN", "7777-8888-9999")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.timeline.carrierLabel").value("한진택배"))
                    .andExpect(jsonPath("$.timeline.trackingNumber").value("777788889999"))
                    .andExpect(jsonPath("$.timeline.lastTrackingAt").value(nullValue()))
                    .andExpect(jsonPath("$.overlays.trackingAlert").value(nullValue()))
                    .andExpect(jsonPath("$.history[0].eventType").value("INVOICE_UPDATED"));

            OrderDeliveryGroup updated = reload(group);
            assertThat(updated.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(updated.getTrackingAlert()).isNull();
            assertThat(updated.getLastTrackingAt()).isNull();
            assertThat(updated.getShippedAt()).isEqualTo(stalled.getShippedAt());
            OrderFulfillmentHistory latest = history(group).get(0);
            assertThat(latest.getDetail()).isEqualTo("CJ대한통운 444455556666 → 한진택배 777788889999");
            assertThat(latest.getActorType()).isEqualTo(FulfillmentActorType.SELLER);
        }

        @Test
        @DisplayName("배송중이 아니면 409 ORDER_STATE_CHANGED — 신규 · 상품준비중 · 반송중 · 배송완료")
        void onlyWhileShipping() throws Exception {
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup preparing = preparingGroup();
            OrderDeliveryGroup returning = returning(shippingGroup("800090001000"));
            OrderDeliveryGroup delivered = delivered(shippingGroup("800090001001"), LocalDateTime.now().withNano(0));

            for (OrderDeliveryGroup group : List.of(fresh, preparing, returning, delivered)) {
                updateShipment(group.getId(), "HANJIN", "800090002000")
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));
                assertThat(historyCount(group, FulfillmentEventType.INVOICE_UPDATED)).isZero();
            }
            assertThat(reload(returning).getTrackingNumber()).isEqualTo("800090001000");
        }

        @Test
        @DisplayName("다른 주문의 살아 있는 송장으로는 바꿀 수 없다(409) · 빈 번호는 400 · 자기 번호·다른 택배사는 된다")
        void duplicateAndFormat() throws Exception {
            OrderDeliveryGroup owner = shippingGroup("111100002222");
            OrderDeliveryGroup group = shippingGroup("333300004444");

            updateShipment(group.getId(), "CJ", "1111-0000-2222")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVOICE_DUPLICATE"));
            updateShipment(group.getId(), "CJ", "--")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVOICE_FORMAT_INVALID"));
            assertThat(reload(group).getTrackingNumber()).isEqualTo("333300004444");

            updateShipment(group.getId(), "CJ", "333300004444").andExpect(status().isOk());
            updateShipment(group.getId(), "LOTTE", "111100002222").andExpect(status().isOk());
            assertThat(reload(group).getCarrier()).isEqualTo(DeliveryCarrier.LOTTE);
            assertThat(reload(owner).getTrackingNumber()).isEqualTo("111100002222");
        }

        @Test
        @DisplayName("없는 하위주문은 404 ORDER_GROUP_NOT_FOUND")
        void unknownGroup() throws Exception {
            updateShipment(999_999L, "CJ", "123412341234")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_GROUP_NOT_FOUND"));
        }
    }

    // ------------------------------------------------------------------ 보조

    /**
     * 한 주문 · 같은 브랜드의 하위주문 2번째 — 결제 경로로 만들려면 같은 브랜드 공구 2건이 동시에 진행돼야 해서,
     * 그룹 행만 덧붙이고 PAID 훅과 같은 전이(activate)로 NEW 에 올린다.
     */
    private OrderDeliveryGroup addSecondGroup(OrderDeliveryGroup first) {
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

    private static List<String[]> dummyRows(int count) {
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new String[]{"NOPE-%05d".formatted(i), "CJ대한통운", "9%011d".formatted(i)});
        }
        return rows;
    }

    private static ResultMatcher rowError(int rowNumber, String errorCode) {
        return result -> {
            rowField(rowNumber, "valid", false).match(result);
            rowField(rowNumber, "errorCode", errorCode).match(result);
        };
    }

    private static ResultMatcher rowField(int rowNumber, String field, Object expected) {
        return jsonPath("$.rows[?(@.rowNumber == " + rowNumber + ")]." + field, contains(expected));
    }

    private static ResultMatcher skippedCode(Long deliveryGroupId, String code) {
        return jsonPath("$.skipped[?(@.deliveryGroupId == " + deliveryGroupId + ")].code", contains(code));
    }
}
