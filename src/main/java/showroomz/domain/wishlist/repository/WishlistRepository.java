package showroomz.domain.wishlist.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.wishlist.entitiy.Wishlist;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Long>, WishlistRepositoryCustom {
    boolean existsByUserAndProduct(Users user, Product product);

    Optional<Wishlist> findByUserAndProduct(Users user, Product product);

    @Modifying
    void deleteByUserAndProduct(Users user, Product product);

    long countByProduct(Product product);

    @Query("SELECT w.product.productId FROM Wishlist w WHERE w.user.id = :userId AND w.product.productId IN :productIds")
    Set<Long> findProductIdsWishedByUserAndProductIdIn(
            @Param("userId") Long userId,
            @Param("productIds") List<Long> productIds
    );

    /**
     * 상품별 Wishlist 수 일괄 조회 (Batch Fetching)
     * @return List of [productId, count] - 찜이 없는 상품은 결과에 포함되지 않음 (0으로 처리)
     */
    @Query("SELECT w.product.productId, COUNT(w) FROM Wishlist w WHERE w.product.productId IN :productIds GROUP BY w.product.productId")
    List<Object[]> countWishlistByProductIds(@Param("productIds") List<Long> productIds);
}
