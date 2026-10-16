package showroomz.domain.performance.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 공구 원가(48 파트너 설계서 6-1) — 브랜드 × 공구 1행. 산출값(소계 · 총 원가 · 수익)은 열이 없다 — 조회 시 계산한다(0-10).
 *
 * <p>저장은 <b>전체 교체</b>다 — 부분 수정 API 가 없다. 계약 상품 목록은 계약 체결로 고정이므로 항목 행은 처음 저장 때 생기고 이후
 * 값만 바뀐다.
 */
@Entity
@Table(name = "group_buy_cost")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupBuyCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_buy_cost_id")
    private Long id;

    @Column(name = "group_buy_id", nullable = false, unique = true, updatable = false)
    private Long groupBuyId;

    @Column(name = "market_id", nullable = false, updatable = false)
    private Long marketId;

    @Column(name = "packaging_cost")
    private Long packagingCost;

    @Column(name = "shipping_cost")
    private Long shippingCost;

    @Column(name = "return_disposal_cost")
    private Long returnDisposalCost;

    @Column(name = "etc_cost")
    private Long etcCost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @OneToMany(mappedBy = "cost", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupBuyCostItem> items = new ArrayList<>();

    public static GroupBuyCost create(Long groupBuyId, Long marketId, Long sellerId, LocalDateTime now) {
        GroupBuyCost cost = new GroupBuyCost();
        cost.groupBuyId = groupBuyId;
        cost.marketId = marketId;
        cost.createdAt = now;
        cost.updatedAt = now;
        cost.updatedBy = sellerId;
        return cost;
    }

    /** 전체 교체 — 공구 전체 비용 4종 + 계약 상품별 개당 원가. 빠진 항목은 지운다(호출자가 계약 항목과 일치를 먼저 본다). */
    public void replace(Long packagingCost, Long shippingCost, Long returnDisposalCost, Long etcCost,
                        Map<Long, Long> unitCostByContractItem, Long sellerId, LocalDateTime now) {
        this.packagingCost = packagingCost;
        this.shippingCost = shippingCost;
        this.returnDisposalCost = returnDisposalCost;
        this.etcCost = etcCost;
        this.updatedAt = now;
        this.updatedBy = sellerId;
        items.removeIf(item -> !unitCostByContractItem.containsKey(item.getContractItemId()));
        unitCostByContractItem.forEach((contractItemId, unitCost) -> itemOf(contractItemId).ifPresentOrElse(
                item -> item.changeUnitCost(unitCost),
                () -> items.add(GroupBuyCostItem.of(this, contractItemId, unitCost))));
    }

    public Optional<GroupBuyCostItem> itemOf(Long contractItemId) {
        return items.stream().filter(item -> item.getContractItemId().equals(contractItemId)).findFirst();
    }
}
