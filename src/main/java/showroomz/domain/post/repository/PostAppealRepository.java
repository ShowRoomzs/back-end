package showroomz.domain.post.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.post.entity.PostAppeal;
import showroomz.domain.post.type.PostAppealStatus;

import java.util.Optional;

public interface PostAppealRepository extends JpaRepository<PostAppeal, Long> {

    /** 게시물당 1회이므로 게시물 기준 조회가 곧 단건 조회다 (§24-5) */
    Optional<PostAppeal> findByPost_Id(Long postId);

    boolean existsByPost_Id(Long postId);

    Page<PostAppeal> findByStatusOrderBySubmittedAtAsc(PostAppealStatus status, Pageable pageable);

    Page<PostAppeal> findAllByOrderBySubmittedAtDesc(Pageable pageable);

    /** 파기 배치 — 게시물 물리 삭제 전에 자식부터 지운다 */
    @Modifying
    @Query("DELETE FROM PostAppeal a WHERE a.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);
}
