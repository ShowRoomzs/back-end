package showroomz.domain.wishlist.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.wishlist.entitiy.Wishlist;

import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Long>, WishlistRepositoryCustom {
    boolean existsByUserAndProduct(Users user, Product product);

    Optional<Wishlist> findByUserAndProduct(Users user, Product product);

    @Modifying
    void deleteByUserAndProduct(Users user, Product product);

    long countByProduct(Product product);
}
