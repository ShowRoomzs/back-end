package showroomz.domain.wishlist.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.wishlist.entitiy.Wishlist;

public interface WishlistRepositoryCustom {
    Page<Wishlist> findByUserWithProduct(Long userId, Long categoryId, Pageable pageable);
}
