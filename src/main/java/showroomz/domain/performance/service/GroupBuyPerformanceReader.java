package showroomz.domain.performance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.repository.ContractItemRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.member.creator.repository.CreatorFollowEventRepository;
import showroomz.domain.member.creator.service.FollowerTimeline;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.PerformanceSourceRow;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.performance.service.PerformanceFacts.BreakdownLine;
import showroomz.domain.performance.type.PerformanceCancelKind;
import showroomz.domain.performance.type.PerformanceExceptionKind;
import showroomz.domain.performance.type.PerformanceSource;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.domain.post.repository.PostImpressionRepository;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.product.repository.ProductDetailViewRepository;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.repository.SettlementItemRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.service.SettlementCalculator;
import showroomz.domain.settlement.service.SettlementItemClassifier;
import showroomz.domain.settlement.service.SettlementItemClassifier.Classification;
import showroomz.domain.settlement.type.SettlementItemStatus;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.domain.showroom.type.ShowroomVisitSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 공구 성과 리더(48 파트너 설계서 7-2) — 파트너 12 · 스튜디오 10 공용. <b>조회 시 계산</b>한다(0-12) · 저장하는 것이 없다.
 *
 * <ul>
 *     <li>돈은 정산 행을 읽기만 한다(0-1). 정산이 없으면 판매 블록을 같은 분류기로 주문 테이블에서 낸다 — 「예정」.</li>
 *     <li>항목 분류는 정산 생성과 같은 {@link SettlementItemClassifier}(0-6) · 정산이 있으면 {@code settlement_item} 상태를 읽는다.</li>
 *     <li>건 수의 단위는 하위주문(0-7) — 확정 · 부분 반품 항목이 하나라도 있으면 구매확정, 아니면 배송 예외 &gt; 반품 &gt; 취소 순.</li>
 *     <li>팔로워 판정은 사건 시각 · {@link FollowerTimeline}(47 공통 결정 #5) — 방문은 첫 방문 · 조회는 첫 조회 · 주문은 결제 시각.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupBuyPerformanceReader {

    /** 문의 응답 평균의 집계 창 — 공구 종료 뒤 이 기간까지 들어온 상품 문의(미정 ④ 잠정). */
    private static final int INQUIRY_TAIL_DAYS = 7;
    private static final Set<SettlementItemStatus> CONFIRMED_ITEM =
            EnumSet.of(SettlementItemStatus.CONFIRMED, SettlementItemStatus.PARTIAL_RETURNED);

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final SettlementCalculator settlementCalculator;
    private final OrderProductRepository orderProductRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderClaimRepository claimRepository;
    private final ContractItemRepository contractItemRepository;
    private final GroupBuyPostRepository groupBuyPostRepository;
    private final GroupBuySalesReader salesReader;
    private final ShowroomVisitRepository showroomVisitRepository;
    private final PostImpressionRepository postImpressionRepository;
    private final PostLikeRepository postLikeRepository;
    private final ProductDetailViewRepository productDetailViewRepository;
    private final CreatorFollowEventRepository followEventRepository;
    private final ProductInquiryRepository productInquiryRepository;

    // ------------------------------------------------------------------ 진입

    /**
     * 성과 모집단(0-3) — 종료 공구({@code ENDED · SETTLED}) ∧ 결제 완료 하위주문 ≥ 1. 진행 중 · 중단 · 판매 0건은 성과가 없다.
     */
    public boolean isListed(GroupBuy groupBuy) {
        return (groupBuy.getStatus() == GroupBuyStatus.ENDED || groupBuy.getStatus() == GroupBuyStatus.SETTLED)
                && deliveryGroupRepository.existsPaidForGroupBuy(groupBuy.getId());
    }

    /** 상세 — 전 블록. {@code groupBuy} 는 계약 · 마켓 · 인플루언서가 올라온 것({@code findDetailById}). */
    public PerformanceFacts read(GroupBuy groupBuy) {
        Settlement settlement = settlementRepository.findByGroupBuyId(groupBuy.getId()).orElse(null);
        return compute(groupBuy, settlement, true, new HashMap<>());
    }

    /**
     * 목록 · KPI — 상품별 · 배송 품질 · 분해는 비운다. 나머지는 {@link #read}와 <b>같은 코드 경로</b>라 두 화면의 숫자가 갈리지 않는다.
     *
     * @return 공구 id → 성과(입력 순서 유지)
     */
    public Map<Long, PerformanceFacts> readRows(Collection<GroupBuy> groupBuys) {
        Map<Long, PerformanceFacts> result = new LinkedHashMap<>();
        if (groupBuys.isEmpty()) {
            return result;
        }
        Map<Long, Settlement> settlements = settlementRepository.findByGroupBuyIdIn(
                        groupBuys.stream().map(GroupBuy::getId).toList()).stream()
                .collect(Collectors.toMap(Settlement::getGroupBuyId, Function.identity()));
        Map<Long, FollowerTimeline> timelines = new HashMap<>();
        for (GroupBuy groupBuy : groupBuys) {
            result.put(groupBuy.getId(), compute(groupBuy, settlements.get(groupBuy.getId()), false, timelines));
        }
        return result;
    }

    // ------------------------------------------------------------------ 계산

    private PerformanceFacts compute(GroupBuy groupBuy, Settlement settlement, boolean full,
                                     Map<Long, FollowerTimeline> timelines) {
        Long groupBuyId = groupBuy.getId();
        Long creatorId = groupBuy.getCreator().getId();
        LocalDateTime from = groupBuy.getStartAt();
        LocalDateTime to = groupBuy.getEndedAt() != null ? groupBuy.getEndedAt() : groupBuy.getEndAt();
        boolean projected = settlement == null;
        FollowerTimeline timeline = timelines.computeIfAbsent(creatorId,
                id -> new FollowerTimeline(followEventRepository.findTimeline(id, LocalDateTime.now())));

        List<ContractItem> contractItems = contractItemRepository.findWithProductByContractIds(
                List.of(groupBuy.getContract().getId()));
        Map<Long, BigDecimal> rateByProduct = new HashMap<>();
        for (ContractItem item : contractItems) {
            if (item.getProduct() != null && item.getRewardRate() != null) {
                rateByProduct.put(item.getProduct().getProductId(), item.getRewardRate());
            }
        }

        List<Line> lines = lines(orderProductRepository.findPerformanceSourceByGroupBuy(groupBuyId), settlement,
                rateByProduct);
        List<Group> groups = groups(lines);
        Long postId = groupBuyPostRepository.findByGroupBuyId(groupBuyId).map(GroupBuyPost::getPostId).orElse(null);

        PerformanceFacts.Orders orders = orders(groups, lines, settlement, postId, timeline);
        List<PerformanceFacts.Buyer> buyers = buyers(groupBuy, groups, from, timeline);
        PerformanceFacts.Traffic traffic = traffic(groupBuyId, creatorId, from, to, timeline);
        PerformanceFacts.PostFacts post = postId == null ? null
                : post(groupBuyId, postId, from, to, groups, timeline);
        PerformanceFacts.Closure closure = projected ? closure(groupBuyId) : null;
        PerformanceFacts.Followers followers = new PerformanceFacts.Followers(timeline.countAt(to),
                timeline.delta(from, to));
        long fixedFee = groupBuy.getContract().getFixedFeeAmount() == null ? 0
                : groupBuy.getContract().getFixedFeeAmount();

        return new PerformanceFacts(groupBuyId, groupBuy.getMarket().getId(), creatorId,
                groupBuy.getContract().getId(), PerformanceStatus.of(projected ? null : settlement.getStatus()), from, to,
                projected, settlement, fixedFee, closure, traffic, post, orders,
                full ? products(groupBuyId, from, to, contractItems, lines) : List.of(), buyers, followers,
                full ? delivery(groups, contractItems, from, to) : null,
                full ? breakdown(groupBuyId, groups) : null);
    }

    // ------------------------------------------------------------------ 분류(0-6 · 0-7)

    /** 항목 1줄 — 원천 행 + 분류 + 항목 리워드. */
    private record Line(PerformanceSourceRow row, Classification c, long reward) {

        long returnedUnits() {
            return switch (c.status()) {
                case RETURNED -> c.quantity();
                case CONFIRMED, PARTIAL_RETURNED -> c.returnedQuantity();
                default -> 0;
            };
        }
    }

    private enum GroupClass { CONFIRMED, CANCELLED, RETURNED, DELIVERY_EXCEPTION }

    private record Group(PerformanceSourceRow head, List<Line> lines, GroupClass groupClass) {

        Long userId() {
            return head.consumerUserId();
        }

        LocalDateTime paidAt() {
            return head.paidAt();
        }
    }

    private List<Line> lines(List<PerformanceSourceRow> rows, Settlement settlement, Map<Long, BigDecimal> rates) {
        Map<Long, SettlementItem> items = settlement == null ? Map.of()
                : settlementItemRepository.findAllBySettlementId(settlement.getId()).stream()
                .collect(Collectors.toMap(SettlementItem::getOrderProductId, Function.identity(), (a, b) -> a));
        List<Line> lines = new ArrayList<>(rows.size());
        for (PerformanceSourceRow row : rows) {
            SettlementItem item = items.get(row.orderProductId());
            if (item != null) {
                lines.add(new Line(row, fromItem(item), item.getRewardAmount()));
                continue;
            }
            Classification c = SettlementItemClassifier.classify(row.itemStatus(), row.itemCancelType(),
                    row.groupStatus(), row.groupCancelType(), row.price(), row.quantity(), row.returnedQuantity());
            BigDecimal rate = rates.get(row.productId());
            long reward = rate == null ? 0 : settlementCalculator.unitReward(c.unitPrice(), rate) * c.settledQuantity();
            lines.add(new Line(row, c, reward));
        }
        return lines;
    }

    /**
     * 정산 명세 행을 분류 결과로 되읽는다 — 상태 · 반영 수량 · 반영액은 저장값 그대로이고, 차감 몫은 상태에서 다시 가른다(운영자 사유
     * 환불로 덜어 낸 몫은 배송 예외로 남는다 · 정산 생성 2-2 와 같다).
     */
    private static Classification fromItem(SettlementItem item) {
        long paid = item.getPaidAmount();
        long settled = item.getSettledAmount();
        long cancel = 0, returned = 0;
        switch (item.getStatus()) {
            case CANCELLED -> cancel = paid;
            case RETURNED -> returned = paid;
            default -> returned = Math.min(paid - settled, item.getUnitPrice() * item.getReturnedQuantity());
        }
        long exception = Math.max(0, paid - settled - cancel - returned);
        return new Classification(item.getStatus(), item.getQuantity(), item.getReturnedQuantity(), item.getUnitPrice(),
                item.getSettledQuantity(), settled, cancel, returned, exception);
    }

    private static List<Group> groups(List<Line> lines) {
        Map<Long, List<Line>> byGroup = new LinkedHashMap<>();
        lines.forEach(line -> byGroup.computeIfAbsent(line.row().deliveryGroupId(), id -> new ArrayList<>()).add(line));
        List<Group> groups = new ArrayList<>(byGroup.size());
        byGroup.values().forEach(groupLines -> groups.add(new Group(groupLines.get(0).row(), groupLines,
                classOf(groupLines))));
        return groups;
    }

    private static GroupClass classOf(List<Line> lines) {
        Set<SettlementItemStatus> statuses = lines.stream().map(line -> line.c().status())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SettlementItemStatus.class)));
        if (statuses.contains(SettlementItemStatus.CONFIRMED) || statuses.contains(SettlementItemStatus.PARTIAL_RETURNED)) {
            return GroupClass.CONFIRMED;
        }
        if (statuses.contains(SettlementItemStatus.DELIVERY_EXCEPTION)) {
            return GroupClass.DELIVERY_EXCEPTION;
        }
        if (statuses.contains(SettlementItemStatus.RETURNED)) {
            return GroupClass.RETURNED;
        }
        return GroupClass.CANCELLED;
    }

    // ------------------------------------------------------------------ 블록

    private static PerformanceFacts.Orders orders(List<Group> groups, List<Line> lines, Settlement settlement,
                                                  Long postId, FollowerTimeline timeline) {
        int confirmed = 0, cancelled = 0, returned = 0, exception = 0, shipped = 0, delivered = 0;
        int orderFollower = 0, confirmedFollower = 0;
        for (Group group : groups) {
            switch (group.groupClass()) {
                case CONFIRMED -> confirmed++;
                case CANCELLED -> cancelled++;
                case RETURNED -> returned++;
                case DELIVERY_EXCEPTION -> exception++;
            }
            shipped += group.head().shippedAt() != null ? 1 : 0;
            delivered += group.head().deliveredAt() != null ? 1 : 0;
            if (timeline.isFollowerAt(group.userId(), group.paidAt())) {
                orderFollower++;
                confirmedFollower += group.groupClass() == GroupClass.CONFIRMED ? 1 : 0;
            }
        }
        long ordered = lines.stream().mapToLong(line -> line.c().quantity()).sum();
        long confirmedQuantity = lines.stream().mapToLong(line -> line.c().settledQuantity()).sum();
        long sales = settlement != null ? settlement.getConfirmedSalesAmount()
                : lines.stream().mapToLong(line -> line.c().settledAmount()).sum();
        long reward = lines.stream().mapToLong(Line::reward).sum();
        return new PerformanceFacts.Orders(groups.size(), confirmed, cancelled, returned, exception, shipped, delivered,
                ordered, confirmedQuantity, sales, reward, orderFollower, confirmedFollower);
    }

    private List<PerformanceFacts.Buyer> buyers(GroupBuy groupBuy, List<Group> groups, LocalDateTime start,
                                                FollowerTimeline timeline) {
        Map<Long, Group> firstConfirmed = new LinkedHashMap<>();
        for (Group group : groups) {
            if (group.groupClass() != GroupClass.CONFIRMED || group.userId() == null) {
                continue;
            }
            firstConfirmed.merge(group.userId(), group, (a, b) ->
                    a.paidAt() != null && (b.paidAt() == null || !b.paidAt().isBefore(a.paidAt())) ? a : b);
        }
        if (firstConfirmed.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = firstConfirmed.keySet();
        Set<Long> repeatBrand = new HashSet<>(deliveryGroupRepository.findRepeatBuyersInMarket(
                groupBuy.getMarket().getId(), groupBuy.getId(), start, userIds));
        Set<Long> repeatCreator = new HashSet<>(deliveryGroupRepository.findRepeatBuyersWithCreator(
                groupBuy.getCreator().getId(), groupBuy.getId(), start, userIds));
        List<PerformanceFacts.Buyer> buyers = new ArrayList<>(firstConfirmed.size());
        firstConfirmed.forEach((userId, group) -> buyers.add(new PerformanceFacts.Buyer(userId,
                group.head().consumerGender(), group.head().consumerBirthday(),
                timeline.isFollowerAt(userId, group.paidAt()), repeatBrand.contains(userId),
                repeatCreator.contains(userId))));
        return buyers;
    }

    private PerformanceFacts.Traffic traffic(Long groupBuyId, Long creatorId, LocalDateTime from, LocalDateTime to,
                                             FollowerTimeline timeline) {
        List<Object[]> visits = showroomVisitRepository.findVisitsInWindow(creatorId, from, to);
        Map<PerformanceSource, Long> bySource = new EnumMap<>(PerformanceSource.class);
        for (PerformanceSource source : PerformanceSource.values()) {
            bySource.put(source, 0L);
        }
        Set<String> seen = new HashSet<>();
        long followerVisitors = 0;
        for (Object[] visit : visits) {
            String visitorKey = (String) visit[0];
            if (!seen.add(visitorKey)) {
                continue; // 사람당 첫 방문만 — 시각순으로 받는다
            }
            bySource.merge(PerformanceSource.of((ShowroomVisitSource) visit[2]), 1L, Long::sum);
            if (timeline.isFollowerAt((Long) visit[1], (LocalDateTime) visit[3])) {
                followerVisitors++;
            }
        }
        long productViews = 0;
        for (Object[] row : productDetailViewRepository.countByProductForGroupBuy(groupBuyId, from, to)) {
            productViews += ((Number) row[1]).longValue();
        }
        return new PerformanceFacts.Traffic(seen.size(), visits.size(), bySource, followerVisitors, productViews,
                productDetailViewRepository.countUniqueViewersForGroupBuy(groupBuyId, from, to));
    }

    private PerformanceFacts.PostFacts post(Long groupBuyId, Long postId, LocalDateTime from, LocalDateTime to,
                                            List<Group> groups, FollowerTimeline timeline) {
        List<Object[]> viewers = postImpressionRepository.findViewersInPeriod(postId, from, to);
        Set<String> keys = new HashSet<>();
        long followerViewers = 0;
        for (Object[] viewer : viewers) {
            if (keys.add((String) viewer[0]) && timeline.isFollowerAt((Long) viewer[1], (LocalDateTime) viewer[2])) {
                followerViewers++;
            }
        }
        long postOrders = groups.stream().filter(group -> postId.equals(group.head().attributedPostId())).count();
        return new PerformanceFacts.PostFacts(postId, postImpressionRepository.countByPostIdInPeriod(postId, from, to),
                keys.size(), followerViewers,
                productDetailViewRepository.countAttributedForGroupBuy(groupBuyId, postId, from, to), postOrders,
                postLikeRepository.countByPostIdInPeriod(postId, from, to));
    }

    private PerformanceFacts.Closure closure(Long groupBuyId) {
        GroupBuySalesReader.GroupBuyOrderClosure closure = salesReader.readClosure(groupBuyId).orElse(null);
        long openReturns = claimRepository.countOpenReturnsForGroupBuy(groupBuyId);
        if (closure == null) {
            return new PerformanceFacts.Closure(0, openReturns, List.of());
        }
        return new PerformanceFacts.Closure(closure.unclosedCount(), openReturns, closure.unclosedStages());
    }

    private List<PerformanceFacts.ProductLine> products(Long groupBuyId, LocalDateTime from, LocalDateTime to,
                                                        List<ContractItem> contractItems, List<Line> lines) {
        Map<Long, long[]> views = new HashMap<>();
        for (Object[] row : productDetailViewRepository.countByProductForGroupBuy(groupBuyId, from, to)) {
            views.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        Map<Long, List<Line>> byProduct = lines.stream()
                .collect(Collectors.groupingBy(line -> line.row().productId(), LinkedHashMap::new, Collectors.toList()));
        List<PerformanceFacts.ProductLine> products = new ArrayList<>(contractItems.size());
        for (ContractItem item : contractItems) {
            Long productId = item.getProduct() == null ? null : item.getProduct().getProductId();
            List<Line> productLines = productId == null ? List.of() : byProduct.getOrDefault(productId, List.of());
            long[] view = productId == null ? null : views.get(productId);
            String name = item.getProductName() != null ? item.getProductName()
                    : item.getProduct() == null ? null : item.getProduct().getName();
            products.add(new PerformanceFacts.ProductLine(item.getId(), productId, name, item.getRewardRate(),
                    view == null ? 0 : view[0], view == null ? 0 : view[1],
                    (int) productLines.stream().map(line -> line.row().deliveryGroupId()).distinct().count(),
                    productLines.stream().mapToLong(line -> line.c().quantity()).sum(),
                    productLines.stream().mapToLong(line -> line.c().settledQuantity()).sum(),
                    productLines.stream().mapToLong(line -> line.c().settledAmount()).sum(),
                    productLines.stream().mapToLong(Line::reward).sum(),
                    productLines.stream().mapToLong(Line::returnedUnits).sum()));
        }
        return products;
    }

    private PerformanceFacts.Delivery delivery(List<Group> groups, List<ContractItem> contractItems,
                                               LocalDateTime from, LocalDateTime to) {
        int shipped = 0, tracked = 0, onTime = 0, claims = 0;
        long shipMinutes = 0;
        for (Group group : groups) {
            PerformanceSourceRow head = group.head();
            if (head.shippedAt() != null) {
                shipped++;
                if (head.paidAt() != null) {
                    shipMinutes += Math.max(0, Duration.between(head.paidAt(), head.shippedAt()).toMinutes());
                }
                if (head.shipDueAt() != null) {
                    tracked++;
                    onTime += head.shippedAt().isAfter(head.shipDueAt()) ? 0 : 1;
                }
            }
            if (isDeliveryClaim(group)) {
                claims++;
            }
        }
        BigDecimal avgShipDays = shipped == 0 ? null
                : BigDecimal.valueOf(shipMinutes).divide(BigDecimal.valueOf(shipped * 1440L), 1, RoundingMode.HALF_UP);

        List<Long> productIds = contractItems.stream().filter(item -> item.getProduct() != null)
                .map(item -> item.getProduct().getProductId()).toList();
        long answerMinutes = 0;
        int answered = 0;
        if (!productIds.isEmpty()) {
            for (Object[] row : productInquiryRepository.findAnswerTimes(productIds, from,
                    to.plusDays(INQUIRY_TAIL_DAYS))) {
                LocalDateTime created = (LocalDateTime) row[0];
                LocalDateTime answeredAt = (LocalDateTime) row[1];
                if (created != null && answeredAt != null) {
                    answerMinutes += Math.max(0, Duration.between(created, answeredAt).toMinutes());
                    answered++;
                }
            }
        }
        BigDecimal avgInquiryHours = answered == 0 ? null
                : BigDecimal.valueOf(answerMinutes).divide(BigDecimal.valueOf(answered * 60L), 1, RoundingMode.HALF_UP);
        return new PerformanceFacts.Delivery(shipped, tracked, onTime, avgShipDays, claims, answered, avgInquiryHours);
    }

    /** 반송 · 분실 — 배송 과정에서 생긴 건(운영자 사유 환불은 배송 클레임이 아니다). */
    private static boolean isDeliveryClaim(Group group) {
        if (group.head().groupStatus() == FulfillmentStatus.RETURNING) {
            return true;
        }
        return group.head().groupCancelType() == OrderCancelType.LOST
                || group.lines().stream().anyMatch(line -> line.row().itemCancelType() == OrderCancelType.LOST);
    }

    private PerformanceFacts.Breakdown breakdown(Long groupBuyId, List<Group> groups) {
        Map<PerformanceCancelKind, BreakdownLine> cancel = emptyLines(PerformanceCancelKind.class);
        Map<ClaimReason, BreakdownLine> returned = new EnumMap<>(ClaimReason.class);
        Map<PerformanceExceptionKind, BreakdownLine> exception = emptyLines(PerformanceExceptionKind.class);

        Map<Long, List<Object[]>> claimsByItem = new HashMap<>();
        for (Object[] row : claimRepository.findRefundedReturnReasons(groupBuyId)) {
            claimsByItem.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add(row);
        }

        for (Group group : groups) {
            PerformanceCancelKind groupCancelKind = null;
            PerformanceExceptionKind groupExceptionKind = null;
            Map<ClaimReason, Long> groupReturnUnits = new EnumMap<>(ClaimReason.class);
            for (Line line : group.lines()) {
                Classification c = line.c();
                if (c.cancelPart() > 0) {
                    OrderCancelType type = line.row().itemCancelType() != null ? line.row().itemCancelType()
                            : line.row().groupCancelType();
                    PerformanceCancelKind kind = PerformanceCancelKind.of(type);
                    cancel.merge(kind, new BreakdownLine(c.quantity(), c.cancelPart(), 0), BreakdownLine::sum);
                    groupCancelKind = groupCancelKind == null ? kind : groupCancelKind;
                }
                if (c.exceptionPart() > 0) {
                    PerformanceExceptionKind kind = exceptionKind(line);
                    long units = c.status() == SettlementItemStatus.DELIVERY_EXCEPTION ? c.quantity() : 0;
                    exception.merge(kind, new BreakdownLine(units, c.exceptionPart(), 0), BreakdownLine::sum);
                    groupExceptionKind = groupExceptionKind == null ? kind : groupExceptionKind;
                }
                if (c.returnPart() > 0) {
                    allocateReturn(line, claimsByItem.getOrDefault(line.row().orderProductId(), List.of()), returned,
                            groupReturnUnits);
                }
            }
            switch (group.groupClass()) {
                case CANCELLED -> cancel.merge(groupCancelKind == null ? PerformanceCancelKind.OTHER : groupCancelKind,
                        new BreakdownLine(0, 0, 1), BreakdownLine::sum);
                case DELIVERY_EXCEPTION -> exception.merge(groupExceptionKind == null
                        ? PerformanceExceptionKind.OPERATOR_REFUND : groupExceptionKind,
                        new BreakdownLine(0, 0, 1), BreakdownLine::sum);
                case RETURNED -> {
                    ClaimReason dominant = groupReturnUnits.entrySet().stream()
                            .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(ClaimReason.OTHER);
                    returned.merge(dominant, new BreakdownLine(0, 0, 1), BreakdownLine::sum);
                }
                case CONFIRMED -> {
                }
            }
        }
        return new PerformanceFacts.Breakdown(cancel, returned, exception);
    }

    private static PerformanceExceptionKind exceptionKind(Line line) {
        if (line.row().groupStatus() == FulfillmentStatus.RETURNING) {
            return PerformanceExceptionKind.RETURNED_TO_SENDER;
        }
        OrderCancelType type = line.row().itemCancelType() != null ? line.row().itemCancelType()
                : line.row().groupCancelType();
        return type == OrderCancelType.LOST ? PerformanceExceptionKind.LOST : PerformanceExceptionKind.OPERATOR_REFUND;
    }

    /** 반품 몫을 그 항목의 반품 클레임 사유로 나눈다 — 클레임으로 설명되지 않는 몫은 「기타」. */
    private static void allocateReturn(Line line, List<Object[]> claims, Map<ClaimReason, BreakdownLine> returned,
                                       Map<ClaimReason, Long> groupUnits) {
        long remainingUnits = line.returnedUnits();
        long remainingAmount = line.c().returnPart();
        long unitPrice = line.c().unitPrice();
        for (Object[] claim : claims) {
            if (remainingUnits <= 0) {
                break;
            }
            ClaimReason reason = claim[1] instanceof ClaimReason r ? r : ClaimReason.OTHER;
            long units = Math.min(remainingUnits, claim[2] instanceof Number n ? n.longValue() : 0);
            if (units <= 0) {
                continue;
            }
            long amount = Math.min(remainingAmount, units * unitPrice);
            returned.merge(reason, new BreakdownLine(units, amount, 0), BreakdownLine::sum);
            groupUnits.merge(reason, units, Long::sum);
            remainingUnits -= units;
            remainingAmount -= amount;
        }
        if (remainingAmount > 0 || remainingUnits > 0) {
            returned.merge(ClaimReason.OTHER, new BreakdownLine(remainingUnits, remainingAmount, 0), BreakdownLine::sum);
            groupUnits.merge(ClaimReason.OTHER, Math.max(1, remainingUnits), Long::sum);
        }
    }

    private static <K extends Enum<K>> Map<K, BreakdownLine> emptyLines(Class<K> type) {
        Map<K, BreakdownLine> lines = new EnumMap<>(type);
        for (K key : type.getEnumConstants()) {
            lines.put(key, BreakdownLine.EMPTY);
        }
        return lines;
    }
}
