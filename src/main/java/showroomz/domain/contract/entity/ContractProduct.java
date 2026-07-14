package showroomz.domain.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.product.entity.Product;

import java.math.BigDecimal;

/**
 * 계약 상품 항목 — 공구가·리워드율의 원천(출생지).
 * "이 상품을, 이 공구가에, 이 리워드율로"를 담는다.
 * 상품에 박지 않고 여기에 저장 — 같은 상품도 쇼룸/계약마다 공구가·리워드율이 다를 수 있다.
 * 공구·게시물·장바구니·정산이 이 값을 참조한다(계약은 체결 후 불변이라 정합).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "contract_product")
public class ContractProduct extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 이 계약에서의 판매가(공구가) — 상품 정가와 분리 */
    @Column(name = "group_buy_price", nullable = false)
    private Integer groupBuyPrice;

    /** 인플루언서 리워드율(%) — 상품별 차등 가능. 정산은 항목별 리워드율로 계산 */
    @Column(name = "reward_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal rewardRate;

    public ContractProduct(Contract contract, Product product, Integer groupBuyPrice, BigDecimal rewardRate) {
        this.contract = contract;
        this.product = product;
        this.groupBuyPrice = groupBuyPrice;
        this.rewardRate = rewardRate;
    }
}
