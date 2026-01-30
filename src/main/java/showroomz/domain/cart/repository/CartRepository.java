package showroomz.domain.cart.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.ProductVariant;

import java.util.Optional;
import java.util.List;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserAndVariant(Users user, ProductVariant variant);

    Optional<Cart> findByIdAndUser(Long id, Users user);

    @EntityGraph(attributePaths = {
            "variant",
            "variant.options",
            "variant.options.optionGroup",
            "variant.product",
            "variant.product.market"
    })
    Page<Cart> findByUser(Users user, Pageable pageable);

    @EntityGraph(attributePaths = {
            "variant",
            "variant.options",
            "variant.options.optionGroup",
            "variant.product",
            "variant.product.market"
    })
    List<Cart> findAllByUser(Users user);

    long countByUser(Users user);

    void deleteByUser(Users user);
}
