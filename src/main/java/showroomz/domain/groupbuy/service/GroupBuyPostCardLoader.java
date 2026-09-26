package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.repository.ContractItemRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.type.GroupBuyProductState;
import showroomz.domain.groupbuy.type.GroupBuySaleState;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.global.utils.DiscountRate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 소비자 앱의 공구 블록을 <b>페이지 단위로</b> 읽는다(공구 게시물 설계 6-2).
 *
 * <p>페이지 크기와 무관하게 쿼리 3회다 — ① 공구 게시물 + 공구 + 브랜드 ② 계약 상품 + 상품 ③ 상품별 재고 있는 옵션 수.
 * 피드가 사진·좋아요·팔로우를 모아 읽는 것과 같은 방식이다. 상세(C5)는 게시물 1건으로 같은 로더를 쓴다.
 *
 * <p>상품·가격은 게시물에 저장하지 않고 계약에서 읽는다(30 설계 1-8) — 상품명·정가·공구가는 계약 스냅샷이고,
 * 썸네일·품절·C7 진입 가능 여부만 현재 상품에서 읽는다.
 */
@Component
@RequiredArgsConstructor
public class GroupBuyPostCardLoader {

    private final GroupBuyPostRepository groupBuyPostRepository;
    private final ContractItemRepository contractItemRepository;
    private final ProductVariantRepository productVariantRepository;

    /**
     * @param postIds 공구 게시물 id — 일반 게시물 id가 섞여도 결과에 없을 뿐이다
     * @return 게시물 id → 공구 블록
     */
    @Transactional(readOnly = true)
    public Map<Long, GroupBuyPostCard> load(Collection<Long> postIds, LocalDateTime now) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        List<GroupBuyPost> posts = groupBuyPostRepository.findCardsByPostIds(postIds);
        if (posts.isEmpty()) {
            return Map.of();
        }

        // 계약은 공구의 지연 참조다 — 프록시에서 id만 꺼내므로 초기화되지 않는다
        List<Long> contractIds = posts.stream()
                .map(post -> post.getGroupBuy().getContract().getId())
                .distinct()
                .toList();
        Map<Long, List<ContractItem>> itemsByContract = contractItemRepository.findWithProductByContractIds(contractIds)
                .stream()
                .collect(Collectors.groupingBy(item -> item.getContract().getId()));
        Map<Long, Long> inStockVariants = inStockVariantCounts(itemsByContract.values());

        Map<Long, GroupBuyPostCard> cards = new HashMap<>();
        for (GroupBuyPost post : posts) {
            GroupBuy groupBuy = post.getGroupBuy();
            List<ContractItem> items = itemsByContract.getOrDefault(groupBuy.getContract().getId(), List.of());
            cards.put(post.getPostId(), toCard(post, groupBuy, items, inStockVariants, now));
        }
        return cards;
    }

    private static GroupBuyPostCard toCard(GroupBuyPost post, GroupBuy groupBuy, List<ContractItem> items,
                                           Map<Long, Long> inStockVariants, LocalDateTime now) {
        List<Boolean> soldOut = items.stream().map(item -> isSoldOut(item.getProduct(), inStockVariants)).toList();
        GroupBuySaleState saleState = GroupBuySaleState.of(groupBuy, soldOut, now);

        List<GroupBuyPostCard.Product> products = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            products.add(toProduct(items.get(i), soldOut.get(i), saleState));
        }

        return new GroupBuyPostCard(
                groupBuy.getId(),
                post.getTitle(),
                saleState,
                saleState.isClosed() ? null : dDay(groupBuy.getEndAt(), now),
                groupBuy.getEndAt(),
                GroupBuyDisclosure.text(groupBuy.getMarket().getMarketName()),
                products);
    }

    private static GroupBuyPostCard.Product toProduct(ContractItem item, boolean soldOut, GroupBuySaleState saleState) {
        Product product = item.getProduct();
        GroupBuyProductState state = saleState.isClosed() ? GroupBuyProductState.CLOSED
                : soldOut ? GroupBuyProductState.SOLD_OUT
                : GroupBuyProductState.ON_SALE;
        return new GroupBuyPostCard.Product(
                item.getProductId(),
                item.getProductName(),
                product == null ? null : product.getThumbnailUrl(),
                item.getRegularPrice(),
                item.getGroupBuyPrice(),
                DiscountRate.of(item.getRegularPrice(), item.getGroupBuyPrice()),
                state,
                !saleState.isClosed() && isDetailVisible(product));
    }

    /** D-day — KST 날짜 차이. 앱 시계를 믿지 않도록 서버가 계산한다(4-4). 마감 당일은 0이다. */
    static int dDay(LocalDateTime endAt, LocalDateTime now) {
        return (int) ChronoUnit.DAYS.between(now.toLocalDate(), endAt.toLocalDate());
    }

    /** C7 {@code status.isOutOfStock}과 같은 식 — 강제 품절 ∨ 재고 있는 옵션 없음. 상품이 없는 행은 살 수 없다. */
    private static boolean isSoldOut(Product product, Map<Long, Long> inStockVariants) {
        if (product == null) {
            return true;
        }
        return Boolean.TRUE.equals(product.getIsOutOfStockForced())
                || inStockVariants.getOrDefault(product.getProductId(), 0L) == 0L;
    }

    /** C7 진입 조건(진열중 ∧ 공구 연결)과 같다 — 막힐 행을 탭 가능으로 내리지 않는다(4-6). */
    private static boolean isDetailVisible(Product product) {
        return product != null
                && product.getDisplayStatus() != null && product.getDisplayStatus().isVisible()
                && product.getGroupBuyStatus() != null && product.getGroupBuyStatus().isConnected();
    }

    private Map<Long, Long> inStockVariantCounts(Collection<List<ContractItem>> itemGroups) {
        List<Long> productIds = itemGroups.stream()
                .flatMap(List::stream)
                .map(ContractItem::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : productVariantRepository.countInStockVariantsByProductIds(productIds)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }
}
