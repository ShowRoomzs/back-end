package showroomz.domain.wishlist.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.wishlist.entitiy.Wishlist;

import java.util.List;

public interface WishlistRepositoryCustom {
    Page<Wishlist> findByUserWithProduct(Long userId, List<Long> categoryIds, Pageable pageable);
}
