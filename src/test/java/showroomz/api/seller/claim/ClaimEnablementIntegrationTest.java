package showroomz.api.seller.claim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S9 에서 켠 것(보강 시나리오 7-3절 ON-01 ~ ON-03) — 주문 관리 요약 두 칸의 집계 범위와 주문 상세의 반품 · 교환 버튼.
 * ON-04(상품 상세)는 {@code ProductServiceTest}, ON-05(셀러 가입)는 {@code SellerCompleteRegistrationRequestValidationTest}가
 * 본다. ON-06(이벤트 커밋 연동)은 받는 리스너가 생길 때 붙인다.
 */
@IntegrationTest
class ClaimEnablementIntegrationTest extends ClaimTestSupport {

    @Test
    @DisplayName("[ON-01] 요약 두 칸 — 내 마켓의 검수 단계(도착 + 입고 확인)와 재발송 대기(교환 + 거절 반송)만 센다. 남의 마켓 · 결제 대기는 빠지고, 반품·교환 요약 탭 카운트와 같다")
    void summaryCountsClaims() throws Exception {
        // 검수 단계 3 — 추적상 도착 1 + 입고 확인 2
        String invoice = newInvoice();
        OrderDeliveryGroup arrivedGroup = deliveredGroup(creamVariant, 1);
        Long arrived = requestClaim(arrivedGroup, showroomz.domain.order.type.ClaimType.RETURN,
                showroomz.domain.order.type.ClaimReason.CHANGE_OF_MIND, allItems(arrivedGroup), invoice)
                .claimIds().get(0);
        LocalDateTime at = LocalDateTime.now().minusHours(1).withNano(0);
        claimService.applyCollectionTracking(collectionIdOf(arrived), showroomz.domain.order.type.DeliveryCarrier.CJ,
                invoice, snapshotOf(at, at, scan(at, "브랜드", "배송완료", 6)), LocalDateTime.now());
        received(returnClaim(deliveredGroup(creamVariant, 1)));
        received(returnClaim(deliveredGroup(creamVariant, 1)));
        // 재발송 대기 2 — 교환 통과 1 + 거절 반송 1
        passed(exchangeClaim(deliveredGroup(creamVariant, 1), creamVariant));
        Long rejected = rejectedClaim(returnClaim(deliveredGroup(creamVariant, 1)));
        claimService.markReshipFeePaid(chargeIdOf(rejected), "clm-test-1", consumer.getId(), LocalDateTime.now());
        // 세지 않는 것 — 남의 마켓 재발송 대기 · 결제 대기 교환
        otherBrandReshipReadyClaim();
        ProductVariant refill = addSamePriceVariant(creamVariant, 5);
        OrderDeliveryGroup pendingGroup = deliveredGroup(creamVariant, 1);
        userPost(USER_CLAIMS, claimBody(pendingGroup, "EXCHANGE", "CHANGE_OF_MIND",
                items(pendingGroup).get(0).getId(), null, refill.getVariantId())).andExpect(status().isCreated());

        sellerGet(SELLER_ORDERS + "/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.actionBar.incomingCheck").value(3))
                .andExpect(jsonPath("$.actionBar.reshipExchange").value(2));
        sellerGet(SELLER_CLAIMS + "/summary")
                .andExpect(jsonPath("$.tabCounts.INSPECTION").value(3))
                .andExpect(jsonPath("$.tabCounts.RESHIP").value(2));
    }

    @Test
    @DisplayName("[ON-02] 교환할 수 있는 옵션의 재고가 하나도 없으면 [교환 요청]은 눌리지 않게 「교환 불가 (재고 없음)」로 내려간다")
    void exchangeSoldOutLabel() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        setStock(creamVariant, 0);

        detail(group.getOrder().getId())
                .andExpect(jsonPath("$.items[0].actions[2].type").value("EXCHANGE_REQUEST"))
                .andExpect(jsonPath("$.items[0].actions[2].enabled").value(false))
                .andExpect(jsonPath("$.items[0].actions[2].label").value("교환 불가 (재고 없음)"))
                .andExpect(jsonPath("$.items[0].actions[1].type").value("RETURN_REQUEST"))
                .andExpect(jsonPath("$.items[0].actions[1].enabled").value(true));

        setStock(creamVariant, 1);
        detail(group.getOrder().getId())
                .andExpect(jsonPath("$.items[0].actions[2].enabled").value(true))
                .andExpect(jsonPath("$.items[0].actions[2].label").value("교환 요청"));
    }

    @Test
    @DisplayName("[ON-03] 반품 · 교환 버튼이 없는 상태 — 구매확정은 배송 조회만 · 반송중은 아무것도 · 전량 반품은 [반품 상세]만")
    void noClaimActions() throws Exception {
        OrderDeliveryGroup confirmed = deliveredGroup(creamVariant, 1, LocalDateTime.now().minusDays(8));
        assertThat(fulfillmentService.confirmIfDue(confirmed.getId(), LocalDateTime.now())).isTrue();
        detail(confirmed.getOrder().getId())
                .andExpect(jsonPath("$.items[0].actions[*].type", contains("TRACK_DELIVERY")));

        OrderDeliveryGroup returning = returning(shippingGroup(newInvoice()));
        detail(returning.getOrder().getId()).andExpect(jsonPath("$.items[0].actions", empty()));

        OrderDeliveryGroup returned = deliveredGroup(creamVariant, 1);
        passed(returnClaim(returned));
        claimService.completeRefund(refundTaskIds(returned).get(0), CREAM_PRICE, 1L, LocalDateTime.now());
        detail(returned.getOrder().getId())
                .andExpect(jsonPath("$.items[0].status").value("RETURNED"))
                .andExpect(jsonPath("$.items[0].actions[*].type", contains("CLAIM_DETAIL")));
        userGet(USER_CLAIMS + "/form?orderProductId=" + items(returned).get(0).getId() + "&type=RETURN")
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_ELIGIBLE"));
    }
}
