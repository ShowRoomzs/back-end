package showroomz.domain.cart.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.ProductVariant;

import java.util.Optional;
import java.util.List;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    /** 병합 판정 — 같은 옵션이라도 공구가 다르면 다른 줄이다(가격·리워드 귀속이 다르다). */
    Optional<Cart> findByUserAndVariantAndGroupBuy(Users user, ProductVariant variant, GroupBuy groupBuy);

    Optional<Cart> findByIdAndUser(Long id, Users user);

    @EntityGraph(attributePaths = {
            "variant",
            "variant.options",
            "variant.options.optionGroup",
            "variant.product",
            "variant.product.market",
            "groupBuy"
    })
    Page<Cart> findByUser(Users user, Pageable pageable);

    @EntityGraph(attributePaths = {
            "variant",
            "variant.options",
            "variant.options.optionGroup",
            "variant.product",
            "variant.product.market",
            "groupBuy"
    })
    List<Cart> findAllByUser(Users user);

    long countByUser(Users user);

    void deleteByUser(Users user);

    /**
     * 로그인한 유저 소유의 장바구니 ID 목록 조회 (삭제 전 소유권 검증용)
     */
    List<Cart> findByIdInAndUser(List<Long> ids, Users user);
}
