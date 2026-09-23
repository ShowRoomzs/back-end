package showroomz.domain.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.product.entity.Product;

import java.math.BigDecimal;

/**
 * 계약 상품 항목.
 *
 * <p>product_id만 들고 있으면 안 된다 — 종결된 계약(B6·B7·B8)도 상품명·정가를 그대로 보여줘야 하고,
 * 그 사이 브랜드가 상품명을 바꾸거나 정가를 내렸을 수 있다. 계약서는 서명 시점의 사실을
 * 보존해야 하므로 상품명·정가를 복사해 저장한다(설계서 0-5).
 *
 * <p>예상 리워드는 저장하지 않는다 — 공구가 × 리워드율의 순수 파생값이고, 저장하면 두 소스가
 * 어긋난다. 응답에서 {@code RewardCalculator}로 계산해 내린다(설계서 1-5).
 */
@Entity
@Table(name = "contract_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ContractItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    /** 미선택 행도 저장할 수 있다(임시저장). 공구 생성·정산 귀속용 참조로만 남긴다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /** 스냅샷. */
    @Column(name = "product_name", length = 255)
    private String productName;

    /** 스냅샷 — 화면의 「정가」. */
    @Column(name = "regular_price")
    private Integer regularPrice;

    @Column(name = "group_buy_price")
    private Integer groupBuyPrice;

    /** 정산이 이 값을 그대로 쓴다(§25-5-3). DOUBLE 금지 — 15.0이 14.999999로 읽히는 날이 온다. */
    @Column(name = "reward_rate", precision = 4, scale = 1)
    private BigDecimal rewardRate;

    @Column(name = "min_quantity")
    private Integer minQuantity;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    void attachTo(Contract contract, int sortOrder) {
        this.contract = contract;
        this.sortOrder = sortOrder;
    }

    /** 상품이 바뀐 행은 공구가·리워드율·최소 물량을 초기화한다(§25-5-3 · 설계서 4-2). */
    public void resetNegotiatedValues() {
        this.groupBuyPrice = null;
        this.rewardRate = null;
        this.minQuantity = null;
    }

    /**
     * 검토 요청·재작성 시점에 현재 상품 값으로 스냅샷을 다시 맞춘다.
     * 옛 정가를 들고 있으면 「공구가 > 정가」(H1)가 통과해버린다(설계서 4-3).
     */
    public void refreshSnapshot(String productName, Integer regularPrice) {
        this.productName = productName;
        this.regularPrice = regularPrice;
    }

    public Long getProductId() {
        return product == null ? null : product.getProductId();
    }
}
