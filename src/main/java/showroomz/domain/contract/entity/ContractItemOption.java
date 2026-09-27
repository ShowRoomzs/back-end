package showroomz.domain.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import showroomz.domain.product.entity.ProductVariant;

/**
 * 계약 상품 옵션 항목 — 옵션별 최소 물량(옵션 계획서 2-2).
 *
 * <p>공구가·리워드율은 상품 단위({@link ContractItem})에 있고 옵션은 받지 않는다. 옵션이 들고 있는 것은
 * 「어느 옵션을 몇 개 확보하기로 했나」와 옵션 정가 스냅샷뿐이다.
 *
 * <p>옵션 판매가·옵션가는 저장하지 않는다 — 정가 스냅샷 둘과 공구가의 파생값이다. 저장하면 스냅샷을
 * 다시 맞출 때(검토 요청·재작성) 같이 고쳐야 하고, 하나를 빠뜨리는 날이 온다.
 *
 * <p>{@code variant}가 null이면 상품 관리에서 그 옵션이 지워진 것이다. 계약서에는 스냅샷이 남는다.
 */
@Entity
@Table(name = "contract_item_option",
        uniqueConstraints = @UniqueConstraint(name = "uk_contract_item_option_variant",
                columnNames = {"contract_item_id", "variant_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ContractItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_item_option_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_item_id", nullable = false)
    private ContractItem contractItem;

    /** 옵션이 지워지면 NULL — 종결된 계약도 스냅샷으로 계약서를 그대로 보여줘야 한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private ProductVariant variant;

    /** 스냅샷 — 「단품」·「2개 세트」. 옵션 없는 상품은 null. */
    @Column(name = "variant_name", length = 255)
    private String variantName;

    /** 스냅샷 — 계약 시점의 옵션 정가. 옵션가 = 이 값 − 상품 정가 스냅샷. */
    @Column(name = "regular_price")
    private Integer regularPrice;

    /** 옵션별 최소 물량. 작성중은 비어 있을 수 있고 검토 요청 때 필수다. */
    @Column(name = "min_quantity")
    private Integer minQuantity;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /** 현재 옵션 값으로 스냅샷을 뜬다. 수량은 호출자가 정한다. */
    public static ContractItemOption snapshotOf(ProductVariant variant, Integer minQuantity) {
        return ContractItemOption.builder()
                .variant(variant)
                .variantName(variant.getName())
                .regularPrice(variant.getRegularPrice())
                .minQuantity(minQuantity)
                .sortOrder(0)
                .build();
    }

    void attachTo(ContractItem contractItem, int sortOrder) {
        this.contractItem = contractItem;
        this.sortOrder = sortOrder;
    }

    /** 검토 요청·재작성 시점에 옵션명·옵션 정가를 현재 값으로 다시 맞춘다. 옵션이 지워졌으면 그대로 둔다. */
    public void refreshSnapshot() {
        if (variant != null) {
            this.variantName = variant.getName();
            this.regularPrice = variant.getRegularPrice();
        }
    }

    public Long getVariantId() {
        return variant == null ? null : variant.getVariantId();
    }

    /**
     * 옵션가 = 옵션 정가 − 상품 정가(스냅샷끼리). 「2개 세트」 60,000 − 상품 32,000 = 28,000.
     * 둘 중 하나라도 비어 있으면 계산하지 않는다.
     */
    public Integer optionExtraPrice() {
        Integer productRegularPrice = contractItem == null ? null : contractItem.getRegularPrice();
        if (regularPrice == null || productRegularPrice == null) {
            return null;
        }
        return regularPrice - productRegularPrice;
    }

    /** 옵션 판매가 = 공구가 + 옵션가. 소비자가 이 옵션을 살 때의 가격이다. 공구가가 없으면 null. */
    public Integer salePrice() {
        Integer extra = optionExtraPrice();
        Integer groupBuyPrice = contractItem == null ? null : contractItem.getGroupBuyPrice();
        if (extra == null || groupBuyPrice == null) {
            return null;
        }
        return groupBuyPrice + extra;
    }
}
