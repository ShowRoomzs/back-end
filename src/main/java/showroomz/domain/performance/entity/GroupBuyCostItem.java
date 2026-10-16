package showroomz.domain.performance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 계약 상품 1개의 개당 원가(48 파트너 설계서 6-1) — 미입력은 {@code null}. */
@Entity
@Table(name = "group_buy_cost_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupBuyCostItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_buy_cost_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_buy_cost_id", nullable = false, updatable = false)
    private GroupBuyCost cost;

    @Column(name = "contract_item_id", nullable = false, updatable = false)
    private Long contractItemId;

    @Column(name = "unit_cost")
    private Long unitCost;

    static GroupBuyCostItem of(GroupBuyCost cost, Long contractItemId, Long unitCost) {
        GroupBuyCostItem item = new GroupBuyCostItem();
        item.cost = cost;
        item.contractItemId = contractItemId;
        item.unitCost = unitCost;
        return item;
    }

    void changeUnitCost(Long unitCost) {
        this.unitCost = unitCost;
    }
}
