package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductGroupBuyStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 상품의 공구 상태 동기화(설계서 1-11). {@code product.group_buy_status}를 처음으로 쓰는 곳이다.
 *
 * <pre>
 * product.groupBuyStatus = 이 상품을 담은 활성 공구 중 가장 앞선 단계
 *   IN_PROGRESS   ← IN_PROGRESS 또는 SUSPENSION_SCHEDULED(아직 팔린다)
 *   READY         ← READY
 *   PREPARING     ← PREPARING
 *   NOT_CONNECTED ← 활성 공구 없음
 * </pre>
 *
 * <p><b>단일 값이 아니라 재계산인 이유</b> — 계약 검증에 「같은 상품 · 기간 겹침」이 없어 한 상품이 두 공구에
 * 걸릴 수 있다. 한 공구가 끝날 때 무조건 NOT_CONNECTED로 내리면 다른 공구가 진행 중인 상품이 판매 불가가 된다.
 *
 * <p>모든 상태 전이와 <b>같은 트랜잭션</b>에서 호출한다({@code MANDATORY}).
 */
@Component
@RequiredArgsConstructor
public class ProductGroupBuyStatusSynchronizer {

    private final GroupBuyRepository groupBuyRepository;
    private final ProductRepository productRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void resync(GroupBuy groupBuy) {
        List<Long> productIds = groupBuy.getContract().getItems().stream()
                .map(ContractItem::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        resync(productIds);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void resync(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }
        // 전이 직후의 status를 읽어야 한다 — 조건부 UPDATE와 엔티티 변경이 아직 플러시 전일 수 있다.
        groupBuyRepository.flush();

        Map<Long, ProductGroupBuyStatus> resolved = new HashMap<>();
        for (Object[] row : groupBuyRepository.findActiveStatusesByProductIds(productIds, GroupBuyStatus.ACTIVE)) {
            Long productId = (Long) row[0];
            ProductGroupBuyStatus stage = toProductStatus((GroupBuyStatus) row[1]);
            resolved.merge(productId, stage, ProductGroupBuyStatusSynchronizer::moreAdvanced);
        }

        Map<ProductGroupBuyStatus, List<Long>> byStatus = new EnumMap<>(ProductGroupBuyStatus.class);
        for (Long productId : new LinkedHashSet<>(productIds)) {
            ProductGroupBuyStatus status = resolved.getOrDefault(productId, ProductGroupBuyStatus.NOT_CONNECTED);
            byStatus.computeIfAbsent(status, key -> new ArrayList<>()).add(productId);
        }
        byStatus.forEach((status, ids) -> productRepository.updateGroupBuyStatus(ids, status));
    }

    private static ProductGroupBuyStatus toProductStatus(GroupBuyStatus status) {
        return switch (status) {
            case IN_PROGRESS, SUSPENSION_SCHEDULED -> ProductGroupBuyStatus.IN_PROGRESS;
            case READY -> ProductGroupBuyStatus.READY;
            case PREPARING -> ProductGroupBuyStatus.PREPARING;
            default -> ProductGroupBuyStatus.NOT_CONNECTED;
        };
    }

    private static final List<ProductGroupBuyStatus> STAGE_ORDER = List.of(
            ProductGroupBuyStatus.NOT_CONNECTED,
            ProductGroupBuyStatus.PREPARING,
            ProductGroupBuyStatus.READY,
            ProductGroupBuyStatus.IN_PROGRESS);

    private static ProductGroupBuyStatus moreAdvanced(ProductGroupBuyStatus a, ProductGroupBuyStatus b) {
        return STAGE_ORDER.indexOf(a) >= STAGE_ORDER.indexOf(b) ? a : b;
    }
}
