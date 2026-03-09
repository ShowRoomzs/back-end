package showroomz.domain.post.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.post.entity.Post;

import java.util.List;

public interface PostRepositoryCustom {
    Page<Post> findByMarketId(Long marketId, Pageable pageable);
    Page<Post> findDisplayedPosts(Pageable pageable);
    Page<Post> findDisplayedPostsByMarketId(Long marketId, Pageable pageable);
    Page<Post> findDisplayedPostsByMarketIds(List<Long> marketIds, Pageable pageable);
}
