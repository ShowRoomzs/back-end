package showroomz.domain.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.seller.order.SellerOrderTestSupport;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader.GroupBuySales;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.OrderClaimService.Invoice;
import showroomz.domain.order.service.OrderClaimService.Item;
import showroomz.domain.order.service.OrderClaimService.RequestCommand;
import showroomz.domain.order.service.OrderClaimService.RequestResult;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackEvent;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 클레임 도메인 진입점 + 구매확정 연동(35 설계서 6절 #3 · #4 · #5 · #6 · #26 · 앱 클레임 설계서 7절 #1 ~ #4 · #11 · #13 · #14).
 * 컨트롤러가 아직 없어 서비스를 직접 부른다. 하위주문은 실제 결제 경로로 만들고 배송완료는 추적 판정으로 태운다.
 * 검수 판정이 없는 단계라 거절·환불 뒤의 상태는 SQL 로 재현한다.
 */
@IntegrationTest
class OrderClaimServiceIntegrationTest extends SellerOrderTestSupport {

    @Autowired private OrderClaimService claimService;
    @Autowired private DeliveryTrackingEventRecorder trackingEventRecorder;
    @Autowired private GroupBuySalesReader salesReader;

    @Nested
    @DisplayName("신청")
    class Request {

        @Test
        @DisplayName("반품 요청 — 송장 없이 내면 회수 대기, 송장 등록 기한은 접수 + 7일의 끝이고 이력이 남는다")
        void requestWithoutInvoice() throws Exception {
            OrderDeliveryGroup group = deliveredGroup(1);
            LocalDateTime now = LocalDateTime.now().withNano(0);

            RequestResult result = claimService.request(command(group, ClaimReason.CHANGE_OF_MIND, null, null), now);

            assertThat(result.created()).isTrue();
            Map<String, Object> claim = claimRow(result.claimIds().get(0));
            assertThat(claim).containsEntry("status", "REQUESTED").containsEntry("type", "RETURN")
                    .containsEntry("fee_bearer", "CONSUMER").containsEntry("quantity", 1);
            Map<String, Object> collection = collectionRow(result.collectionId());
            assertThat(time(collection.get("invoice_due_at")))
                    .isEqualTo(now.toLocalDate().plusDays(7).atTime(LocalTime.of(23, 59, 59)));
            assertThat(collection.get("reship_recipient")).isEqualTo("김수민");
            // 배송비를 내고 받은 주문 — 차감이 없다(그 배송비를 돌려주지 않는 것으로 같은 효과).
            assertThat(collection.get("return_deduction")).isEqualTo(0);
            assertThat(events(result.claimIds().get(0))).containsExactly("REQUESTED");
        }

        @Test
        @DisplayName("송장을 같이 내면 회수 중으로 시작한다 — 송장번호는 숫자만 저장한다")
        void requestWithInvoice() throws Exception {
            OrderDeliveryGroup group = deliveredGroup(1);

            RequestResult result = claimService.request(command(group, ClaimReason.CHANGE_OF_MIND, null,
                    new Invoice(DeliveryCarrier.CJ, "6849-2201 3378")), LocalDateTime.now());

            assertThat(claimRow(result.claimIds().get(0))).containsEntry("status", "COLLECTING");
            assertThat(collectionRow(result.collectionId())).containsEntry("carrier", "CJ")
                    .containsEntry("tracking_number", "684922013378");
            assertThat(events(result.claimIds().get(0)))
                    .containsExactlyInAnyOrder("REQUESTED", "COLLECTION_INVOICE_REGISTERED");
        }

        @Test
        @DisplayName("반품 배송비 차감 — 무료배송으로 받은 주문의 고객 귀책 반품만 주문 시점 배송비를 뺀다. 브랜드 귀책은 0")
        void returnDeduction() throws Exception {
            OrderDeliveryGroup freeByConsumer = deliveredGroup(4);
            OrderDeliveryGroup freeBySeller = deliveredGroup(4);
            // 주문 뒤에 브랜드가 배송비를 바꿔도 금액은 주문 시점 값이다.
            jdbc.update("UPDATE market SET default_delivery_fee = 4000 WHERE market_id = ?", brand.marketId());

            RequestResult consumerFault = claimService.request(
                    command(freeByConsumer, ClaimReason.CHANGE_OF_MIND, null, null), LocalDateTime.now());
            RequestResult sellerFault = claimService.request(
                    command(freeBySeller, ClaimReason.DAMAGED_OR_DEFECTIVE, "뚜껑이 깨져서 왔어요", null),
                    LocalDateTime.now());

            assertThat(collectionRow(consumerFault.collectionId()).get("return_deduction")).isEqualTo(DELIVERY_FEE);
            assertThat(collectionRow(sellerFault.collectionId()).get("return_deduction")).isEqualTo(0);
            assertThat(collectionRow(sellerFault.collectionId())).containsEntry("fee_bearer", "SELLER");
        }

        @Test
        @DisplayName("브랜드 부담 사유는 상세 내용이 필수다")
        void detailRequired() throws Exception {
            OrderDeliveryGroup group = deliveredGroup(1);

            assertError(() -> claimService.request(command(group, ClaimReason.DAMAGED_OR_DEFECTIVE, "  ", null),
                    LocalDateTime.now()), ErrorCode.CLAIM_REASON_DETAIL_REQUIRED);
            assertThat(count("order_claim")).isZero();
        }

        @Test
        @DisplayName("같은 멱등키의 재시도는 요청을 둘 만들지 않는다")
        void idempotent() throws Exception {
            OrderDeliveryGroup group = deliveredGroup(1);
            RequestCommand command = command(group, ClaimReason.CHANGE_OF_MIND, null, null);

            RequestResult first = claimService.request(command, LocalDateTime.now());
            RequestResult second = claimService.request(command, LocalDateTime.now());

            assertThat(second.created()).isFalse();
            assertThat(second.collectionId()).isEqualTo(first.collectionId());
            assertThat(second.claimIds()).isEqualTo(first.claimIds());
            assertThat(count("order_claim_collection")).isEqualTo(1);
        }

        @Test
        @DisplayName("배송완료가 아니면 받지 않는다 — 배송중 · 구매확정 뒤는 CLAIM_NOT_ELIGIBLE, 남의 주문은 없는 주문이다(#6)")
        void eligibility() throws Exception {
            OrderDeliveryGroup shipping = shippingGroup("111122223333");
            OrderDeliveryGroup confirmed = deliveredGroup(1, LocalDateTime.now().minusDays(8));
            assertThat(fulfillmentService.confirmIfDue(confirmed.getId(), LocalDateTime.now())).isTrue();
            OrderDeliveryGroup mine = deliveredGroup(1);
            Users stranger = createConsumer("stranger", "박타인");

            assertError(() -> claimService.request(command(shipping, ClaimReason.CHANGE_OF_MIND, null, null),
                    LocalDateTime.now()), ErrorCode.CLAIM_NOT_ELIGIBLE);
            assertError(() -> claimService.request(command(confirmed, ClaimReason.CHANGE_OF_MIND, null, null),
                    LocalDateTime.now()), ErrorCode.CLAIM_NOT_ELIGIBLE);
            assertError(() -> claimService.request(new RequestCommand(stranger.getId(), mine.getId(), ClaimType.RETURN,
                    ClaimReason.CHANGE_OF_MIND, null, List.of(), List.of(new Item(items(mine).get(0).getId(), 1)),
                    null, null), LocalDateTime.now()), ErrorCode.ORDER_GROUP_NOT_FOUND);
        }

        @Test
        @DisplayName("잔여 수량을 넘겨 신청할 수 없다 — 진행 중 수량은 점유하고, 철회하면 돌아온다(#3)")
        void remainingQuantity() throws Exception {
            OrderDeliveryGroup group = deliveredGroup(2);
            Long productId = items(group).get(0).getId();

            RequestResult one = claimService.request(command(group, productId, 1), LocalDateTime.now());
            assertError(() -> claimService.request(command(group, productId, 2), LocalDateTime.now()),
                    ErrorCode.CLAIM_QUANTITY_EXCEEDED);
            claimService.request(command(group, productId, 1), LocalDateTime.now());
            assertError(() -> claimService.request(command(group, productId, 1), LocalDateTime.now()),
                    ErrorCode.CLAIM_QUANTITY_EXCEEDED);

            claimService.withdraw(one.claimIds().get(0), consumer.getId(), LocalDateTime.now());
            assertThat(claimService.request(command(group, productId, 1), LocalDateTime.now()).created()).isTrue();
        }

        @Test
        @DisplayName("거절된 수량은 종결 뒤에도 돌아오지 않고, 환불된 수량은 returned_quantity 가 뺀다")
        void rejectedAndReturnedQuantityNeverComeBack() throws Exception {
            OrderDeliveryGroup group = deliveredGroup(2);
            Long productId = items(group).get(0).getId();
            RequestResult rejected = claimService.request(command(group, productId, 1), LocalDateTime.now());
            jdbc.update("UPDATE order_claim SET status = 'COMPLETED', result = 'REJECTED', rejected_at = ?, "
                    + "completed_at = ? WHERE claim_id = ?", LocalDateTime.now(), LocalDateTime.now(),
                    rejected.claimIds().get(0));
            jdbc.update("UPDATE order_product SET returned_quantity = 1 WHERE order_product_id = ?", productId);

            assertError(() -> claimService.request(command(group, productId, 1), LocalDateTime.now()),
                    ErrorCode.CLAIM_QUANTITY_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("회수 송장")
    class CollectionInvoice {

        @Test
        @DisplayName("송장을 넣으면 묶음 전체가 회수 중으로 간다 — 두 번은 넣을 수 없다(#4)")
        void registerMovesWholeBox() throws Exception {
            OrderDeliveryGroup group = deliveredTwoItemGroup();
            RequestResult result = claimService.request(commandForAll(group), LocalDateTime.now());

            claimService.registerCollectionInvoice(result.collectionId(), consumer.getId(),
                    new Invoice(DeliveryCarrier.EPOST, "6012345678901"), LocalDateTime.now());

            assertThat(result.claimIds()).hasSize(2)
                    .allSatisfy(id -> assertThat(claimRow(id)).containsEntry("status", "COLLECTING"));
            assertThat(collectionRow(result.collectionId())).containsEntry("carrier", "EPOST");
            assertError(() -> claimService.registerCollectionInvoice(result.collectionId(), consumer.getId(),
                    new Invoice(DeliveryCarrier.CJ, "999988887777"), LocalDateTime.now()), ErrorCode.CLAIM_STATE_CHANGED);
        }

        @Test
        @DisplayName("등록 기한이 지났거나 남의 요청이면 넣을 수 없다")
        void registerGuards() throws Exception {
            OrderDeliveryGroup group = deliveredGroup(1);
            RequestResult result = claimService.request(command(group, ClaimReason.CHANGE_OF_MIND, null, null),
                    LocalDateTime.now());
            Users stranger = createConsumer("stranger", "박타인");
            Invoice invoice = new Invoice(DeliveryCarrier.CJ, "684922013378");

            assertError(() -> claimService.registerCollectionInvoice(result.collectionId(), stranger.getId(), invoice,
                    LocalDateTime.now()), ErrorCode.CLAIM_NOT_FOUND);
            assertError(() -> claimService.registerCollectionInvoice(result.collectionId(), consumer.getId(), invoice,
                    LocalDateTime.now().plusDays(8)), ErrorCode.CLAIM_STATE_CHANGED);
            assertError(() -> claimService.registerCollectionInvoice(result.collectionId(), consumer.getId(),
                    new Invoice(DeliveryCarrier.CJ, "---"), LocalDateTime.now()), ErrorCode.INVOICE_FORMAT_INVALID);
            assertThat(claimRow(result.claimIds().get(0))).containsEntry("status", "REQUESTED");
        }

        @Test
        @DisplayName("송장 수정 — 추적 이력이 없을 때만. 택배사가 이미 스캔한 송장과 기한이 지난 송장은 고칠 수 없다")
        void updateOnlyBeforeScan() throws Exception {
            OrderDeliveryGroup group = deliveredGroup(1);
            LocalDateTime now = LocalDateTime.now().withNano(0);
            RequestResult result = claimService.request(command(group, ClaimReason.CHANGE_OF_MIND, null,
                    new Invoice(DeliveryCarrier.CJ, "684922013378")), now);
            LocalDateTime registeredAt = time(collectionRow(result.collectionId()).get("invoice_registered_at"));

            claimService.updateCollectionInvoice(result.collectionId(), consumer.getId(),
                    new Invoice(DeliveryCarrier.HANJIN, "512345678901"), now.plusHours(1));

            Map<String, Object> collection = collectionRow(result.collectionId());
            assertThat(collection).containsEntry("carrier", "HANJIN").containsEntry("tracking_number", "512345678901");
            assertThat(time(collection.get("invoice_registered_at"))).isEqualTo(registeredAt);
            assertThat(events(result.claimIds().get(0))).contains("COLLECTION_INVOICE_UPDATED");

            assertError(() -> claimService.updateCollectionInvoice(result.collectionId(), consumer.getId(),
                    new Invoice(DeliveryCarrier.CJ, "111111111111"), now.plusDays(8)),
                    ErrorCode.CLAIM_INVOICE_NOT_EDITABLE);
            trackingEventRecorder.record(DeliveryCarrier.HANJIN, "512345678901",
                    List.of(new TrackEvent(now, "서울강남", "집화처리", 2)));
            assertError(() -> claimService.updateCollectionInvoice(result.collectionId(), consumer.getId(),
                    new Invoice(DeliveryCarrier.CJ, "111111111111"), now.plusHours(2)),
                    ErrorCode.CLAIM_INVOICE_NOT_EDITABLE);
        }
    }

    @Nested
    @DisplayName("철회 · 자동 취소 · 직권 종결")
    class Closing {

        @Test
        @DisplayName("철회는 회수 대기에서만 — 요청 취소로 닫히고, 회수 중이면 409 · 남의 요청은 404(#26)")
        void withdraw() throws Exception {
            OrderDeliveryGroup group = deliveredTwoItemGroup();
            RequestResult waiting = claimService.request(command(group, items(group).get(0).getId(), 1),
                    LocalDateTime.now());
            RequestResult collecting = claimService.request(new RequestCommand(consumer.getId(), group.getId(),
                    ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, null, List.of(),
                    List.of(new Item(items(group).get(1).getId(), 1)), new Invoice(DeliveryCarrier.CJ, "684922013378"),
                    null), LocalDateTime.now());
            Users stranger = createConsumer("stranger", "박타인");

            assertError(() -> claimService.withdraw(waiting.claimIds().get(0), stranger.getId(), LocalDateTime.now()),
                    ErrorCode.CLAIM_NOT_FOUND);
            assertError(() -> claimService.withdraw(collecting.claimIds().get(0), consumer.getId(),
                    LocalDateTime.now()), ErrorCode.CLAIM_WITHDRAW_NOT_ALLOWED);
            claimService.withdraw(waiting.claimIds().get(0), consumer.getId(), LocalDateTime.now());

            assertThat(claimRow(waiting.claimIds().get(0))).containsEntry("status", "COMPLETED")
                    .containsEntry("result", "CANCELLED").containsEntry("cancel_reason", "WITHDRAWN");
            assertThat(events(waiting.claimIds().get(0))).contains("WITHDRAWN");
            assertThat(claimRow(collecting.claimIds().get(0))).containsEntry("status", "COLLECTING");
        }

        @Test
        @DisplayName("송장 등록 기한이 지나면 묶음 전체가 자동 취소된다 — 기한 전에는 닫지 않는다(#26)")
        void expireInvoice() throws Exception {
            OrderDeliveryGroup group = deliveredTwoItemGroup();
            LocalDateTime now = LocalDateTime.now().withNano(0);
            RequestResult result = claimService.request(commandForAll(group), now);

            assertThat(claimService.expireInvoice(result.collectionId(), now.plusDays(1))).isZero();
            assertThat(claimService.findCollectionIdsWithExpiredInvoice(now.plusDays(1), 10)).isEmpty();

            LocalDateTime after = now.plusDays(8);
            assertThat(claimService.findCollectionIdsWithExpiredInvoice(after, 10))
                    .containsExactly(result.collectionId());
            assertThat(claimService.expireInvoice(result.collectionId(), after)).isEqualTo(2);

            assertThat(result.claimIds()).allSatisfy(id -> {
                assertThat(claimRow(id)).containsEntry("status", "COMPLETED").containsEntry("result", "CANCELLED")
                        .containsEntry("cancel_reason", "INVOICE_EXPIRED");
                assertThat(events(id)).contains("INVOICE_EXPIRED");
            });
            assertThat(claimService.expireInvoice(result.collectionId(), after)).isZero();
            assertThat(claimService.findCollectionIdsWithExpiredInvoice(after, 10)).isEmpty();
        }

        @Test
        @DisplayName("회수 중인 요청은 기한이 지나도 자동 취소하지 않는다 — 운영자 직권이 요청 취소로 닫는다")
        void closeByAdmin() throws Exception {
            OrderDeliveryGroup group = deliveredGroup(1);
            LocalDateTime now = LocalDateTime.now().withNano(0);
            RequestResult result = claimService.request(command(group, ClaimReason.CHANGE_OF_MIND, null,
                    new Invoice(DeliveryCarrier.CJ, "684922013378")), now);
            Long claimId = result.claimIds().get(0);

            assertThat(claimService.expireInvoice(result.collectionId(), now.plusDays(8))).isZero();
            claimService.closeByAdmin(claimId, 1L, "물건이 도착하지 않음", now.plusDays(9));

            assertThat(claimRow(claimId)).containsEntry("status", "COMPLETED").containsEntry("result", "CANCELLED")
                    .containsEntry("cancel_reason", "ADMIN");
            assertError(() -> claimService.closeByAdmin(claimId, 1L, null, now.plusDays(9)),
                    ErrorCode.CLAIM_STATE_CHANGED);
        }
    }

    @Nested
    @DisplayName("구매확정 연동")
    class PurchaseConfirm {

        @Test
        @DisplayName("보류 클레임이 있는 하위주문은 7일이 지나도 확정하지 않는다 — 철회하는 순간 그 자리에서 확정된다(#5 · #26)")
        void blockedUntilClaimCloses() throws Exception {
            OrderDeliveryGroup group = deliveredGroup(1, LocalDateTime.now().minusDays(8));
            LocalDateTime now = LocalDateTime.now();
            RequestResult result = claimService.request(command(group, ClaimReason.CHANGE_OF_MIND, null, null), now);

            assertThat(fulfillmentService.findIdsToConfirm(now.minusDays(7), 10)).doesNotContain(group.getId());
            assertThat(fulfillmentService.confirmPurchase(group.getId(), now, now.minusDays(7))).isFalse();
            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);

            claimService.withdraw(result.claimIds().get(0), consumer.getId(), now);

            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
            assertThat(items(group).get(0).getStatus()).isEqualTo(OrderProductStatus.PURCHASE_CONFIRMED);
        }

        @Test
        @DisplayName("자동 취소도 보류를 푼다 — 7일이 안 지났으면 확정하지 않고 원래 타이머가 돈다(#26)")
        void expiryReleasesHold() throws Exception {
            OrderDeliveryGroup late = deliveredGroup(1, LocalDateTime.now().minusDays(8));
            OrderDeliveryGroup early = deliveredGroup(1, LocalDateTime.now().minusDays(1));
            LocalDateTime now = LocalDateTime.now();
            RequestResult lateClaim = claimService.request(command(late, ClaimReason.CHANGE_OF_MIND, null, null), now);
            RequestResult earlyClaim = claimService.request(command(early, ClaimReason.CHANGE_OF_MIND, null, null), now);
            jdbc.update("UPDATE order_claim_collection SET invoice_due_at = ?", now.minusMinutes(1));

            claimService.expireInvoice(lateClaim.collectionId(), now);
            claimService.expireInvoice(earlyClaim.collectionId(), now);

            assertThat(reload(late).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
            assertThat(reload(early).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        }

        @Test
        @DisplayName("거절된 클레임은 진행 중이어도 구매확정을 세우지 않는다 — 같은 그룹의 다른 보류 클레임은 세운다")
        void rejectedClaimDoesNotBlock() throws Exception {
            OrderDeliveryGroup group = deliveredTwoItemGroup(LocalDateTime.now().minusDays(8));
            LocalDateTime now = LocalDateTime.now();
            RequestResult rejected = claimService.request(command(group, items(group).get(0).getId(), 1), now);
            RequestResult open = claimService.request(command(group, items(group).get(1).getId(), 1), now);
            jdbc.update("UPDATE order_claim SET status = 'REJECT_HOLD', rejected_at = ? WHERE claim_id = ?", now,
                    rejected.claimIds().get(0));

            assertThat(fulfillmentService.confirmIfDue(group.getId(), now)).isFalse();
            claimService.withdraw(open.claimIds().get(0), consumer.getId(), now);

            assertThat(reload(group).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CONFIRMED);
            assertThat(claimRow(rejected.claimIds().get(0))).containsEntry("status", "REJECT_HOLD");
        }

        @Test
        @DisplayName("교환 재발송이 도착했으면 그 시각부터 다시 7일이다 — 최초 배송완료 시각으로 확정하지 않는다")
        void restartAfterExchange() throws Exception {
            OrderDeliveryGroup group = deliveredGroup(1, LocalDateTime.now().minusDays(20));
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("UPDATE order_delivery_group SET confirm_restart_at = ? WHERE delivery_group_id = ?",
                    now.minusDays(2), group.getId());

            assertThat(fulfillmentService.findIdsToConfirm(now.minusDays(7), 10)).doesNotContain(group.getId());
            assertThat(fulfillmentService.confirmIfDue(group.getId(), now)).isFalse();

            jdbc.update("UPDATE order_delivery_group SET confirm_restart_at = ? WHERE delivery_group_id = ?",
                    now.minusDays(8), group.getId());
            assertThat(fulfillmentService.confirmIfDue(group.getId(), now)).isTrue();
        }

        @Test
        @DisplayName("전량 반품된 항목은 구매확정 때 PURCHASE_CONFIRMED 로 올라가지 않는다 — 그룹은 그대로 닫힌다")
        void returnedItemStaysReturned() throws Exception {
            OrderDeliveryGroup group = deliveredTwoItemGroup(LocalDateTime.now().minusDays(8));
            OrderProduct returned = items(group).get(0);
            jdbc.update("UPDATE order_product SET returned_quantity = quantity, status = 'RETURNED' "
                    + "WHERE order_product_id = ?", returned.getId());

            assertThat(fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now())).isTrue();

            assertThat(items(group)).extracting(OrderProduct::getStatus)
                    .containsExactly(OrderProductStatus.RETURNED, OrderProductStatus.PURCHASE_CONFIRMED);
        }
    }

    @Test
    @DisplayName("판매 집계는 유효 수량으로 센다 — 부분 반품은 수량만큼 빠지고 전량 반품 줄은 통째로 빠진다")
    void salesUseEffectiveQuantity() throws Exception {
        OrderDeliveryGroup partial = deliveredGroup(2);
        OrderDeliveryGroup full = deliveredGroup(1);
        GroupBuySales before = salesReader.readSales(groupBuy.getId()).orElseThrow();
        assertThat(before.amount()).isEqualTo(3L * CREAM_PRICE);

        jdbc.update("UPDATE order_product SET returned_quantity = 1 WHERE order_product_id = ?",
                items(partial).get(0).getId());
        jdbc.update("UPDATE order_product SET returned_quantity = 1, status = 'RETURNED' WHERE order_product_id = ?",
                items(full).get(0).getId());

        GroupBuySales after = salesReader.readSales(groupBuy.getId()).orElseThrow();
        assertThat(after.amount()).isEqualTo((long) CREAM_PRICE);
        assertThat(after.orderCount()).isEqualTo(1);
        assertThat(after.itemQuantities()).singleElement()
                .satisfies(quantity -> assertThat(quantity.quantity()).isEqualTo(1));
    }

    // ------------------------------------------------------------------ 픽스처

    private OrderDeliveryGroup deliveredGroup(int quantity) throws Exception {
        return deliveredGroup(quantity, LocalDateTime.now().minusHours(1));
    }

    private OrderDeliveryGroup deliveredGroup(int quantity, LocalDateTime deliveredAt) throws Exception {
        return delivered(shipped(prepared(paidGroup(creamVariant, quantity)), "CJ", newInvoice()),
                deliveredAt.withNano(0));
    }

    private OrderDeliveryGroup deliveredTwoItemGroup() throws Exception {
        return deliveredTwoItemGroup(LocalDateTime.now().minusHours(1));
    }

    private OrderDeliveryGroup deliveredTwoItemGroup(LocalDateTime deliveredAt) throws Exception {
        return delivered(shipped(prepared(paidTwoItemGroup()), "CJ", newInvoice()), deliveredAt.withNano(0));
    }

    private static String newInvoice() {
        return String.valueOf(100_000_000_000L + (long) (Math.random() * 899_999_999_999L));
    }

    /** 첫 항목 전량 — 사유·송장을 고른다. */
    private RequestCommand command(OrderDeliveryGroup group, ClaimReason reason, String detail, Invoice invoice) {
        OrderProduct product = items(group).get(0);
        return new RequestCommand(consumer.getId(), group.getId(), ClaimType.RETURN, reason, detail, List.of(),
                List.of(new Item(product.getId(), product.getQuantity())), invoice, UUID.randomUUID().toString());
    }

    private RequestCommand command(OrderDeliveryGroup group, Long orderProductId, int quantity) {
        return new RequestCommand(consumer.getId(), group.getId(), ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND,
                null, List.of(), List.of(new Item(orderProductId, quantity)), null, null);
    }

    /** 그 하위주문의 전 항목을 한 박스로. */
    private RequestCommand commandForAll(OrderDeliveryGroup group) {
        return new RequestCommand(consumer.getId(), group.getId(), ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND,
                null, List.of(), items(group).stream().map(p -> new Item(p.getId(), p.getQuantity())).toList(),
                null, null);
    }

    private Map<String, Object> claimRow(Long claimId) {
        return jdbc.queryForMap("SELECT * FROM order_claim WHERE claim_id = ?", claimId);
    }

    private Map<String, Object> collectionRow(Long collectionId) {
        return jdbc.queryForMap("SELECT * FROM order_claim_collection WHERE collection_id = ?", collectionId);
    }

    private List<String> events(Long claimId) {
        return jdbc.queryForList("SELECT event_type FROM order_claim_history WHERE claim_id = ? "
                + "ORDER BY claim_history_id", String.class, claimId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static LocalDateTime time(Object value) {
        return value instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
