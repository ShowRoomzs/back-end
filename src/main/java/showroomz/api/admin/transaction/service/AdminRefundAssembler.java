package showroomz.api.admin.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.admin.transaction.dto.AdminTransactionDto.RefundRoute;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCharge;
import showroomz.domain.order.entity.OrderClaimHistory;
import showroomz.domain.order.entity.OrderFulfillmentHistory;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderClaimChargeRepository;
import showroomz.domain.order.repository.OrderClaimHistoryRepository;
import showroomz.domain.order.repository.OrderClaimPaymentRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderFulfillmentHistoryRepository;
import showroomz.domain.order.type.ClaimChargeStatus;
import showroomz.domain.order.type.ClaimChargeType;
import showroomz.domain.order.type.ClaimEventType;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.OperatorRefundReason;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.entity.PaymentCancel;
import showroomz.domain.payment.repository.PaymentCancelRepository;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.global.config.properties.OrderProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 어드민 환불 관리(06c) 행 · 상세 조립(39 설계서 3-1 · 3-3 · 3-5) — 경로 · 결제 · 상태 열의 <b>문장은 서버가 만든다</b>. 목록은
 * 페이지 단위로 연관(결제 · 취소 요청 · 클레임 · 운영자 이름)을 한 번에 읽는다. 트랜잭션 안에서 부른다.
 */
@Component
@RequiredArgsConstructor
public class AdminRefundAssembler {

    private static final DateTimeFormatter NOTE_TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final Set<FulfillmentEventType> REFUND_EVENTS = EnumSet.of(
            FulfillmentEventType.REFUND_ENQUEUED_BY_OPERATOR, FulfillmentEventType.REFUND_EXECUTED,
            FulfillmentEventType.REFUND_FAILED, FulfillmentEventType.REFUND_VOIDED,
            FulfillmentEventType.REFUND_RECORDED_MANUALLY);
    private static final Set<ClaimEventType> CLAIM_REFUND_EVENTS = EnumSet.of(ClaimEventType.DISPUTE_ACCEPTED,
            ClaimEventType.REFUND_EXECUTED);
    private static final String ORIGINAL = "ORIGINAL";
    private static final String ADDITIONAL = "ADDITIONAL";

    private final PaymentRepository paymentRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final OrderCancelRequestRepository cancelRequestRepository;
    private final OrderClaimRepository claimRepository;
    private final OrderClaimChargeRepository chargeRepository;
    private final OrderClaimPaymentRepository claimPaymentRepository;
    private final OrderClaimHistoryRepository claimHistoryRepository;
    private final OrderFulfillmentHistoryRepository fulfillmentHistoryRepository;
    private final AdminOperatorResolver operatorResolver;
    private final OrderProperties orderProperties;

    /** 한 페이지의 연관 — 행마다 쿼리하지 않는다. */
    private record Context(Map<String, Payment> payments, Map<Long, OrderCancelRequest> cancelRequests,
                           Set<Long> adminCancelledGroups, Map<Long, List<OrderClaim>> claimsByCollection,
                           Map<Long, OrderClaim> claimsById, Map<Long, String> names) {
    }

    public List<AdminTransactionDto.RefundItem> items(List<OrderRefundTask> tasks) {
        Context context = context(tasks);
        return tasks.stream().map(task -> item(task, context)).toList();
    }

    public AdminTransactionDto.RefundItem item(OrderRefundTask task) {
        return items(List.of(task)).get(0);
    }

    // ------------------------------------------------------------------ 상세(M1 · M2)

