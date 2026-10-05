package showroomz.api.seller.claim;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 재발송 · 회수 송장의 중복 · 형식(보강 시나리오 4절 IV-01 ~ IV-08). 판정 대기 N3 에 걸린 방향(주문 송장 → 클레임 송장 ·
 * 회수 송장)은 권장안을 기대값으로 두고 비활성으로 보관한다.
 */
@IntegrationTest
class ClaimInvoiceDuplicateIntegrationTest extends ClaimTestSupport {

    @Test
    @DisplayName("[IV-01] 재발송 송장이 내 마켓의 배송 중인 주문 송장과 같으면 그 행만 제외 — 겹치는 주문번호를 알려 준다")
    void duplicatesOwnOrderInvoice() throws Exception {
        String invoice = newInvoice();
        OrderDeliveryGroup shipping = shippingGroup(invoice);
        Long claimId = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));

        registerReship(claimId, "CJ", invoice)
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.skipped[0].code").value("INVOICE_DUPLICATE"))
                .andExpect(jsonPath("$.skipped[0].message", containsString(orderNumberOf(shipping))));
        assertThat(claimStatus(claimId)).isEqualTo("RESHIP_READY");
    }

    @Test
    @DisplayName("[IV-02] 남의 마켓 주문 송장과 같으면 제외하되 번호는 숨긴다")
    void duplicatesOtherMarketInvoiceHidden() throws Exception {
        BrandFixture.Brand other = otherBrand();
        String otherToken = sellerToken(other.seller());
        ProductVariant otherVariant = openOtherBrandGroupBuy(other);
        OrderDeliveryGroup otherGroup = paidOtherBrandGroup(other, otherVariant);
        String invoice = newInvoice();
        sellerPost(otherToken, SELLER_ORDERS + "/prepare-start", Map.of("deliveryGroupIds", List.of(otherGroup.getId())))
                .andExpect(jsonPath("$.succeeded").value(1));
        sellerPost(otherToken, SELLER_ORDERS + "/shipments",
                Map.of("rows", List.of(shipmentRow(otherGroup.getId(), "CJ", invoice))))
                .andExpect(jsonPath("$.succeeded").value(1));
        Long claimId = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));

        registerReship(claimId, "CJ", invoice)
                .andExpect(jsonPath("$.skipped[0].code").value("INVOICE_DUPLICATE"))
                .andExpect(jsonPath("$.skipped[0].message").value("다른 주문에 이미 등록된 번호입니다."));
    }

    @Test
    @DisplayName("[IV-03] 종결된 주문(구매확정) · 종결된 재발송이 쓰던 번호는 다시 쓸 수 있다")
    void closedInvoiceReusable() throws Exception {
        String confirmedInvoice = newInvoice();
        OrderDeliveryGroup confirmed = delivered(shipped(prepared(paidGroup(creamVariant, 1)), "CJ", confirmedInvoice),
                LocalDateTime.now().minusDays(8).withNano(0));
        assertThat(fulfillmentService.confirmIfDue(confirmed.getId(), LocalDateTime.now())).isTrue();

        String reshipInvoice = newInvoice();
        Long done = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));
        registerReship(done, "CJ", reshipInvoice).andExpect(jsonPath("$.succeeded").value(1));
        LocalDateTime at = LocalDateTime.now().withNano(0);
        claimService.applyReshipTracking(done, DeliveryCarrier.CJ, reshipInvoice,
                snapshotOf(at, at, scan(at, "강남", "배송완료", 6)), at);
        assertThat(claimStatus(done)).isEqualTo("COMPLETED");

        Long first = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));
        Long second = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));
        registerReship(first, "CJ", confirmedInvoice).andExpect(jsonPath("$.succeeded").value(1));
        registerReship(second, "CJ", reshipInvoice).andExpect(jsonPath("$.succeeded").value(1));
    }

    @Test
    @DisplayName("[IV-04] 재발송 송장 수정 — 자기 번호 그대로 · 택배사만 바꾸는 것은 중복이 아니다")
    void updateWithOwnNumber() throws Exception {
        Long claimId = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));
        String invoice = newInvoice();
        registerReship(claimId, "CJ", invoice).andExpect(jsonPath("$.succeeded").value(1));

        sellerPatch(SELLER_CLAIMS + "/" + claimId + "/reshipment", Map.of("carrier", "CJ", "trackingNumber", invoice))
                .andExpect(status().isOk());
        sellerPatch(SELLER_CLAIMS + "/" + claimId + "/reshipment",
                Map.of("carrier", "HANJIN", "trackingNumber", invoice))
                .andExpect(status().isOk());

        assertThat(claimRow(claimId)).containsEntry("reship_carrier", "HANJIN")
                .containsEntry("reship_tracking_number", invoice);
    }

    @Disabled("판정 대기 — 보강 시나리오 N3. 현행은 주문 송장 등록이 클레임 송장을 보지 않는다")
    @Test
    @DisplayName("[IV-05 · IV-06] 주문 송장 등록이 재발송 중인 클레임 송장과 겹치면 제외 · 회수 송장은 겹쳐도 받는다(권장안)")
    void orderInvoiceAgainstReship() throws Exception {
        Long claimId = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));
        String invoice = newInvoice();
        registerReship(claimId, "CJ", invoice).andExpect(jsonPath("$.succeeded").value(1));
        OrderDeliveryGroup preparing = preparingGroup();

        registerShipment(preparing.getId(), "CJ", invoice)
                .andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.skipped[0].code").value("INVOICE_DUPLICATE"));

        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group), invoice);
        assertThat(count("order_claim_collection")).isEqualTo(2);
    }

    @Test
    @DisplayName("[IV-07] 추적 연동이 켜져 형식 판정이 INVALID 면 — 재발송 송장은 그 행만 · 회수 송장은 요청을 거부한다")
    void invalidFormatWhenTrackerEnabled() throws Exception {
        Long claimId = passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        Object target = AopTestUtils.getTargetObject(claimService);
        Object original = ReflectionTestUtils.getField(target, "tracker");
        ReflectionTestUtils.setField(target, "tracker", new InvalidTracker());
        try {
            registerReship(claimId, "CJ", newInvoice())
                    .andExpect(jsonPath("$.succeeded").value(0))
                    .andExpect(jsonPath("$.skipped[0].code").value("INVOICE_FORMAT_INVALID"));

            Map<String, Object> body = claimBody(group, "RETURN", "CHANGE_OF_MIND", items(group).get(0).getId(), null,
                    null);
            body.put("invoice", Map.of("carrier", "CJ", "trackingNumber", newInvoice()));
            userPost(USER_CLAIMS, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVOICE_FORMAT_INVALID"));
        } finally {
            ReflectionTestUtils.setField(target, "tracker", original);
        }
        assertThat(claimStatus(claimId)).isEqualTo("RESHIP_READY");
        assertThat(count("order_claim")).isEqualTo(1);
    }

    @Test
    @DisplayName("[IV-08] 재발송 등록 — 빈 목록 · 500건 초과는 400")
    void registerBounds() throws Exception {
        sellerPost(SELLER_CLAIMS + "/reshipments", Map.of("items", List.of()))
                .andExpect(status().isBadRequest());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(reshipRow((long) i + 1, "CJ", "1234567890" + String.format("%02d", i % 100)));
        }
        sellerPost(SELLER_CLAIMS + "/reshipments", Map.of("items", rows)).andExpect(status().isBadRequest());
    }

    /** 연동이 켜진 상태의 형식 판정 — 무엇이든 INVALID. */
    private static final class InvalidTracker implements DeliveryTrackerPort {
        @Override
        public ValidationResult validateInvoice(DeliveryCarrier carrier, String trackingNumber) {
            return ValidationResult.INVALID;
        }

        @Override
        public Optional<TrackSnapshot> track(DeliveryCarrier carrier, String trackingNumber) {
            return Optional.empty();
        }
    }
}
