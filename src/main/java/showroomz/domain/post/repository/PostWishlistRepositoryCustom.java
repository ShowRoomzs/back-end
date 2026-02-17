package showroomz.domain.post.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.post.entity.Post;

public interface PostWishlistRepositoryCustom {
    Page<Post> findWishlistedPostsByUserId(Long userId, Pageable pageable);
}