    public AdminTransactionDto.RefundDetail detail(OrderRefundTask task) {
        Context context = context(List.of(task));
        Payment payment = task.getPaymentId() == null ? null : context.payments().get(task.getPaymentId());
        String paymentKind = paymentKind(task);
        boolean partial = partial(task, payment);
        Long claimId = task.getSource() == RefundTaskSource.OPERATOR_REASON ? task.getSourceId() : null;
        Long collectionId = collectionIdOf(task, context);
        Long cancelRequestId = task.getSource() == RefundTaskSource.CANCEL_REQUEST_APPROVED ? task.getSourceId() : null;
        List<OrderClaimHistory> claimHistory = claimId == null ? List.of() : claimHistoryRepository.findByClaimId(claimId);
        List<OrderFulfillmentHistory> groupHistory = fulfillmentHistoryRepository
                .findByDeliveryGroupId(task.getDeliveryGroup().getId()).stream()
                .filter(h -> REFUND_EVENTS.contains(h.getEventType()) && belongsTo(h.getDetail(), task))
                .sorted(Comparator.comparing(OrderFulfillmentHistory::getOccurredAt)
                        .thenComparing(OrderFulfillmentHistory::getId))
                .toList();
        Map<Long, String> names = operatorResolver.namesOf(Stream.concat(
                Stream.of(task.getRequestedBy(), task.getExecutedBy()),
                Stream.concat(groupHistory.stream().filter(h -> h.getActorType() == FulfillmentActorType.ADMIN)
                                .map(OrderFulfillmentHistory::getActorId),
                        claimHistory.stream().filter(h -> h.getActorType() == FulfillmentActorType.ADMIN)
                                .map(OrderClaimHistory::getActorId))).filter(Objects::nonNull).toList());

        AdminTransactionDto.RefundReason reason = task.getOrigin() != RefundTaskOrigin.OPERATOR ? null
                : new AdminTransactionDto.RefundReason(task.getReasonCode(),
                task.getReasonCode() == null ? "운영자 사유 환불" : task.getReasonCode().getLabel(), task.getReasonDetail(),
                task.getRequestedBy(), names.get(task.getRequestedBy()), task.getCreatedAt());
        AdminTransactionDto.RefundTarget target = payment == null ? null
                : new AdminTransactionDto.RefundTarget(payment.getPaymentId(), payment.getPgTxId(), paymentKind,
                payment.methodLabel(), payment.getAmount(), payment.getCancelledAmount(), payment.cancellableAmount(),
                task.getRefundAmount(), partial, payment.getStatus().name());
        String disputeDetail = claimHistory.stream().filter(h -> h.getEventType() == ClaimEventType.DISPUTE_ACCEPTED)
                .map(OrderClaimHistory::getDetail).filter(Objects::nonNull).reduce((a, b) -> b).orElse(null);
        List<AdminTransactionDto.RefundAdditionalPayment> additional = collectionId == null ? List.of()
                : chargeRepository.findByCollectionId(collectionId).stream()
                .map(charge -> additionalPayment(charge, task, disputeDetail)).toList();
        AdminTransactionDto.RefundFailure failure = task.getStatus() != RefundTaskStatus.FAILED ? null
                : new AdminTransactionDto.RefundFailure(task.getLastError(), task.getLastErrorCode(), task.getModifiedAt(),
                groupHistory.stream().filter(h -> h.getEventType() == FulfillmentEventType.REFUND_FAILED)
                        .map(h -> new AdminTransactionDto.RefundFailureAttempt(h.getOccurredAt(), h.getActorType().name(),
                                stripRefundNo(h.getDetail(), task))).toList());
        AdminTransactionDto.RefundExecution execution = task.getStatus() != RefundTaskStatus.DONE ? null
                : new AdminTransactionDto.RefundExecution(task.getExecutedAt(), task.getExecutedBy(),
                names.get(task.getExecutedBy()), task.getPaymentCancelId(),
                task.getPaymentCancelId() == null ? null : paymentCancelRepository.findById(task.getPaymentCancelId())
                        .map(PaymentCancel::getPgCancellationId).orElse(null));

        List<AdminTransactionDto.RefundHistory> history = new ArrayList<>();
        groupHistory.forEach(h -> history.add(new AdminTransactionDto.RefundHistory(h.getOccurredAt(),
                h.getEventType().name(), h.getEventType().getLabel(), h.getActorType().name(),
                h.getActorType() == FulfillmentActorType.ADMIN ? names.get(h.getActorId()) : null,
                stripRefundNo(h.getDetail(), task))));
        claimHistory.stream().filter(h -> CLAIM_REFUND_EVENTS.contains(h.getEventType())).forEach(h ->
                history.add(new AdminTransactionDto.RefundHistory(h.getOccurredAt(), h.getEventType().name(),
                        h.getEventType().getLabel(), h.getActorType().name(),
                        h.getActorType() == FulfillmentActorType.ADMIN ? names.get(h.getActorId()) : null, h.getDetail())));
        history.sort(Comparator.comparing(AdminTransactionDto.RefundHistory::at));

        return new AdminTransactionDto.RefundDetail(task.getId(), task.refundNo(),
                new AdminTransactionDto.RefundOrderRef(task.getOrder().getId(), task.getOrder().getOrderNumber(),
                        task.getDeliveryGroup().getId(), task.getDeliveryGroup().getSubOrderNumber(),
                        task.getDeliveryGroup().getMarketName()),
                new AdminTransactionDto.RefundSourceRef(task.getSource(), RefundRoute.of(task.getSource()),
                        sourceLabel(task, context), sourceRef(task, context), claimId, cancelRequestId, collectionId),
                task.getOrigin(), task.getOrigin().getLabel(), reason, target, additional,
                new AdminTransactionDto.RefundSettlement("BEFORE_SETTLEMENT", "정산 전 · 클로백 없음", null),
                task.getStatus(), statusLabel(task.getStatus()), task.getAttempt(),
                orderProperties.getRefundAutoMaxAttempts(), failure, execution, executable(task), task.isVoidable(),
                task.isExecutable(), history);
    }

