package showroomz.domain.wishlist.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.wishlist.entitiy.Wishlist;

import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    boolean existsByUserAndProduct(Users user, Product product);

    Optional<Wishlist> findByUserAndProduct(Users user, Product product);

    @Modifying
    void deleteByUserAndProduct(Users user, Product product);

    long countByProduct(Product product);

    @Query("SELECT w FROM Wishlist w " +
           "JOIN FETCH w.product p " +
           "LEFT JOIN FETCH p.productImages " +
           "LEFT JOIN FETCH p.category " +
           "LEFT JOIN FETCH p.market " +
           "WHERE w.user.id = :userId " +
           "ORDER BY w.createdAt DESC")
    Page<Wishlist> findByUserWithProduct(@Param("userId") Long userId, Pageable pageable);
}
