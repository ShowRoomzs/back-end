package showroomz.api.app.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.api.app.order.service.UserOrderItemAssembler.Context;
import showroomz.api.app.order.service.UserOrderItemAssembler.View;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderCancelRequestItem;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.service.DeliveryArrivalEstimator;
import showroomz.domain.order.type.CancelRequestReason;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.UserOrderAction;
import showroomz.domain.order.type.UserOrderItemStatus;
import showroomz.domain.order.type.UserOrderTone;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("앱 주문 항목 행 조립 — 표시 상태 유도 · 보조 문구 · 반려 줄 · 액션(C10 설계서 1절)")
class UserOrderItemAssemblerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 14, 20);
    private static final Long ORDER_ID = 812L;
    private static final Long GROUP_ID = 1L;
    private static final Context EMPTY = Context.of(List.of(), Set.of(), Set.of());

    /** 운영 설정 — {@link UserOrderItemAssembler#ENABLED_ACTIONS}. */
    private final UserOrderItemAssembler assembler = new UserOrderItemAssembler(new OrderProperties(), estimator());
    /** 노출 규칙표(1-6) 전체를 보는 조립기. */
    private final UserOrderItemAssembler allActions =
            new UserOrderItemAssembler(new OrderProperties(), estimator(), EnumSet.allOf(UserOrderAction.class));

    // ------------------------------------------------------------------ 표시 상태(1-1)

    @Test
    @DisplayName("그룹 이행 상태가 항목의 표시 상태·라벨·톤이 된다")
    void statusFollowsGroup() {
        assertStatus(FulfillmentStatus.NEW, UserOrderItemStatus.PAID, "결제완료", UserOrderTone.ACTIVE);
        assertStatus(FulfillmentStatus.PREPARING, UserOrderItemStatus.PREPARING, "상품준비중", UserOrderTone.MUTED);
        assertStatus(FulfillmentStatus.SHIPPING, UserOrderItemStatus.SHIPPING, "배송중", UserOrderTone.ACTIVE);
        assertStatus(FulfillmentStatus.RETURNING, UserOrderItemStatus.RETURNING, "반송중", UserOrderTone.MUTED);
        assertStatus(FulfillmentStatus.DELIVERED, UserOrderItemStatus.DELIVERED, "배송완료", UserOrderTone.ACTIVE);
        assertStatus(FulfillmentStatus.CONFIRMED, UserOrderItemStatus.CONFIRMED, "구매확정", UserOrderTone.MUTED);
        assertStatus(FulfillmentStatus.PENDING, UserOrderItemStatus.PAYMENT_PENDING, "결제 대기", UserOrderTone.MUTED);
    }

    @Test
    @DisplayName("취소된 항목은 그룹 상태와 무관하게 「취소」 · 탈색")
    void cancelledWinsOverGroup() {
        OrderProduct item = item(1L, group(FulfillmentStatus.PREPARING), OrderProductStatus.CANCELLED);
        cancelMeta(item, OrderCancelType.REQUEST_APPROVED);

        UserOrderDto.ItemRow row = assembler.toRow(item, EMPTY, View.LIST);

        assertThat(row.getStatus()).isEqualTo(UserOrderItemStatus.CANCELLED);
        assertThat(row.getDimmed()).isTrue();
        assertThat(row.getDates().getCancelledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("검토 중 취소 요청의 대상 항목만 「취소 요청중」 — 같은 그룹의 요청 밖 항목은 원래 상태 그대로")
    void pendingRequestOverlaysOnlyTargets() {
        OrderDeliveryGroup group = group(FulfillmentStatus.PREPARING);
        OrderProduct requested = item(1L, group, OrderProductStatus.PAID);
        OrderProduct other = item(2L, group, OrderProductStatus.PAID);
        Context context = Context.of(List.of(request(31L, CancelRequestStatus.PENDING, requested)), Set.of(), Set.of());

        UserOrderDto.ItemRow requestedRow = assembler.toRow(requested, context, View.LIST);
        UserOrderDto.ItemRow otherRow = assembler.toRow(other, context, View.LIST);

        assertThat(requestedRow.getStatus()).isEqualTo(UserOrderItemStatus.CANCEL_REQUESTED);
        assertThat(requestedRow.getStatusSub()).isEqualTo("브랜드 확인 중");
        assertThat(requestedRow.getCancelRequestId()).isEqualTo(31L);
        assertThat(otherRow.getStatus()).isEqualTo(UserOrderItemStatus.PREPARING);
        assertThat(otherRow.getCancelRequestId()).isNull();
    }

    @Test
    @DisplayName("그룹 없는 옛 행은 항목 상태만으로 판정한다 — 구매확정 · 결제완료(액션 없음)")
    void legacyRowWithoutGroup() {
        Context cancellable = Context.of(List.of(), Set.of(), Set.of(ORDER_ID));

        UserOrderDto.ItemRow confirmed = allActions.toRow(
                item(1L, null, OrderProductStatus.PURCHASE_CONFIRMED), cancellable, View.LIST);
        UserOrderDto.ItemRow paid = allActions.toRow(item(2L, null, OrderProductStatus.PAID), cancellable, View.LIST);

        assertThat(confirmed.getStatus()).isEqualTo(UserOrderItemStatus.CONFIRMED);
        assertThat(confirmed.getStatusSub()).isNull();
        assertThat(confirmed.getBrandName()).isNull();
        assertThat(paid.getStatus()).isEqualTo(UserOrderItemStatus.PAID);
        assertThat(paid.getActions()).isEmpty();
    }

    // ------------------------------------------------------------------ 보조 문구(1-2)

    @Test
    @DisplayName("보조 문구 — 발송 예정 · 반송 · 확정은 그 상태의 날짜 하나(MM.dd). 발송기한이 아직 없으면(공구 진행 중) 약정 문구")
    void statusSubByStatus() {
        OrderDeliveryGroup paid = group(FulfillmentStatus.NEW);
        ReflectionTestUtils.setField(paid, "shipDueAt", LocalDateTime.of(2026, 9, 15, 10, 0));
        OrderDeliveryGroup confirmed = group(FulfillmentStatus.CONFIRMED);
        ReflectionTestUtils.setField(confirmed, "confirmedAt", LocalDateTime.of(2026, 9, 23, 0, 5));

        assertThat(sub(paid, View.LIST)).isEqualTo("09.15 발송 예정");
        // 공구 진행 중 — 기한은 마감 뒤에 확정되므로 「마감 후 N영업일」 약정을 보여 준다(1009 기획 수정본 1-3).
        assertThat(sub(group(FulfillmentStatus.PREPARING), View.LIST))
                .isEqualTo("공구 마감 후 3영업일 이내 발송 (주말·공휴일 제외)");
        assertThat(sub(group(FulfillmentStatus.RETURNING), View.LIST)).isEqualTo("반송 처리 중");
        assertThat(sub(confirmed, View.LIST)).isEqualTo("09.23 확정");
        assertThat(sub(group(FulfillmentStatus.CONFIRMED), View.LIST)).isNull();
    }

    @Test
    @DisplayName("배송중 — 집화 전에는 날짜 없이 「배송중」만, 집화 후에는 집화일 + 3배송일 「도착 예정」, 예정일이 지나면 다시 날짜를 지운다")
    void shippingArrivalDue() {
        OrderDeliveryGroup shipping = group(FulfillmentStatus.SHIPPING);
        ReflectionTestUtils.setField(shipping, "shippedAt", LocalDateTime.of(2026, 9, 14, 11, 0));
        OrderProduct item = item(1L, shipping, OrderProductStatus.PAID);

        UserOrderDto.ItemRow beforePickup = assembler.toRow(item, on(LocalDate.of(2026, 9, 14)), View.LIST);
        assertThat(beforePickup.getStatus()).isEqualTo(UserOrderItemStatus.SHIPPING);
        assertThat(beforePickup.getStatusSub()).isNull();
        assertThat(beforePickup.getDates().getArrivalDueDate()).isNull();

        // 09.14(월) 집화 → 화·수·목 = 09.17
        ReflectionTestUtils.setField(shipping, "pickedUpAt", LocalDateTime.of(2026, 9, 14, 19, 30));
        for (LocalDate today : List.of(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 17))) {
            UserOrderDto.ItemRow row = assembler.toRow(item, on(today), View.DETAIL);
            assertThat(row.getStatusSub()).as(today.toString()).isEqualTo("09.17 도착 예정");
            assertThat(row.getDates().getArrivalDueDate()).isEqualTo(LocalDate.of(2026, 9, 17));
        }

        UserOrderDto.ItemRow overdue = assembler.toRow(item, on(LocalDate.of(2026, 9, 18)), View.LIST);
        assertThat(overdue.getStatus()).isEqualTo(UserOrderItemStatus.SHIPPING);
        assertThat(overdue.getStatusSub()).isNull();
        assertThat(overdue.getDates().getArrivalDueDate()).isNull();
    }

    @Test
    @DisplayName("배송완료 — 목록은 구매확정 예정일(배송완료 + 7일), 상세는 배송완료 시각. confirmDueAt 은 배송완료에서만 실린다")
    void deliveredSubDiffersByView() {
        OrderDeliveryGroup delivered = group(FulfillmentStatus.DELIVERED);
        ReflectionTestUtils.setField(delivered, "deliveredAt", NOW);
        OrderProduct item = item(1L, delivered, OrderProductStatus.PAID);

        UserOrderDto.ItemRow list = assembler.toRow(item, EMPTY, View.LIST);
        UserOrderDto.ItemRow detail = assembler.toRow(item, EMPTY, View.DETAIL);

        assertThat(list.getStatusSub()).isEqualTo("09.23 구매확정 예정");
        assertThat(detail.getStatusSub()).isEqualTo("09.16 14:20");
        assertThat(list.getDates().getConfirmDueAt()).isEqualTo(NOW.plusDays(7));

        OrderDeliveryGroup confirmed = group(FulfillmentStatus.CONFIRMED);
        ReflectionTestUtils.setField(confirmed, "deliveredAt", NOW);
        assertThat(assembler.toRow(item(2L, confirmed, OrderProductStatus.PURCHASE_CONFIRMED), EMPTY, View.LIST)
                .getDates().getConfirmDueAt()).isNull();
    }

    @Test
    @DisplayName("취소 — 소비자 취소는 항상 「완료」, 브랜드 승인·직권은 환불 큐가 남아 있으면 「환불 처리 중」. 금액은 「환불 N원」")
    void cancelledSubAndAmount() {
        OrderDeliveryGroup group = group(FulfillmentStatus.PREPARING);
        Context refundPending = Context.of(List.of(), Set.of(GROUP_ID), Set.of());

        OrderProduct consumer = item(1L, group, OrderProductStatus.CANCELLED);
        cancelMeta(consumer, OrderCancelType.CONSUMER);
        OrderProduct approved = item(2L, group, OrderProductStatus.CANCELLED);
        cancelMeta(approved, OrderCancelType.REQUEST_APPROVED);
        OrderProduct direct = item(3L, group, OrderProductStatus.CANCELLED);
        cancelMeta(direct, OrderCancelType.SELLER_DIRECT);

        assertThat(assembler.toRow(consumer, refundPending, View.LIST).getStatusSub()).isEqualTo("완료");
        assertThat(assembler.toRow(approved, refundPending, View.LIST).getStatusSub()).isEqualTo("환불 처리 중");
        assertThat(assembler.toRow(direct, refundPending, View.LIST).getStatusSub()).isEqualTo("환불 처리 중");
        assertThat(assembler.toRow(approved, EMPTY, View.LIST).getStatusSub()).isEqualTo("완료");

        UserOrderDto.ItemRow row = assembler.toRow(approved, EMPTY, View.LIST);
        assertThat(row.getAmount()).isEqualTo(54_400L);
        assertThat(row.getAmountLabel()).isEqualTo("환불 54,400원");
    }

    @Test
    @DisplayName("결제 전에 닫힌 주문의 항목(취소 유형 없음)은 환불이 아니다 — 보조 문구·환불 표기·취소 상세 없음")
    void cancelledBeforePaymentIsNotRefund() {
        OrderProduct item = item(1L, group(FulfillmentStatus.CANCELLED), OrderProductStatus.CANCELLED);

        UserOrderDto.ItemRow row = allActions.toRow(item, EMPTY, View.DETAIL);

        assertThat(row.getStatus()).isEqualTo(UserOrderItemStatus.CANCELLED);
        assertThat(row.getStatusSub()).isNull();
        assertThat(row.getAmountLabel()).isEqualTo("54,400원");
        assertThat(row.getActions()).isEmpty();
    }

    // ------------------------------------------------------------------ 반려 줄(1-4)

    @Test
    @DisplayName("반려 줄 — 가장 최근 반려 요청 하나, 결제완료~배송완료에서만. 구매확정·재요청·취소에서는 사라진다")
    void cancelRejectionLine() {
        OrderDeliveryGroup shipping = group(FulfillmentStatus.SHIPPING);
        OrderProduct item = item(1L, shipping, OrderProductStatus.PAID);
        OrderCancelRequest older = request(30L, CancelRequestStatus.REJECTED, item);
        OrderCancelRequest latest = request(31L, CancelRequestStatus.REJECTED, item);
        ReflectionTestUtils.setField(latest, "decidedAt", NOW);
        Context rejected = Context.of(List.of(latest, older), Set.of(), Set.of());

        UserOrderDto.ItemRow row = assembler.toRow(item, rejected, View.LIST);
        assertThat(row.getStatus()).isEqualTo(UserOrderItemStatus.SHIPPING);
        assertThat(row.getCancelRejection().getCancelRequestId()).isEqualTo(31L);
        assertThat(row.getCancelRejection().getRejectedAt()).isEqualTo(NOW);

        for (FulfillmentStatus visible : List.of(FulfillmentStatus.NEW, FulfillmentStatus.PREPARING,
                FulfillmentStatus.DELIVERED)) {
            ReflectionTestUtils.setField(shipping, "fulfillmentStatus", visible);
            assertThat(assembler.toRow(item, rejected, View.LIST).getCancelRejection()).as(visible.name()).isNotNull();
        }
        for (FulfillmentStatus hidden : List.of(FulfillmentStatus.CONFIRMED, FulfillmentStatus.RETURNING)) {
            ReflectionTestUtils.setField(shipping, "fulfillmentStatus", hidden);
            assertThat(assembler.toRow(item, rejected, View.LIST).getCancelRejection()).as(hidden.name()).isNull();
        }

        // 반려 뒤 재요청 — 상태가 이미 「취소 요청중」을 말한다.
        ReflectionTestUtils.setField(shipping, "fulfillmentStatus", FulfillmentStatus.PREPARING);
        Context reRequested = Context.of(
                List.of(latest, request(32L, CancelRequestStatus.PENDING, item)), Set.of(), Set.of());
        UserOrderDto.ItemRow again = assembler.toRow(item, reRequested, View.LIST);
        assertThat(again.getStatus()).isEqualTo(UserOrderItemStatus.CANCEL_REQUESTED);
        assertThat(again.getCancelRejection()).isNull();
    }

    // ------------------------------------------------------------------ 액션(1-6)

    @Test
    @DisplayName("운영 설정은 API 가 있는 액션만 내린다 — CANCEL 은 주문 단위로 취소 가능할 때만, 그 밖의 결제완료·상품준비중은 취소 요청(1009 기획 수정본 3-1)")
    void onlyActionsWithApiAreEnabled() {
        OrderProduct paid = item(1L, group(FulfillmentStatus.NEW), OrderProductStatus.PAID);
        Context cancellable = Context.of(List.of(), Set.of(), Set.of(ORDER_ID));

        assertThat(types(assembler.toRow(paid, cancellable, View.LIST))).containsExactly(UserOrderAction.CANCEL);
        assertThat(assembler.toRow(paid, cancellable, View.LIST).getActions().get(0).getLabel()).isEqualTo("주문 취소");
        // 준비 시작된 그룹이 섞인 주문 — 「결제완료」로 보이고 전액 취소 대신 취소 요청으로 접수한다.
        assertThat(types(assembler.toRow(paid, EMPTY, View.LIST))).containsExactly(UserOrderAction.CANCEL_REQUEST);

        OrderProduct preparing = item(2L, group(FulfillmentStatus.PREPARING), OrderProductStatus.PAID);
        assertThat(types(assembler.toRow(preparing, cancellable, View.DETAIL)))
                .containsExactly(UserOrderAction.CANCEL_REQUEST);
        OrderProduct returning = item(2L, group(FulfillmentStatus.RETURNING), OrderProductStatus.PAID);
        assertThat(assembler.toRow(returning, cancellable, View.DETAIL).getActions()).isEmpty();
        // 배송 조회는 송장이 생긴 뒤부터다 — 반품·교환은 배송완료에서만 열리고 구매확정에서 닫힌다.
        for (FulfillmentStatus status : List.of(FulfillmentStatus.SHIPPING, FulfillmentStatus.CONFIRMED)) {
            OrderProduct item = item(2L, group(status), OrderProductStatus.PAID);
            assertThat(types(assembler.toRow(item, cancellable, View.DETAIL))).as(status.name())
                    .containsExactly(UserOrderAction.TRACK_DELIVERY);
        }
        OrderProduct delivered = item(2L, group(FulfillmentStatus.DELIVERED), OrderProductStatus.PAID);
        assertThat(types(assembler.toRow(delivered, cancellable, View.LIST)))
                .containsExactly(UserOrderAction.TRACK_DELIVERY, UserOrderAction.RETURN_EXCHANGE);
        assertThat(types(assembler.toRow(delivered, cancellable, View.DETAIL))).containsExactly(
                UserOrderAction.TRACK_DELIVERY, UserOrderAction.RETURN_REQUEST, UserOrderAction.EXCHANGE_REQUEST);
    }

    @Test
    @DisplayName("노출 규칙표 — 상태별 목표 액션. 배송완료만 목록·상세가 다르고 반송중은 액션이 없다")
    void targetActionTable() {
        assertThat(actionsOf(FulfillmentStatus.PREPARING, View.LIST)).containsExactly(UserOrderAction.CANCEL_REQUEST);
        assertThat(actionsOf(FulfillmentStatus.SHIPPING, View.LIST)).containsExactly(UserOrderAction.TRACK_DELIVERY);
        assertThat(actionsOf(FulfillmentStatus.DELIVERED, View.LIST))
                .containsExactly(UserOrderAction.TRACK_DELIVERY, UserOrderAction.RETURN_EXCHANGE);
        assertThat(actionsOf(FulfillmentStatus.DELIVERED, View.DETAIL)).containsExactly(
                UserOrderAction.TRACK_DELIVERY, UserOrderAction.RETURN_REQUEST, UserOrderAction.EXCHANGE_REQUEST);
        assertThat(actionsOf(FulfillmentStatus.CONFIRMED, View.DETAIL)).containsExactly(UserOrderAction.TRACK_DELIVERY);
        assertThat(actionsOf(FulfillmentStatus.RETURNING, View.DETAIL)).isEmpty();

        OrderProduct cancelled = item(1L, group(FulfillmentStatus.CANCELLED), OrderProductStatus.CANCELLED);
        cancelMeta(cancelled, OrderCancelType.CONSUMER);
        assertThat(types(allActions.toRow(cancelled, EMPTY, View.LIST))).containsExactly(UserOrderAction.CANCEL_DETAIL);

        OrderProduct requested = item(2L, group(FulfillmentStatus.PREPARING), OrderProductStatus.PAID);
        Context pending = Context.of(List.of(request(31L, CancelRequestStatus.PENDING, requested)), Set.of(), Set.of());
        assertThat(types(allActions.toRow(requested, pending, View.LIST))).containsExactly(UserOrderAction.CANCEL_DETAIL);
    }

    @Test
    @DisplayName("거절된 수량은 다시 신청할 수 없다 — 전량이 거절 종결된 배송완료 항목에는 반품·교환 버튼이 없고 반려 줄이 붙는다(#28)")
    void rejectedQuantityCannotBeClaimedAgain() {
        OrderProduct item = item(1L, group(FulfillmentStatus.DELIVERED), OrderProductStatus.PAID);

        Context partlyRejected = EMPTY.withClaims(UserOrderClaimContext.of(List.of(rejectedClaim(51L, item, 1)), List.of()));
        assertThat(types(allActions.toRow(item, partlyRejected, View.DETAIL))).containsExactly(
                UserOrderAction.TRACK_DELIVERY, UserOrderAction.RETURN_REQUEST, UserOrderAction.EXCHANGE_REQUEST);

        Context fullyRejected = EMPTY.withClaims(UserOrderClaimContext.of(List.of(rejectedClaim(52L, item, 2)), List.of()));
        UserOrderDto.ItemRow row = allActions.toRow(item, fullyRejected, View.DETAIL);
        assertThat(types(row)).containsExactly(UserOrderAction.TRACK_DELIVERY);
        assertThat(types(allActions.toRow(item, fullyRejected, View.LIST))).containsExactly(UserOrderAction.TRACK_DELIVERY);
        assertThat(row.getStatus()).isEqualTo(UserOrderItemStatus.DELIVERED);
        assertThat(row.getClaimRejection().getClaimId()).isEqualTo(52L);
        assertThat(row.getClaim()).isNull();
    }

    @Test
    @DisplayName("교환할 옵션의 재고가 없으면 [교환 요청]은 눌리지 않는 「교환 불가 (재고 없음)」으로 내린다 — 반품은 그대로(#23 · #28-2)")
    void exchangeSoldOut() {
        OrderProduct item = item(1L, group(FulfillmentStatus.DELIVERED), OrderProductStatus.PAID);

        UserOrderDto.ItemRow available = allActions.toRow(item, EMPTY, View.DETAIL);
        assertThat(available.getActions()).allSatisfy(action -> assertThat(action.getEnabled()).isTrue());

        UserOrderDto.ItemRow soldOut = allActions.toRow(item, EMPTY.withExchangeUnavailable(Set.of(1L)), View.DETAIL);
        assertThat(types(soldOut)).containsExactly(UserOrderAction.TRACK_DELIVERY, UserOrderAction.RETURN_REQUEST,
                UserOrderAction.EXCHANGE_REQUEST);
        assertThat(soldOut.getActions().get(1).getEnabled()).isTrue();
        assertThat(soldOut.getActions().get(2).getEnabled()).isFalse();
        assertThat(soldOut.getActions().get(2).getLabel()).isEqualTo("교환 불가 (재고 없음)");
    }

    // ------------------------------------------------------------------ 안내(3-3)

    @Test
    @DisplayName("CONFIRM_DUE 는 배송완료 항목 중 가장 이른 구매확정 예정일 — 배송완료 항목이 없으면 안내도 없다")
    void confirmDueNotice() {
        OrderDeliveryGroup early = group(FulfillmentStatus.DELIVERED);
        ReflectionTestUtils.setField(early, "deliveredAt", NOW);
        OrderDeliveryGroup late = group(FulfillmentStatus.DELIVERED);
        ReflectionTestUtils.setField(late, "deliveredAt", NOW.plusDays(2));
        List<UserOrderDto.ItemRow> rows = List.of(
                assembler.toRow(item(1L, late, OrderProductStatus.PAID), EMPTY, View.DETAIL),
                assembler.toRow(item(2L, early, OrderProductStatus.PAID), EMPTY, View.DETAIL),
                assembler.toRow(item(3L, group(FulfillmentStatus.SHIPPING), OrderProductStatus.PAID), EMPTY, View.DETAIL));

        assertThat(assembler.notices(rows)).singleElement().satisfies(notice -> {
            assertThat(notice.getType()).isEqualTo(UserOrderDto.NoticeType.CONFIRM_DUE);
            assertThat(notice.getTone()).isEqualTo(UserOrderTone.ACTIVE);
            assertThat(notice.getDate()).isEqualTo(NOW.plusDays(7));
        });
        assertThat(assembler.notices(rows.subList(2, 3))).isEmpty();
    }

    @Test
    @DisplayName("CANCEL_BY_REQUEST 는 취소 요청 액션이 켜져 있을 때만 — 운영 설정에서 켜졌다(1009 기획 수정본 3-1)")
    void cancelByRequestNoticeFollowsAction() {
        OrderProduct preparing = item(1L, group(FulfillmentStatus.PREPARING), OrderProductStatus.PAID);

        for (UserOrderItemAssembler target : List.of(assembler, allActions)) {
            assertThat(target.notices(List.of(target.toRow(preparing, EMPTY, View.DETAIL))))
                    .singleElement().satisfies(notice -> {
                        assertThat(notice.getType()).isEqualTo(UserOrderDto.NoticeType.CANCEL_BY_REQUEST);
                        assertThat(notice.getTone()).isEqualTo(UserOrderTone.MUTED);
                    });
        }
        UserOrderItemAssembler withoutRequest = new UserOrderItemAssembler(new OrderProperties(), estimator(),
                EnumSet.of(UserOrderAction.CANCEL));
        assertThat(withoutRequest.notices(List.of(withoutRequest.toRow(preparing, EMPTY, View.DETAIL)))).isEmpty();
    }

    // ------------------------------------------------------------------ 보조

    private void assertStatus(FulfillmentStatus groupStatus, UserOrderItemStatus expected, String label,
                              UserOrderTone tone) {
        UserOrderDto.ItemRow row = assembler.toRow(item(1L, group(groupStatus), OrderProductStatus.PAID), EMPTY,
                View.LIST);
        assertThat(row.getStatus()).as(groupStatus.name()).isEqualTo(expected);
        assertThat(row.getStatusLabel()).isEqualTo(label);
        assertThat(row.getStatusTone()).isEqualTo(tone);
        assertThat(row.getDimmed()).isFalse();
        assertThat(row.getBrandName()).isEqualTo("라보에이치");
        assertThat(row.getAmountLabel()).isEqualTo("54,400원");
    }

    private String sub(OrderDeliveryGroup group, View view) {
        return assembler.toRow(item(1L, group, OrderProductStatus.PAID), EMPTY, view).getStatusSub();
    }

    private List<UserOrderAction> actionsOf(FulfillmentStatus status, View view) {
        return types(allActions.toRow(item(1L, group(status), OrderProductStatus.PAID), EMPTY, view));
    }

    private static List<UserOrderAction> types(UserOrderDto.ItemRow row) {
        return row.getActions().stream().map(UserOrderDto.Action::getType).toList();
    }

    private static Context on(LocalDate today) {
        return Context.of(List.of(), Set.of(), Set.of(), today);
    }

    /** 실측 표본 없음 — 기본 3배송일. */
    private static DeliveryArrivalEstimator estimator() {
        return new DeliveryArrivalEstimator(new BusinessCalendar(new String[0]),
                mock(OrderDeliveryGroupRepository.class), new OrderProperties());
    }

    private static Order order() {
        Order order = Order.create(null, "20260912-000201", new Order.Totals(68_000, 13_600, 3_000, 57_400),
                new Order.AddressSnapshot("김수민", "010-1234-5678", "06234", "서울 강남구 테헤란로 000", "12층"),
                "문 앞에 놓아주세요", "크림", "key", NOW.plusMinutes(30));
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private static OrderDeliveryGroup group(FulfillmentStatus status) {
        OrderDeliveryGroup group = OrderDeliveryGroup.builder()
                .order(order()).productTotal(54_400).deliveryFee(3_000).marketName("라보에이치").build();
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        ReflectionTestUtils.setField(group, "fulfillmentStatus", status);
        return group;
    }

    /** 크림 2개 · 단가 27,200 — 항목 금액 54,400. */
    private static OrderProduct item(Long id, OrderDeliveryGroup group, OrderProductStatus status) {
        OrderProduct item = OrderProduct.builder()
                .order(group != null ? group.getOrder() : order()).deliveryGroup(group)
                .productName("크림").optionName("기본").quantity(2).price(27_200).status(status).build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    /** 검수 거절이 종결된 반품 클레임 — 반송 완료. */
    private static OrderClaim rejectedClaim(Long id, OrderProduct item, int quantity) {
        OrderClaim claim = OrderClaim.builder()
                .collection(OrderClaimCollection.builder().type(ClaimType.RETURN).invoiceDueAt(NOW).createdAt(NOW).build())
                .deliveryGroup(item.getDeliveryGroup()).orderProduct(item).type(ClaimType.RETURN).quantity(quantity)
                .status(ClaimStatus.COMPLETED).requestedAt(NOW).collectDueAt(NOW).build();
        ReflectionTestUtils.setField(claim, "id", id);
        ReflectionTestUtils.setField(claim, "result", ClaimResult.REJECTED);
        ReflectionTestUtils.setField(claim, "rejectedAt", NOW);
        return claim;
    }

    private static void cancelMeta(OrderProduct item, OrderCancelType cancelType) {
        ReflectionTestUtils.setField(item, "cancelType", cancelType);
        ReflectionTestUtils.setField(item, "cancelledAt", NOW);
    }

    private static OrderCancelRequest request(Long id, CancelRequestStatus status, OrderProduct... targets) {
        OrderCancelRequest request = OrderCancelRequest.builder()
                .reasonCode(CancelRequestReason.CHANGE_OF_MIND)
                .statusAtRequest(FulfillmentStatus.PREPARING)
                .requestedAt(NOW.minusHours(1))
                .build();
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "status", status);
        for (OrderProduct target : targets) {
            request.addItem(OrderCancelRequestItem.builder()
                    .cancelRequest(request).orderProduct(target)
                    .quantity(target.getQuantity()).refundAmount(target.getPrice() * target.getQuantity()).build());
        }
        return request;
    }
}
