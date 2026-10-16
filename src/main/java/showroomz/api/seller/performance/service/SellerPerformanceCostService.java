package showroomz.api.seller.performance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.groupbuy.service.GroupBuyAccessGuard;
import showroomz.api.seller.performance.dto.SellerPerformanceDto;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.repository.ContractItemRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.performance.entity.GroupBuyCost;
import showroomz.domain.performance.repository.GroupBuyCostRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 원가 · 수익 저장(48 파트너 설계서 4절) — 성과 화면의 <b>유일한 쓰기</b>. 브랜드 전용 · 전체 교체 · 집계 중(정산 전)에는 잠근다.
 * 저장 뒤 응답은 상세 전체다 — 수익 · 수익률은 저장하지 않고 조회 때 계산한다(0-10).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerPerformanceCostService {

    /** 금액 상한 — 입력 실수(자릿수 오타)를 막는 경계. 원가 해석은 하지 않는다. */
    static final long MAX_AMOUNT = 1_000_000_000L;

    private final GroupBuyAccessGuard accessGuard;
    private final SellerPerformanceQueryService queryService;
    private final SettlementRepository settlementRepository;
    private final ContractItemRepository contractItemRepository;
    private final GroupBuyCostRepository costRepository;

    @Transactional
    public SellerPerformanceDto.Detail save(String sellerEmail, Long groupBuyId, SellerPerformanceDto.CostRequest request) {
        GroupBuyAccessGuard.SellerScope scope = accessGuard.resolve(sellerEmail);
        GroupBuy groupBuy = queryService.loadListed(groupBuyId, scope.market());
        if (settlementRepository.findByGroupBuyId(groupBuyId).isEmpty()) {
            throw new BusinessException(ErrorCode.PERFORMANCE_COST_LOCKED);
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "요청 본문이 비어 있습니다.");
        }
        Map<Long, Long> unitCosts = unitCosts(groupBuy, request.items());
        validate(request.packagingCost(), "부자재비");
        validate(request.shippingCost(), "배송비");
        validate(request.returnDisposalCost(), "반품 폐기 손실");
        validate(request.etcCost(), "기타");

        LocalDateTime now = LocalDateTime.now();
        GroupBuyCost cost = costRepository.findWithItemsByGroupBuyId(groupBuyId)
                .orElseGet(() -> GroupBuyCost.create(groupBuyId, scope.market().getId(), scope.sellerId(), now));
        cost.replace(request.packagingCost(), request.shippingCost(), request.returnDisposalCost(), request.etcCost(),
                unitCosts, scope.sellerId(), now);
        costRepository.saveAndFlush(cost);
        log.info("성과 원가 저장 - groupBuyId: {}, sellerId: {}", groupBuyId, scope.sellerId());
        return queryService.getDetail(sellerEmail, groupBuyId);
    }

    /** 계약 상품 전부 · 정확히 한 번씩 — 빠지거나 남의 항목이면 400. */
    private Map<Long, Long> unitCosts(GroupBuy groupBuy, List<SellerPerformanceDto.CostItemRequest> items) {
        Set<Long> contractItemIds = contractItemRepository.findWithProductByContractIds(
                        List.of(groupBuy.getContract().getId())).stream()
                .map(ContractItem::getId).collect(Collectors.toSet());
        Map<Long, Long> unitCosts = new HashMap<>();
        for (SellerPerformanceDto.CostItemRequest item : items == null ? List.<SellerPerformanceDto.CostItemRequest>of() : items) {
            if (item == null || item.contractItemId() == null || !contractItemIds.contains(item.contractItemId())
                    || unitCosts.containsKey(item.contractItemId())) {
                throw new BusinessException(ErrorCode.PERFORMANCE_COST_ITEM_MISMATCH);
            }
            validate(item.unitCost(), "개당 원가");
            unitCosts.put(item.contractItemId(), item.unitCost());
        }
        if (!unitCosts.keySet().equals(contractItemIds)) {
            throw new BusinessException(ErrorCode.PERFORMANCE_COST_ITEM_MISMATCH);
        }
        return unitCosts;
    }

    private static void validate(Long amount, String label) {
        if (amount != null && (amount < 0 || amount > MAX_AMOUNT)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, label + "은(는) 0원 이상 10억 원 이하로 입력해 주세요.");
        }
    }
}
