package showroomz.domain.order.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import showroomz.domain.notification.service.NotificationCommand;
import showroomz.domain.notification.service.NotificationDeadlineNoticeService;
import showroomz.domain.notification.service.NotificationDeadlineNoticeService.OrderLine;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.event.ClaimHistoryRecordedEvent;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.type.ClaimOpenedBy;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.RefundTaskSource;

import java.util.List;

/**
 * 주문 · 클레임 사건 → 알림함(1017 설계서 1-3 소비자 · 1-4 파트너 「판매」). 이력이 쌓이는 같은 지점에서 부른다 — 하위주문은
 * {@code OrderFulfillmentService#appendHistory}, 클레임은 {@link ClaimHistoryRecordedEvent}. 이력이 빠진 전이가 없으니 알림도 빠지지 않는다.
 *
 * <p>소비자 쪽 사건(신규 주문 · 취소 요청 · 반품 신청)은 파트너 알림이 아니다 — 처리 대기 숫자로만 보인다. 파트너에게는 브랜드가 아닌 쪽
 * (운영팀 · 시스템)이 처리해 상황이 바뀐 일만 간다.
 */
@Component
@RequiredArgsConstructor
public class OrderNotificationService {

    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderClaimRepository claimRepository;
    private final NotificationPublisher publisher;
    private final NotificationDeadlineNoticeService lines;

    @PersistenceContext
    private EntityManager entityManager;

    /** 하위주문 이력 한 줄 — 호출자 트랜잭션 안. 알림은 커밋 뒤에 쓰인다. */
    public void onFulfillment(Long deliveryGroupId, FulfillmentEventType eventType, FulfillmentActorType actorType) {
        switch (eventType) {
            case INVOICE_REGISTERED, DELIVERED, CANCEL_REQUEST_APPROVED, CANCEL_REQUEST_REJECTED, REFUND_EXECUTED,
                 REFUND_ENQUEUED_BY_OPERATOR, RETURN_DETECTED, TRACKING_STALLED, STALL_CHOICE_MADE -> {
            }
            default -> {
                return;
            }
        }
        OrderDeliveryGroup group = deliveryGroupRepository.findById(deliveryGroupId).orElse(null);
        if (group == null) {
            return;
        }
        Long userId = group.getOrder().getUser().getId();
        Long marketId = group.getMarketId();
        OrderLine line = lines.firstLine(deliveryGroupId);
        String product = line == null ? "주문 상품" : line.label();
        String image = line == null ? null : line.imageUrl();
        String code = group.getSubOrderNumber() == null ? String.valueOf(group.getId()) : group.getSubOrderNumber();

        switch (eventType) {
            case INVOICE_REGISTERED -> {
                toUser(userId, NotificationType.ORDER_SHIPPED, "주문하신 " + product + "이 발송되었어요", product,
                        deliveryGroupId, image, "SHIPPED:" + deliveryGroupId);
                if (actorType == FulfillmentActorType.ADMIN) {
                    toSeller(marketId, NotificationType.ORDER_INVOICE_BY_ADMIN, NotificationActorType.OPERATOR,
                            "주문 " + code + " 송장을 운영팀이 대행 등록했습니다", deliveryGroupId);
                }
            }
            case DELIVERED -> {
                toUser(userId, NotificationType.ORDER_DELIVERED, product + " 배송이 완료되었어요", product,
                        deliveryGroupId, image, "DELIVERED:" + deliveryGroupId);
                if (actorType == FulfillmentActorType.ADMIN) {
                    toSeller(marketId, NotificationType.ORDER_DELIVERED_BY_ADMIN, NotificationActorType.OPERATOR,
                            "주문 " + code + "을 운영팀이 배송완료로 처리했습니다", deliveryGroupId);
                }
            }
            case CANCEL_REQUEST_APPROVED -> {
                toUser(userId, NotificationType.CANCEL_REQUEST_APPROVED, "취소 요청이 승인되었어요 · 환불이 진행돼요",
                        "승인", deliveryGroupId, image, null);
                if (actorType == FulfillmentActorType.SYSTEM) {
                    toSeller(marketId, NotificationType.CANCEL_AUTO_APPROVED, NotificationActorType.SYSTEM,
                            "주문 " + code + " 취소 요청이 응답 기한 경과로 자동 승인되었습니다", deliveryGroupId);
                }
            }
            case CANCEL_REQUEST_REJECTED -> toUser(userId, NotificationType.CANCEL_REQUEST_REJECTED,
                    "취소 요청이 반려되었어요 · 주문은 그대로 배송돼요", "반려", deliveryGroupId, image, null);
            case REFUND_EXECUTED -> {
                // 반품 검수 통과 환불은 클레임 알림(REFUND_COMPLETED · 클레임으로 이동)이 따로 간다 — 같은 환불을 두 번 알리지 않는다.
                if (!refundedByClaim(deliveryGroupId)) {
                    toUser(userId, NotificationType.REFUND_COMPLETED, product + " 환불이 완료되었어요", product,
                            deliveryGroupId, image, null);
                }
            }
            case REFUND_ENQUEUED_BY_OPERATOR -> toSeller(marketId, NotificationType.ORDER_OPERATOR_REFUND,
                    NotificationActorType.OPERATOR, "주문 " + code + "에 운영자 사유 환불이 승인되었습니다", deliveryGroupId);
            case RETURN_DETECTED -> toSeller(marketId, NotificationType.RETURN_DETECTED, NotificationActorType.SYSTEM,
                    "주문 " + code + " 반송이 감지되었습니다", deliveryGroupId);
            case TRACKING_STALLED -> toSeller(marketId, NotificationType.TRACKING_STALLED, NotificationActorType.SYSTEM,
                    "주문 " + code + " 배송 추적이 멈췄습니다 — 택배사 확인이 필요합니다", deliveryGroupId);
            case STALL_CHOICE_MADE -> toSeller(marketId, NotificationType.ORDER_STALL_CHOICE_MADE, NotificationActorType.SYSTEM,
                    "주문 " + code + " 소비자가 " + (group.getStallChoice() == null ? "처리 방법"
                            : group.getStallChoice().getLabel()) + "을 선택했어요", deliveryGroupId);
            default -> {
            }
        }
    }

