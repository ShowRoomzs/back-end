package showroomz.domain.post.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.post.entity.Post;

public interface PostLikeRepositoryCustom {

    /** 소비자가 좋아요한 게시물 목록 — 그 사이 내려간 게시물은 보이지 않는다 */
    Page<Post> findLikedPostsByUserId(Long userId, Pageable pageable);
}
