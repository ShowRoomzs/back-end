package showroomz.api.app.order;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.api.app.order.service.UserOrderQueryService;
import showroomz.api.seller.order.SellerOrderTestSupport;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.member.user.entity.Users;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.service.DeliveryArrivalEstimator;
import showroomz.domain.order.type.CancelRequestReason;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.dto.PageResponse;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소비자 앱 주문 내역 · 주문 상세(C10 설계서 6절 #1~15). 하위주문은 실제 결제 경로로 만들고 이행 상태는 브랜드 API·도메인 서비스로
 * 옮긴다 — 파트너센터 주문 테스트와 같은 배선이다. 반품·교환 표시(#16~24)는 클레임 모듈 뒤(설계서 P5)다.
 */
@IntegrationTest
class UserOrderQueryIntegrationTest extends SellerOrderTestSupport {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM.dd");

    @Autowired private UserOrderQueryService userOrderQueryService;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private DeliveryArrivalEstimator arrivalEstimator;

    @Nested
    @DisplayName("목록 범위")
    class Scope {

        @Test
        @DisplayName("결제 대기·결제 전 취소 주문은 목록에 없다 — 결제된 적 있는 주문만(#1)")
        void unpaidOrdersAreHidden() throws Exception {
            placeCardOrder(creamVariant, 1);
            Created cancelledBeforePay = placeCardOrder(creamVariant, 1);
            cancel(cancelledBeforePay.orderId()).andExpect(status().isOk());

            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", empty()))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));
        }

        @Test
        @DisplayName("최근 6개월 밖 주문은 빠지고, 최신 주문이 먼저다(#11)")
        void sixMonthWindowAndOrder() throws Exception {
            OrderDeliveryGroup old = paidGroup();
            OrderDeliveryGroup first = paidGroup();
            OrderDeliveryGroup second = paidGroup();
            jdbc.update("UPDATE orders SET created_at = ? WHERE order_id = ?",
                    LocalDateTime.now().minusMonths(6).minusDays(1), old.getOrder().getId());

            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].orderId").value(second.getOrder().getId()))
                    .andExpect(jsonPath("$.content[0].orderNumber").value(orderNumberOf(second)))
                    .andExpect(jsonPath("$.content[1].orderId").value(first.getOrder().getId()));
        }

        @Test
        @DisplayName("페이지 단위는 주문이다 — 항목 2줄 주문이 페이지 경계에서 쪼개지지 않는다(#11)")
        void pagesByOrder() throws Exception {
            paidGroup();
            OrderDeliveryGroup twoItems = paidTwoItemGroup();
            paidGroup();

            orderList("?page=1&size=2").andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[1].orderId").value(twoItems.getOrder().getId()))
                    .andExpect(jsonPath("$.content[1].items", hasSize(2)))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(3))
                    .andExpect(jsonPath("$.pageInfo.hasNext").value(true));
            orderList("?page=2&size=2").andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.pageInfo.hasNext").value(false));
        }

        @Test
        @DisplayName("size 가 1~50 밖이면 400(#11)")
        void pageSizeBounds() throws Exception {
            orderList("?size=0").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("페이지 크기는 1~50 사이로 입력해 주세요."));
            orderList("?size=51").andExpect(status().isBadRequest());
            orderList("?size=50").andExpect(status().isOk());
        }

        @Test
        @DisplayName("남의 주문은 목록에 없고 상세는 403(#12)")
        void othersOrders() throws Exception {
            OrderDeliveryGroup mine = paidGroup();
            Users stranger = createConsumer("stranger", "박타인");
            String strangerToken = bearerToken(stranger.getUsername(), RoleType.USER, stranger.getId());

            mockMvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, strangerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", empty()));
            mockMvc.perform(get(ORDERS + "/" + mine.getOrder().getId()).header(HttpHeaders.AUTHORIZATION, strangerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("항목 표시 상태")
    class ItemStatus {

        @Test
        @DisplayName("전 그룹 NEW · 결제 PAID — 「결제완료」 + 주문 취소 버튼, 상세 cancellable 과 일치(#10)")
        void paidItemIsCancellable() throws Exception {
            OrderDeliveryGroup group = paidGroup();

            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items[0].status").value("PAID"))
                    .andExpect(jsonPath("$.content[0].items[0].statusLabel").value("결제완료"))
                    .andExpect(jsonPath("$.content[0].items[0].statusTone").value("ACTIVE"))
                    .andExpect(jsonPath("$.content[0].items[0].statusSub")
                            .value(group.getShipDueAt().format(DAY) + " 발송 예정"))
                    .andExpect(jsonPath("$.content[0].items[0].brandName").value(group.getMarketName()))
                    .andExpect(jsonPath("$.content[0].items[0].amount").value(CREAM_PRICE))
                    .andExpect(jsonPath("$.content[0].items[0].amountLabel").value("27,200원"))
                    .andExpect(jsonPath("$.content[0].items[0].returnedQuantity").value(0))
                    .andExpect(jsonPath("$.content[0].items[0].claim").value(nullValue()))
                    .andExpect(jsonPath("$.content[0].items[0].actions", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].items[0].actions[0].type").value("CANCEL"))
                    .andExpect(jsonPath("$.content[0].items[0].actions[0].label").value("주문 취소"))
                    .andExpect(jsonPath("$.content[0].items[0].actions[0].enabled").value(true));
            detail(group.getOrder().getId()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancellable").value(true))
                    .andExpect(jsonPath("$.items[0].actions[0].type").value("CANCEL"));
        }

        @Test
        @DisplayName("결제 후 전액 취소 — 전 항목 「취소」 · 탈색 · 「환불 N원」 · 보조 「완료」(#2)")
        void fullyCancelledAfterPayment() throws Exception {
            OrderDeliveryGroup group = paidTwoItemGroup();
            cancel(group.getOrder().getId()).andExpect(status().isOk());

            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].items[0].status").value("CANCELLED"))
                    .andExpect(jsonPath("$.content[0].items[0].statusTone").value("MUTED"))
                    .andExpect(jsonPath("$.content[0].items[0].dimmed").value(true))
                    .andExpect(jsonPath("$.content[0].items[0].statusSub").value("완료"))
                    .andExpect(jsonPath("$.content[0].items[0].amountLabel").value("환불 27,200원"))
                    .andExpect(jsonPath("$.content[0].items[0].actions", empty()))
                    .andExpect(jsonPath("$.content[0].items[1].status").value("CANCELLED"))
                    .andExpect(jsonPath("$.content[0].items[1].amountLabel").value("환불 24,000원"));
        }

        @Test
        @DisplayName("한 주문 · 두 브랜드 — 항목마다 상태가 갈리고, 준비 시작된 그룹이 섞이면 NEW 항목에 취소 버튼이 없다(#3 · #9)")
        void statusSplitsByGroup() throws Exception {
            BrandFixture.Brand other = otherBrand();
            ProductVariant otherVariant = openOtherBrandGroupBuy(other);
            GroupBuy otherGroupBuy = groupBuyRepository.findAll().stream()
                    .filter(gb -> !gb.getId().equals(groupBuy.getId())).findFirst().orElseThrow();
            Cart cream = cartItem(creamVariant, 1);
            Cart otherCart = cartRepository.save(new Cart(consumer, otherVariant, otherGroupBuy, 1));
            Created created = created(createOrder(Map.of(
                    "idempotencyKey", newKey(),
                    "cartItemIds", List.of(cream.getId(), otherCart.getId()),
                    "payment", Map.of("method", "CARD", "cardIssuer", "SHINHAN")))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            complete(created.paymentId()).andExpect(status().isOk());

            // 둘 다 NEW — 두 항목 모두 취소 버튼.
            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items[0].actions[0].type").value("CANCEL"))
                    .andExpect(jsonPath("$.content[0].items[1].actions[0].type").value("CANCEL"));

            OrderDeliveryGroup mine = deliveryGroupRepository.findByOrderId(created.orderId()).stream()
                    .filter(g -> g.getMarketId().equals(brand.marketId())).findFirst().orElseThrow();
            shipped(prepared(reload(mine)), "CJ", "100020009001");

            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].items", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].items[0].status").value("SHIPPING"))
                    // 집화 전 — 날짜 없이 「배송중」만.
                    .andExpect(jsonPath("$.content[0].items[0].statusSub").value(nullValue()))
                    .andExpect(jsonPath("$.content[0].items[0].dates.arrivalDueDate").value(nullValue()))
                    .andExpect(jsonPath("$.content[0].items[1].status").value("PAID"))
                    .andExpect(jsonPath("$.content[0].items[1].brandName").value("타브랜드"))
                    .andExpect(jsonPath("$.content[0].items[1].actions", empty()));
            detail(created.orderId()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancellable").value(false))
                    .andExpect(jsonPath("$.items[1].actions", empty()));
        }

        @Test
        @DisplayName("배송중 — 집화가 확인되면 「도착 예정」(집화일 + 3배송일), 예정일이 지나도 미완료면 날짜를 지운다")
        void arrivalDueAfterPickup() throws Exception {
            OrderDeliveryGroup shipping = shippingGroup("100020009005");
            LocalDateTime pickedUpAt = LocalDateTime.now().withNano(0);
            track(shipping, new TrackSnapshot(pickedUpAt, null, false, false), batchNow());
            LocalDate due = arrivalEstimator.estimate(shipping.getCarrier(), pickedUpAt);

            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items[0].status").value("SHIPPING"))
                    .andExpect(jsonPath("$.content[0].items[0].statusSub").value(due.format(DAY) + " 도착 예정"))
                    .andExpect(jsonPath("$.content[0].items[0].dates.arrivalDueDate").value(due.toString()));
            // 이후 이벤트가 와도 집화 시각은 처음 것 그대로다.
            track(shipping, new TrackSnapshot(pickedUpAt.plusHours(5), null, false, false), batchNow());
            assertThat(reload(shipping).getPickedUpAt()).isEqualTo(pickedUpAt);

            jdbc.update("UPDATE order_delivery_group SET picked_up_at = ? WHERE delivery_group_id = ?",
                    pickedUpAt.minusDays(10), shipping.getId());
            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items[0].status").value("SHIPPING"))
                    .andExpect(jsonPath("$.content[0].items[0].statusSub").value(nullValue()))
                    .andExpect(jsonPath("$.content[0].items[0].dates.arrivalDueDate").value(nullValue()));
        }

        @Test
        @DisplayName("송장을 수정하면 집화 시각이 지워진다 — 새 송장 기준으로 다시 잡는다")
        void invoiceUpdateResetsPickup() throws Exception {
            OrderDeliveryGroup shipping = shippingGroup("100020009006");
            track(shipping, new TrackSnapshot(LocalDateTime.now().withNano(0), null, false, false), batchNow());
            assertThat(reload(shipping).getPickedUpAt()).isNotNull();

            updateShipment(shipping.getId(), "CJ", "100020009007").andExpect(status().isOk());

            assertThat(reload(shipping).getPickedUpAt()).isNull();
            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items[0].statusSub").value(nullValue()));
        }

        @Test
        @DisplayName("부분 취소 요청 — 요청 항목만 「취소 요청중」, 같은 그룹의 나머지는 「상품준비중」(#4)")
        void partialCancelRequest() throws Exception {
            OrderDeliveryGroup group = preparingTwoItemGroup();
            OrderCancelRequest request = seedCancelRequest(group, List.of(itemOf(group, creamVariant)),
                    CancelRequestReason.CHANGE_OF_MIND, null, LocalDateTime.now().withNano(0));

            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items[0].status").value("CANCEL_REQUESTED"))
                    .andExpect(jsonPath("$.content[0].items[0].statusLabel").value("취소 요청중"))
                    .andExpect(jsonPath("$.content[0].items[0].statusSub").value("브랜드 확인 중"))
                    .andExpect(jsonPath("$.content[0].items[0].cancelRequestId").value(request.getId()))
                    .andExpect(jsonPath("$.content[0].items[1].status").value("PREPARING"))
                    .andExpect(jsonPath("$.content[0].items[1].cancelRequestId").value(nullValue()))
                    .andExpect(jsonPath("$.content[0].items[1].actions", empty()));
        }

        @Test
        @DisplayName("반려 뒤 배송중 — 상태는 실제 그대로 + 반려 줄. 구매확정되면 반려 줄이 사라진다(#5 · #6)")
        void rejectionLineUntilConfirmed() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            OrderCancelRequest request = seedCancelRequest(group);
            reject(request.getId(), "이미 포장이 끝났습니다.").andExpect(status().isOk());
            OrderDeliveryGroup shipping = shipped(group, "CJ", "100020009002");

            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items[0].status").value("SHIPPING"))
                    .andExpect(jsonPath("$.content[0].items[0].cancelRejection.cancelRequestId").value(request.getId()))
                    .andExpect(jsonPath("$.content[0].items[0].cancelRejection.rejectedAt").isNotEmpty());

            LocalDateTime deliveredAt = LocalDateTime.now().withNano(0).minusDays(8);
            delivered(shipping, deliveredAt);
            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items[0].status").value("DELIVERED"))
                    .andExpect(jsonPath("$.content[0].items[0].statusSub")
                            .value(deliveredAt.plusDays(7).format(DAY) + " 구매확정 예정"))
                    .andExpect(jsonPath("$.content[0].items[0].cancelRejection.cancelRequestId").value(request.getId()));

            LocalDateTime now = batchNow();
            assertThat(fulfillmentService.confirmPurchase(group.getId(), now, now.minusDays(7))).isTrue();
            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items[0].status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.content[0].items[0].statusSub").value(now.format(DAY) + " 확정"))
                    .andExpect(jsonPath("$.content[0].items[0].cancelRejection").value(nullValue()));
        }

        @Test
        @DisplayName("반려 뒤 재요청 — 「취소 요청중」이고 반려 줄은 없다(#7)")
        void reRequestAfterRejection() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            reject(seedCancelRequest(group).getId(), "이미 포장이 끝났습니다.").andExpect(status().isOk());
            OrderCancelRequest again = seedCancelRequest(group);

            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items[0].status").value("CANCEL_REQUESTED"))
                    .andExpect(jsonPath("$.content[0].items[0].cancelRequestId").value(again.getId()))
                    .andExpect(jsonPath("$.content[0].items[0].cancelRejection").value(nullValue()));
        }

        @Test
        @DisplayName("요청 승인 — 환불 큐가 남아 있으면 「환불 처리 중」, 집행되면 「완료」(#8)")
        void refundPendingThenDone() throws Exception {
            OrderDeliveryGroup group = preparingGroup();
            approve(seedCancelRequest(group).getId()).andExpect(status().isOk());

            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items[0].status").value("CANCELLED"))
                    .andExpect(jsonPath("$.content[0].items[0].statusSub").value("환불 처리 중"))
                    .andExpect(jsonPath("$.content[0].items[0].amountLabel").value("환불 27,200원"));

            jdbc.update("UPDATE order_refund_task SET status = 'DONE' WHERE delivery_group_id = ?", group.getId());
            orderList().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].items[0].statusSub").value("완료"));
        }
    }

    @Nested
    @DisplayName("주문 상세 확장")
    class Detail {

        @Test
        @DisplayName("기존 필드는 그대로이고 평면 항목 · 마스킹 배송지 · 할인율이 더해진다(#13 · #14)")
        void detailKeepsExistingFields() throws Exception {
            OrderDeliveryGroup group = paidTwoItemGroup();

            detail(group.getOrder().getId()).andExpect(status().isOk())
                    // 기존 필드
                    .andExpect(jsonPath("$.orderNumber").value(orderNumberOf(group)))
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.groups", hasSize(1)))
                    .andExpect(jsonPath("$.groups[0].items", hasSize(2)))
                    .andExpect(jsonPath("$.groups[0].items[0].status").value("PAID"))
                    .andExpect(jsonPath("$.deliveryAddress.recipientName").value("김수민"))
                    .andExpect(jsonPath("$.deliveryAddress.phoneNumber").value("010-1234-5678"))
                    .andExpect(jsonPath("$.deliveryAddress.detailAddress").value("쇼룸타워 12층"))
                    .andExpect(jsonPath("$.summary.itemCount").value(2))
                    .andExpect(jsonPath("$.summary.totalAmount").value(CREAM_PRICE + SERUM_PRICE + DELIVERY_FEE))
                    .andExpect(jsonPath("$.payment.methodLabel").isNotEmpty())
                    .andExpect(jsonPath("$.cancellable").value(true))
                    // 추가 필드
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.itemCount").value(2))
                    .andExpect(jsonPath("$.items[0].status").value("PAID"))
                    .andExpect(jsonPath("$.items[0].brandName").value(group.getMarketName()))
                    .andExpect(jsonPath("$.maskedAddress.recipientName").value("김수*"))
                    .andExpect(jsonPath("$.maskedAddress.phoneNumber").value("010-****-5678"))
                    .andExpect(jsonPath("$.maskedAddress.address").value("서울 강남구 테헤란로 000"))
                    .andExpect(jsonPath("$.maskedAddress.detailAddress").value("******"))
                    .andExpect(jsonPath("$.maskedAddress.memo").value("문 앞에 놓아주세요"))
                    // (34,000 + 30,000) → (27,200 + 24,000) = 20%
                    .andExpect(jsonPath("$.summary.discountRate").value(20))
                    .andExpect(jsonPath("$.notices", empty()));
        }

        @Test
        @DisplayName("결제 전 주문도 열린다 — 항목은 「결제 대기」 · 버튼 없음")
        void pendingOrderDetail() throws Exception {
            Created created = placeCardOrder(creamVariant, 1);

            detail(created.orderId()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancellable").value(true))
                    .andExpect(jsonPath("$.items[0].status").value("PAYMENT_PENDING"))
                    .andExpect(jsonPath("$.items[0].actions", empty()));
        }

        @Test
        @DisplayName("배송완료 — 상세 보조 문구는 배송완료 시각, 상단에 구매확정 기한 안내")
        void deliveredDetail() throws Exception {
            LocalDateTime deliveredAt = LocalDateTime.now().withNano(0).minusDays(1);
            OrderDeliveryGroup group = delivered(shippingGroup("100020009003"), deliveredAt);

            detail(group.getOrder().getId()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].status").value("DELIVERED"))
                    .andExpect(jsonPath("$.items[0].statusSub")
                            .value(deliveredAt.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))))
                    .andExpect(jsonPath("$.notices", hasSize(1)))
                    .andExpect(jsonPath("$.notices[0].type").value("CONFIRM_DUE"))
                    .andExpect(jsonPath("$.notices[0].tone").value("ACTIVE"))
                    .andExpect(jsonPath("$.notices[0].date").value(deliveredAt.plusDays(7)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))));
        }
    }

    @Test
    @DisplayName("목록 쿼리 수는 주문 수에 비례하지 않는다 — 상태가 섞인 페이지에서 6회 이하(#15)")
    void listQueryCountIsFixed() throws Exception {
        paidGroup();
        paidTwoItemGroup();
        seedCancelRequest(preparingGroup());
        approve(seedCancelRequest(preparingGroup()).getId()).andExpect(status().isOk());
        shippingGroup("100020009004");
        cancel(paidGroup().getOrder().getId()).andExpect(status().isOk());

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            PageResponse<UserOrderDto.OrderCard> page = userOrderQueryService.getOrders(consumer.getId(), 1, 20);

            assertThat(page.getContent()).hasSize(6);
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    // ------------------------------------------------------------------ 보조

    private ResultActions orderList() throws Exception {
        return orderList("");
    }

    private ResultActions orderList(String query) throws Exception {
        return mockMvc.perform(get(ORDERS + query).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }
}
