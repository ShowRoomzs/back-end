package showroomz.api.admin.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.transaction.dto.AdminOrderDto;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.DeliveryTrackingEvent;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderCancelRequestItem;
import showroomz.domain.order.entity.OrderClaimCharge;
import showroomz.domain.order.entity.OrderClaimPayment;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderFulfillmentHistory;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.order.repository.AdminOrderSearchCondition;
import showroomz.domain.order.repository.DeliveryTrackingEventRepository;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderClaimChargeRepository;
import showroomz.domain.order.repository.OrderClaimPaymentRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderFulfillmentHistoryRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.service.port.OrderSettlementReader;
import showroomz.domain.order.type.AdminOrderTab;
import showroomz.domain.order.type.ClaimChargeType;
import showroomz.domain.order.type.ClaimPaymentStatus;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.TrackingAlert;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 어드민 주문 조회(06a · 1009 기획 수정본 8-1) — 전 브랜드 · 전 공구 주문의 조회 입구. 운영자 조치는 상세의 {@code actions}로만
 * 시작한다. 금액 · 상태 · 기한은 파트너센터와 같은 원천(하위주문 · 취소 요청 · 환불 큐)을 읽는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOrderQueryService {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM.dd");

    private final OrderRepository orderRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProductRepository orderProductRepository;
    private final OrderCancelRequestRepository cancelRequestRepository;
    private final OrderRefundTaskRepository refundTaskRepository;
    private final OrderFulfillmentHistoryRepository historyRepository;
    private final PaymentRepository paymentRepository;
    private final OrderClaimRepository claimRepository;
    private final OrderClaimChargeRepository chargeRepository;
    private final OrderClaimPaymentRepository claimPaymentRepository;
    private final OneToOneInquiryRepository inquiryRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final DeliveryTrackingEventRepository trackingEventRepository;
    private final OrderProperties orderProperties;
    private final ActOnBehalfPolicy actOnBehalfPolicy;
    private final StalledPolicy stalledPolicy;
    private final OperatorRefundLimit refundLimit;
    private final OrderSettlementReader settlementReader;

    public PageResponse<AdminOrderDto.ListItem> getOrders(AdminOrderDto.SearchParams params, LocalDate from,
                                                          LocalDate to, PagingRequest paging) {
        int size = paging.getSize();
        if (size < 1 || size > orderProperties.getListPageSizeMax()) {
            throw new BusinessException(ErrorCode.ORDER_PAGE_SIZE_INVALID);
        }
        Pageable pageable = PageRequest.of(Math.max(paging.getPage() - 1, 0), size);
        AdminOrderSearchCondition condition = condition(params, from, to);
        Page<Long> ids = deliveryGroupRepository.searchOrderIdsForAdmin(condition, pageable);
        LocalDateTime now = LocalDateTime.now();
        List<Long> orderIds = ids.getContent();
        Map<Long, List<OrderDeliveryGroup>> groupsByOrder = orderIds.isEmpty() ? Map.of()
                : orderIds.stream().collect(Collectors.toMap(id -> id, deliveryGroupRepository::findByOrderId));
        List<OrderDeliveryGroup> pageGroups = groupsByOrder.values().stream().flatMap(List::stream).toList();
        Set<Long> pendingGroupIds = pendingCancelGroupIds(pageGroups);
        // 행 확장(46 수정계획 4-1) — 페이지의 하위주문 전체로 항목 · 공구를 한 번씩 읽는다.
        Map<Long, List<OrderProduct>> itemsByGroup = itemsByGroup(pageGroups);
        Map<Long, GroupBuy> groupBuys = groupBuys(pageGroups);
        Map<Long, Order> orders = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, order -> order));
        List<AdminOrderDto.ListItem> rows = orderIds.stream().map(id -> {
            Order order = orders.get(id);
            List<OrderDeliveryGroup> entities = groupsByOrder.getOrDefault(id, List.of());
            List<AdminOrderDto.GroupSummary> groups = entities.stream()
                    .map(group -> summary(group, itemsByGroup.getOrDefault(group.getId(), List.of()),
                            groupBuyOf(groupBuys, group), pendingGroupIds.contains(group.getId()), now))
                    .toList();
            int attention = (int) entities.stream()
                    .filter(group -> needsAttention(group, pendingGroupIds.contains(group.getId()), now)).count();
            return new AdminOrderDto.ListItem(order.getId(), order.getOrderNumber(), order.getPaidAt(),
                    order.getRecipientName(), order.getTotalAmount(), groups, attention);
        }).toList();
        return PageResponse.of(new PageImpl<>(rows, pageable, ids.getTotalElements()));
    }

    /**
     * 탭 건수 — 브랜드 · 기간 · 검색은 같고 탭만 바꿔 센다(상태 셀렉트는 무시). 상태 셀렉트 · 이상 유형 셀렉트 건수(46 수정계획
     * 4-3)도 같은 조건에서 센다 — 각 값은 그 값을 골랐을 때의 목록 건수와 같다.
     */
    public AdminOrderDto.SummaryResponse getSummary(AdminOrderDto.SearchParams params, LocalDate from, LocalDate to) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AdminOrderTab tab : AdminOrderTab.values()) {
            counts.put(tab.name(), deliveryGroupRepository.countOrdersForAdmin(condition(params.forTab(tab), from, to)));
        }
        Map<FulfillmentStatus, Long> byStatus = deliveryGroupRepository.countOrdersByStatusForAdmin(
                condition(params.forTab(AdminOrderTab.ALL), from, to));
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (FulfillmentStatus status : FulfillmentStatus.values()) {
            if (status != FulfillmentStatus.PENDING) { // 결제 전 — 목록에 오지 않는다.
                statusCounts.put(status.name(), byStatus.getOrDefault(status, 0L));
            }
        }
        AdminOrderDto.SearchParams issueTab = params.forTab(AdminOrderTab.DELIVERY_ISSUE);
        Map<TrackingAlert, Long> byAlert = deliveryGroupRepository.countOrdersByTrackingAlertForAdmin(
                condition(issueTab, from, to));
        Map<String, Long> issueCounts = new LinkedHashMap<>();
        for (TrackingAlert alert : TrackingAlert.values()) {
            issueCounts.put(alert.name(), byAlert.getOrDefault(alert, 0L));
        }
        // 「반송 중」은 배지가 아니라 상태다 — 목록은 tab=DELIVERY_ISSUE&status=RETURNING 으로 거른다.
        issueCounts.put(FulfillmentStatus.RETURNING.name(), deliveryGroupRepository.countOrdersForAdmin(
                condition(issueTab.withStatus(FulfillmentStatus.RETURNING), from, to)));
        return new AdminOrderDto.SummaryResponse(counts, statusCounts, issueCounts);
    }

    public AdminOrderDto.DetailResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(found -> found.getPaidAt() != null)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        List<OrderDeliveryGroup> groups = deliveryGroupRepository.findByOrderId(orderId);
        Map<Long, List<OrderProduct>> itemsByGroup = itemsByGroup(groups);
        Map<Long, OrderCancelRequest> pending = groups.isEmpty() ? Map.of()
                : cancelRequestRepository.findPendingByDeliveryGroupIds(
                        groups.stream().map(OrderDeliveryGroup::getId).toList()).stream()
                .collect(Collectors.toMap(request -> request.getDeliveryGroup().getId(), request -> request,
                        (a, b) -> a));
        Map<Long, List<OrderClaim>> activeClaims = groups.isEmpty() ? Map.of()
                : claimRepository.findOpenByDeliveryGroupIds(groups.stream().map(OrderDeliveryGroup::getId).toList())
                .stream().collect(Collectors.groupingBy(claim -> claim.getDeliveryGroup().getId()));
        Map<Long, GroupBuy> groupBuys = groupBuys(groups);
        Users user = order.getUser();
        Payment paid = order.getPaidPaymentId() == null ? null
                : paymentRepository.findById(order.getPaidPaymentId()).orElse(null);
        // 운영자 사유 환불 상한(46 수정계획 1절) — 결제 단위라 주문당 한 번. 결제가 없으면 null(상한 없음).
        Integer refundable = paid == null ? null : refundLimit.available(paid);
        AdminOrderDto.Payment payment = paid == null ? null : new AdminOrderDto.Payment(
                paid.getPaymentId(), paid.getStatus().name(), paid.methodLabel(), paid.getAmount(),
                paid.getCancelledAmount(), paid.getPaidAt(), refundable);
        List<AdminOrderDto.Inquiry> inquiries = inquiryRepository.findByOrderIdOrderByIdDesc(orderId).stream()
                .map(inquiry -> new AdminOrderDto.Inquiry(inquiry.getId(),
                        inquiry.getType() == null ? null : inquiry.getType().getDescription(), inquiry.getCreatedAt(),
                        inquiry.isAnswered()))
                .toList();
        return new AdminOrderDto.DetailResponse(
                order.getId(), order.getOrderNumber(), order.getStatus().name(), order.getPaidAt(),
                new AdminOrderDto.Consumer(user == null ? null : user.getId(),
                        user == null ? null : (user.getName() != null ? user.getName() : user.getNickname()),
                        user == null ? null : user.getEmail()),
                new AdminOrderDto.Recipient(order.getRecipientName(), order.getRecipientPhone(), order.getZipCode(),
                        order.getAddress(), order.getDetailAddress(), order.getDeliveryMemo()),
                payment,
                inquiries.size(),
                inquiries,
                additionalPayments(orderId),
                groups.stream().map(group -> detail(group, itemsByGroup.getOrDefault(group.getId(), List.of()),
                        groupBuyOf(groupBuys, group), pending.get(group.getId()),
                        activeClaims.getOrDefault(group.getId(), List.of()), refundable, now)).toList());
    }

    // ------------------------------------------------------------------ 조립

    private AdminOrderDto.GroupSummary summary(OrderDeliveryGroup group, List<OrderProduct> items, GroupBuy groupBuy,
                                               boolean cancelRequested, LocalDateTime now) {
        FulfillmentStatus status = group.getFulfillmentStatus();
        Issue issue = issue(group, now);
        return new AdminOrderDto.GroupSummary(group.getId(), group.getSubOrderNumber(), group.getMarketName(), status,
                status.getLabel(), status.getTone(), group.getTrackingAlert(),
                group.getTrackingAlert() == null ? null : group.getTrackingAlert().getLabel(), group.getShipDueAt(),
                ActOnBehalfPolicy.isShipOverdue(group, now), cancelRequested,
                itemSummary(items), items.stream().mapToInt(OrderProduct::getQuantity).sum(),
                group.getProductTotal() + group.getDeliveryFee(), group.getCarrier(),
                group.getCarrier() == null ? null : group.getCarrier().getLabel(), group.getTrackingNumber(),
                groupBuy == null ? null : groupBuy.getContract().getTitle(),
                issue == null ? null : issue.since(), issue == null ? null : issue.label());
    }

    private AdminOrderDto.GroupDetail detail(OrderDeliveryGroup group, List<OrderProduct> items, GroupBuy groupBuy,
                                             OrderCancelRequest pendingRequest, List<OrderClaim> activeClaims,
                                             Integer refundable, LocalDateTime now) {
        FulfillmentStatus status = group.getFulfillmentStatus();
        int confirmDays = orderProperties.getPurchaseConfirmDays();
        boolean paused = group.getConfirmPausedAt() != null;
        AdminOrderDto.PurchaseConfirm purchaseConfirm = group.confirmBaseAt() == null ? null
                : new AdminOrderDto.PurchaseConfirm(paused, group.confirmRemainingDays(confirmDays, now),
                paused || group.getConfirmedAt() != null ? null : group.confirmBaseAt().plusDays(confirmDays),
                group.getConfirmedAt());
        List<OrderRefundTask> refunds = refundTaskRepository.findByDeliveryGroupIdOrderByIdAsc(group.getId());
        // 파트너용 공용 조회는 최신순이다 — 송장 출처는 이 순서로 맨 앞 것을, 06a ⑤ 이력은 뒤집어(오래된순 · 37 설계서 2-5) 쓴다.
        List<OrderFulfillmentHistory> latestFirst = historyRepository.findByDeliveryGroupId(group.getId());
        FulfillmentActorType invoiceActor = group.getTrackingNumber() == null ? null : latestFirst.stream()
                .filter(h -> h.getEventType() == FulfillmentEventType.INVOICE_REGISTERED
                        || h.getEventType() == FulfillmentEventType.INVOICE_UPDATED)
                .map(OrderFulfillmentHistory::getActorType).findFirst().orElse(null);
        String lastTrackingLabel = group.getCarrier() == null || group.getTrackingNumber() == null ? null
                : trackingEventRepository.findFirstByCarrierAndTrackingNumberOrderBySeqDesc(group.getCarrier(),
                group.getTrackingNumber()).map(DeliveryTrackingEvent::getDescription).orElse(null);
        return new AdminOrderDto.GroupDetail(
                group.getId(), group.getSubOrderNumber(), group.getMarketId(), group.getMarketName(),
                group.getGroupBuyNumber(),
                groupBuy == null ? null : groupBuy.getId(),
                groupBuy == null ? null : groupBuy.getContract().getTitle(),
                groupBuy == null ? null : groupBuy.getCreator().getId(),
                groupBuy == null ? null : groupBuy.getCreator().getShowroomName(),
                status, status.getLabel(), status.getTone(),
                new AdminOrderDto.Shipping(group.getShipDueAt(), group.getShipDueBusinessDays(),
                        ActOnBehalfPolicy.isShipOverdue(group, now), group.getOverdueNoticeCount(),
                        group.getLastOverdueNoticeAt(), group.getPrepareStartedAt(),
                        group.getShippedAt(), group.getCarrier(),
                        group.getCarrier() == null ? null : group.getCarrier().getLabel(), group.getTrackingNumber(),
                        invoiceActor == null ? null : invoiceActor.name(), invoiceSourceLabel(invoiceActor),
                        group.getTrackingAlert(),
                        group.getTrackingAlert() == null ? null : group.getTrackingAlert().getLabel(),
                        group.getLastTrackingAt(), lastTrackingLabel, group.getReturnDetectedAt(),
                        group.getReturnCompletedAt(), group.getDeliveredAt(),
                        group.getDeliveredSource() == null ? null : group.getDeliveredSource().getLabel()),
                purchaseConfirm,
                group.getCancelledAt() == null ? null : new AdminOrderDto.Cancel(group.getCancelledAt(),
                        group.getCancelType() == null ? null : group.getCancelType().getLabel(),
                        group.getCancelReasonCode() == null ? null : group.getCancelReasonCode().getLabel(),
                        group.getCancelReasonDetail()),
                pendingRequest == null ? null : new AdminOrderDto.CancelRequest(pendingRequest.getId(),
                        pendingRequest.getReasonCode().getLabel(), pendingRequest.getReasonDetail(),
                        pendingRequest.getRequestedAt(), pendingRequest.getRespondDueAt(),
                        pendingRequest.getItems().stream().map(OrderCancelRequestItem::getOrderProduct)
                                .map(OrderProduct::getId).toList()),
                items.stream().sorted(Comparator.comparing(OrderProduct::getId)).map(item -> new AdminOrderDto.Item(
                        item.getId(), item.getProductName(), item.getOptionName(), item.getQuantity(),
                        item.getReturnedQuantity(), item.getPrice(), item.getStatus().name(),
                        item.getCancelType() == null ? null : item.getCancelType().getLabel())).toList(),
                refunds.stream().map(task -> new AdminOrderDto.Refund(task.getId(), task.refundNo(), task.getSource(), task.getOrigin(),
                        task.getOrigin().getLabel(), task.getRefundAmount(), task.getStatus(), task.getLastError(),
                        task.getExecutedAt(), task.getCreatedAt())).toList(),
                activeClaims.stream().map(claim -> new AdminOrderDto.ActiveClaim(claim.getId(), claim.claimNumber(),
                        claim.getType(), claim.getStatus(), claim.getStatus().getLabel())).toList(),
                latestFirst.reversed().stream()
                        .map(h -> new AdminOrderDto.History(h.getEventType().name(), h.getEventType().getLabel(),
                                h.getActorType().name(), h.getDetail(), h.getOccurredAt())).toList(),
                settlementReader.readByDeliveryGroup(group.getId()).map(AdminOrderQueryService::settlement).orElse(null),
                actions(group, pendingRequest != null, refundable, now));
    }

    private static AdminOrderDto.Settlement settlement(OrderSettlementReader.GroupSettlement s) {
        return new AdminOrderDto.Settlement(s.settlementId(), s.settlementNumber(), s.status(), s.statusLabel(),
                s.confirmedAt(), s.settledAmount(), s.rewardAmount(),
                s.clawbacks().stream().map(c -> new AdminOrderDto.SettlementClawback(c.clawbackNumber(), c.side(),
                        c.status(), c.statusLabel(), c.amount())).toList());
    }

    /**
     * ① 추가 결제(46 수정계획 4-7) — 이 주문 클레임 요청(박스)의 재발송비 청구 중 PG 결제가 붙은 것. 06c M1 「추가 결제」와 같은
     * 원천이다. 링크는 그 요청의 첫 클레임으로 단다.
     */
    private List<AdminOrderDto.AdditionalPayment> additionalPayments(Long orderId) {
        Map<Long, OrderClaim> firstClaims = claimRepository.findByOrderIdsWithCollection(List.of(orderId)).stream()
                .collect(Collectors.toMap(claim -> claim.getCollection().getId(), Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
        if (firstClaims.isEmpty()) {
            return List.of();
        }
        List<AdminOrderDto.AdditionalPayment> rows = new ArrayList<>();
        for (OrderClaimCharge charge : chargeRepository.findByCollectionIds(firstClaims.keySet())) {
            OrderClaimPayment paid = charge.getPaidPaymentId() == null ? null
                    : claimPaymentRepository.findById(charge.getPaidPaymentId()).orElse(null);
            OrderClaim claim = firstClaims.get(charge.getCollection().getId());
            if (paid == null || claim == null) {
                continue;
            }
            rows.add(new AdminOrderDto.AdditionalPayment(claim.getDeliveryGroup().getId(), charge.getCollection().getId(),
                    claim.getId(), claim.claimNumber(), charge.getId(), charge.getType(),
                    charge.getType() == ClaimChargeType.REJECT_RESHIP ? "반려 재발송비" : "교환 재발송비",
                    Objects.requireNonNullElse(charge.getAmount(), 0), charge.getStatus(), charge.getStatus().getLabel(),
                    paid.getPaymentId(), paid.getPgTxId(), paid.methodLabel(), paid.getPaidAt(),
                    paid.getStatus() == ClaimPaymentStatus.PAID ? Objects.requireNonNullElse(paid.getAmount(), 0) : 0));
        }
        return rows;
    }

    /** 행 확장 「글로우 크림 50ml」 · 「데일리 선크림 외 1종」 — 첫 항목 기준. 1종이면 옵션까지 붙인다. */
    static String itemSummary(List<OrderProduct> items) {
        if (items.isEmpty()) {
            return null;
        }
        List<OrderProduct> sorted = items.stream().sorted(Comparator.comparing(OrderProduct::getId)).toList();
        OrderProduct first = sorted.get(0);
        if (sorted.size() > 1) {
            return first.getProductName() + " 외 " + (sorted.size() - 1) + "종";
        }
        return first.getOptionName() == null || first.getOptionName().isBlank() ? first.getProductName()
                : first.getProductName() + " " + first.getOptionName();
    }

    /** 배송 이상 경과(46 수정계획 4-2) — 기준 시각과 문구. 추적 정지는 마지막 추적이 없으면 발송 시각부터 센다({@link StalledPolicy}와 같다). */
    static Issue issue(OrderDeliveryGroup group, LocalDateTime now) {
        TrackingAlert alert = group.getTrackingAlert();
        if (alert == TrackingAlert.PICKUP_UNCONFIRMED && group.getShippedAt() != null) {
            return new Issue(group.getShippedAt(),
                    "등록 후 " + Duration.between(group.getShippedAt(), now).toHours() + "시간");
        }
        if (alert == TrackingAlert.STALLED) {
            LocalDateTime base = group.getLastTrackingAt() != null ? group.getLastTrackingAt() : group.getShippedAt();
            if (base != null) {
                return new Issue(base, ChronoUnit.DAYS.between(base, now) + "일 무갱신");
            }
        }
        if (group.getFulfillmentStatus() == FulfillmentStatus.RETURNING && group.getReturnCompletedAt() == null
                && group.getReturnDetectedAt() != null) {
            return new Issue(group.getReturnDetectedAt(), "감지 " + group.getReturnDetectedAt().format(MONTH_DAY));
        }
        return null;
    }

    record Issue(LocalDateTime since, String label) {
    }

    private static String invoiceSourceLabel(FulfillmentActorType actor) {
        if (actor == null) {
            return null;
        }
        return switch (actor) {
            case SELLER -> "브랜드 등록";
            case ADMIN -> "운영자 대행";
            default -> actor.getLabel();
        };
    }

    /**
     * 운영자가 볼 것이 있는 하위주문 — 배송 이상 탭(배지 ∨ 반송 완료 전 반송중)과 같은 식 + 발송 기한 경과 + 검토 중 취소 요청.
     * 반송 완료는 PG 자동 환불로 넘어간 건이라 세지 않는다(탭에서 빠진 건이 주의 건수에 남지 않게).
     */
    private static boolean needsAttention(OrderDeliveryGroup group, boolean cancelRequested, LocalDateTime now) {
        boolean returning = group.getFulfillmentStatus() == FulfillmentStatus.RETURNING && group.getReturnCompletedAt() == null;
        return group.getTrackingAlert() != null || returning || cancelRequested || ActOnBehalfPolicy.isShipOverdue(group, now);
    }

    /** @param refundable 운영자 사유 환불 상한({@link OperatorRefundLimit}) — null 이면 결제가 없는 주문(상한 없음) */
    AdminOrderDto.Actions actions(OrderDeliveryGroup group, boolean cancelRequested, Integer refundable,
                                  LocalDateTime now) {
        FulfillmentStatus status = group.getFulfillmentStatus();
        boolean actOnBehalf = actOnBehalfPolicy.canActOnBehalf(group, now);
        boolean deliveredWithin3Months = group.getDeliveredAt() != null
                && !group.getDeliveredAt().plusMonths(3).isBefore(now);
        return new AdminOrderDto.Actions(
                status == FulfillmentStatus.DELIVERED,
                status == FulfillmentStatus.PREPARING && actOnBehalf && !cancelRequested,
                FulfillmentStatus.WORKABLE.contains(status) && !cancelRequested,
                // 반송중은 제외 — 반송 완료 감지가 PG 자동 환불로 닫는다(B2 레일 조치 없음).
                // 남은 잔액이 없으면 숨긴다 — 편입 API 의 409 와 같은 식(46 수정계획 1절).
                (status == FulfillmentStatus.SHIPPING || status == FulfillmentStatus.DELIVERED
                        || status == FulfillmentStatus.CONFIRMED) && (refundable == null || refundable > 0),
                (status == FulfillmentStatus.CONFIRMED || status == FulfillmentStatus.DELIVERED)
                        && deliveredWithin3Months,
                stalledPolicy.isResolvable(group, now),
                stalledPolicy.isResolvable(group, now));
    }

    private Map<Long, List<OrderProduct>> itemsByGroup(List<OrderDeliveryGroup> groups) {
        if (groups.isEmpty()) {
            return Map.of();
        }
        return orderProductRepository.findByDeliveryGroupIds(groups.stream().map(OrderDeliveryGroup::getId).toList())
                .stream().collect(Collectors.groupingBy(item -> item.getDeliveryGroup().getId()));
    }

    /** 공구명 · 인플루언서 — 하위주문의 공구 id로 한 번에(계약 · 인플루언서 함께). */
    private Map<Long, GroupBuy> groupBuys(List<OrderDeliveryGroup> groups) {
        Set<Long> ids = groups.stream().map(OrderDeliveryGroup::getGroupBuyId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return groupBuyRepository.findAllWithContractAndCreator(ids).stream()
                .collect(Collectors.toMap(GroupBuy::getId, Function.identity()));
    }

    /** 공구 밖 하위주문은 공구 id 가 null 이다 — {@code Map.of().get(null)}은 예외라 먼저 거른다. */
    private static GroupBuy groupBuyOf(Map<Long, GroupBuy> groupBuys, OrderDeliveryGroup group) {
        return group.getGroupBuyId() == null ? null : groupBuys.get(group.getGroupBuyId());
    }

    private Set<Long> pendingCancelGroupIds(List<OrderDeliveryGroup> groups) {
        if (groups.isEmpty()) {
            return Set.of();
        }
        return cancelRequestRepository.findPendingByDeliveryGroupIds(groups.stream().map(OrderDeliveryGroup::getId)
                .toList()).stream().map(request -> request.getDeliveryGroup().getId()).collect(Collectors.toSet());
    }

    private static AdminOrderSearchCondition condition(AdminOrderDto.SearchParams params, LocalDate from,
                                                       LocalDate to) {
        return new AdminOrderSearchCondition(params.tab() == null ? AdminOrderTab.ALL : params.tab(), params.status(),
                params.marketId(), params.groupBuyId(), params.creatorId(), params.paymentMethod(),
                params.trackingAlert(), params.searchType(), params.keyword(), params.sort(),
                from == null ? null : from.atStartOfDay(), to == null ? null : to.atTime(23, 59, 59));
    }
}
