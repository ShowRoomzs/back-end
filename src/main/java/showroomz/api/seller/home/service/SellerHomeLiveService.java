package showroomz.api.seller.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.home.dto.SellerHomeDto;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.repository.ContractItemRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.order.service.OrderGroupBuyLiveReader;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 파트너 홈 진행 중 공구 실시간(1017 설계서 4-2) — FE 가 1분마다 부른다. 서버 캐시 없음(마켓 단위 · 공구당 쿼리 4개).
 * 금액 · 주문은 <b>결제 기준 · 취소 전 포함</b>({@link OrderGroupBuyLiveReader}), 방문은 그 인플루언서 쇼룸의 사람 수다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerHomeLiveService {

    private final GroupBuyRepository groupBuyRepository;
    private final OrderGroupBuyLiveReader liveReader;
    private final ShowroomVisitRepository visitRepository;
    private final ContractItemRepository contractItemRepository;
    private final ProductVariantRepository variantRepository;

    public SellerHomeDto.Live read(Long marketId) {
        LocalDateTime now = LocalDateTime.now();
        List<GroupBuy> groupBuys = groupBuyRepository.findByMarketAndStatuses(marketId, GroupBuyStatus.SELLING);
        if (groupBuys.isEmpty()) {
            return new SellerHomeDto.Live(now, List.of());
        }
        Map<Long, List<ContractItem>> itemsByContract = new LinkedHashMap<>();
        for (ContractItem item : contractItemRepository.findWithProductByContractIds(
                groupBuys.stream().map(g -> g.getContract().getId()).toList())) {
            itemsByContract.computeIfAbsent(item.getContract().getId(), id -> new ArrayList<>()).add(item);
        }
        Map<Long, Long> stockByProduct = stockByProduct(itemsByContract);

        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        List<SellerHomeDto.LiveItem> items = new ArrayList<>();
        for (GroupBuy groupBuy : groupBuys) {
            Long creatorId = groupBuy.getCreator().getId();
            OrderGroupBuyLiveReader.LiveSales sales = liveReader.read(groupBuy.getId(), groupBuy.getStartAt(), now);
            List<ContractItem> contractItems = itemsByContract.getOrDefault(groupBuy.getContract().getId(), List.of());
            items.add(new SellerHomeDto.LiveItem(
                    groupBuy.getId(),
                    groupBuy.getContract().getTitle(),
                    creatorId,
                    groupBuy.getCreator().getShowroomName(),
                    groupBuy.getStartAt(),
                    groupBuy.getEndAt(),
                    progressRatio(groupBuy.getStartAt(), groupBuy.getEndAt(), now),
                    visitRepository.countVisitors(creatorId, todayStart.isAfter(groupBuy.getStartAt())
                            ? todayStart : groupBuy.getStartAt(), now),
                    visitRepository.countVisitors(creatorId, groupBuy.getStartAt(), now),
                    sales.ordersToday(),
                    sales.ordersYesterdaySameTime(),
                    sales.paidToday(),
                    sales.paidCumulative(),
                    sales.ordersCumulative(),
                    sales.soldQuantity(),
                    stock(contractItems, stockByProduct),
                    sales.hourly().stream()
                            .map(h -> new SellerHomeDto.HourBucket(h.hour(), h.orders(), h.amount(), h.collecting()))
                            .toList()));
        }
        return new SellerHomeDto.Live(now, items);
    }

    private Map<Long, Long> stockByProduct(Map<Long, List<ContractItem>> itemsByContract) {
        List<Long> productIds = itemsByContract.values().stream()
                .flatMap(List::stream)
                .map(item -> item.getProduct() == null ? null : item.getProduct().getProductId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Long> stock = new HashMap<>();
        if (productIds.isEmpty()) {
            return stock;
        }
        for (Object[] row : variantRepository.sumStockByProductIds(productIds)) {
            stock.put((Long) row[0], row[1] instanceof Number n ? n.longValue() : 0L);
        }
        return stock;
    }

    /** 재고 = 공구 상품 옵션 재고 합 · 최소 물량 = 계약 항목 최소 물량 합(하나라도 모르면 null). */
    private static SellerHomeDto.Stock stock(List<ContractItem> items, Map<Long, Long> stockByProduct) {
        long remaining = 0;
        Integer minQuantity = items.isEmpty() ? null : 0;
        Long firstProductId = null;
        for (ContractItem item : items) {
            Long productId = item.getProduct() == null ? null : item.getProduct().getProductId();
            if (productId != null) {
                remaining += stockByProduct.getOrDefault(productId, 0L);
                if (firstProductId == null) {
                    firstProductId = productId;
                }
            }
            Integer itemMin = item.getMinQuantity();
            minQuantity = minQuantity == null || itemMin == null ? null : minQuantity + itemMin;
        }
        return new SellerHomeDto.Stock(remaining, minQuantity, firstProductId);
    }

    static double progressRatio(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime now) {
        long total = Duration.between(startAt, endAt).toSeconds();
        if (total <= 0) {
            return 1.0;
        }
        long elapsed = Duration.between(startAt, now).toSeconds();
        return Math.max(0.0, Math.min(1.0, (double) elapsed / total));
    }
}