    /** 운임 환급 계좌 송금 완료(1021 설계서 4-5) — 금액이 문장에 들어가 이력 사건과 따로 부른다. 호출자 트랜잭션 안. */
    public void onRefundTransferred(Long deliveryGroupId, Long refundTaskId, int amount) {
        OrderDeliveryGroup group = deliveryGroupRepository.findById(deliveryGroupId).orElse(null);
        if (group == null) {
            return;
        }
        OrderLine line = lines.firstLine(deliveryGroupId);
        toUser(group.getOrder().getUser().getId(), NotificationType.CLAIM_REFUND_TRANSFERRED,
                String.format("운임 환급 %,d원을 계좌로 송금했어요", amount), String.format("%,d원", amount), deliveryGroupId,
                line == null ? null : line.imageUrl(), "REFUND_TRANSFERRED:" + refundTaskId);
    }

    /** 방금 집행된 환불이 반품 검수 통과 환불인가 — 같은 트랜잭션에서 완료 처리된 행을 읽는다(조회 전 자동 flush). */
    private boolean refundedByClaim(Long deliveryGroupId) {
        List<RefundTaskSource> sources = entityManager.createQuery("SELECT t.source FROM OrderRefundTask t "
                        + "WHERE t.deliveryGroup.id = :groupId "
                        + "AND t.status = showroomz.domain.order.type.RefundTaskStatus.DONE "
                        + "ORDER BY t.executedAt DESC, t.id DESC", RefundTaskSource.class)
                .setParameter("groupId", deliveryGroupId).setMaxResults(1).getResultList();
        return !sources.isEmpty() && sources.get(0) == RefundTaskSource.CLAIM_RETURN_PASSED;
    }