    // ------------------------------------------------------------------ 행

    private AdminTransactionDto.RefundItem item(OrderRefundTask task, Context context) {
        Payment payment = task.getPaymentId() == null ? null : context.payments().get(task.getPaymentId());
        String paymentKind = paymentKind(task);
        boolean partial = partial(task, payment);
        return new AdminTransactionDto.RefundItem(task.getId(), task.refundNo(), task.getOrder().getId(),
                task.getOrder().getOrderNumber(), task.getDeliveryGroup().getId(),
                task.getDeliveryGroup().getSubOrderNumber(), task.getDeliveryGroup().getMarketName(), task.getSource(),
                RefundRoute.of(task.getSource()), sourceLabel(task, context), sourceRef(task, context), task.getOrigin(),
                task.getOrigin().getLabel(), task.getReasonCode() == null ? null : task.getReasonCode().getLabel(),
                task.getReasonDetail(), paymentKind, partial, payment == null ? null : payment.methodLabel(),
                paymentLabel(task, payment, paymentKind, partial), task.getRefundAmount(), task.getStatus(),
                statusLabel(task.getStatus()), statusNote(task, context.names()), task.getAttempt(), task.getLastError(),
                displayAt(task), task.getCreatedAt(), task.getExecutedAt(), executable(task), task.isVoidable(),
                task.isExecutable());
    }

