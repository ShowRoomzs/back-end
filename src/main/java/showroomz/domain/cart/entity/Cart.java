package showroomz.domain.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.ProductVariant;

/**
 * 장바구니 한 줄 — 「무엇을」뿐 아니라 <b>「어느 공구에서」</b> 담았는지를 기억한다(가격 계획서 3-1).
 *
 * <p>소비자 가격은 공구 계약의 스냅샷에서 나온다. 같은 옵션도 공구가 다르면 가격·리워드 귀속이 다른 두 건이라
 * 유니크 키에 공구가 들어간다. {@link #groupBuy}가 null인 행은 귀속을 정하지 못한 옛 행뿐이고 목록에서 마감으로 보인다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "cart",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cart_user_variant_group_buy",
                        columnNames = {"user_id", "variant_id", "group_buy_id"}
                )
        }
)
public class Cart extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    /** 담은 공구. 담기 이후 바뀌지 않는다 — 옵션 변경은 같은 공구 안에서만이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_id")
    private GroupBuy groupBuy;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    public Cart(Users user, ProductVariant variant, GroupBuy groupBuy, Integer quantity) {
        this.user = user;
        this.variant = variant;
        this.groupBuy = groupBuy;
        this.quantity = quantity;
    }

    public void updateQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public void updateVariant(ProductVariant variant) {
        this.variant = variant;
    }

    public Long getGroupBuyId() {
        return groupBuy == null ? null : groupBuy.getId();
    }
}