    /** 클레임 이력 한 줄 — 같은 트랜잭션에서 동기로 받는다. */
    @EventListener
    public void onClaim(ClaimHistoryRecordedEvent event) {
        switch (event.eventType()) {
            case REQUESTED, INSPECTION_PASSED, INSPECTION_REJECTED, REFUND_EXECUTED -> {
            }
            default -> {
                return;
            }
        }
        OrderClaim claim = claimRepository.findById(event.claimId()).orElse(null);
        if (claim == null) {
            return;
        }
        Long collectionId = claim.getCollection() == null ? claim.getId() : claim.getCollection().getId();
        String product = claim.getOrderProduct() == null ? "주문 상품" : claim.getOrderProduct().getProductName();
        String image = claim.getOrderProduct() == null ? null : claim.getOrderProduct().getImageUrl();
        String kind = claim.getType() == ClaimType.EXCHANGE ? "교환" : "반품";

        switch (event.eventType()) {
            case REQUESTED -> {
                if (claim.getOpenedBy() == ClaimOpenedBy.OPERATOR) {
                    toSeller(claim.getMarketId(), NotificationType.CLAIM_OPENED_BY_ADMIN, NotificationActorType.OPERATOR,
                            "운영팀이 " + product + " " + kind + " 클레임을 개설했습니다", claim.getId(),
                            NotificationTargetType.CLAIM);
                    return;
                }
                if (claim.getStatus() == ClaimStatus.PAYMENT_PENDING) {
                    publisher.publish(claimCommand(claim, NotificationType.CLAIM_RESHIP_FEE_NEEDED,
                            product + " 교환 재발송비를 결제해 주세요", product, image, collectionId,
                            "CLAIM_FEE:" + collectionId, "재발송비 결제 필요", null));
                } else if (claim.getStatus() == ClaimStatus.REQUESTED) {
                    var due = claim.getCollection() == null ? null : claim.getCollection().getInvoiceDueAt();
                    publisher.publish(claimCommand(claim, NotificationType.CLAIM_COLLECT_INVOICE_NEEDED,
                            kind + "할 " + product + "의 회수 송장을 등록해 주세요", product, image, collectionId,
                            "CLAIM_INVOICE:" + collectionId, "회수 송장 등록 필요", due));
                }
            }
            case INSPECTION_PASSED -> publisher.publish(claimCommand(claim, NotificationType.CLAIM_INSPECTION_PASSED,
                    claim.getType() == ClaimType.RETURN ? kind + " 검수가 끝나 " + product + " 환불이 진행돼요"
                            : kind + " 검수가 끝나 " + product + " 교환 상품이 발송될 예정이에요",
                    product, image, collectionId, "CLAIM_PASSED:" + claim.getId(), null, null));
            case INSPECTION_REJECTED -> publisher.publish(claimCommand(claim, NotificationType.CLAIM_INSPECTION_REJECTED,
                    product + " " + kind + " 검수가 반려되었어요 · 사유를 확인해 주세요", product, image, collectionId,
                    "CLAIM_REJECTED:" + claim.getId(), null, null));
            case REFUND_EXECUTED -> publisher.publish(claimCommand(claim, NotificationType.REFUND_COMPLETED,
                    product + " " + kind + " 환불이 완료되었어요", product, image, collectionId,
                    "CLAIM_REFUND:" + collectionId, null, null));
            default -> {
            }
        }
    }

    private NotificationCommand claimCommand(OrderClaim claim, NotificationType type, String title, String emphasis,
                                             String image, Long collectionId, String dedupKey, String todo,
                                             java.time.LocalDateTime todoDue) {
        return NotificationCommand.toUser(claim.getUserId(), type).actorType(NotificationActorType.SYSTEM)
                .title(title).emphasis(emphasis).body(todo)
                .targetType(NotificationTargetType.CLAIM).targetId(claim.getId())
                .targetSub(String.valueOf(collectionId))
                .imageKind(NotificationImageKind.THUMB).imageRef(image)
                .todoDueAt(todo == null ? null : todoDue)
                .dedupKey(dedupKey).build();
    }

    private void toUser(Long userId, NotificationType type, String title, String emphasis, Long groupId, String image,
                        String dedupKey) {
        publisher.publish(NotificationCommand.toUser(userId, type).actorType(NotificationActorType.SYSTEM)
                .title(title).emphasis(emphasis)
                .targetType(NotificationTargetType.ORDER_GROUP).targetId(groupId)
                .imageKind(NotificationImageKind.THUMB).imageRef(image).dedupKey(dedupKey).build());
    }

    private void toSeller(Long marketId, NotificationType type, NotificationActorType actor, String title, Long groupId) {
        toSeller(marketId, type, actor, title, groupId, NotificationTargetType.ORDER_GROUP);
    }

    private void toSeller(Long marketId, NotificationType type, NotificationActorType actor, String title, Long targetId,
                          NotificationTargetType targetType) {
        if (marketId == null) {
            return;
        }
        publisher.publish(NotificationCommand.toSeller(marketId, type).actorType(actor).title(title)
                .targetType(targetType).targetId(targetId).build());
    }
}