    private Context context(List<OrderRefundTask> tasks) {
        Map<String, Payment> payments = paymentRepository.findAllById(tasks.stream().map(OrderRefundTask::getPaymentId)
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Payment::getPaymentId, Function.identity()));
        Map<Long, OrderCancelRequest> cancelRequests = cancelRequestRepository.findAllById(
                        sourceIds(tasks, RefundTaskSource.CANCEL_REQUEST_APPROVED)).stream()
                .collect(Collectors.toMap(OrderCancelRequest::getId, Function.identity()));
        List<Long> directGroups = tasks.stream().filter(t -> t.getSource() == RefundTaskSource.SELLER_DIRECT_CANCEL)
                .map(t -> t.getDeliveryGroup().getId()).distinct().toList();
        Set<Long> adminCancelled = directGroups.isEmpty() ? Set.of()
                : new HashSet<>(fulfillmentHistoryRepository.findGroupIdsWithEvent(directGroups,
                FulfillmentEventType.CANCELLED_BY_SELLER, FulfillmentActorType.ADMIN));
        List<Long> collectionIds = Stream.concat(sourceIds(tasks, RefundTaskSource.CLAIM_RETURN_PASSED).stream(),
                sourceIds(tasks, RefundTaskSource.CLAIM_PAYMENT_CANCELLED).stream()).distinct().toList();
        Map<Long, List<OrderClaim>> claimsByCollection = collectionIds.isEmpty() ? Map.of()
                : claimRepository.findByCollectionIds(collectionIds).stream()
                .collect(Collectors.groupingBy(claim -> claim.getCollection().getId()));
        List<Long> claimIds = sourceIds(tasks, RefundTaskSource.OPERATOR_REASON);
        Map<Long, OrderClaim> claimsById = claimIds.isEmpty() ? Map.of()
                : claimRepository.findAllById(claimIds).stream()
                .collect(Collectors.toMap(OrderClaim::getId, Function.identity()));
        Map<Long, String> names = operatorResolver.namesOf(tasks.stream()
                .flatMap(t -> Stream.of(t.getRequestedBy(), t.getExecutedBy())).filter(Objects::nonNull).toList());
        return new Context(payments, cancelRequests, adminCancelled, claimsByCollection, claimsById, names);
    }

    private static List<Long> sourceIds(Collection<OrderRefundTask> tasks, RefundTaskSource source) {
        return tasks.stream().filter(t -> t.getSource() == source && t.getSourceId() != null)
                .map(OrderRefundTask::getSourceId).distinct().toList();
    }

    // ------------------------------------------------------------------ 문장(3-5)

    private static String sourceLabel(OrderRefundTask task, Context context) {
        return switch (task.getSource()) {
            case CANCEL_REQUEST_APPROVED -> {
                OrderCancelRequest request = task.getSourceId() == null ? null
                        : context.cancelRequests().get(task.getSourceId());
                yield request != null && request.isAutoApproved() ? "취소 요청 자동 승인" : "취소 요청 승인";
            }
            case SELLER_DIRECT_CANCEL -> context.adminCancelledGroups().contains(task.getDeliveryGroup().getId())
                    ? "운영자 대행 직권 취소" : "브랜드 직권 취소";
            case USER_CANCEL_BEFORE_PREPARE -> "결제완료 소비자 취소";
            case LOST_IN_TRANSIT -> "배송 분실 처리";
            case RETURN_COMPLETED -> "반송 완료";
            case CLAIM_RETURN_PASSED -> claimsOf(task, context).stream().anyMatch(c -> c.getRejectedAt() != null)
                    ? "반품 · 일부 반려" : "반품 검수 통과";
            case CLAIM_PAYMENT_CANCELLED -> {
                List<OrderClaim> claims = claimsOf(task, context);
                boolean exchange = claims.isEmpty()
                        || claims.get(0).getType() == showroomz.domain.order.type.ClaimType.EXCHANGE;
                boolean withdrawn = !claims.isEmpty() && claims.stream()
                        .allMatch(c -> c.getResult() == ClaimResult.CANCELLED);
                String base = exchange ? "교환 재발송비 환불" : "반려 재발송비 환불";
                yield withdrawn && exchange ? base + " · 교환 철회" : base;
            }
            case OPERATOR_REASON -> task.getReasonCode() == null ? "운영자 사유 환불" : task.getReasonCode().getLabel();
        };
    }

    private static String sourceRef(OrderRefundTask task, Context context) {
        if (task.getSourceId() == null) {
            return null;
        }
        return switch (task.getSource()) {
            case CANCEL_REQUEST_APPROVED -> "CRQ-" + task.getSourceId();
            case CLAIM_RETURN_PASSED, CLAIM_PAYMENT_CANCELLED -> claimsOf(task, context).stream()
                    .min(Comparator.comparing(OrderClaim::getId)).map(OrderClaim::claimNumber).orElse(null);
            case OPERATOR_REASON -> {
                OrderClaim claim = context.claimsById().get(task.getSourceId());
                yield claim != null ? claim.claimNumber() : "CLM-" + task.getSourceId();
            }
            default -> null;
        };
    }

    private static List<OrderClaim> claimsOf(OrderRefundTask task, Context context) {
        return task.getSourceId() == null ? List.of()
                : context.claimsByCollection().getOrDefault(task.getSourceId(), List.of());
    }

    private static Long collectionIdOf(OrderRefundTask task, Context context) {
        return switch (task.getSource()) {
            case CLAIM_RETURN_PASSED, CLAIM_PAYMENT_CANCELLED -> task.getSourceId();
            case OPERATOR_REASON -> {
                OrderClaim claim = task.getSourceId() == null ? null : context.claimsById().get(task.getSourceId());
                yield claim == null ? null : claim.getCollection().getId();
            }
            default -> null;
        };
    }

    /** 재발송비 청구의 운영자 문장 — 반려 이의 인용이 청구를 어떻게 정리했는지(41 보고 1번). */
    private AdminTransactionDto.RefundAdditionalPayment additionalPayment(OrderClaimCharge charge, OrderRefundTask task,
                                                                         String disputeDetail) {
        boolean dispute = task.getReasonCode() == OperatorRefundReason.DISPUTE_ACCEPTED
                && charge.getType() == ClaimChargeType.REJECT_RESHIP;
        String note = null;
        if (dispute) {
            note = switch (charge.getStatus()) {
                case REFUNDED -> "인용 시 결제 취소됨";
                case VOID -> disputeDetail != null && disputeDetail.contains("차감 환원")
                        ? "차감분 환원 — 환불액에 포함" : "재발송비 결제 요청은 인용 시 취소됨";
                case PAID -> "결제된 재발송비 — 같은 박스의 다른 반려 재발송에 쓰인다";
                default -> null;
            };
        }
        String claimPaymentStatus = charge.getPaidPaymentId() == null ? null
                : claimPaymentRepository.findById(charge.getPaidPaymentId()).map(p -> p.getStatus().name()).orElse(null);
        return new AdminTransactionDto.RefundAdditionalPayment(charge.getId(), charge.getType(),
                charge.getType() == ClaimChargeType.REJECT_RESHIP ? "반려 재발송비" : "교환 재발송비",
                charge.getAmount() == null ? 0 : charge.getAmount(), charge.getStatus(), charge.getStatus().getLabel(),
                note, charge.getPaidPaymentId(), claimPaymentStatus);
    }

    /** 결제 종류 — 적재 때 정한 값(V177). */
    private static String paymentKind(OrderRefundTask task) {
        return task.getPaymentKind() == null ? ORIGINAL : task.getPaymentKind().name();
    }

    /** 원래 결제의 부분 취소인가 — 적재 때 정한 값(V177). 결제 전액 취소의 하위주문 분할 기록은 거짓이다. */
    private static boolean partial(OrderRefundTask task, Payment payment) {
        return task.isPartialCancel();
    }

    private String paymentLabel(OrderRefundTask task, Payment payment, String paymentKind, boolean partial) {
        if (ADDITIONAL.equals(paymentKind)) {
            // 추가 결제는 원래 결제 테이블이 아니라 클레임 결제다 — 수단은 그 결제에서 읽는다.
            String method = task.getPaymentId() == null ? null : claimPaymentRepository.findById(task.getPaymentId())
                    .map(p -> p.getMethod() == null ? null : p.getMethod().getLabel()).orElse(null);
            return (method == null ? "결제" : method) + " · 추가";
        }
        if (payment == null) {
            return null;
        }
        return payment.getMethod().getLabel() + " · 원래" + (partial ? " 부분" : "");
    }

    private static String statusLabel(RefundTaskStatus status) {
        return switch (status) {
            case PENDING -> "집행 대기";
            case EXECUTING -> "환불 처리 중";
            case DONE -> "환불 완료";
            case FAILED -> "환불 실패";
            case VOID -> "취소됨";
        };
    }

    private static String statusNote(OrderRefundTask task, Map<Long, String> names) {
        return switch (task.getStatus()) {
            case PENDING -> task.getOrigin() == RefundTaskOrigin.OPERATOR
                    ? "편입 " + task.getCreatedAt().format(NOTE_TIME) + " · " + names.getOrDefault(task.getRequestedBy(), "운영자")
                    : null;
            case EXECUTING -> "PG 응답 확인 중";
            case FAILED -> {
                String error = task.getLastError() == null ? "환불 실패" : task.getLastError();
                yield task.getAttempt() > 1 ? error + " · 재시도 " + (task.getAttempt() - 1) + "회 실패" : error;
            }
            case DONE -> task.getExecutedBy() == null ? null
                    : "집행 " + names.getOrDefault(task.getExecutedBy(), "운영자") + " · 재확인";
            case VOID -> "편입 철회";
        };
    }

    /** 「일시」 열 — 집행 대기 = 편입 · 실패 = 마지막 실패 · 완료 = 집행(39 설계서 3-1). */
    private static LocalDateTime displayAt(OrderRefundTask task) {
        return switch (task.getStatus()) {
            case PENDING, EXECUTING -> task.getCreatedAt();
            case FAILED, VOID -> task.getModifiedAt();
            case DONE -> task.getExecutedAt();
        };
    }

    private static boolean executable(OrderRefundTask task) {
        return task.isExecutable() && task.getPaymentId() != null;
    }

    /** 이력이 이 큐 행의 것인가 — detail 의 환불번호 접두로 가른다(39 설계서 7-2 #5). */
    private static boolean belongsTo(String detail, OrderRefundTask task) {
        return detail != null && detail.startsWith(task.refundNo() + " ");
    }

    private static String stripRefundNo(String detail, OrderRefundTask task) {
        String prefix = task.refundNo() + " · ";
        return detail != null && detail.startsWith(prefix) ? detail.substring(prefix.length()) : detail;
    }
}
