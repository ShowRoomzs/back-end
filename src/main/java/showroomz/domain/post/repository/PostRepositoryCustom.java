package showroomz.domain.post.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.post.entity.Post;

import java.util.List;

public interface PostRepositoryCustom {
    Page<Post> findDisplayedPosts(Pageable pageable);
    Page<Post> findDisplayedPostsByCreatorId(Long creatorId, Pageable pageable);
    Page<Post> findDisplayedPostsByCreatorIds(List<Long> creatorIds, Pageable pageable);
}
