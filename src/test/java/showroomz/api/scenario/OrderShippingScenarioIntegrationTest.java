package showroomz.api.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.support.IntegrationTest;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오 4절 — 송장·배송 이상·반송(E2E-D · E2E-E).
 *
 * <p>추적 연동 전(스텁)이라 배송 구간은 시나리오 5-2대로 추적 반영 서비스에 포트 결과를 넣어 재현한다.
 * 「24시간 경과」 같은 조건은 {@code shipped_at} 시각만 당기고, 상태는 반영 서비스가 바꾼다.
 */
@IntegrationTest
@DisplayName("[시나리오 E2E-D·E] 송장 · 배송 이상 · 반송")
class OrderShippingScenarioIntegrationTest extends OrderFlowTestSupport {

    // ================================================================== E2E-D

    @Nested
    @DisplayName("E2E-D 엑셀 일괄 · 배송 이상 · 송장 수정")
    class BulkInvoiceAndAlerts {

        @Test
        @DisplayName("[D-01·D-02] 양식 → 업로드 검증(상태 불변 · 신규 제외) → 채운 행으로 확정 — 수기 입력과 같은 확정 지점")
        void uploadParseThenRegister() throws Exception {
            OrderDeliveryGroup first = preparingGroup();
            OrderDeliveryGroup second = preparingGroup();
            OrderDeliveryGroup fresh = paidGroup();

            byte[] template = sellerGet(SELLER_ORDERS + "/shipments/template")
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            assertThat(readSheet(template).get(0)).containsExactly("주문번호", "택배사", "송장번호");

            String parsed = parseShipments(shipmentFile(List.of(
                    List.of(orderNumberOf(first), "CJ대한통운", "1111-2222-3333"),
                    List.of(orderNumberOf(second), "한진택배", "4444 5555 6666"),
                    List.of(orderNumberOf(fresh), "CJ대한통운", "777788889999"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRows").value(3))
                    .andExpect(jsonPath("$.validRows").value(2))
                    .andExpect(jsonPath("$.rows[0].valid").value(true))
                    .andExpect(jsonPath("$.rows[0].deliveryGroupId").value(first.getId()))
                    .andExpect(jsonPath("$.rows[0].carrier").value("CJ"))
                    .andExpect(jsonPath("$.rows[0].trackingNumber").value("111122223333"))
                    .andExpect(jsonPath("$.rows[1].carrier").value("HANJIN"))
                    .andExpect(jsonPath("$.rows[2].valid").value(false))
                    .andExpect(jsonPath("$.rows[2].rowNumber").value(4))
                    .andExpect(jsonPath("$.rows[2].errorCode").value("NEW_NOT_ALLOWED"))
                    .andExpect(jsonPath("$.rows[2].message").value("신규(준비 대기) 주문 · 준비 시작 전이라 송장을 등록할 수 없습니다."))
                    .andReturn().getResponse().getContentAsString();

            // 「채우기」는 상태를 바꾸지 않는다.
            assertThat(reloadGroup(first).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(reloadGroup(second).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.PREPARING);
            assertThat(reloadGroup(fresh).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
            assertThat(fulfillmentEvents(first)).doesNotContain("INVOICE_REGISTERED");

            sellerPost(SELLER_ORDERS + "/shipments", Map.of("rows", validRowsOf(parsed)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.succeeded").value(2))
                    .andExpect(jsonPath("$.skipped.length()").value(0));
            assertThat(reloadGroup(first).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPING);
            assertThat(reloadGroup(second).getTrackingNumber()).isEqualTo("444455556666");
        }

        @Test
        @DisplayName("[D-03] 업로드 분류 — 살아 있는 송장 중복(겹치는 주문 지목) · 파일 내 중복 · 이미 배송중 · 주문번호 없음 · 미지원 택배사")
        void uploadClassifiesEachRow() throws Exception {
            OrderDeliveryGroup shipped = shippingGroup("123412341234");
            OrderDeliveryGroup p1 = preparingGroup();
            OrderDeliveryGroup p2 = preparingGroup();
            OrderDeliveryGroup p3 = preparingGroup();

            parseShipments(shipmentFile(List.of(
                    List.of(orderNumberOf(p1), "CJ대한통운", "123412341234"),
                    List.of(orderNumberOf(p2), "CJ대한통운", "565656565656"),
                    List.of(orderNumberOf(p3), "CJ대한통운", "565656565656"),
                    List.of(orderNumberOf(shipped), "CJ대한통운", "999988887777"),
                    List.of("20990101-999999", "CJ대한통운", "121212121212"),
                    List.of(orderNumberOf(p1), "DHL", "343434343434"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.validRows").value(1))
                    .andExpect(jsonPath("$.rows[0].errorCode").value("INVOICE_DUPLICATE"))
                    .andExpect(jsonPath("$.rows[0].message").value(orderNumberOf(shipped) + "에 이미 등록된 번호입니다."))
                    .andExpect(jsonPath("$.rows[1].valid").value(true))
                    .andExpect(jsonPath("$.rows[2].errorCode").value("INVOICE_DUPLICATE"))
                    .andExpect(jsonPath("$.rows[2].message").value("파일 안에서 중복된 송장번호입니다."))
                    .andExpect(jsonPath("$.rows[3].errorCode").value("ALREADY_SHIPPED"))
                    .andExpect(jsonPath("$.rows[4].errorCode").value("ORDER_NOT_FOUND"))
                    .andExpect(jsonPath("$.rows[5].errorCode").value("CARRIER_INVALID"));
        }

        @Test
        @DisplayName("[D-04] 1,001행은 400 SHIPMENT_FILE_TOO_MANY_ROWS · xlsx 가 아닌 파일은 400 SHIPMENT_FILE_INVALID")
        void uploadLimits() throws Exception {
            List<List<String>> rows = new ArrayList<>();
            for (int i = 0; i < 1_001; i++) {
                rows.add(List.of("20990101-%06d".formatted(i), "CJ대한통운", "%012d".formatted(i)));
            }
            parseShipments(shipmentFile(rows))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SHIPMENT_FILE_TOO_MANY_ROWS"));

            mockMvc.perform(multipart(SELLER_ORDERS + "/shipments/parse")
                            .file(new MockMultipartFile("file", "송장.csv", "text/csv", "주문번호,택배사".getBytes()))
                            .header(AUTHORIZATION, brandToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SHIPMENT_FILE_INVALID"));
        }

        @Test
        @DisplayName("[D-05~D-07] 24시간 미조회 → 집화 확인 필요(1회 기록) · 송장 수정(배지 리셋 · shipped_at 불변) · 7일 정지 → 추적 정지 · 재개 → 해제")
        void pickupAlertThenFixThenStall() throws Exception {
            OrderDeliveryGroup group = shippingGroup("246824682468");
            LocalDateTime now = LocalDateTime.now();

            // 등록 직후에는 데이터가 없는 게 정상이다 — 경고하지 않는다.
            assertThat(track(group, null, now).getTrackingAlert()).isNull();

            // [D-05] 24시간 경과 + 이벤트 0건.
            backdateShippedAt(group, now.minusHours(25));
            assertThat(track(group, null, now).getTrackingAlert()).isEqualTo(TrackingAlert.PICKUP_UNCONFIRMED);
            track(group, null, now);
            assertThat(fulfillmentEvents(group)).filteredOn("PICKUP_UNCONFIRMED"::equals).hasSize(1);
            sellerSummary().andExpect(jsonPath("$.actionBar.deliveryIssue").value(1));
            sellerOrders("tab=SHIPPING")
                    .andExpect(jsonPath("$.content[0].overlays.trackingAlert").value("PICKUP_UNCONFIRMED"))
                    .andExpect(jsonPath("$.content[0].overlays.trackingAlertLabel").value("집화 확인 필요"));

            // [D-06] 다른 주문의 송장을 붙여넣은 경우 — 수정하면 배지가 풀리고 판정값은 그대로다.
            LocalDateTime shippedAt = reloadGroup(group).getShippedAt();
            updateShipment(group, "HANJIN", "135713571357").andExpect(status().isOk());
            OrderDeliveryGroup fixed = reloadGroup(group);
            assertThat(fixed.getTrackingAlert()).isNull();
            assertThat(fixed.getLastTrackingAt()).isNull();
            assertThat(fixed.getShippedAt()).isEqualTo(shippedAt);
            assertThat(fulfillmentHistoryRepository.findByDeliveryGroupId(group.getId()))
                    .filteredOn(h -> h.getEventType().name().equals("INVOICE_UPDATED"))
                    .singleElement()
                    .satisfies(h -> assertThat(h.getDetail()).isEqualTo("CJ대한통운 246824682468 → 한진택배 135713571357"));
            sellerSummary().andExpect(jsonPath("$.actionBar.deliveryIssue").value(0));

            // [D-07] 집화 후 7일 갱신 없음 — 추적 정지. 분실·누락 판정 수단은 없고 송장 수정만 열린다.
            assertThat(track(group, new TrackSnapshot(now.minusDays(8), null, false, false), now).getTrackingAlert())
                    .isEqualTo(TrackingAlert.STALLED);
            assertThat(fulfillmentEvents(group)).contains("TRACKING_STALLED");
            sellerOrder(group)
                    .andExpect(jsonPath("$.overlays.trackingAlert").value("STALLED"))
                    .andExpect(jsonPath("$.actions.canUpdateInvoice").value(true));

            // 이벤트가 다시 들어오면 배지가 풀린다(해제 이력은 남기지 않는다).
            assertThat(track(group, new TrackSnapshot(now, null, false, false), now).getTrackingAlert()).isNull();
        }

        @Test
        @Disabled("확인 대기 — §34-6 「발송기한 경과와 배송 이상은 별 축 · 한 행에 둘 다 뜰 수 있다(늦게 보낸 데다 픽업도 안 된 건)」와 "
                + "현행 판정(발송기한 경과는 신규·상품준비중에서만 · 34 설계서 0-5)이 어긋난다. 기획 쪽으로 확정되면 판정을 "
                + "「발송됐으면 shipped_at > ship_due_at」으로 바꾸고 이 테스트를 켠다.")
        @DisplayName("[D-08] 늦게 발송했고 집화도 안 된 건 — 한 행에 「발송기한 경과」와 「집화 확인 필요」가 함께 뜬다")
        void lateShipmentShowsBothBadges() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("UPDATE order_delivery_group SET ship_due_at = ? WHERE delivery_group_id = ?",
                    Timestamp.valueOf(now.minusDays(3)), group.getId());
            registerShipment(group, "CJ", "808080808080").andExpect(jsonPath("$.succeeded").value(1));
            backdateShippedAt(group, now.minusHours(25));
            track(group, null, now);

            sellerOrders("tab=SHIPPING")
                    .andExpect(jsonPath("$.content[0].overlays.trackingAlert").value("PICKUP_UNCONFIRMED"))
                    .andExpect(jsonPath("$.content[0].overlays.shipOverdue").value(true));
        }
    }

    // ================================================================== E2E-E

    @Nested
    @DisplayName("E2E-E 반송")
    class Returning {

        @Test
        @DisplayName("[E-01~E-04] 반송 감지 → 별 탭 · 브랜드 액션 없음 · 구매확정 제외 → 입고 감지 → 운영자 환불 큐 1회 · 환불 전까지 미종결")
        void returnDetectedThenCompleted() throws Exception {
            OrderDeliveryGroup group = shippingGroup("369036903690");
            LocalDateTime now = LocalDateTime.now();

            // [E-01] 반송 코드 감지 — 사유는 받지도 저장하지도 않는다(감지 시각만).
            OrderDeliveryGroup returning = track(group, new TrackSnapshot(now, null, true, false), now);
            assertThat(returning.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.RETURNING);
            assertThat(returning.getReturnDetectedAt()).isNotNull();
            sellerOrder(group)
                    .andExpect(jsonPath("$.status").value("RETURNING"))
                    .andExpect(jsonPath("$.statusTone").value("DANGER"))
                    .andExpect(jsonPath("$.timeline.returnDetectedAt").exists())
                    .andExpect(jsonPath("$.actions.canUpdateInvoice").value(false))
                    .andExpect(jsonPath("$.actions.canCancelDirectly").value(false));
            updateShipment(group, "CJ", "369036903691")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));
            sellerOrders("tab=RETURNING").andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()));
            sellerOrders("tab=SHIPPING").andExpect(jsonPath("$.content.length()").value(0));
            sellerSummary().andExpect(jsonPath("$.actionBar.deliveryIssue").value(1));

            // [E-02] 구매확정 타이머는 구조적으로 취소된다 — 배송완료가 아니므로 배치 대상이 아니다.
            assertThat(fulfillmentService.findIdsToConfirm(now.plusDays(30), 100)).doesNotContain(group.getId());
            assertThat(fulfillmentService.confirmPurchase(group.getId(), now.plusDays(30), now.plusDays(30))).isFalse();

            // [E-04] 환불 전까지 미종결 — 공구 정산 게이트가 이 건 때문에 닫혀 있다.
            sellerGet(GROUP_BUYS + "/" + groupBuy.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderClosure.unclosedCount").value(1));

            // [E-03] 반송 완료(입고) — 운영자 환불 큐에 1회만 편입. 이 주문으로 재발송하지 않는다.
            track(group, new TrackSnapshot(now, null, true, true), now);
            track(group, new TrackSnapshot(now, null, true, true), now);
            OrderDeliveryGroup completed = reloadGroup(group);
            assertThat(completed.getReturnCompletedAt()).isNotNull();
            assertThat(completed.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.RETURNING);
            assertThat(refundTasks(group)).containsExactly(
                    new RefundTask("RETURN_COMPLETED", CREAM_PRICE + DELIVERY_FEE, "PENDING"));
            assertThat(fulfillmentEvents(group)).containsSubsequence("RETURN_COMPLETED", "RETURN_DETECTED");
            sellerGet(GROUP_BUYS + "/" + groupBuy.getId())
                    .andExpect(jsonPath("$.orderClosure.unclosedCount").value(1));
        }
    }

    // ------------------------------------------------------------------ 픽스처

    private String orderNumberOf(OrderDeliveryGroup group) {
        return group.getOrder().getOrderNumber();
    }

    /** 송장 등록 시각만 소급한다(시나리오 5-2) — 상태는 추적 반영 서비스가 바꾼다. */
    private void backdateShippedAt(OrderDeliveryGroup group, LocalDateTime shippedAt) {
        jdbc.update("UPDATE order_delivery_group SET shipped_at = ? WHERE delivery_group_id = ?",
                Timestamp.valueOf(shippedAt), group.getId());
    }

    private List<Map<String, Object>> validRowsOf(String parseResponse) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode row : objectMapper.readTree(parseResponse).get("rows")) {
            if (row.get("valid").asBoolean()) {
                rows.add(Map.of("deliveryGroupId", row.get("deliveryGroupId").asLong(),
                        "carrier", row.get("carrier").asText(),
                        "trackingNumber", row.get("trackingNumber").asText()));
            }
        }
        return rows;
    }
}
