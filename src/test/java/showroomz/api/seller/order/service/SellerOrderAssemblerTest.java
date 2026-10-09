package showroomz.api.seller.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.seller.order.dto.SellerOrderDetailResponse;
import showroomz.api.seller.order.dto.SellerOrderListItem;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.DeliveryTrackingEvent;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderCancelRequestItem;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.type.CancelRequestReason;
import showroomz.domain.order.type.DeliveredSource;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderBadgeTone;
import showroomz.domain.order.type.OrderProductStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("주문 응답 조립 — 파생값(발송기한 경과 · 구매확정 D-N)과 버튼 노출 규칙(34 설계서 0-5 · 4-1 · 4-3)")
class SellerOrderAssemblerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 15, 0);
    private static final int CONFIRM_DAYS = 7;

    private final SellerOrderAssembler assembler = new SellerOrderAssembler();

    // ------------------------------------------------------------------ 발송기한 경과

    @Test
    @DisplayName("발송기한 경과 = 작업 큐(NEW·PREPARING) ∧ 기한 < 지금 — 기한과 같은 시각은 아직 경과가 아니다")
    void shipOverdue() {
        assertThat(overdue(FulfillmentStatus.NEW, NOW.minusMinutes(1))).isTrue();
        assertThat(overdue(FulfillmentStatus.PREPARING, NOW.minusMinutes(1))).isTrue();
        assertThat(overdue(FulfillmentStatus.NEW, NOW)).isFalse();
        assertThat(overdue(FulfillmentStatus.NEW, NOW.plusHours(1))).isFalse();
        assertThat(overdue(FulfillmentStatus.NEW, null)).isFalse();
    }

    @Test
    @DisplayName("송장이 등록된 뒤에는 기한이 지나 있어도 경과로 보지 않는다 — 판정값은 송장 등록 시각이다")
    void noOverdueAfterShipping() {
        for (FulfillmentStatus status : List.of(FulfillmentStatus.SHIPPING, FulfillmentStatus.RETURNING,
                FulfillmentStatus.DELIVERED, FulfillmentStatus.CONFIRMED, FulfillmentStatus.CANCELLED)) {
            assertThat(overdue(status, NOW.minusDays(3))).as(status.name()).isFalse();
        }
    }

    // ------------------------------------------------------------------ 구매확정 D-N

    @Test
    @DisplayName("구매확정 D-N 은 남은 시간을 일 단위로 올림 · 기한이 지나면 0 · 배송완료가 아니면 null")
    void confirmRemainingDays() {
        assertThat(remainingDays(NOW)).isEqualTo(7);
        assertThat(remainingDays(NOW.minusDays(5).minusHours(12))).isEqualTo(2);
        assertThat(remainingDays(NOW.minusDays(6).minusHours(23))).isEqualTo(1);
        assertThat(remainingDays(NOW.minusDays(7))).isZero();
        assertThat(remainingDays(NOW.minusDays(9))).isZero();

        OrderDeliveryGroup shipping = group(FulfillmentStatus.SHIPPING);
        ReflectionTestUtils.setField(shipping, "deliveredAt", NOW);
        assertThat(listItem(shipping, items(), null).confirmRemainingDays()).isNull();
        assertThat(listItem(group(FulfillmentStatus.DELIVERED), items(), null).confirmRemainingDays()).isNull();
    }

    // ------------------------------------------------------------------ 목록 행

    @Test
    @DisplayName("상품 요약 — 1건이면 상품명 · 여러 건이면 「첫 상품 외 N건」 · 항목 없으면 null")
    void productSummary() {
        OrderDeliveryGroup group = group(FulfillmentStatus.NEW);

        assertThat(listItem(group, List.of(item(1L, "크림", 2, 27_200, OrderProductStatus.PAID)), null)
                .productSummary()).isEqualTo("크림");
        SellerOrderListItem three = listItem(group, List.of(
                item(1L, "크림", 2, 27_200, OrderProductStatus.PAID),
                item(2L, "세럼", 1, 24_000, OrderProductStatus.PAID),
                item(3L, "토너", 3, 18_000, OrderProductStatus.PAID)), null);
        assertThat(three.productSummary()).isEqualTo("크림 외 2건");
        assertThat(three.totalQuantity()).isEqualTo(6);
        SellerOrderListItem empty = listItem(group, List.of(), null);
        assertThat(empty.productSummary()).isNull();
        assertThat(empty.totalQuantity()).isZero();
    }

    @Test
    @DisplayName("행 공통값 — 배지 톤은 서버가 · 결제금액 = 판매가 합 + 배송비 · 정산은 null · 출처 라벨 병기")
    void rowCommonValues() {
        OrderDeliveryGroup delivered = group(FulfillmentStatus.DELIVERED);
        ReflectionTestUtils.setField(delivered, "deliveredAt", NOW.minusDays(1));
        ReflectionTestUtils.setField(delivered, "deliveredSource", DeliveredSource.TRACKER);

        SellerOrderListItem row = listItem(delivered, items(), null);

        assertThat(row.statusLabel()).isEqualTo("배송완료");
        assertThat(row.statusTone()).isEqualTo(OrderBadgeTone.SUCCESS);
        assertThat(row.paidAmount()).isEqualTo(51_200 + 3_000);
        assertThat(row.settlementLabel()).isNull();
        assertThat(row.deliveredSourceLabel()).isEqualTo("자동 확인");
        assertThat(row.cancelRequest()).isNull();
        assertThat(row.overlays().cancelRequested()).isFalse();
    }

    @Test
    @DisplayName("항목 라벨 — 취소 항목은 「취소」 · 구매확정 항목은 「구매확정」 · 나머지는 그룹 상태 라벨")
    void itemLabels() {
        SellerOrderListItem row = listItem(group(FulfillmentStatus.PREPARING), List.of(
                item(1L, "크림", 1, 27_200, OrderProductStatus.CANCELLED),
                item(2L, "세럼", 1, 24_000, OrderProductStatus.PAID),
                item(3L, "토너", 1, 18_000, OrderProductStatus.PURCHASE_CONFIRMED)), null);

        assertThat(row.items()).extracting(SellerOrderListItem.Item::itemStatusLabel)
                .containsExactly("취소", "상품준비중", "구매확정");
        assertThat(row.items()).extracting(SellerOrderListItem.Item::cancelled).containsExactly(true, false, false);
        assertThat(row.items().get(1).amount()).isEqualTo(24_000);
    }

    @Test
    @DisplayName("취소 요청 요약 — 「N건 중 M건 요청 · 남은 K건 발송 대기」(이미 취소된 항목은 남은 수에서 빠진다) · 경과 시간")
    void cancelRequestSummary() {
        OrderProduct cream = item(1L, "크림", 1, 27_200, OrderProductStatus.PAID);
        OrderProduct serum = item(2L, "세럼", 1, 24_000, OrderProductStatus.PAID);
        OrderProduct toner = item(3L, "토너", 1, 18_000, OrderProductStatus.CANCELLED);
        OrderCancelRequest request = request(NOW.minusHours(5), cream);

        SellerOrderListItem row = listItem(group(FulfillmentStatus.PREPARING), List.of(cream, serum, toner), request);

        assertThat(row.overlays().cancelRequested()).isTrue();
        assertThat(row.cancelRequest().summary()).isEqualTo("3건 중 1건 요청 · 남은 1건 발송 대기");
        assertThat(row.cancelRequest().elapsedHours()).isEqualTo(5);
        assertThat(row.cancelRequest().reasonLabel()).isEqualTo("기타");
        assertThat(row.cancelRequest().reasonDetail()).isEqualTo("사이즈를 잘못 골랐어요");
        assertThat(row.items()).extracting(SellerOrderListItem.Item::cancelRequested).containsExactly(true, false, false);
        // 요청 시각이 미래(시계 차)여도 음수 경과는 내리지 않는다.
        assertThat(listItem(group(FulfillmentStatus.PREPARING), List.of(cream), request(NOW.plusHours(1), cream))
                .cancelRequest().elapsedHours()).isZero();
    }

    // ------------------------------------------------------------------ 상세

    @Test
    @DisplayName("버튼 노출 규칙 — 상태 × 취소 요청 매트릭스(준비 시작 · 송장 등록 · 송장 수정 · 직권 취소 · 요청 처리)")
    void actionsMatrix() {
        assertActions(FulfillmentStatus.NEW, false, true, false, false, true, false);
        assertActions(FulfillmentStatus.NEW, true, false, false, false, false, true);
        assertActions(FulfillmentStatus.PREPARING, false, false, true, false, true, false);
        assertActions(FulfillmentStatus.PREPARING, true, false, false, false, false, true);
        assertActions(FulfillmentStatus.SHIPPING, false, false, false, true, false, false);
        for (FulfillmentStatus status : List.of(FulfillmentStatus.RETURNING, FulfillmentStatus.DELIVERED,
                FulfillmentStatus.CONFIRMED, FulfillmentStatus.CANCELLED)) {
            assertActions(status, false, false, false, false, false, false);
        }
    }

    @Test
    @DisplayName("작업 큐 밖에 남은 검토 중 요청은 처리 버튼을 열지 않는다 — 발송 뒤 취소는 반품 경로다(CX-01 · CX-05)")
    void pendingRequestOutsideWorkQueueIsNotDecidable() {
        assertActions(FulfillmentStatus.SHIPPING, true, false, false, true, false, false);
        for (FulfillmentStatus status : List.of(FulfillmentStatus.RETURNING, FulfillmentStatus.DELIVERED,
                FulfillmentStatus.CONFIRMED, FulfillmentStatus.CANCELLED)) {
            assertActions(status, true, false, false, false, false, false);
        }
    }

    @Test
    @DisplayName("금액 요약 — 확정 취소분 = 취소 항목 합 · 요청분 = 검토 중 요청의 환불 예정 합 · 총액 = 판매가 합 + 배송비")
    void detailAmounts() {
        OrderProduct cream = item(1L, "크림", 1, 27_200, OrderProductStatus.CANCELLED);
        OrderProduct serum = item(2L, "세럼", 2, 12_000, OrderProductStatus.PAID);

        SellerOrderDetailResponse withRequest = detail(group(FulfillmentStatus.PREPARING), List.of(cream, serum),
                request(NOW.minusHours(1), serum));
        SellerOrderDetailResponse withoutRequest = detail(group(FulfillmentStatus.PREPARING), List.of(cream, serum), null);

        assertThat(withRequest.amounts().totalAmount()).isEqualTo(51_200 + 3_000);
        assertThat(withRequest.amounts().cancelledAmount()).isEqualTo(27_200);
        assertThat(withRequest.amounts().cancelRequestedAmount()).isEqualTo(24_000);
        assertThat(withoutRequest.amounts().cancelRequestedAmount()).isNull();
        assertThat(withoutRequest.cancelRequest()).isNull();
    }

    @Test
    @DisplayName("취소 요청 블록 — 준비 시작 후 경과는 요청 시각 기준 · 준비 시작 전 요청이면 null")
    void cancelRequestBlockHoursSincePrepare() {
        OrderProduct cream = item(1L, "크림", 1, 27_200, OrderProductStatus.PAID);
        OrderDeliveryGroup preparing = group(FulfillmentStatus.PREPARING);
        ReflectionTestUtils.setField(preparing, "prepareStartedAt", NOW.minusHours(10));

        SellerOrderDetailResponse.CancelRequestBlock block =
                detail(preparing, List.of(cream), request(NOW.minusHours(4), cream)).cancelRequest();
        assertThat(block.hoursSincePrepareStart()).isEqualTo(6);
        assertThat(block.totalRefundAmount()).isEqualTo(27_200);
        assertThat(block.remainingItemCount()).isZero();

        assertThat(detail(group(FulfillmentStatus.NEW), List.of(cream), request(NOW.minusHours(4), cream))
                .cancelRequest().hoursSincePrepareStart()).isNull();
    }

    @Test
    @DisplayName("목록 행 — 송장 등록 시각을 내린다. 추적 기록 전(집화 확인 필요)에는 최종 갱신이 비어 이 값으로 대신 그린다")
    void listShippedAt() {
        OrderDeliveryGroup shipping = group(FulfillmentStatus.SHIPPING);
        ReflectionTestUtils.setField(shipping, "shippedAt", NOW.minusDays(1));

        SellerOrderListItem row = listItem(shipping, items(), null);

        assertThat(row.shippedAt()).isEqualTo(NOW.minusDays(1));
        assertThat(row.lastTrackingAt()).isNull();
        assertThat(listItem(group(FulfillmentStatus.PREPARING), items(), null).shippedAt()).isNull();
    }

    @Test
    @DisplayName("취소 요청 블록 — 요청자(실명, 없으면 닉네임) · 수취인과 같은지 · 공구 상태(백필 주문은 null)")
    void cancelRequestBlockRequesterAndGroupBuy() {
        OrderProduct cream = item(1L, "크림", 1, 27_200, OrderProductStatus.PAID);

        OrderDeliveryGroup sameName = group(FulfillmentStatus.PREPARING);
        ReflectionTestUtils.setField(sameName.getOrder(), "user", user("김수민", "수민이"));
        ReflectionTestUtils.setField(sameName, "groupBuy", GroupBuy.builder().status(GroupBuyStatus.IN_PROGRESS).build());
        SellerOrderDetailResponse.CancelRequestBlock same =
                detail(sameName, List.of(cream), request(NOW.minusHours(1), cream)).cancelRequest();
        assertThat(same.requesterName()).isEqualTo("김수민");
        assertThat(same.requesterIsRecipient()).isTrue();
        assertThat(same.groupBuyStatus()).isEqualTo(GroupBuyStatus.IN_PROGRESS);
        assertThat(same.groupBuyStatusLabel()).isEqualTo("진행중");

        OrderDeliveryGroup nicknameOnly = group(FulfillmentStatus.PREPARING);
        ReflectionTestUtils.setField(nicknameOnly.getOrder(), "user", user(" ", "수민이"));
        SellerOrderDetailResponse.CancelRequestBlock other =
                detail(nicknameOnly, List.of(cream), request(NOW.minusHours(1), cream)).cancelRequest();
        assertThat(other.requesterName()).isEqualTo("수민이");
        assertThat(other.requesterIsRecipient()).isFalse();
        assertThat(other.groupBuyStatus()).isNull();
        assertThat(other.groupBuyStatusLabel()).isNull();
    }

    @Test
    @DisplayName("우 레일 마지막 스캔 — 위치·문구는 택배사 원문 그대로 · 스캔이 없으면 null")
    void lastTrackingScan() {
        OrderDeliveryGroup shipping = group(FulfillmentStatus.SHIPPING);
        DeliveryTrackingEvent scan = DeliveryTrackingEvent.builder()
                .carrier(DeliveryCarrier.CJ).trackingNumber("640012345678").seq(3)
                .occurredAt(NOW.minusHours(2)).location("대전 허브").description("출발").level(3).build();

        SellerOrderDetailResponse.Timeline timeline = assembler.toDetail(shipping, null, null, items(), null,
                List.of(), NOW, CONFIRM_DAYS, SellerOrderAssembler.ClaimOverlay.NONE, scan).timeline();
        assertThat(timeline.lastTrackingLocation()).isEqualTo("대전 허브");
        assertThat(timeline.lastTrackingDescription()).isEqualTo("출발");

        SellerOrderDetailResponse.Timeline none = detail(shipping, items(), null).timeline();
        assertThat(none.lastTrackingLocation()).isNull();
        assertThat(none.lastTrackingDescription()).isNull();
    }

    @Test
    @DisplayName("구매확정 예정 = 배송완료 + 설정 일수 — 약관 개정은 설정값으로 따라간다")
    void confirmDueAtFollowsConfig() {
        OrderDeliveryGroup delivered = group(FulfillmentStatus.DELIVERED);
        ReflectionTestUtils.setField(delivered, "deliveredAt", NOW.minusDays(1));

        assertThat(assembler.toDetail(delivered, null, null, items(), null, List.of(), NOW, 7)
                .timeline().confirmDueAt()).isEqualTo(NOW.minusDays(1).plusDays(7));
        assertThat(assembler.toDetail(delivered, null, null, items(), null, List.of(), NOW, 10)
                .timeline().confirmDueAt()).isEqualTo(NOW.minusDays(1).plusDays(10));
        assertThat(detail(group(FulfillmentStatus.SHIPPING), items(), null).timeline().confirmDueAt()).isNull();
    }

    // ------------------------------------------------------------------ 보조

    private boolean overdue(FulfillmentStatus status, LocalDateTime shipDueAt) {
        OrderDeliveryGroup group = group(status);
        ReflectionTestUtils.setField(group, "shipDueAt", shipDueAt);
        return listItem(group, items(), null).overlays().shipOverdue();
    }

    private Integer remainingDays(LocalDateTime deliveredAt) {
        OrderDeliveryGroup group = group(FulfillmentStatus.DELIVERED);
        ReflectionTestUtils.setField(group, "deliveredAt", deliveredAt);
        return listItem(group, items(), null).confirmRemainingDays();
    }

    private void assertActions(FulfillmentStatus status, boolean pendingCancel, boolean prepareStart,
                               boolean registerInvoice, boolean updateInvoice, boolean cancelDirectly,
                               boolean decideCancelRequest) {
        OrderProduct cream = item(1L, "크림", 1, 27_200, OrderProductStatus.PAID);
        SellerOrderDetailResponse.Actions actions = detail(group(status), List.of(cream),
                pendingCancel ? request(NOW.minusHours(1), cream) : null).actions();
        String label = status + (pendingCancel ? " + 취소 요청" : "");
        assertThat(actions.canPrepareStart()).as(label + " · 준비 시작").isEqualTo(prepareStart);
        assertThat(actions.canRegisterInvoice()).as(label + " · 송장 등록").isEqualTo(registerInvoice);
        assertThat(actions.canUpdateInvoice()).as(label + " · 송장 수정").isEqualTo(updateInvoice);
        assertThat(actions.canCancelDirectly()).as(label + " · 직권 취소").isEqualTo(cancelDirectly);
        assertThat(actions.canDecideCancelRequest()).as(label + " · 요청 처리").isEqualTo(decideCancelRequest);
    }

    private SellerOrderListItem listItem(OrderDeliveryGroup group, List<OrderProduct> items,
                                         OrderCancelRequest pending) {
        return assembler.toListItem(group, "20261003-000001", NOW.minusDays(1), "김수민", "글로우 크림 앵콜 공구",
                items, pending, NOW, CONFIRM_DAYS);
    }

    private SellerOrderDetailResponse detail(OrderDeliveryGroup group, List<OrderProduct> items,
                                             OrderCancelRequest pending) {
        return assembler.toDetail(group, "글로우 크림 앵콜 공구", "카드", items, pending, List.of(), NOW, CONFIRM_DAYS);
    }

    private static OrderDeliveryGroup group(FulfillmentStatus status) {
        Order order = Order.create(null, "20261003-000001", new Order.Totals(51_200, 0, 3_000, 54_200),
                new Order.AddressSnapshot("김수민", "010-1234-5678", "06234", "서울 강남구 테헤란로 000", "12층"),
                "문 앞에 놓아주세요", "크림 외 1건", "key", NOW.plusMinutes(30));
        OrderDeliveryGroup group = OrderDeliveryGroup.builder()
                .order(order).productTotal(51_200).deliveryFee(3_000).build();
        ReflectionTestUtils.setField(group, "id", 1L);
        ReflectionTestUtils.setField(group, "fulfillmentStatus", status);
        ReflectionTestUtils.setField(group, "subOrderNumber", "20261003-000001-01");
        return group;
    }

    private static Users user(String name, String nickname) {
        Users user = new Users();
        user.setName(name);
        user.setNickname(nickname);
        return user;
    }

    private static List<OrderProduct> items() {
        return List.of(item(1L, "크림", 1, 27_200, OrderProductStatus.PAID));
    }

    private static OrderProduct item(Long id, String name, int quantity, int price, OrderProductStatus status) {
        OrderProduct item = OrderProduct.builder()
                .productName(name).optionName("기본").quantity(quantity).price(price).status(status).build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static OrderCancelRequest request(LocalDateTime requestedAt, OrderProduct... targets) {
        OrderCancelRequest request = OrderCancelRequest.builder()
                .reasonCode(CancelRequestReason.ETC)
                .reasonDetail("사이즈를 잘못 골랐어요")
                .statusAtRequest(FulfillmentStatus.PREPARING)
                .requestedAt(requestedAt)
                .build();
        ReflectionTestUtils.setField(request, "id", 10L);
        for (OrderProduct target : targets) {
            request.addItem(OrderCancelRequestItem.builder()
                    .cancelRequest(request)
                    .orderProduct(target)
                    .quantity(target.getQuantity())
                    .refundAmount(target.getPrice() * target.getQuantity())
                    .build());
        }
        return request;
    }
}
