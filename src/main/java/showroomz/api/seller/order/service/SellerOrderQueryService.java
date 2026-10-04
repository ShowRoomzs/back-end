package showroomz.api.seller.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.order.dto.SellerOrderDetailResponse;
import showroomz.api.seller.order.dto.SellerOrderListItem;
import showroomz.api.seller.order.dto.SellerOrderSummaryResponse;
import showroomz.api.seller.order.service.SellerOrderAccessGuard.SellerScope;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderFulfillmentHistoryRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.SellerOrderRow;
import showroomz.domain.order.repository.SellerOrderSearchCondition;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderDateBasis;
import showroomz.domain.order.type.OrderSearchType;
import showroomz.domain.order.type.OrderSortType;
import showroomz.domain.order.type.OrderTab;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 파트너센터 주문 조회(34 설계서 4-1 ~ 4-3). 기본값(탭 기본 기간·기본 정렬)과 1년 상한은 여기서 끝낸다 —
 * 탭이 소유한 값이라 FE 가 다시 계산하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerOrderQueryService {

    private final SellerOrderAccessGuard accessGuard;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProductRepository orderProductRepository;
    private final OrderCancelRequestRepository cancelRequestRepository;
    private final OrderFulfillmentHistoryRepository historyRepository;
    private final PaymentRepository paymentRepository;
    private final SellerOrderAssembler assembler;
    private final OrderProperties orderProperties;

    /** 목록 — 행마다 항목·취소 요청이 필요하다. 페이지의 그룹 id 로 테이블당 IN 쿼리 1번씩 모아 조립한다. */
    public PageResponse<SellerOrderListItem> getOrders(String sellerEmail, OrderTab tab, OrderDateBasis dateBasis,
                                                       LocalDate from, LocalDate to, OrderSearchType searchType,
                                                       String keyword, OrderSortType sort,
                                                       PagingRequest pagingRequest) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        SellerOrderSearchCondition condition = buildCondition(scope.market().getId(), tab, dateBasis, from, to,
                searchType, keyword, sort, LocalDateTime.now());
        // page 는 1 미만이면 첫 페이지로 보정하지만, size 는 보정하지 않는다 — 0 이하는 PageRequest 가 500 으로 터지고
        // 상한이 없으면 한 번에 마켓 전체를 끌어온다.
        int size = pagingRequest.getSize();
        if (size < 1 || size > orderProperties.getListPageSizeMax()) {
            throw new BusinessException(ErrorCode.ORDER_PAGE_SIZE_INVALID);
        }
        Pageable pageable = PageRequest.of(Math.max(pagingRequest.getPage() - 1, 0), size);

        Page<SellerOrderRow> page = deliveryGroupRepository.searchForSeller(condition, pageable);
        List<SellerOrderListItem> content = assembleRows(page.getContent());
        return PageResponse.of(new PageImpl<>(content, pageable, page.getTotalElements()));
    }

    /** 요약 바 5칸 + 탭 카운트 9종 — 한 응답(동시 갱신 요구). 검색 결과 없음에도 요약 바는 전체 기준이라 숫자가 유지된다(§34-2). */
    public SellerOrderSummaryResponse getSummary(String sellerEmail) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        Long marketId = scope.market().getId();

        Map<FulfillmentStatus, Long> byStatus = new EnumMap<>(FulfillmentStatus.class);
        for (Object[] row : deliveryGroupRepository.countByStatus(marketId)) {
            byStatus.put((FulfillmentStatus) row[0], (Long) row[1]);
        }
        Map<FulfillmentStatus, Long> pendingByStatus = new EnumMap<>(FulfillmentStatus.class);
        for (Object[] row : deliveryGroupRepository.countPendingCancelByStatus(marketId)) {
            pendingByStatus.put((FulfillmentStatus) row[0], (Long) row[1]);
        }
        long pendingTotal = pendingByStatus.values().stream().mapToLong(Long::longValue).sum();
        long newCount = byStatus.getOrDefault(FulfillmentStatus.NEW, 0L)
                - pendingByStatus.getOrDefault(FulfillmentStatus.NEW, 0L);
        long preparingCount = byStatus.getOrDefault(FulfillmentStatus.PREPARING, 0L)
                - pendingByStatus.getOrDefault(FulfillmentStatus.PREPARING, 0L);
        long returningCount = byStatus.getOrDefault(FulfillmentStatus.RETURNING, 0L);

        Map<String, Long> tabCounts = new LinkedHashMap<>();
        for (OrderTab tab : OrderTab.values()) {
            long count = switch (tab) {
                case NEW -> newCount;
                case PREPARING -> preparingCount;
                case CANCEL_REQUESTED -> pendingTotal;
                default -> tab.getStatuses().stream().mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum();
            };
            tabCounts.put(tab.name(), count);
        }

        long deliveryIssue = deliveryGroupRepository.countTrackingAlerts(marketId) + returningCount;
        return new SellerOrderSummaryResponse(
                new SellerOrderSummaryResponse.ActionBar(newCount, preparingCount, deliveryIssue, null, null),
                tabCounts);
    }

    /** 상세 모달(§34-9) — 마스킹 해제 배송지 · 항목 표 · 우 레일 · 처리 이력 · 가능한 액션. */
    public SellerOrderDetailResponse getOrder(String sellerEmail, Long deliveryGroupId) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        OrderDeliveryGroup group = accessGuard.loadOwned(deliveryGroupId, scope);

        List<OrderProduct> items = orderProductRepository.findByDeliveryGroupIds(List.of(deliveryGroupId));
        OrderCancelRequest pending = cancelRequestRepository.findPendingByDeliveryGroupIds(List.of(deliveryGroupId))
                .stream().findFirst().orElse(null);
        String groupBuyTitle = resolveGroupBuyTitle(group);
        String paymentMethod = group.getOrder().getPaidPaymentId() == null ? null
                : paymentRepository.findById(group.getOrder().getPaidPaymentId())
                        .map(payment -> payment.getMethod().getLabel()).orElse(null);
        return assembler.toDetail(group, groupBuyTitle, paymentMethod, items, pending,
                historyRepository.findByDeliveryGroupId(deliveryGroupId), LocalDateTime.now(),
                orderProperties.getPurchaseConfirmDays());
    }

    // ------------------------------------------------------------------ 내부 · 공용

    /** 발주서 「선택 없이 열면 현재 탭 전체」(§34-4)가 같은 조건 빌더를 쓴다. */
    SellerOrderSearchCondition buildCondition(Long marketId, OrderTab tab, OrderDateBasis dateBasis, LocalDate from,
                                              LocalDate to, OrderSearchType searchType, String keyword,
                                              OrderSortType sort, LocalDateTime now) {
        OrderTab resolvedTab = tab == null ? OrderTab.ALL : tab;
        LocalDate resolvedTo = to != null ? to : now.toLocalDate();
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(resolvedTab.getDefaultPeriodDays());
        // 역전된 기간은 빈 목록이 아니라 입력 오류다 — 빈 결과로 내리면 「주문이 없다」로 읽힌다.
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new BusinessException(ErrorCode.ORDER_SEARCH_RANGE_INVALID);
        }
        if (ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) > orderProperties.getSearchRangeMaxDays()) {
            throw new BusinessException(ErrorCode.ORDER_SEARCH_RANGE_EXCEEDED);
        }
        return new SellerOrderSearchCondition(
                marketId,
                resolvedTab,
                dateBasis == null ? OrderDateBasis.PAID : dateBasis,
                resolvedFrom.atStartOfDay(),
                resolvedTo.atTime(LocalTime.MAX),
                searchType,
                keyword,
                sort == null ? resolvedTab.defaultSort() : sort);
    }

    List<SellerOrderListItem> assembleRows(List<SellerOrderRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(row -> row.group().getId()).toList();
        Map<Long, List<OrderProduct>> itemsByGroup = orderProductRepository.findByDeliveryGroupIds(ids).stream()
                .collect(Collectors.groupingBy(item -> item.getDeliveryGroup().getId()));
        Map<Long, OrderCancelRequest> pendingByGroup = cancelRequestRepository.findPendingByDeliveryGroupIds(ids)
                .stream()
                .collect(Collectors.toMap(r -> r.getDeliveryGroup().getId(), Function.identity(), (a, b) -> a));
        LocalDateTime now = LocalDateTime.now();
        int confirmDays = orderProperties.getPurchaseConfirmDays();
        return rows.stream()
                .map(row -> assembler.toListItem(row.group(), row.orderNumber(), row.paidAt(), row.recipientName(),
                        row.groupBuyTitle(), itemsByGroup.getOrDefault(row.group().getId(), List.of()),
                        pendingByGroup.get(row.group().getId()), now, confirmDays))
                .toList();
    }

    String resolveGroupBuyTitle(OrderDeliveryGroup group) {
        // 공구명은 계약의 불변 원본에서(30 설계서 0-3) — 백필 행은 공구가 없어 null.
        return group.getGroupBuy() == null || group.getGroupBuy().getContract() == null ? null
                : group.getGroupBuy().getContract().getTitle();
    }
}
