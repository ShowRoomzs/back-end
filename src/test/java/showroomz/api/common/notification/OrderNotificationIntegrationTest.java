package showroomz.api.common.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소비자 주문 · 클레임 알림(1017 설계서 1-3 · C11) — 발송 · 배송완료 · 취소 요청 승인 · 반려 · 회수 송장 할 일 · 검수 통과 · 환불 완료와
 * 운영자 대행 시 브랜드 알림. 같은 사건은 한 번만(재등록 · 재실행에도).
 */
@DisplayName("1017 주문 · 클레임 알림")
class OrderNotificationIntegrationTest extends SettlementTestSupport {

    @Autowired private NotificationRepository notificationRepository;

    @Test
    @DisplayName("ON-01 발송 → 배송완료(추적) — 소비자 2건 · 주문 상세로 이동 · 브랜드에게는 오지 않는다(브랜드가 한 일)")
    void shippedAndDelivered() {
        OrderDeliveryGroup delivered = deliveredFrom(paidGroup(), LocalDateTime.now().minusHours(1));

        List<Notification> rows = userRows();
        assertThat(rows).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.ORDER_SHIPPED, NotificationType.ORDER_DELIVERED);
        assertThat(rows).allSatisfy(n -> {
            assertThat(n.getTargetType()).isEqualTo(NotificationTargetType.ORDER_GROUP);
            assertThat(n.getTargetId()).isEqualTo(delivered.getId());
            assertThat(n.isAdvertising()).isFalse();
        });
        assertThat(sellerRows()).isEmpty();
    }

    @Test
    @DisplayName("ON-02 송장을 고쳐 다시 등록해도 발송 알림은 한 건")
    void reregisteringInvoiceDoesNotRepeat() throws Exception {
        OrderDeliveryGroup group = preparing(paidGroup());
        registerShipment(group, "CJ", nextTrackingNumber()).andExpect(status().isOk());
        updateShipment(reloadGroup(group), "CJ", nextTrackingNumber()).andExpect(status().isOk());

        assertThat(userRows(NotificationType.ORDER_SHIPPED)).hasSize(1);
    }

    @Test
    @DisplayName("ON-03 취소 요청 — 승인 · 반려가 각각 소비자에게(브랜드가 직접 처리하면 브랜드 알림 없음)")
    void cancelRequestDecisions() throws Exception {
        OrderDeliveryGroup approvedGroup = paidGroup();
        OrderCancelRequest approved = seedCancelRequest(approvedGroup, itemsOf(approvedGroup));
        approveRequest(approved).andExpect(status().isOk());
        OrderDeliveryGroup rejectedGroup = paidGroup();
        OrderCancelRequest rejected = seedCancelRequest(rejectedGroup, itemsOf(rejectedGroup));
        rejectRequest(rejected, "이미 출고 준비가 끝났습니다").andExpect(status().isOk());

        assertThat(userRows(NotificationType.CANCEL_REQUEST_APPROVED)).singleElement()
                .satisfies(n -> assertThat(n.getTargetId()).isEqualTo(approvedGroup.getId()));
        assertThat(userRows(NotificationType.CANCEL_REQUEST_REJECTED)).singleElement()
                .satisfies(n -> assertThat(n.getTargetId()).isEqualTo(rejectedGroup.getId()));
        assertThat(sellerRows()).extracting(Notification::getType).doesNotContain(NotificationType.CANCEL_AUTO_APPROVED);
    }

    @Test
    @DisplayName("ON-04 반품 접수 — 회수 송장을 아직 안 넣었으면 「회수 송장 등록 필요」 할 일(기한) · 넣었으면 할 일 없음")
    void collectInvoiceTodo() {
        OrderDeliveryGroup withoutInvoice = deliveredFrom(paidGroup(), LocalDateTime.now().minusHours(2));
        Long claimId = requestReturn(withoutInvoice, null);

        Notification todo = userRows(NotificationType.CLAIM_COLLECT_INVOICE_NEEDED).get(0);
        assertThat(todo.getTargetType()).isEqualTo(NotificationTargetType.CLAIM);
        assertThat(todo.getTargetId()).isEqualTo(claimId);
        assertThat(todo.getBody()).isEqualTo("회수 송장 등록 필요");
        assertThat(todo.getTodoDueAt()).isNotNull();

        OrderDeliveryGroup withInvoice = deliveredFrom(paidGroup(), LocalDateTime.now().minusHours(2));
        requestReturn(withInvoice, new OrderClaimService.Invoice(showroomz.domain.order.type.DeliveryCarrier.CJ,
                nextTrackingNumber()));
        assertThat(userRows(NotificationType.CLAIM_COLLECT_INVOICE_NEEDED)).hasSize(1);
    }

    @Test
    @DisplayName("ON-05 검수 통과 → 환불 완료 — 클레임으로 이동(회수 묶음 id 를 함께) · 각 한 건")
    void inspectionPassedThenRefunded() {
        OrderDeliveryGroup delivered = deliveredFrom(paidGroup(), LocalDateTime.now().minusHours(2));
        Long claimId = returnClaim(delivered, itemsOf(delivered).get(0), 1, ClaimReason.DAMAGED_OR_DEFECTIVE);
        passClaim(claimId);

        Notification passed = userRows(NotificationType.CLAIM_INSPECTION_PASSED).get(0);
        assertThat(passed.getTargetId()).isEqualTo(claimId);
        assertThat(passed.getTargetSub()).isNotBlank();
        assertThat(passed.getTitle()).contains("환불이 진행돼요");
        assertThat(userRows(NotificationType.REFUND_COMPLETED)).hasSize(1);
    }

    @Test
    @DisplayName("ON-06 운영자 사유 환불 — 브랜드에게 「운영자 사유 환불 승인」 · 집행되면 소비자에게 환불 완료")
    void operatorReasonRefund() {
        OrderDeliveryGroup confirmed = confirmedGroup(creamVariant, 1);
        operatorRefund(confirmed, "POST_CONFIRM_DEFECT", 5_000);

        assertThat(sellerRows()).extracting(Notification::getType).contains(NotificationType.ORDER_OPERATOR_REFUND);
        assertThat(userRows(NotificationType.REFUND_COMPLETED)).singleElement()
                .satisfies(n -> assertThat(n.getTargetId()).isEqualTo(confirmed.getId()));
    }

    // ------------------------------------------------------------------ 픽스처

    private Long requestReturn(OrderDeliveryGroup group, OrderClaimService.Invoice invoice) {
        return claimService.request(new OrderClaimService.RequestCommand(consumer.getId(), group.getId(), ClaimType.RETURN,
                ClaimReason.DAMAGED_OR_DEFECTIVE, ClaimReason.DAMAGED_OR_DEFECTIVE.isDetailRequired() ? "상세 내용입니다" : null,
                List.of(), List.of(new OrderClaimService.Item(itemsOf(group).get(0).getId(), 1)), invoice, null, null),
                LocalDateTime.now()).claimIds().get(0);
    }

    private List<Notification> userRows() {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientType() == NotificationRecipientType.USER && n.getRecipientId().equals(consumer.getId()))
                .toList();
    }

    private List<Notification> userRows(NotificationType type) {
        return userRows().stream().filter(n -> n.getType() == type).toList();
    }

    private List<Notification> sellerRows() {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientType() == NotificationRecipientType.SELLER && n.getRecipientId().equals(brand.marketId()))
                .toList();
    }
}
