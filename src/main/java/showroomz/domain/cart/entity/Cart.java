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
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.ProductVariant;

/**
 * 장바구니 항목 — 상품+옵션(SKU) 단위.
 * 같은 SKU라도 공구 게시물(쇼룸)이 다르면 별개 항목이며,
 * 공구가는 담을 때 스냅샷으로 저장(공구 기간 내 불변 — MVP 범위 §3-E).
 * 공구 종료 시 그 공구 참조 항목은 자동 삭제, 품절은 결제 시 재확인.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "cart",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "cart_uk",
                        columnNames = {"user_id", "variant_id", "group_buy_post_id"}
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

    /** 어느 공구 게시물(쇼룸+공구)을 통해 담았는지 — 공구가 적용 근거 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_post_id")
    private GroupBuyPost groupBuyPost;

    /** 담을 때의 공구가 스냅샷 — 공구 기간 내 불변 */
    @Column(name = "group_buy_price")
    private Integer groupBuyPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    public Cart(Users user, ProductVariant variant, Integer quantity) {
        this.user = user;
        this.variant = variant;
        this.quantity = quantity;
    }

    public Cart(Users user, ProductVariant variant, GroupBuyPost groupBuyPost, Integer groupBuyPrice, Integer quantity) {
        this.user = user;
        this.variant = variant;
        this.groupBuyPost = groupBuyPost;
        this.groupBuyPrice = groupBuyPrice;
        this.quantity = quantity;
    }

    public void updateQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public void updateVariant(ProductVariant variant) {
        this.variant = variant;
    }
}
