package showroomz.domain.post.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.post.entity.PostWishlist;

import java.util.List;
import java.util.Optional;

public interface PostWishlistRepository extends JpaRepository<PostWishlist, Long>, PostWishlistRepositoryCustom {
    
    Optional<PostWishlist> findByUserIdAndPostId(Long userId, Long postId);
    
    boolean existsByUserIdAndPostId(Long userId, Long postId);
    
    void deleteByUserIdAndPostId(Long userId, Long postId);
    
    @Query("SELECT pw.post.id FROM PostWishlist pw WHERE pw.user.id = :userId AND pw.post.id IN :postIds")
    List<Long> findWishlistedPostIdsByUserIdAndPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
