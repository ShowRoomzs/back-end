package showroomz.domain.post.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.post.entity.Post;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {

    /** 포스트 + 등록 상품 목록(상품 엔티티 포함) 한 번에 조회 (N+1 방지) */
    @Query("SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.postProducts pp " +
           "LEFT JOIN FETCH pp.product " +
           "WHERE p.id = :postId")
    Optional<Post> findByIdWithPostProductsAndProducts(@Param("postId") Long postId);
}
